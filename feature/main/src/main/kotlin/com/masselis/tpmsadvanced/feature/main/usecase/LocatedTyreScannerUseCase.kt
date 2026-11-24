package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.demo.DemoLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class LocatedTyreScannerUseCase(
    private val source: BluetoothLeScanner,
    private val currentLocation: Location,
    private val sensorBindingUseCase: SensorBindingUseCase,
    /** The demo's sensors are never bound, they're shown where they advertise they are */
    private val showsUnbound: Boolean,
) {
    private val detected = MutableStateFlow<Detection?>(null)

    fun highDutyScan(): Flow<Tyre.Located> = source.highDutyScan().mapWithLocation()

    fun normalScan(): Flow<Tyre.Located> = source.normalScan().mapWithLocation()

    fun lowPowerScan(): Flow<Tyre.Located> = source.lowPowerScan().mapWithLocation()

    /**
     * An unbound sensor advertising this location (Sysgration) while none is bound here, heard
     * by the scans above in the last [DETECTION_TIMEOUT], ready to be bound here. It's no longer
     * detected once bound, here or anywhere else.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun detectedSensor(): Flow<Sensor?> = detected
        .transformLatest { detection ->
            emit(detection?.sensor)
            detection
                ?.also { delay(DETECTION_TIMEOUT - it.heardAt.elapsedNow()) }
                ?.also { emit(null) }
        }
        .flatMapLatest { sensor ->
            sensor
                ?.let { sensorBindingUseCase.boundVehicle(it).map { vehicle -> sensor.takeIf { vehicle == null } } }
                ?: flowOf(null)
        }

    // Filtered here, before ListenTyreWithDatabaseUseCase stores the records: a record of another
    // sensor stored for this location would be replayed at the next start, then dropped by
    // ListenBoundTyreUseCase, leaving the location empty.
    @Suppress("CyclomaticComplexMethod")
    private fun Flow<Tyre.SensorInput>.mapWithLocation() = this
        .mapNotNull { tyre ->
            when (val sensor = sensorBindingUseCase.boundSensor().value) {
                // Nothing advertises a spare, the demo has a sensor of its own for it
                null if showsUnbound && currentLocation == Location.Spare -> tyre
                    .takeIf { it.sensorId == DemoLeScanner.SPARE_SENSOR_ID }
                    ?.let { Tyre.Located(it, currentLocation) }

                null -> (tyre as? Tyre.SensorLocated)
                    // A sensor advertising its location (Sysgration) is detected there, unless
                    // it's bound: its binding says where it belongs, maybe to another vehicle
                    ?.takeIf { sensorInput ->
                        when (currentLocation) {
                            is Location.Axle -> currentLocation.axle == sensorInput.location.axle
                            is Location.Side -> currentLocation.side == sensorInput.location.side
                            is Location.Wheel -> currentLocation.location == sensorInput.location
                            // Sysgration sensors only advertise the four wheels of a car
                            Location.Spare -> false
                            // Whichever wheel it was made for, it's the only one
                            Location.Single -> true
                        }
                    }
                    ?.takeIf { sensorBindingUseCase.isBound(it.sensorId).not() }
                    // Its readings wait for it to be bound, so it can't be relied on unbound
                    ?.also {
                        if (showsUnbound.not()) detected.value = Detection(
                            Sensor(it.sensorId, currentLocation, it.brand),
                            TimeSource.Monotonic.markNow(),
                        )
                    }
                    ?.takeIf { showsUnbound }
                    ?.let { Tyre.Located(it, currentLocation) }

                // A location with a bound sensor only shows that sensor
                else -> tyre
                    .takeIf { it.sensorId == sensor.id }
                    ?.let { Tyre.Located(it, sensor.location) }
            }
        }

    private data class Detection(val sensor: Sensor, val heardAt: ValueTimeMark)

    internal companion object {
        /** Sysgration sensors advertise rarely while parked, but a neighbour's car can leave */
        val DETECTION_TIMEOUT = 10.minutes
    }
}
