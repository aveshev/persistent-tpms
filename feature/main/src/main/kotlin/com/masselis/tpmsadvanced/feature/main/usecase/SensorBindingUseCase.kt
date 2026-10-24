package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.plus

/** The id of the sensor bound, once per sensor: a binding change, not a database refresh */
internal fun Flow<Sensor?>.boundIds(): Flow<Int?> = map { it?.id }.distinctUntilChanged()

/**
 * What [transform] tells of the sensor bound, starting over from [notDetected] when another one is
 * bound. Collected again, by a screen coming back, the state kept from the last collection stays
 * until [transform] tells otherwise rather than [notDetected] in between, unless it was another
 * sensor's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<Int?>.flatMapLatestBound(notDetected: T, transform: (Int?) -> Flow<T>): Flow<T> {
    // The sensor the kept state is from, across collections as stateIn keeps the state
    var keptFor: Int? = null
    return flatMapLatest { id ->
        transform(id).onStart {
            if (id != keptFor) emit(notDetected)
            keptFor = id
        }
    }
}

internal interface SensorBindingUseCase {

    fun boundSensor(): StateFlow<Sensor?>
    fun boundVehicle(sensor: Sensor): Flow<Vehicle?>

    /** `true` when the sensor [sensorId] is bound to any location of any vehicle */
    fun isBound(sensorId: Int): Boolean

    suspend fun bind(sensor: Sensor)

    /** Unbinds the sensor bound to this location, if any */
    suspend fun unbind()

    /** Every vehicle but this one, the sensor bound here can be moved to them */
    fun otherVehicles(): Flow<List<OtherVehicle>>

    /**
     * Moves the sensor bound here to [location] of [vehicle], see [SensorDatabase.moveToVehicle]:
     * the sensor there comes here when [swap], it's unbound otherwise
     */
    suspend fun moveToVehicle(vehicle: Vehicle, location: Location, swap: Boolean)

    /** Another vehicle, and its locations with a sensor */
    data class OtherVehicle(val vehicle: Vehicle, val occupied: Set<Location>)

    class Impl(
        private val currentVehicle: Vehicle,
        private val vehicleDatabase: VehicleDatabase,
        private val sensorDatabase: SensorDatabase,
        private val currentLocation: Location,
        scope: CoroutineScope,
    ) : SensorBindingUseCase {

        private val boundSensor = sensorDatabase.selectByVehicleAndLocation(
            currentVehicle.uuid,
            currentLocation
        ).asStateFlow(scope + IO, Eagerly)

        override fun boundSensor(): StateFlow<Sensor?> = boundSensor

        override fun boundVehicle(sensor: Sensor) = vehicleDatabase
            .selectBySensorId(sensor.id)
            .asFlow()

        override fun isBound(sensorId: Int): Boolean =
            sensorDatabase.selectById(sensorId).execute() != null

        override suspend fun bind(sensor: Sensor) =
            sensorDatabase.upsert(sensor, currentVehicle.uuid)

        override suspend fun unbind() =
            sensorDatabase.deleteFromVehicle(currentVehicle.uuid, currentLocation)

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun otherVehicles(): Flow<List<OtherVehicle>> = vehicleDatabase
            .selectAll()
            .asFlow()
            .map { vehicles -> vehicles.filter { it.uuid != currentVehicle.uuid } }
            .flatMapLatest { vehicles ->
                vehicles
                    .map { vehicle ->
                        sensorDatabase
                            .selectListByVehicleId(vehicle.uuid)
                            .asFlow()
                            .map { sensors -> OtherVehicle(vehicle, sensors.map { it.location }.toSet()) }
                    }
                    // Combining nothing never emits
                    .takeIf { it.isNotEmpty() }
                    ?.let { flows -> combine(flows) { it.toList() } }
                    ?: flowOf(emptyList())
            }

        override suspend fun moveToVehicle(vehicle: Vehicle, location: Location, swap: Boolean) =
            sensorDatabase.moveToVehicle(currentVehicle.uuid, currentLocation, vehicle.uuid, location, swap)
    }

    object NoOp : SensorBindingUseCase {
        override fun boundSensor(): StateFlow<Sensor?> = MutableStateFlow(null)
        override fun boundVehicle(sensor: Sensor): Flow<Vehicle?> = MutableStateFlow(null)
        override fun isBound(sensorId: Int): Boolean = false
        override suspend fun bind(sensor: Sensor) {}
        override suspend fun unbind() {}
        override fun otherVehicles(): Flow<List<OtherVehicle>> = flowOf(emptyList())
        override suspend fun moveToVehicle(vehicle: Vehicle, location: Location, swap: Boolean) {}
    }
}
