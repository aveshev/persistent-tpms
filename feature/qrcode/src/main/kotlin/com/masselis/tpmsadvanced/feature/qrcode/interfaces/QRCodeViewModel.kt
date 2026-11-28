package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import android.net.Uri
import androidx.camera.view.CameraController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase.Sight
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Reads a QR code from the camera, a typed sensor ID or pictures, until one is found: [Event.Found].
 * The camera also reads the ID printed next to a code, see [suggestedId].
 * Without [controller], the camera isn't allowed, only the other two are.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
internal class QRCodeViewModel(
    private val qrCodeSensorUseCase: QrCodeSensorUseCase,
    @Assisted controller: CameraController?
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(controller: CameraController?): QRCodeViewModel
    }

    sealed interface Event {
        data object LeaveBecauseCameraUnavailable : Event

        /** None of the pictures picked holds a TPMS code */
        data object NoCodeInPictures : Event

        @JvmInline
        value class Found(val result: QrCodeResult) : Event
    }

    private val channel = Channel<Event>(BUFFERED)
    val eventChannel: ReceiveChannel<Event> = channel

    private val mutableIsUnknownCode = MutableStateFlow(false)

    /** The camera sees a QR code that isn't a TPMS one, scanning goes on */
    val isUnknownCode: StateFlow<Boolean> = mutableIsUnknownCode.asStateFlow()

    private val mutableIsReading = MutableStateFlow(false)

    /** The pictures picked are being read */
    val isReading: StateFlow<Boolean> = mutableIsReading.asStateFlow()

    private val mutableChoices = MutableStateFlow(emptyList<QrCodeResult>())

    /** The pictures picked hold several codes, one is chosen with [choose] */
    val choices: StateFlow<List<QrCodeResult>> = mutableChoices.asStateFlow()

    private val mutableSuggestedId = MutableStateFlow<String?>(null)

    /**
     * The sensor ID printed next to its code, offered once the code itself hasn't been read for
     * [SUGGESTION_DELAY]: a code too small or blurry to be decoded can still be read as text. Only an
     * ID read [MIN_READINGS] times is, the one read the most.
     */
    val suggestedId: StateFlow<String?> = mutableSuggestedId.asStateFlow()

    /** Typed in or chosen from the pictures */
    private val given = Channel<QrCodeResult>(CONFLATED)

    init {
        val sights = controller
            ?.let { qrCodeSensorUseCase.analyse(it) }
            ?.catch { exc ->
                if (exc is CameraAnalyser.CameraUnavailable) channel.send(Event.LeaveBecauseCameraUnavailable)
                else throw exc
            }
            // The codes and the printed IDs, from the same frames: the camera stops once neither is looked for
            ?.shareIn(viewModelScope, SharingStarted.WhileSubscribed())
        val suggesting = sights
            ?.filterIsInstance<Sight.PrintedId>()
            ?.runningFold(emptyMap<String, Int>()) { readings, sight ->
                readings + (sight.id to (readings[sight.id] ?: 0) + 1)
            }
            ?.combine(flow { emit(false); delay(SUGGESTION_DELAY); emit(true) }) { readings, isDue ->
                readings
                    .takeIf { isDue }
                    ?.filterValues { it >= MIN_READINGS }
                    ?.maxByOrNull { it.value }
                    ?.key
            }
            ?.onEach { mutableSuggestedId.value = it }
            ?.launchIn(viewModelScope)
        merge(
            sights
                ?.filterIsInstance<Sight.Code>()
                ?.map { it.result }
                // The camera reports a code at every frame it's in: the flag stays up while it's in view
                ?.transformLatest { result ->
                    mutableIsUnknownCode.value = result == null
                    result
                        ?.also { emit(it) }
                        ?: run {
                            delay(UNKNOWN_CODE_LINGER)
                            mutableIsUnknownCode.value = false
                        }
                }
                ?: emptyFlow(),
            given.receiveAsFlow(),
        )
            // The first code found closes the scan
            .take(1)
            .onEach {
                suggesting?.cancel()
                channel.send(Event.Found(it))
            }
            .launchIn(viewModelScope)
    }

    /** A sensor ID typed in, read like a code holding it */
    fun enter(id: String) {
        qrCodeSensorUseCase
            .decode(id)
            ?.also { given.trySend(it) }
    }

    /** Reads the pictures [uris], the code found in them, or the choice of [choices] when several are */
    fun read(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mutableIsReading.value = true
            qrCodeSensorUseCase
                .read(uris)
                // A code that can't be used isn't worth a choice when another can
                .let { results -> results.filter { it.isUsable }.ifEmpty { results } }
                .also { mutableIsReading.value = false }
                .also { results ->
                    when (results.size) {
                        0 -> channel.send(Event.NoCodeInPictures)
                        1 -> given.send(results.single())
                        else -> mutableChoices.value = results
                    }
                }
        }
    }

    /** [result], one of [choices], or none of them when null */
    fun choose(result: QrCodeResult?) {
        mutableChoices.value = emptyList()
        result?.also { given.trySend(it) }
    }

    private val QrCodeResult.isUsable
        get() = when (this) {
            is QrCodeResult.Sensors, is QrCodeResult.Wicarlink, is QrCodeResult.Shared -> true
            else -> false
        }

    private companion object {
        val UNKNOWN_CODE_LINGER = 2.seconds
        val SUGGESTION_DELAY = 10.seconds
        const val MIN_READINGS = 3
    }
}
