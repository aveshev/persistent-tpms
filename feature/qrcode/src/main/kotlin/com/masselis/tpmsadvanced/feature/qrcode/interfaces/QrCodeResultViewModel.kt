package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.sensorsFor
import com.masselis.tpmsadvanced.feature.qrcode.usecase.BoundSensorMapUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * What a scanned QR code leads to on the vehicle [vehicleUuid] it was scanned for, see [QrCodeResult].
 * A code with a single sensor goes to [location], the wheel the scan was started from.
 */
@AssistedInject
internal class QrCodeResultViewModel(
    private val vehicleDatabase: VehicleDatabase,
    private val sensorDatabase: SensorDatabase,
    private val boundSensorMapUseCase: BoundSensorMapUseCase,
    @Assisted vehicleUuid: UUID,
    @Assisted location: Location,
    @Assisted result: QrCodeResult,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(vehicleUuid: UUID, location: Location, result: QrCodeResult): QrCodeResultViewModel
    }

    sealed interface State {
        data object Loading : State

        /**
         * Assign [sensors] to [vehicle]? [overwrites] when one of its wheels has another sensor,
         * [reassigns] when one of [sensors] is assigned already, to another wheel or vehicle
         */
        data class Ask(
            val vehicle: Vehicle,
            val sensors: List<Sensor>,
            val overwrites: Boolean,
            val reassigns: Assigned? = null,
        ) : State

        /** Where a sensor is assigned */
        data class Assigned(val vehicle: Vehicle, val location: Location)

        /** [vehicle] has fewer wheels than the code's [count] sensors */
        data class TooManySensors(val vehicle: Vehicle, val count: Int) : State

        /**
         * The code's sensors fit the vehicle's wheels, but two of them land on the same one, e.g.
         * both rear sensors of a car's kit on a tadpole's single rear wheel
         */
        data object Unreadable : State

        /** The code can't be assigned as it is */
        @JvmInline
        value class Unusable(val result: QrCodeResult) : State
    }

    private val mutableStateFlow = MutableStateFlow<State>(State.Loading)
    val stateFlow: StateFlow<State> = mutableStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = withContext(IO) { vehicleDatabase.selectByUuid(vehicleUuid).execute() }
            mutableStateFlow.value = when (result) {
                is QrCodeResult.Sensors -> result
                    .sensors
                    .sensorsFor(vehicle.kind)
                    ?.let { sensors ->
                        // Assigning both would break the one sensor per wheel the database holds
                        if (sensors.distinctBy { it.location }.size < sensors.size) State.Unreadable
                        else ask(vehicle, sensors)
                    }
                    ?: State.TooManySensors(vehicle, result.sensors.size)

                is QrCodeResult.Wicarlink -> ask(vehicle, listOf(Sensor(result.id, location, WICARLINK)))

                is QrCodeResult.DuplicateWheelLocation, is QrCodeResult.DuplicateId -> State.Unusable(result)
            }
        }
    }

    private suspend fun ask(vehicle: Vehicle, sensors: List<Sensor>) = withContext(IO) {
        val assigned = sensorDatabase.selectListByVehicleId(vehicle.uuid).execute()
        State.Ask(
            vehicle,
            sensors,
            overwrites = sensors.any { sensor ->
                assigned.any { it.location == sensor.location && it.id != sensor.id }
            },
            reassigns = sensors.firstNotNullOfOrNull { sensor ->
                sensorDatabase
                    .selectById(sensor.id)
                    .execute()
                    ?.let { current ->
                        State.Assigned(
                            checkNotNull(vehicleDatabase.selectBySensorId(sensor.id).execute()),
                            current.location,
                        )
                    }
                    ?.takeIf { it.vehicle.uuid != vehicle.uuid || it.location != sensor.location }
            },
        )
    }

    /** Kept by the activity, it outlives the dialog closing as soon as it's answered */
    fun bind() = viewModelScope.launch {
        val state = mutableStateFlow.value as? State.Ask ?: return@launch
        boundSensorMapUseCase.bind(state.vehicle.uuid, state.sensors)
    }
}
