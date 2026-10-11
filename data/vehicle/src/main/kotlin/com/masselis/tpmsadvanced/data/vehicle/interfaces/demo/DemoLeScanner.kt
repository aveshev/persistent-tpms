package com.masselis.tpmsadvanced.data.vehicle.interfaces.demo

import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.AdvertisingPacket
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Real advertisements, decoded like a scan's, so the demo's readings are like any other: the
 * instrumented tests run this scanner with the demo mode off, storing them. For each wheel, a
 * Sysgration sensor advertising it then a Pecham one, from flat and cold at the front left to hot
 * at the rear right, and a Pecham one for a spare, see [SPARE_SENSOR_ID].
 */
@Suppress("MagicNumber")
public class DemoLeScanner : BluetoothLeScanner {

    @OptIn(ExperimentalStdlibApi::class)
    private val source = flow {
        listOf(
            1 to "0201060303b0fb13ff010080eaca010000409c0000dc0500005a00",
            2 to "0303a5270308425208ff101f0f00cb74e4",
            3 to "0201060303b0fb13ff010081eaca03000000710200d00700005a00",
            4 to "0303a5270308425208ff101e140179da44",
            5 to "0201060303b0fb13ff010082eaca050000400d0300ac0d00005a00",
            6 to "0303a5270308425208ff101d2301b30a51",
            7 to "0201060303b0fb13ff010083eaca070000c04504001c2500005a00",
            8 to "0303a5270308425208ff101e5f02275c67",
            SPARE_SENSOR_ID to "0303a5270308425208ff101e1201c2f50d",
        ).forEach { (sensorId, advertisement) ->
            AdvertisingPacket(advertisement.hexToByteArray())
                .decode()
                ?.asTyre(now(), -20, sensorId, advertisement)
                ?.also { emit(it) }
        }
        awaitCancellation()
    }

    override fun highDutyScan(): Flow<Tyre.SensorInput> = source

    override fun normalScan(): Flow<Tyre.SensorInput> = source

    // The demo's readings aren't stored, nothing alerts about them
    override val tyreScanStatus: Flow<BluetoothLeScanner.ScanStatus?> = flowOf(null)

    // No device around in the demo
    override fun advertisements(
        mode: BluetoothLeScanner.ScanMode,
        devices: List<BluetoothLeScanner.DeviceMatch>?,
    ): Flow<BluetoothLeScanner.Advertisement> = emptyFlow()

    override fun missingPermission(): List<String> = emptyList()

    override val isBluetoothRequired: Boolean = false

    public companion object {
        /**
         * No sensor advertises a spare, the demo shows this one's readings there (210 kPa, 18°C)
         * while it's unbound
         */
        public const val SPARE_SENSOR_ID: Int = 9
    }
}
