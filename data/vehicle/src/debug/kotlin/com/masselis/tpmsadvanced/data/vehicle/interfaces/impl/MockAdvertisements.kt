package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.DUMP
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.PHY_LE_1M
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanResult.PERIODIC_INTERVAL_NOT_PRESENT
import android.bluetooth.le.ScanResult.PHY_UNUSED
import android.bluetooth.le.ScanResult.SID_NOT_PRESENT
import android.bluetooth.le.ScanResult.TX_POWER_NOT_PRESENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcel
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

private const val ACTION = "com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT"
private const val EXTRA_BYTES = "bytes"
private const val EXTRA_ADDRESS = "address"
private const val EXTRA_RSSI = "rssi"
private const val EXTRA_BRAND = "brand"
private const val EXTRA_KPA = "kpa"
private const val EXTRA_CELSIUS = "celsius"
private const val EXTRA_BATTERY = "battery"
private const val EXTRA_ID = "id"
private const val EXTRA_WHEEL = "wheel"
private const val EXTRA_ALARM = "alarm"
private const val EXTRA_FLAGS = "flags"
private const val DEFAULT_ADDRESS = "00:11:22:33:44:55"
private const val DEFAULT_RSSI = -60
private const val DEFAULT_CELSIUS = 20f
private const val DEFAULT_ID = 0x123400 // Fits every sensor, including SYSGRATION's multiples of 256
private val WHEELS = mapOf("FL" to FRONT_LEFT, "FR" to FRONT_RIGHT, "RL" to REAR_LEFT, "RR" to REAR_RIGHT)

// What the legacy `ScanResult` constructor sets: complete data, legacy PDU, connectable
private const val LEGACY_EVENT_TYPE = 0x11

private val logger = Logger.withTag("MockAdvertisements")

/**
 * Debug builds only: merges BLE advertisements sent over adb into the real scan, so they go through
 * the same scan filters and decoders as packets received over the air.
 *
 * Either replay a raw advertisement, in hex as `ScanRecord.getBytes()` returns it (the scan logs
 * print it for real sensors):
 * ```
 * adb shell am broadcast -a com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT -p com.aveshev.persistenttpms.pro \
 *     --es bytes 0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206
 * ```
 * or have [MockSensor] encode a reading for a brand:
 * ```
 * adb shell am broadcast -a com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT -p com.aveshev.persistenttpms.pro \
 *     --es brand pecham --ef kpa 230 --ef celsius 21 --ei battery 30
 * ```
 * - `brand`: pecham, bekubee_ky, wicarlink, bekubee_tpms or sysgration. `kpa` is required,
 *   `celsius` (20), `battery` (a healthy value) and `id` (0x123400) are optional.
 * - Sysgration only: `wheel` (FL, FR, RL or RR, default FL). Sysgration and Wicarlink only:
 *   `alarm` (`--ez`, default false).
 * - `flags`: the raw status byte (0 to 255, in decimal), see [MockSensor] for where each brand
 *   carries it. Defaults to what real sensors send.
 * - Both forms: `address` (the ID of Pecham and Bekubee KY sensors) and `rssi` are optional.
 *
 * Numbers can be sent with `--ei` or `--ef`, a value sent with the wrong type is rejected rather
 * than replaced by its default. Packets are only received while a scan is running, and only from a
 * sender holding `android.permission.DUMP`, which the adb shell does and regular apps can't.
 */
@Suppress("MaxLineLength")
internal fun Flow<ScanResult>.withMockAdvertisements(
    context: Context,
    filters: List<ScanFilter>,
): Flow<ScanResult> = merge(
    this,
    callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    (
                        intent.string(EXTRA_BYTES)?.hexToByteArray()
                            ?: intent.string(EXTRA_BRAND)?.let { brand ->
                                MockSensor.entries
                                    .firstOrNull { it.name.equals(brand, ignoreCase = true) }
                                    ?.advertisement(intent.asReading())
                                    ?: error("Unknown brand \"$brand\", expected one of ${MockSensor.entries}")
                            }
                            ?: error("Send either \"$EXTRA_BYTES\" or \"$EXTRA_BRAND\"")
                        )
                        .asScanResult(
                            device = context.getSystemService<BluetoothManager>()
                                ?.adapter
                                ?.getRemoteDevice(intent.string(EXTRA_ADDRESS)?.uppercase() ?: DEFAULT_ADDRESS)
                                ?: error("No Bluetooth adapter"),
                            rssi = intent.number(EXTRA_RSSI)?.toInt() ?: DEFAULT_RSSI,
                        )
                        // No filter at all is an unfiltered scan, which receives everything
                        .also { result -> check(filters.isEmpty() || filters.any { it.matches(result) }) { "Matches none of the scan filters" } }
                }
                    .onSuccess { logger.d { "Mock advertisement received from ${it.device.address}" } }
                    .onSuccess { trySend(it) }
                    // Only the reason, the stack trace is noise for whoever reads it through adb
                    .onFailure { logger.w { "Mock advertisement dropped: ${it.message}" } }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), DUMP, null, RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
)

private fun Intent.asReading() = MockSensor.Reading(
    kpa = requireNotNull(number(EXTRA_KPA)) { "Missing \"$EXTRA_KPA\"" }.toFloat(),
    celsius = number(EXTRA_CELSIUS)?.toFloat() ?: DEFAULT_CELSIUS,
    battery = number(EXTRA_BATTERY)?.toInt(),
    id = number(EXTRA_ID)?.toInt() ?: DEFAULT_ID,
    location = string(EXTRA_WHEEL)
        ?.let { wheel ->
            WHEELS[wheel.uppercase()]
                ?: SensorLocation.entries.firstOrNull { it.name.equals(wheel, ignoreCase = true) }
                ?: error("Unknown wheel \"$wheel\", expected one of ${WHEELS.keys}")
        }
        ?: FRONT_LEFT,
    isAlarm = extra(EXTRA_ALARM)?.let { requireNotNull(it as? Boolean) { "\"$EXTRA_ALARM\" must be sent with --ez" } } ?: false,
    flags = number(EXTRA_FLAGS)?.toInt(),
)

// `Intent.getXxxExtra` return their default when the extra has another type, which hides a wrong
// adb flag. Reading the raw value lets a mismatch be reported instead.
@Suppress("DEPRECATION")
private fun Intent.extra(key: String): Any? = extras?.get(key)

private fun Intent.string(key: String): String? =
    extra(key)?.let { requireNotNull(it as? String) { "\"$key\" must be sent with --es" } }

private fun Intent.number(key: String): Number? =
    extra(key)?.let { requireNotNull(it as? Number) { "\"$key\" must be a number, sent with --ei or --ef" } }

/**
 * `ScanRecord` has no public constructor and its parser, `ScanRecord.parseFromBytes`, is hidden.
 * `ScanResult` is parcelable though, and reading it from a parcel runs that parser, so this writes
 * the parcel the system would send over Binder and reads it back. The fields and their order mirror
 * `ScanResult.writeToParcel`, which hasn't changed since API 26.
 */
private fun ByteArray.asScanResult(device: BluetoothDevice, rssi: Int): ScanResult = Parcel
    .obtain()
    .run {
        try {
            writeInt(1) // Device is present
            device.writeToParcel(this, 0)
            writeInt(1) // Scan record is present
            writeByteArray(this@asScanResult)
            writeInt(rssi)
            writeLong(SystemClock.elapsedRealtimeNanos())
            writeInt(LEGACY_EVENT_TYPE)
            writeInt(PHY_LE_1M) // Primary PHY
            writeInt(PHY_UNUSED) // Secondary PHY
            writeInt(SID_NOT_PRESENT)
            writeInt(TX_POWER_NOT_PRESENT)
            writeInt(PERIODIC_INTERVAL_NOT_PRESENT)
            setDataPosition(0)
            ScanResult.CREATOR.createFromParcel(this)
        } finally {
            recycle()
        }
    }
