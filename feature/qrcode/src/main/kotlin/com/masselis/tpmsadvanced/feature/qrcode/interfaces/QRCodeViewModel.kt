package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.camera.view.CameraController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.seconds

/** Reads a QR code from the camera, until one is found: [Event.Found] */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
internal class QRCodeViewModel(
    qrCodeSensorUseCase: QrCodeSensorUseCase,
    @Assisted controller: CameraController
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(controller: CameraController): QRCodeViewModel
    }

    sealed interface Event {
        data object LeaveBecauseCameraUnavailable : Event

        @JvmInline
        value class Found(val result: QrCodeResult) : Event
    }

    private val channel = Channel<Event>(BUFFERED)
    val eventChannel: ReceiveChannel<Event> = channel

    private val mutableIsUnknownCode = MutableStateFlow(false)

    /** The camera sees a QR code that isn't a TPMS one, scanning goes on */
    val isUnknownCode: StateFlow<Boolean> = mutableIsUnknownCode.asStateFlow()

    init {
        qrCodeSensorUseCase
            .analyse(controller)
            // The camera reports a code at every frame it's in: the flag stays up while it's in view
            .transformLatest { result ->
                mutableIsUnknownCode.value = result == null
                result
                    ?.also { emit(it) }
                    ?: run {
                        delay(UNKNOWN_CODE_LINGER)
                        mutableIsUnknownCode.value = false
                    }
            }
            .catch { exc ->
                when (exc) {
                    is CameraAnalyser.CameraUnavailable -> channel.send(Event.LeaveBecauseCameraUnavailable)

                    is QrCodeSensors.DuplicateWheelLocation ->
                        emit(QrCodeResult.DuplicateWheelLocation(exc.wheels.duplicates()))

                    is QrCodeSensors.DuplicateId -> emit(QrCodeResult.DuplicateId(exc.ids.duplicates()))

                    else -> throw exc
                }
            }
            // The first code found closes the camera
            .take(1)
            .onEach { channel.send(Event.Found(it)) }
            .launchIn(viewModelScope)
    }

    private companion object {
        val UNKNOWN_CODE_LINGER = 2.seconds
    }

    private fun <T> Iterable<T>.duplicates() = groupingBy { it }
        .eachCount()
        .mapNotNull { (value, count) -> if (count > 1) value else null }
}
