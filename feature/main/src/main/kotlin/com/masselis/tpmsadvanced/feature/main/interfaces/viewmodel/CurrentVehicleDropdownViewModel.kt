package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import java.util.UUID

internal interface CurrentVehicleDropdownViewModel {

    sealed class State : Parcelable {
        @Parcelize
        data object Loading : State()

        @Parcelize
        data class Vehicles(
            val current: Vehicle,
            val list: List<Vehicle>,
        ) : State()
    }

    sealed interface Event {
        data class VehicleAdded(val uuid: UUID) : Event
    }

    val stateFlow: StateFlow<State>
    val eventChannel: ReceiveChannel<Event>

    /** The red or crimson alert of each vehicle other than the current one, by its uuid */
    val otherAlerts: StateFlow<Map<UUID, AlertLevel>>

    fun setCurrent(vehicle: Vehicle)

    fun insert(carName: String, kind: Vehicle.Kind)
}
