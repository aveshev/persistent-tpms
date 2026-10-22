package com.masselis.tpmsadvanced.feature.background.usecase

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * A debug option: speaks the status of persistent scanning and its reasons each time they change
 * ("TPMS active, cable"...), to hear when the phone switches modes with the screen off or in a
 * pocket. Quiet while the main screen is in front of the user, who sees the status on the bell
 * instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanStatusAnnouncer(
    appPreferences: AppPreferences,
    scanPolicyUseCase: ScanPolicyUseCase,
    scope: CoroutineScope,
) {
    private val logger = Logger.withTag("ScanStatusAnnouncer")
    // Lazy: only needed once there is something to say
    private val powerManager by lazy { appContext.getSystemService<PowerManager>()!! }
    private val keyguardManager by lazy { appContext.getSystemService<KeyguardManager>()!! }

    /**
     * How many main screens are started, see QuietScanStatusAnnouncementsEffect. A count rather
     * than a flag: while navigating back to it, the main screen starts before the previous one stops.
     */
    val mainScreensStarted = MutableStateFlow(0)

    /** What to say about each change of status or reasons, the one at the time of collecting excluded */
    val statusChanges: Flow<String> = scanPolicyUseCase
        .decision
        .map { decision ->
            when (decision) {
                // Without persistent scanning, the decision is the one of the manual button
                is ScanDecision.Active -> if (MANUAL in decision.causes) "TPMS off" else decision
                    .causes
                    .sorted()
                    .joinToString(" and ", prefix = "TPMS active, ") { it.spoken }

                is ScanDecision.Suspended -> decision
                    .reasons
                    .sorted()
                    .joinToString(" and ", prefix = "TPMS suspended, ") { it.spoken }

                ScanDecision.Idle -> "TPMS idle"
            }
        }
        // A change of reasons is told too, but not a change of the devices' names behind them
        .distinctUntilChanged()
        .drop(1)

    /** Screen off, locked, or showing anything but the main screen (another app included) */
    private val isUnattended: Boolean
        get() = mainScreensStarted.value == 0 ||
                powerManager.isInteractive.not() ||
                keyguardManager.isKeyguardLocked

    init {
        // Only while the switch of the debug options is on, as the other ones
        combine(appPreferences.debugOptionsInUse, appPreferences.announceScanStatus, Boolean::and)
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) textToSpeech().flatMapLatest { tts ->
                    statusChanges
                        .filter { isUnattended }
                        .onEach { tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
                }
                else emptyFlow()
            }
            .launchIn(scope)
    }

    /** The default engine once it is ready, in English, shut down when the collection ends */
    private fun textToSpeech(): Flow<TextToSpeech> = callbackFlow {
        val status = CompletableDeferred<Int>()
        val tts = TextToSpeech(appContext) { status.complete(it) }
        launch {
            if (status.await() == TextToSpeech.SUCCESS) tts
                .apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    // The announcements are English, while the engine follows the phone's language:
                    // a Russian voice mangles "TPMS"
                    setLanguage(Locale.US)
                        .takeIf { it < TextToSpeech.LANG_AVAILABLE }
                        ?.also { logger.w { "English is not available ($it), spoken in the default language" } }
                }
                .also { send(it) }
            else logger.w { "Text-to-speech failed to initialize, status changes won't be spoken" }
        }
        awaitClose { tts.shutdown() }
    }

    private companion object {
        const val UTTERANCE_ID = "ScanStatusAnnouncer"

        // Short words for the ear, the full sentences are on the bell, see explanation()
        val ScanDecision.ActivateCause.spoken
            get() = when (this) {
                MANUAL -> "manual"
                CABLE -> "cable"
                WIRELESS -> "wireless"
                ANDROID_AUTO -> "Auto"
                ActivateCause.BLUETOOTH -> "Bluetooth"
                BEACON -> "beacon"
                STAY_ACTIVE -> "stay"
                ALWAYS -> "always"
            }

        val Reason.spoken
            get() = when (this) {
                // Not "idle", which would sound like the idle status
                IDLE -> "sleep"
                WIFI -> "WiFi"
                Reason.BLUETOOTH -> "Bluetooth"
            }
    }
}
