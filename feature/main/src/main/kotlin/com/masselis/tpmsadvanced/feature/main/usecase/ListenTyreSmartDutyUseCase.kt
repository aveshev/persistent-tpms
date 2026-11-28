package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.BALANCED
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_POWER
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

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
 * [backgroundMode] is the scan used in the background: low latency on the phones which don't do
 * that, see [ALWAYS_LOW_LATENCY_IN_BACKGROUND], and otherwise a debug option to find out how each phone
 * treats each mode, see [BACKGROUND_SCAN_MODES]. [balancedAfterFirstReading] overrides it, it
 * switches to the normal scan after the first reading. [lowPowerWatch] overrides them all
 * while the app isn't visible, with the low power scan.
 */
internal class ListenTyreSmartDutyUseCase(
    private val scanner: LocatedTyreScannerUseCase,
    private val balancedAfterFirstReading: Flow<Boolean>,
    private val isAppVisible: Flow<Boolean>,
    private val backgroundMode: Flow<ScanMode>,
    private val lowPowerWatch: Flow<Boolean>,
) : ListenTyreUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun listen(): Flow<Tyre.Located> = lowPowerWatch
        .combine(isAppVisible) { watch, visible -> watch && visible.not() }
        .distinctUntilChanged()
        .flatMapLatest { lowPower ->
            if (lowPower) scanner.lowPowerScan()
            else balancedAfterFirstReading
                .distinctUntilChanged()
                .flatMapLatest { balancedAfterFirstReading ->
                    if (balancedAfterFirstReading) flow {
                        emit(scanner.highDutyScan().first())
                        emitAll(scanner.normalScan())
                    }
                    else isAppVisible
                        .combine(backgroundMode) { visible, mode -> if (visible) LOW_LATENCY else mode }
                        .distinctUntilChanged()
                        .flatMapLatest { mode ->
                            when (mode) {
                                LOW_LATENCY -> scanner.highDutyScan()
                                BALANCED -> scanner.normalScan()
                                LOW_POWER -> scanner.lowPowerScan()
                            }
                        }
                }
        }

    internal companion object {
        /**
         * Below Android 12, the high duty scan runs in the background too. A Galaxy S9+ on Android
         * 10 kept it at full duty for 45 minutes with the screen off. There, a scan started while
         * the app isn't visible needs location in the background, see `MonitorService`, so
         * switching scans on leaving the app risks a deaf one. Android 12 and later scan without
         * location, and the Pixel's drop happens there.
         */
        val ALWAYS_LOW_LATENCY_IN_BACKGROUND: Boolean = SDK_INT < S

        /** The modes the tyre scan can use while the app isn't visible, from Android 12, a debug option */
        val BACKGROUND_SCAN_MODES: List<ScanMode> = listOf(BALANCED, LOW_LATENCY, LOW_POWER)

        /** The mode of the tyre scan while the app isn't visible when the debug option was never set */
        val DEFAULT_BACKGROUND_SCAN_MODE: ScanMode = BALANCED

        /** The mode a stored name stands for, the default one for an unknown or missing name */
        fun String?.asBackgroundScanMode(): ScanMode =
            BACKGROUND_SCAN_MODES.firstOrNull { it.name == this } ?: DEFAULT_BACKGROUND_SCAN_MODE
    }
}
