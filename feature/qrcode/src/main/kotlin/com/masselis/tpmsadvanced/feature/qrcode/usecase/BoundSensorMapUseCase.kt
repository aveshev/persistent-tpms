package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

internal interface BoundSensorMapUseCase {
    /**
     * Assigns [sensors] to [vehicleUuid], replacing the sensors of their wheels, then gives the
     * sensors in [calibrations] theirs: a sensor moved from another vehicle loses its own
     */
    suspend fun bind(
        vehicleUuid: UUID,
        sensors: List<Sensor>,
        calibrations: Map<Int, SensorCalibration> = emptyMap(),
    )

    class Impl(private val sensorDatabase: SensorDatabase) : BoundSensorMapUseCase {
        override suspend fun bind(
            vehicleUuid: UUID,
            sensors: List<Sensor>,
            calibrations: Map<Int, SensorCalibration>,
        ) {
            coroutineScope {
                sensors
                    .map { async { sensorDatabase.upsert(it, vehicleUuid) } }
                    .awaitAll()
            }
            calibrations.forEach { (id, calibration) -> sensorDatabase.updateCalibration(id, calibration) }
        }
    }

    object NoOp : BoundSensorMapUseCase {
        override suspend fun bind(
            vehicleUuid: UUID,
            sensors: List<Sensor>,
            calibrations: Map<Int, SensorCalibration>,
        ) {}
    }
}
