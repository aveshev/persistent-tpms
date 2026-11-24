package com.masselis.tpmsadvanced.feature.main.usecase

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether the tyres are listened to with the low power scan while the app isn't visible: persistent
 * scanning sets it while it only waits for a known sensor to be heard, see
 * `ScanDecision.ActivateCause.SENSOR`. The open app scans at its own duty whatever this says.
 */
public class LowPowerWatchUseCase {
    public val isWatching: MutableStateFlow<Boolean> = MutableStateFlow(false)
}
