package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The button silencing the alerts' speech for a while, on the main screen and in Android Auto, see
 * docs/alerts.md. On the main screen, it also ignores the alerts going on, which then no longer keep
 * the screen bright, see [AlertingVehiclesUseCase.unsilenced]: with the alerts making no sound nor
 * vibration, that's all it does, and it says so.
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
         * Silences the alerts, all of them once [isCritical]. Told as ignoring them once [isIgnoring],
         * the alerts making no sound nor vibration: they would only stop keeping the screen bright.
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
        if (sound == NONE && vibrate.not()) State.Disabled
        else stateOf(highest, silence, isIgnoring = false)
    }.distinctUntilChanged()

    /**
     * What the main screen's button shows: the same as [state], silencing and ignoring being one, with
     * the alerts going on offered too since they keep the screen bright, see [AlertingVehiclesUseCase].
     * The ignored ones are only offered again by a new alert, as the silenced speech by a new reading.
     * Only its label tells whether the alerts make a sound.
     */
    public val button: Flow<State> = combine(
        appPreferences.alertSound,
        appPreferences.vibrateAlerts,
        alertSpeaker.level,
        alertSpeaker.ongoing,
        alertSilenceUseCase.silence,
    ) { sound, vibrate, spoken, ongoing, silence ->
        listOfNotNull(spoken, ongoing.values.filter { it.isIgnored.not() }.maxOfOrNull { it.level })
            .maxOrNull()
            .let { highest -> stateOf(highest, silence, isIgnoring = sound == NONE && vibrate.not()) }
    }.distinctUntilChanged()

    private fun stateOf(
        highest: AlertLevel?,
        silence: AlertSilenceUseCase.Silence?,
        isIgnoring: Boolean,
    ): State = when {
        // A critical alert gets through a silence of the red ones, which can be silenced too
        highest == CRIMSON && silence?.level != CRIMSON -> State.Offer(isCritical = true, isIgnoring)
        silence != null -> State.Silenced(silence.level == CRIMSON, silence.until, isIgnoring)
        highest == RED -> State.Offer(isCritical = false, isIgnoring)
        else -> State.Idle
    }

    public fun silence(isCritical: Boolean): Unit = alertSilenceUseCase.silence(if (isCritical) CRIMSON else RED)

    public fun unmute(): Unit = alertSilenceUseCase.unmute()

    public companion object {
        public val DURATION: kotlin.time.Duration = AlertSilenceUseCase.DURATION
    }
}
