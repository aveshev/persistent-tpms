package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOne
import com.masselis.tpmsadvanced.core.database.QueryOne.Companion.asOne
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.Sensor as SensorRow
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

@Suppress("TooManyFunctions")
public class SensorDatabase internal constructor(
    private val database: Database,
) {
    private val vehicleQueries = database.vehicleQueries
    private val queries = database.sensorQueries

    /**
     * This method insert a sensor for a vehicle. If the sensor is already saved for an other
     * vehicle, this method moves the sensor to the new vehicle. If a sensor already exists at the
     * same location, it replaces it. This method also check the vehicle kind when before inserting
     * the sensor.
     */
    @Suppress("CyclomaticComplexMethod")
    public suspend fun upsert(
        sensor: Sensor,
        vehicleId: UUID,
    ): Unit = withContext(IO) {
        database.transaction {
            val kind = vehicleQueries.selectByUuid(vehicleId).executeAsOne().kind
            require(kind.locations.any { it == sensor.location }) {
                @Suppress("MaxLineLength")
                "Filled sensor points to a location which is not handled by the vehicle kind. Kind: $kind, sensor: $sensor"
            }
            queries.deleteByVehicleAndLocation(vehicleId, sensor.location)
            queries.upsert(sensor.id, sensor.location, vehicleId, sensor.brand)
        }
    }

    public suspend fun deleteFromVehicle(vehicleId: UUID): Unit = withContext(IO) {
        queries.deleteByVehicle(vehicleId)
    }

    /**
     * Moves the vehicle's sensors at once, each from the first location of a pair to the second,
     * along with their readings. Every location moved from must have a sensor, a location moved
     * to without being moved from must not, so no sensor is lost.
     */
    public suspend fun move(vehicleId: UUID, moves: List<Pair<Location, Location>>): Unit = withContext(IO) {
        database.transaction {
            moves
                .map { (from, to) ->
                    Placement(queries.selectByVehicleAndLocation(vehicleId, from).executeAsOne(), vehicleId, to)
                }
                .place()
        }
    }

    /**
     * Moves the sensor at [location] of the vehicle [vehicleId] to [toLocation] of the vehicle
     * [toVehicleId], along with its readings and its calibration. The sensor already there, if any,
     * comes to [location] when [swap], with its own, and is unbound otherwise.
     */
    public suspend fun moveToVehicle(
        vehicleId: UUID,
        location: Location,
        toVehicleId: UUID,
        toLocation: Location,
        swap: Boolean,
    ): Unit = withContext(IO) {
        require(vehicleId != toVehicleId) { "Moving on the same vehicle, see move()" }
        database.transaction {
            vehicleQueries
                .selectByUuid(toVehicleId)
                .executeAsOne()
                .kind
                .also { kind -> require(toLocation in kind.locations) { "$toLocation isn't a location of $kind" } }
            listOfNotNull(
                queries
                    .selectByVehicleAndLocation(vehicleId, location)
                    .executeAsOne()
                    .let { Placement(it, toVehicleId, toLocation) },
                queries
                    .selectByVehicleAndLocation(toVehicleId, toLocation)
                    .executeAsOneOrNull()
                    // Replaced, its readings stay with the tyre like those of any sensor unbound
                    ?.also { if (swap.not()) queries.deleteByVehicleAndLocation(toVehicleId, toLocation) }
                    ?.takeIf { swap }
                    ?.let { Placement(it, vehicleId, location) },
            ).place()
        }
    }

    /** A sensor's whole row, so it keeps its calibration, and where it goes */
    private data class Placement(val sensor: SensorRow, val vehicleId: UUID, val location: Location)

    /** Within a transaction, puts each sensor where it goes, with its readings */
    private fun List<Placement>.place() {
        // All freed first, a sensor can take a location another one is leaving
        onEach { (sensor) -> queries.deleteByVehicleAndLocation(sensor.vehicleId, sensor.location) }
            .forEach { (sensor, vehicleId, location) ->
                queries.insertMoved(
                    sensor.id,
                    location,
                    vehicleId,
                    sensor.brand,
                    sensor.pressureCalibration,
                    sensor.pressureOffset,
                    sensor.pressureMultiplier,
                )
                database.readingQueries.parkSensor(vehicleId, location, sensor.vehicleId, sensor.location, sensor.id)
            }
        flatMap { listOf(it.sensor.vehicleId, it.vehicleId) }
            .distinct()
            .forEach { vehicleId ->
                database.readingQueries.unparkSensors(vehicleId)
                database.readingQueries.dropParked(vehicleId)
            }
    }

    /** Unbinds the sensor at [location] of the vehicle, if any */
    public suspend fun deleteFromVehicle(vehicleId: UUID, location: Location): Unit = withContext(IO) {
        queries.deleteByVehicleAndLocation(vehicleId, location)
    }

    public fun selectByVehicleAndLocation(
        vehicleId: UUID,
        location: Location,
    ): QueryOneOrNull<Sensor> = queries
        .selectByVehicleAndLocation(vehicleId, location, mapper)
        .asOneOrNull()

    public fun countByVehicle(vehicleId: UUID): QueryOne<Long> = queries
        .countByVehicle(vehicleId)
        .asOne()

    public fun selectById(id: Int): QueryOneOrNull<Sensor> = queries
        .selectById(id, mapper)
        .asOneOrNull()

    public fun selectListByVehicleId(uuid: UUID): QueryList<Sensor> = queries
        .selectListByVehicleId(uuid, mapper)
        .asList()

    /** The brands of the sensors bound to the vehicle, what its battery alarms apply to */
    public fun brandsByVehicleId(uuid: UUID): QueryList<SensorBrand> = queries
        .brandsByVehicleId(uuid)
        .asList()

    public fun selectListExcludingVehicleId(uuid: UUID): QueryList<Sensor> = queries
        .selectListExcludingVehicleId(uuid, mapper)
        .asList()

    /** Each sensor's calibration, on or off */
    public fun selectCalibrations(vehicleId: UUID): QueryList<Pair<Int, SensorCalibration>> = queries
        .selectCalibrationsByVehicleId(vehicleId) { id, enabled, offset, multiplier ->
            id to SensorCalibration(enabled, PressureCalibration(offset, multiplier.toFloat()))
        }
        .asList()

    public fun selectCalibration(id: Int): QueryOneOrNull<SensorCalibration> = queries
        .selectCalibrationById(id) { enabled, offset, multiplier ->
            SensorCalibration(enabled, PressureCalibration(offset, multiplier.toFloat()))
        }
        .asOneOrNull()

    public suspend fun updateCalibration(id: Int, calibration: SensorCalibration): Unit = withContext(IO) {
        queries.updateCalibration(
            calibration.isEnabled,
            calibration.calibration.offset,
            calibration.calibration.multiplier.toDouble(),
            id,
        )
    }

    /** Gives every sensor of the vehicle the same calibration, see `VehicleCalibrationUseCase.applyToAll` */
    public suspend fun updateCalibrations(vehicleId: UUID, calibration: SensorCalibration): Unit = withContext(IO) {
        queries.updateCalibrationsByVehicleId(
            calibration.isEnabled,
            calibration.calibration.offset,
            calibration.calibration.multiplier.toDouble(),
            vehicleId,
        )
    }

    private companion object {
        // Its calibration is read on its own, see selectCalibrations
        @Suppress("LongParameterList")
        private val mapper: (
            id: Int,
            location: Location,
            vehicleId: UUID,
            brand: SensorBrand,
            pressureCalibration: Boolean,
            pressureOffset: Pressure,
            pressureMultiplier: Double,
        ) -> Sensor = { id, location, _, brand, _, _, _ ->
            Sensor(id, location, brand)
        }
    }
}
