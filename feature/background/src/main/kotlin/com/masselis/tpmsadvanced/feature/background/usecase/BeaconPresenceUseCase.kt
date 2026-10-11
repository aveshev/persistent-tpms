package com.masselis.tpmsadvanced.feature.background.usecase

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.DeviceMatch
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tells which of the user's beacons are around, from their advertisements */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconPresenceUseCase(
    private val scanner: BluetoothLeScanner,
    private val bluetoothOn: Flow<Boolean>,
    /** The signal (dBm) from which a beacon counts as nearby */
    private val minRssi: StateFlow<Int>,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("BeaconPresenceUseCase")

    /**
     * The addresses of the [beacons] heard nearby, with the signal (dBm) of their last packet. A
     * beacon becomes nearby once [MIN_PACKETS] of its packets at least as loud as [minRssi] were
     * heard within [WINDOW], and stays so while one of them was: a beacon far away (the
     * neighbour's) doesn't count, nor does a single stray packet, nor one gone quiet. Scans while
     * Bluetooth is on.
     */
    fun nearby(beacons: List<Beacon>, mode: ScanMode): Flow<Map<String, Int>> = bluetoothOn
        .distinctUntilChanged()
        .flatMapLatest { on ->
            if (on.not()) return@flatMapLatest flowOf(emptyMap())
            merge(
                scanner
                    .advertisements(mode, beacons.map { DeviceMatch(it.address, it.advertisedName) })
                    // The scanner starts over by itself after what can go away: what ends it can't,
                    // the beacons then go quiet
                    .catch { logger.e(it) { "Beacon scan stopped" } }
                    .mapNotNull { advertisement ->
                        beacons
                            .firstOrNull { it.isSending(advertisement) }
                            // Without a measured signal, how far it is isn't known
                            ?.takeIf { advertisement.hasRssi && advertisement.rssi >= minRssi.value }
                            ?.let { Packet(it.address, advertisement.rssi, timeSource.markNow()) }
                    },
                // Packets stop coming when a beacon goes away: the time passing is what tells it
                flow<Packet?> {
                    while (true) {
                        delay(TICK)
                        emit(null)
                    }
                },
            )
                .runningFold(emptyMap<String, Heard>()) { heard, packet ->
                    heard
                        .let { heard ->
                            packet
                                ?.let { heard[it.address].plus(it) }
                                ?.let { heard + (packet.address to it) }
                                ?: heard
                        }
                        .mapValues { (_, it) -> it.withinWindow() }
                        .filterValues { it.packets.isNotEmpty() }
                        .also { next ->
                            if (next.nearby().keys != heard.nearby().keys) logger.i {
                                "Nearby beacons with $mode, from ${minRssi.value} dBm: " +
                                    next
                                        .nearby()
                                        .values
                                        .joinToString { "${it.address} at ${it.rssi} dBm" }
                                        .ifEmpty { "none" }
                            }
                        }
                }
                .map { heard -> heard.nearby().mapValues { (_, it) -> it.rssi } }
        }
        .distinctUntilChanged()

    private class Packet(val address: String, val rssi: Int, val at: TimeMark)

    /** The last loud packets of a beacon within [WINDOW], the last one first: [MIN_PACKETS] at most */
    private class Heard(val packets: List<Packet>, val isNearby: Boolean) {
        val address get() = packets.first().address
        val rssi get() = packets.first().rssi

        // Once nearby, a single packet within the window keeps it so: only becoming nearby needs more
        fun withinWindow() = Heard(packets.filter { it.at.elapsedNow() < WINDOW }, isNearby)
    }

    private fun Heard?.plus(packet: Packet) = (listOf(packet) + this?.packets.orEmpty())
        .take(MIN_PACKETS)
        .let { packets -> Heard(packets, this?.isNearby == true || packets.size >= MIN_PACKETS) }

    private fun Map<String, Heard>.nearby() = filterValues { it.isNearby }

    private fun Beacon.isSending(advertisement: Advertisement) =
        advertisement.address.equals(address, ignoreCase = true) ||
            (advertisedName != null && advertisement.name == advertisedName)

    companion object {
        /** The modes the background scan for beacons can use, a debug option */
        val BACKGROUND_SCAN_MODES = listOf(ScanMode.LOW_POWER, ScanMode.BALANCED)

        /** The mode of the background scan for beacons when the debug option was never set */
        val DEFAULT_SCAN_MODE = ScanMode.LOW_POWER

        /** The mode a stored name stands for, the default one for an unknown or missing name */
        fun String?.asBeaconScanMode(): ScanMode =
            BACKGROUND_SCAN_MODES.firstOrNull { it.name == this } ?: DEFAULT_SCAN_MODE

        private val WINDOW = 30.seconds

        // A single packet may be a stray one: the neighbour's beacon heard through a reflection
        private const val MIN_PACKETS = 2
        private val TICK = 5.seconds
    }
}
