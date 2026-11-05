package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The high duty scan while the app is visible, the normal scan otherwise.
 *
 * A sensor sends rarely (every minute while riding for some), and from a phone mounted in the
 * cockpit only some of its packets get through, so a lower duty misses many of them. But a Pixel 7 on
 * Android 17 silently drops a low latency scan to 1400/140 about 6 minutes after the scan first
 * started, without telling the app: it stays there whenever the app isn't visible until Bluetooth is
 * toggled, and restarting the scan doesn't clear it. A balanced scan wasn't dropped, it stayed at
 * 730/182 in the background, even after a long low latency stint with the app visible.
 *
 * [keepsLowLatencyInBackground] keeps the high duty scan running in the background, for the phones
 * which don't do that, see [KEEPS_LOW_LATENCY_IN_BACKGROUND]. [balancedAfterFirstReading] overrides
 * both, it switches to the normal scan after the first reading.
 */
internal class ListenTyreSmartDutyUseCase(
    private val scanner: LocatedTyreScannerUseCase,
    private val balancedAfterFirstReading: Flow<Boolean>,
    private val isAppVisible: Flow<Boolean>,
    private val keepsLowLatencyInBackground: Boolean,
) : ListenTyreUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun listen(): Flow<Tyre.Located> = balancedAfterFirstReading
        .distinctUntilChanged()
        .flatMapLatest { balancedAfterFirstReading ->
            if (balancedAfterFirstReading) flow {
                emit(scanner.highDutyScan().first())
                emitAll(scanner.normalScan())
            }
            else isAppVisible
                .map { it || keepsLowLatencyInBackground }
                .distinctUntilChanged()
                .flatMapLatest { highDuty -> if (highDuty) scanner.highDutyScan() else scanner.normalScan() }
        }

    internal companion object {
        /**
         * Whether this phone keeps a low latency scan at low latency in the background. False for
         * every phone for now: only one model was measured, and the app can't detect the drop, so
         * enable it here once a phone or an Android version is known to be spared.
         */
        const val KEEPS_LOW_LATENCY_IN_BACKGROUND: Boolean = false
    }
}
