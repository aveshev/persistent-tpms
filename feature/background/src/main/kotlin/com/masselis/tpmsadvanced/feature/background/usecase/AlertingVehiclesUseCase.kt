package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSpeaker.Ongoing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The worst red or crimson alert going on for each vehicle alerting, by its uuid: one the speech
 * loops would repeat, whether the speech is silenced or off, see [AlertSpeaker.ongoing]. It goes
 * on until a reading lowers or clears it, it's dismissed, or the loops stop with the scans.
 */
public class AlertingVehiclesUseCase internal constructor(
    alertSpeaker: AlertSpeaker,
    alertSilenceUseCase: AlertSilenceUseCase,
) {
    public val levels: Flow<Map<UUID, AlertLevel>> = alertSpeaker
        .ongoing
        .map { ongoing ->
            ongoing
                .values
                .groupBy(Ongoing::vehicle, Ongoing::level)
                .mapValues { (_, levels) -> levels.max() }
        }
        .distinctUntilChanged()

    /**
     * [levels] without the alerts silenced or ignored from the main screen's button, see
     * [SilenceAlertsUseCase]: what keeps the main screen bright. A critical alert gets through a
     * silence of the red ones, and they're back once the silence ends.
     */
    public val unsilenced: Flow<Map<UUID, AlertLevel>> = levels
        .combine(alertSilenceUseCase.silence) { levels, silence ->
            silence
                ?.let { levels.filterValues { level -> level > it.level } }
                ?: levels
        }
        .distinctUntilChanged()
}
