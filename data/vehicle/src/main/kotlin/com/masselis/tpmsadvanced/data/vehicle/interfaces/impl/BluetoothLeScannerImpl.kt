package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED
import android.bluetooth.BluetoothAdapter.EXTRA_STATE
import android.bluetooth.BluetoothAdapter.STATE_OFF
import android.bluetooth.BluetoothAdapter.STATE_ON
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE
import android.bluetooth.le.ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock.elapsedRealtime
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.asFlow
import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.DeviceMatch
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.SimulatedReadings.withSimulatedReadings
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
@Suppress("MaxLineLength")
internal class BluetoothLeScannerImpl(
    private val context: Context
) : BluetoothLeScanner {

    private val logger = Logger.withTag("BluetoothLeScannerImpl")

    /** When the last scans started, on the clock of `SystemClock.elapsedRealtime()`, see [rawScan] */
    private var lastStarts = emptyList<Duration>()
    private val startLock = Mutex()

    private val bluetoothAdapter get() = context.getSystemService<BluetoothManager>()?.adapter

    /** What each running scan is doing, by its key in [sharedScans] */
    private val scanStatuses = MutableStateFlow(emptyMap<Any, ScanStatus>())

    override val tyreScanStatus: Flow<ScanStatus?> = scanStatuses
        .map { statuses ->
            statuses
                .filterKeys { it is TyreScan }
                .values
                // One scanning is enough to read the tyres
                .minByOrNull {
                    when (it) {
                        Scanning -> 0
                        is Retrying -> 1
                        WaitingForBluetooth -> 2
                    }
                }
        }
        .distinctUntilChanged()

    /** Whether Bluetooth is on, from now on */
    private val isBluetoothOn = IntentFilter(ACTION_STATE_CHANGED)
        // Protected system broadcast, see asFlow()
        .asFlow(RECEIVER_EXPORTED)
        .map { it.getIntExtra(EXTRA_STATE, STATE_OFF) == STATE_ON }
        // Read just before the receiver is registered: Bluetooth takes far longer to turn on than
        // the time in between, its "on" broadcast isn't missed
        .onStart { emit(bluetoothAdapter?.isEnabled == true) }

    /** Every result of a scan with [filters] and [settings], debug builds adding the mock ones */
    @SuppressLint("InlinedApi")
    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private fun rawScan(filters: List<ScanFilter>, settings: ScanSettings) = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                launch { send(result) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                launch { results.forEach { send(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Failure.Scan(errorCode))
            }
        }

        // Told right away, rather than after waiting for a start below
        if (bluetoothAdapter?.isEnabled != true) {
            close(BluetoothOff(bluetoothAdapter?.state))
            awaitCancellation()
        }

        // Android refuses a start once the app started MAX_STARTS scans within START_WINDOW, and
        // silently: no onScanFailed(), the scan just never delivers anything ("scanning too
        // frequently" in the system log). Every scan of the app counts, the tyre ones and the
        // others alike, so each start waits for the oldest of the last ones to be out of the window.
        startLock.withLock {
            lastStarts
                .takeIf { it.size >= MAX_STARTS }
                ?.first()
                ?.let { oldest -> delay(oldest + START_WINDOW + START_MARGIN - elapsedRealtime().milliseconds) }
            lastStarts = (lastStarts + elapsedRealtime().milliseconds).takeLast(MAX_STARTS)
        }

        // Listened to before starting, so that a turning off right after the start isn't missed
        launch {
            isBluetoothOn.first { it.not() }
            // The system drops the scan without calling onScanFailed(): ending it here is what
            // starts it over once Bluetooth is back, see recovering()
            close(BluetoothOff(bluetoothAdapter?.state))
        }

        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            close(BluetoothOff(bluetoothAdapter?.state))
            awaitCancellation()
        }
        leScanner.startScan(filters, settings, callback)
        awaitClose {
            if (bluetoothAdapter?.isEnabled == true) {
                leScanner.flushPendingScanResults(callback)
                leScanner.stopScan(callback)
            }
        }
    }.flowOn(Dispatchers.Main) // System's BluetoothLeScanner class as issues if called on a background thread
        .withMockAdvertisements(context, filters)

    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private fun scan(mode: Int) = rawScan(
        FILTERS,
        ScanSettings
            .Builder()
            .setScanMode(mode)
            .setMatchMode(MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(MATCH_NUM_ONE_ADVERTISEMENT)
            .build(),
    )
        .mapNotNull { result ->
            logger.v { "Sensor found during scan. Address: ${result.device.address}, scan bytes: ${result.scanRecord?.bytes?.toHexString()}" }
            result.scanRecord?.advertisementHex?.let {
                Packet(result.device.address, result.rssi, it, result.timestampNanos.nanoseconds)
            }
        }
        // A real sensor sends the same advertisement up to 10 times in a short time, only the first
        // copy goes through. The signal strength isn't compared, it changes from one to the next.
        .distinctWithin(BluetoothLeScanner.BURST, { it.address to it.advertisement }, Packet::receivedAt)
        .mapNotNull { (address, rssi, advertisement) ->
            AdvertisingPacket(advertisement.hexToByteArray())
                .decode()
                ?.let { raw -> raw.asTyre(now(), rssi, raw.sensorId ?: sensorIdOf(address), advertisement) }
                ?: run {
                    logger.d { "Sensor not parsed. Scan bytes: $advertisement" }
                    null
                }
        }
        .withSimulatedReadings()
        .onEach { logger.d("Sensor content: $it") }

    override fun highDutyScan(): Flow<Tyre.SensorInput> =
        shared(TyreScan(ScanSettings.SCAN_MODE_LOW_LATENCY)) { scan(ScanSettings.SCAN_MODE_LOW_LATENCY) }

    @SuppressLint("MissingPermission")
    override fun normalScan(): Flow<Tyre.SensorInput> =
        shared(TyreScan(ScanSettings.SCAN_MODE_BALANCED)) { scan(ScanSettings.SCAN_MODE_BALANCED) }

    @SuppressLint("MissingPermission")
    override fun advertisements(mode: ScanMode, devices: List<DeviceMatch>?): Flow<Advertisement> =
        if (devices?.isEmpty() == true) emptyFlow()
        else shared(mode to devices) {
            rawScan(
                devices.orEmpty().flatMap { it.asFilters() },
                // Every packet, the broadcast period is read from them
                ScanSettings
                    .Builder()
                    .setScanMode(mode.value)
                    .build(),
            ).map { result ->
                Advertisement(
                    address = result.device.address,
                    name = result.scanRecord?.deviceName,
                    rssi = result.rssi,
                    timestamp = result.timestampNanos.nanoseconds,
                    isTyreSensor = result.scanRecord?.serviceUuids.orEmpty().any { it in SERVICE_UUIDS },
                )
            }
        }

    private val ScanMode.value
        get() = when (this) {
            ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
            ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
        }

    /**
     * Either one matches, the system delivering what matches any of the filters. The public API
     * only filters on a public address, and some phones then miss a random one (most beacons): the
     * name makes up for it.
     */
    private fun DeviceMatch.asFilters(): List<ScanFilter> = buildList {
        add(ScanFilter.Builder().setDeviceAddress(address).build())
        name?.let { add(ScanFilter.Builder().setDeviceName(it).build()) }
    }

    /** The key of a tyre scan in [sharedScans], by its `ScanSettings.SCAN_MODE_*` */
    private data class TyreScan(val mode: Int)

    /** The running scans by what they scan for, see [shared] */
    private val sharedScans = ConcurrentHashMap<Any, Flow<*>>()

    /**
     * The scan for [key], started by [scan] unless already running. It keeps running for
     * [SCAN_LINGER] after its last collector is gone, so that coming back to a page reuses it:
     * starting another one counts towards Android's limit, see [rawScan]. It starts over by itself
     * after Bluetooth was turned off or a refused start, see [recovering], its collectors only see
     * the readings stop meanwhile. A scan that ended, because the phone can't scan or after
     * lingering, is forgotten right away: the next collection starts a new one, rather than
     * attaching to one that will never deliver anything again.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun <T> shared(key: Any, scan: () -> Flow<T>): Flow<T> = sharedScans.getOrPut(key) {
        scan()
            .recovering(isBluetoothOn) { status -> scanStatuses.update { it + (key to status) } }
            .materializeCompletion()
            .onCompletion {
                // Before forgetting the scan: the next one for this key can't start meanwhile
                scanStatuses.update { it - key }
                sharedScans.remove(key)
            }
            .shareIn(GlobalScope + Dispatchers.Default, WhileSubscribed(SCAN_LINGER))
            .dematerializeCompletion()
    } as Flow<T>

    @SuppressLint("InlinedApi")
    @Suppress("MagicNumber")
    override fun missingPermission(): List<String> = when (Build.VERSION.SDK_INT) {
        in Int.MIN_VALUE..28 -> listOf(ACCESS_COARSE_LOCATION)
        in 29..30 -> listOf(ACCESS_FINE_LOCATION)
        in 31..Int.MAX_VALUE -> listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        else -> error("Unreachable condition")
    }.filter { checkSelfPermission(context, it) != PERMISSION_GRANTED }

    override val isBluetoothRequired = true

    /**
     * Bluetooth is off: the scan couldn't start, or it was turned off while scanning. The system
     * drops a running scan then, without telling it to the app. See [recovering].
     */
    internal class BluetoothOff(adapterState: Int?) : Exception("BluetoothOff(adapterState=$adapterState)")

    /** [receivedAt] is on the clock of `SystemClock.elapsedRealtimeNanos()` */
    private data class Packet(val address: String, val rssi: Int, val advertisement: String, val receivedAt: Duration)

    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val SERVICE_UUIDS = listOf(
            RawSysgration.SERVICE_UUID,
            RawPecham.SERVICE_UUID,
            RawWicarlink.SERVICE_UUID,
            RawBekubeeKy.SERVICE_UUID,
            RawBekubeeTpms.SERVICE_UUID
        )
        private val FILTERS = SERVICE_UUIDS.map { ScanFilter.Builder().setServiceUuid(it).build() }

        // Android's limit, see AppScanStats in the Bluetooth stack
        private const val MAX_STARTS = 5
        private val START_WINDOW = 30.seconds
        private val START_MARGIN = 1.seconds

        private const val SCAN_LINGER = 30_000L
    }
}
