package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibrations
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The pressure calibration of each of the vehicle's sensors. Turning a calibration off keeps its
 * values, so turning it back on restores them.
 */
public class VehicleCalibrationUseCase internal constructor(
    private val vehicle: Vehicle,
    private val sensorDatabase: SensorDatabase,
    private val readingDatabase: ReadingDatabase,
) {

    /** The calibration to apply to each sensor's read pressures */
    public val calibrations: Flow<PressureCalibrations> = sensorDatabase
        .selectCalibrations(vehicle.uuid)
        .asFlow()
        .map { all ->
            all
                .mapNotNull { (id, calibration) -> calibration.applied?.let { id to it } }
                .toMap()
                .let(::PressureCalibrations)
        }

    public fun of(sensorId: Int): Flow<SensorCalibration> = sensorDatabase
        .selectCalibration(sensorId)
        .asFlow()
        .map { it ?: SensorCalibration.None }

    /** The calibration of the vehicle's sensors other than [sensorId] */
    public fun othersThan(sensorId: Int): Flow<List<SensorCalibration>> = sensorDatabase
        .selectCalibrations(vehicle.uuid)
        .asFlow()
        .map { all -> all.mapNotNull { (id, calibration) -> calibration.takeIf { id != sensorId } } }

    /** Where [sensorId] is on the vehicle, null once it's no longer assigned */
    public fun locationOf(sensorId: Int): Flow<Location?> = sensorDatabase
        .selectById(sensorId)
        .asFlow()
        .map { it?.location }

    /** The pressure [sensorId] read last, as it sent it: before its calibration */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun latestRead(sensorId: Int): Flow<Pressure?> = sensorDatabase
        .selectById(sensorId)
        .asFlow()
        .flatMapLatest { sensor ->
            sensor
                ?.let { readingDatabase.latestBySensorByLocation(sensorId, it.location, vehicle.uuid).asFlow() }
                ?.map { it?.pressure }
                ?: flowOf(null)
        }

    public suspend fun set(sensorId: Int, calibration: SensorCalibration): Unit =
        sensorDatabase.updateCalibration(sensorId, calibration)

    /** Gives every sensor of the vehicle [calibration], once: the ones assigned later have none */
    public suspend fun applyToAll(calibration: SensorCalibration): Unit =
        sensorDatabase.updateCalibrations(vehicle.uuid, calibration)
}
