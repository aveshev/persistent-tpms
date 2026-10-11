package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOne
import com.masselis.tpmsadvanced.core.database.QueryOne.Companion.asOne
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

@Suppress("TooManyFunctions")
public class VehicleDatabase internal constructor(database: Database) {

    private val queries = database.vehicleQueries

    init {
        queries.deleteIsDeleting()
    }

    public suspend fun insert(
        id: UUID,
        kind: Vehicle.Kind,
        name: String,
        isCurrent: Boolean,
        defaultsToSeparateFrontRearPressure: Boolean = false,
    ): Unit = withContext(IO) {
        // Runs as a single transaction so a reactive observer (e.g. a settings screen navigated
        // to right after creation) never sees the row with the rear override half-applied.
        queries.transaction {
            queries.insert(id, kind, name, isCurrent)
            // The schema's default predates it, see DEFAULT_HIGH_PRESSURE
            queries.updateHighPressure(DEFAULT_HIGH_PRESSURE, id)
            if (defaultsToSeparateFrontRearPressure) {
                val lowPressure = queries.selectLowPressureByVehicleId(id).executeAsOne()
                val highPressure = queries.selectHighPressureByVehicleId(id).executeAsOne()
                queries.updateRearLowPressure(lowPressure, id)
                queries.updateRearHighPressure(highPressure, id)
                queries.updateSeparateRearPressure(true, id)
            }
        }
    }

    public suspend fun setIsCurrent(uuid: UUID, isCurrent: Boolean): Unit = withContext(IO) {
        queries.setAsFavourite(isCurrent, uuid)
    }

    public suspend fun updateName(name: String, uuid: UUID): Unit = withContext(IO) {
        queries.updateName(name, uuid)
    }

    public fun selectLowPressure(vehicleId: UUID): Pressure =
        queries.selectLowPressureByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateLowPressure(lowPressure: Pressure, vehicleId: UUID): Unit =
        withContext(IO) {
            queries.updateLowPressure(lowPressure, vehicleId)
        }

    public fun selectHighPressure(vehicleId: UUID): Pressure =
        queries.selectHighPressureByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateHighPressure(highPressure: Pressure, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateHighPressure(highPressure, uuid)
        }

    public fun selectRearLowPressure(vehicleId: UUID): Pressure? =
        queries.selectRearLowPressureByVehicleId(vehicleId).executeAsOne().rearLowPressure

    public suspend fun updateRearLowPressure(rearLowPressure: Pressure?, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateRearLowPressure(rearLowPressure, uuid)
        }

    public fun selectRearHighPressure(vehicleId: UUID): Pressure? =
        queries.selectRearHighPressureByVehicleId(vehicleId).executeAsOne().rearHighPressure

    public suspend fun updateRearHighPressure(rearHighPressure: Pressure?, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateRearHighPressure(rearHighPressure, uuid)
        }

    public fun selectSeparateRearPressure(vehicleId: UUID): Boolean =
        queries.selectSeparateRearPressureByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateSeparateRearPressure(separate: Boolean, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateSeparateRearPressure(separate, uuid)
        }

    public fun selectSpareLowPressure(vehicleId: UUID): Pressure? =
        queries.selectSpareLowPressureByVehicleId(vehicleId).executeAsOne().spareLowPressure

    public suspend fun updateSpareLowPressure(spareLowPressure: Pressure?, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateSpareLowPressure(spareLowPressure, uuid)
        }

    public fun selectSpareHighPressure(vehicleId: UUID): Pressure? =
        queries.selectSpareHighPressureByVehicleId(vehicleId).executeAsOne().spareHighPressure

    public suspend fun updateSpareHighPressure(spareHighPressure: Pressure?, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateSpareHighPressure(spareHighPressure, uuid)
        }

    public fun selectSeparateSparePressure(vehicleId: UUID): Boolean =
        queries.selectSeparateSparePressureByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateSeparateSparePressure(separate: Boolean, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateSeparateSparePressure(separate, uuid)
        }

    public fun selectLowBatteryVoltage(vehicleId: UUID): Voltage =
        queries.selectLowBatteryVoltageByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateLowBatteryVoltage(voltage: Voltage, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateLowBatteryVoltage(voltage, uuid)
        }

    public fun selectLowBatteryPercent(vehicleId: UUID): Int =
        queries.selectLowBatteryPercentByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateLowBatteryPercent(percent: Int, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateLowBatteryPercent(percent, uuid)
        }

    public fun selectLowTemp(vehicleId: UUID): Temperature =
        queries.selectLowTempByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateLowTemp(lowTemp: Temperature, uuid: UUID): Unit = withContext(IO) {
        queries.updateLowTemp(lowTemp, uuid)
    }

    public fun selectNormalTemp(vehicleId: UUID): Temperature =
        queries.selectNormalTempByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateNormalTemp(normalTemp: Temperature, uuid: UUID): Unit =
        withContext(IO) {
            queries.updateNormalTemp(normalTemp, uuid)
        }

    public fun selectHighTemp(vehicleId: UUID): Temperature =
        queries.selectHighTempByVehicleId(vehicleId).executeAsOne()

    public suspend fun updateHighTemp(highTemp: Temperature, uuid: UUID): Unit = withContext(IO) {
        queries.updateHighTemp(highTemp, uuid)
    }

    public suspend fun setIsDeleting(uuid: UUID): Unit = withContext(IO) {
        queries.updateIsDeleting(true, uuid)
    }

    public suspend fun delete(uuid: UUID): Unit = withContext(IO) {
        queries.delete(uuid)
    }

    public fun currentVehicle(): QueryOne<Vehicle> = queries.currentFavourite(mapper).asOne()

    public fun count(): QueryOne<Long> = queries.count().asOne()

    public fun selectUuidIsDeleting(): QueryList<UUID> = queries.selectUuidIsDeleting().asList()

    public fun selectAll(): QueryList<Vehicle> = queries.selectAll(mapper).asList()

    public fun selectByUuid(vehicleId: UUID): QueryOne<Vehicle> = queries
        .selectByUuid(vehicleId, mapper)
        .asOne()

    public fun selectBySensorId(sensorId: Int): QueryOneOrNull<Vehicle> = queries
        .selectBySensorId(sensorId, mapper)
        .asOneOrNull()

    public companion object {
        /**
         * The maximum pressure of a new vehicle, at the high end of the maximum inflation pressures
         * marked on tyre sidewalls, which the maximum is meant to be set to. The schema's default,
         * 300 kPa, predates it: SQLite can't change it without rebuilding the table.
         */
        public val DEFAULT_HIGH_PRESSURE: Pressure = 50f.psi

        private val mapper: (
            UUID,
            String,
            Boolean,
            Pressure,
            Pressure,
            Temperature,
            Temperature,
            Temperature,
            Vehicle.Kind,
            Boolean,
            Pressure?,
            Pressure?,
            Boolean,
            Boolean,
            Pressure,
            Double,
            Voltage,
            Int,
            Pressure?,
            Pressure?,
            Boolean,
        ) -> Vehicle =
            // The unused pressure calibration, the low battery alarms and the spare's range are
            // only read by the vehicle scope's own queries
            { uuid, name, _, lowPressure, highPressure, lowTemp, normalTemp, highTemp, kind, _,
              rearLowPressure, rearHighPressure, separateRearPressure, _, _, _, _, _, _, _, _ ->
                Vehicle(
                    uuid,
                    kind,
                    name,
                    lowPressure,
                    highPressure,
                    lowTemp,
                    normalTemp,
                    highTemp,
                    rearLowPressure,
                    rearHighPressure,
                    separateRearPressure,
                )
            }
    }
}
