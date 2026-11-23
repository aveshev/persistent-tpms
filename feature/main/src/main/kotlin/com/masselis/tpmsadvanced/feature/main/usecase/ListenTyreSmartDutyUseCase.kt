package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
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
         * Below Android 12, the high duty scan runs in the background too. A Galaxy S9+ on Android
         * 10 kept it at full duty for 45 minutes with the screen off. There, a scan started while
         * the app isn't visible needs location in the background, see `MonitorService`, so
         * switching scans on leaving the app risks a deaf one. Android 12 and later scan without
         * location, and the Pixel's drop happens there.
         */
        val KEEPS_LOW_LATENCY_IN_BACKGROUND: Boolean = SDK_INT < S
    }
}
