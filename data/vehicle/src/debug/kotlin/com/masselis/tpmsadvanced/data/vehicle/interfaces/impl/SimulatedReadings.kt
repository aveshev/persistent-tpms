package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

/**
 * Debug builds only: readings typed in the app, to try the alerts away from the sensors. Encoded
 * into the advertisement a [brand]'s sensor would send ([MockSensor]) and decoded back, then merged
 * into the scan right after its decoders: they go wherever a real one goes, matched to a tyre by
 * its binding or its location, stored, shown and alerted on. Only received while a scan runs, as
 * the main screen keeps one running.
 */
public object SimulatedReadings {

    public const val IS_AVAILABLE: Boolean = true

    private const val RSSI = -60

    private val readings = MutableSharedFlow<Tyre.SensorInput>(extraBufferCapacity = 16)

    /**
     * [battery] is in decivolts, or a percentage for Sysgration, null for a healthy one. Only
     * Sysgration sends [location], only Sysgration and Wicarlink [isAlarm]. Throws when a value can't be carried by the
     * brand's advertisement, see [MockSensor].
     */
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("LongParameterList")
    public fun send(
        brand: SensorBrand,
        sensorId: Int,
        location: SensorLocation,
        kpa: Float,
        celsius: Float,
        battery: Int?,
        isAlarm: Boolean,
    ) {
        MockSensor
            .valueOf(brand.name)
            .advertisement(MockSensor.Reading(kpa, celsius, battery, sensorId, location, isAlarm && brand in listOf(SYSGRATION, WICARLINK)))
            .let { bytes ->
                AdvertisingPacket(bytes)
                    .decode()
                    ?.asTyre(now(), RSSI, sensorId, bytes.toHexString())
                    ?: error("$brand's advertisement didn't decode back")
            }
            .let(readings::tryEmit)
    }

    internal fun Flow<Tyre.SensorInput>.withSimulatedReadings(): Flow<Tyre.SensorInput> = merge(this, readings)
}
