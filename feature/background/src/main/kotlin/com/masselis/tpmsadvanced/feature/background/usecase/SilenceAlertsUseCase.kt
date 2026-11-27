package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The button silencing the alerts' speech for a while, on the main screen and in Android Auto, see
 * docs/alerts.md. On the main screen, it also ignores the alerts going on for that while, which then
 * no longer keep the screen bright, see [AlertingVehiclesUseCase.unsilenced]: with the alerts making
 * no sound nor vibration, or nothing left to say, that's all it does, and it says so.
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

        /**
         * Silences the alerts, all of them once [isCritical]. Ignores them once [isIgnoring]: nothing
         * would be said, only the alerts going on would stop keeping the screen bright.
         */
        public data class Offer(val isCritical: Boolean, val isIgnoring: Boolean = false) : State

        /**
         * The alerts are silent until [until], in milliseconds since the epoch, all of them once
         * [isCritical]. Ignored once [isIgnoring], the alerts making no sound nor vibration.
         */
        public data class Silenced(val isCritical: Boolean, val until: Long, val isIgnoring: Boolean = false) : State
    }

    /** What Android Auto shows: only what the speech has to say, it has no screen to dim */
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

    /**
     * What the main screen's button shows: also offered for an alert going on with nothing left to
     * say, the sound off or its speech over, since it keeps the screen bright, see [AlertingVehiclesUseCase]
     */
    @Suppress("MaxLineLength")
    public val button: Flow<State> = combine(
        appPreferences.alertSound,
        appPreferences.vibrateAlerts,
        alertSpeaker.level,
        alertSpeaker.ongoing,
        alertSilenceUseCase.silence,
    ) { sound, vibrate, spoken, ongoing, silence ->
        val isSoundOff = sound == NONE && vibrate.not()
        val highest = listOfNotNull(spoken, ongoing.values.maxOfOrNull { it.level }).maxOrNull()
        // Only ignored when there's no speech to silence: none of the alerts at the level offered
        val isIgnoring = isSoundOff || (spoken ?: AMBER) < (highest ?: AMBER)
        when {
            // A critical alert gets through a silence of the red ones, which can be silenced too
            highest == CRIMSON && silence?.level != CRIMSON -> State.Offer(isCritical = true, isIgnoring)
            // Silent alerts have nothing left to tell once they're over
            silence != null && (isSoundOff.not() || highest != null) -> State.Silenced(silence.level == CRIMSON, silence.until, isSoundOff)
            highest == RED -> State.Offer(isCritical = false, isIgnoring)
            isSoundOff -> State.Disabled
            else -> State.Idle
        }
    }.distinctUntilChanged()

    public fun silence(isCritical: Boolean): Unit = alertSilenceUseCase.silence(if (isCritical) CRIMSON else RED)

    public fun unmute(): Unit = alertSilenceUseCase.unmute()

    public companion object {
        public val DURATION: kotlin.time.Duration = AlertSilenceUseCase.DURATION
    }
}
