package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

public interface BluetoothLeScanner {

    /**
     * Why a scan ended. The scans start over by themselves after anything that can go away
     * (Bluetooth turned off, a start refused for a while), telling it in [tyreScanStatus]: only
     * this one ends them.
     */
    public sealed interface Failure {
        /** The system refused the scan, [reason] is one of `ScanCallback.SCAN_FAILED_*` */
        public class Scan(public val reason: Int) : Exception("Failure.Scan(reason=$reason)"), Failure {
            /**
             * Whether starting the scan again later may work. Only a phone without the feature
             * can't: the others are the system or the Bluetooth stack being busy for a while
             * (too many scanners registered, scanning too frequently, internal error...).
             */
            public val isRecoverable: Boolean
                get() = reason != SCAN_FAILED_FEATURE_UNSUPPORTED
        }
    }

    /** What a running scan is doing */
    public sealed interface ScanStatus {
        /** The radio listens */
        public data object Scanning : ScanStatus

        /** Bluetooth is off, the scan starts again once it's turned on */
        public data object WaitingForBluetooth : ScanStatus

        /** The system refused the scan for a [reason] that can go away, it's started again shortly */
        public data class Retrying(val reason: Int) : ScanStatus
    }

    /** How much the radio listens, from a small part of the time to all the time */
    public enum class ScanMode {
        LOW_POWER,
        BALANCED,
        LOW_LATENCY,
    }

    /** One advertisement, whatever device sent it */
    public data class Advertisement(
        val address: String,
        val name: String?,
        val rssi: Int,
        /** When it was received, on the clock of `SystemClock.elapsedRealtimeNanos()` */
        val timestamp: Duration,
        /** Sent by a tyre sensor of one of the brands this app reads */
        val isTyreSensor: Boolean,
    ) {
        /** Whether [rssi] was measured: the Bluetooth stack reports [RSSI_UNAVAILABLE] otherwise */
        public val hasRssi: Boolean get() = rssi != RSSI_UNAVAILABLE

        public companion object {
            /** "RSSI not available" in the Bluetooth spec, louder than any real signal */
            public const val RSSI_UNAVAILABLE: Int = 127
        }
    }

    /** A device to look for, its advertisements match by [address] or by [name] */
    public data class DeviceMatch(val address: String, val name: String?)

    public fun highDutyScan(): Flow<Tyre.SensorInput>
    public fun normalScan(): Flow<Tyre.SensorInput>

    /**
     * The status of the [highDutyScan] and the [normalScan], whoever collects them: the one
     * scanning when both run, null while neither does
     */
    public val tyreScanStatus: Flow<ScanStatus?>

    /** Whether a [highDutyScan] or a [normalScan] is scanning, whoever collects it */
    public val isScanningTyres: Flow<Boolean>
        get() = tyreScanStatus
            .map { it == ScanStatus.Scanning }
            .distinctUntilChanged()

    /**
     * Every advertisement of the devices matching [devices], or of every device around when null.
     * Completes right away for an empty [devices]: no filter at all would mean every device instead.
     */
    public fun advertisements(mode: ScanMode, devices: List<DeviceMatch>?): Flow<Advertisement>

    public fun missingPermission(): List<String>

    public val isBluetoothRequired: Boolean
}
