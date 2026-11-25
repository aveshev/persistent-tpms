package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * The vehicle the database is created with, see Vehicle.sq, which the introduction replaces with
 * the user's own: only while it's still as created, the only vehicle, named and shaped the same,
 * without any sensor. It can't be deleted before another vehicle exists, the app always shows one.
 */
internal class DefaultVehicleUseCase(
    private val vehicleDatabase: VehicleDatabase,
    private val sensorDatabase: SensorDatabase,
    private val currentVehicleUseCase: CurrentVehicleUseCase,
    private val delete: suspend (Vehicle) -> Unit,
) {

    /** The vehicle created with the database, null once the user has used or changed it */
    suspend fun untouched(): Vehicle? = withContext(IO) {
        vehicleDatabase
            .selectAll()
            .execute()
            .singleOrNull()
            ?.takeIf { it.name == DEFAULT_NAME && it.kind == CAR }
            ?.takeIf { sensorDatabase.countByVehicle(it.uuid).execute() == 0L }
    }

    /**
     * Adds the vehicle [name] of [kind] as the current one, then deletes the default vehicle if
     * it's still [untouched]
     */
    suspend fun replace(name: String, kind: Vehicle.Kind) {
        val default = untouched()
        currentVehicleUseCase.insertAsCurrent(name, kind)
        default?.also { delete(it) }
    }

    companion object {
        const val DEFAULT_NAME = "My car"
    }
}
