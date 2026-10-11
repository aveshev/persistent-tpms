package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The button silencing the alerts' speech for a while, on the main screen and in Android Auto, see
 * docs/alerts.md
 */
public class SilenceAlertsUseCase internal constructor(
    appPreferences: AppPreferences,
    alertSpeaker: AlertSpeaker,
    private val alertSilenceUseCase: AlertSilenceUseCase,
) {

    public sealed interface State {
        /** The alerts make no sound nor vibration of their own */
        public data object Disabled : State

        /** No red or critical alert left to say */
        public data object Idle : State

        /** Silences the alerts, all of them once [isCritical] */
        public data class Offer(val isCritical: Boolean) : State

        /** The alerts are silent until [until], in milliseconds since the epoch, all of them once [isCritical] */
        public data class Silenced(val isCritical: Boolean, val until: Long) : State
    }

    public val state: Flow<State> = combine(
        appPreferences.alertSound,
        appPreferences.vibrateAlerts,
        alertSpeaker.level,
        alertSilenceUseCase.silence,
    ) { sound, vibrate, highest, silence ->
        when {
            sound == NONE && vibrate.not() -> State.Disabled
            // A critical alert gets through a silence of the red ones, which can be silenced too
            highest == CRIMSON && silence?.level != CRIMSON -> State.Offer(isCritical = true)
            silence != null -> State.Silenced(silence.level == CRIMSON, silence.until)
            highest == RED -> State.Offer(isCritical = false)
            else -> State.Idle
        }
    }.distinctUntilChanged()

    public fun silence(isCritical: Boolean): Unit = alertSilenceUseCase.silence(if (isCritical) CRIMSON else RED)

    public fun unmute(): Unit = alertSilenceUseCase.unmute()

    public companion object {
        public val DURATION: kotlin.time.Duration = AlertSilenceUseCase.DURATION
    }
}
