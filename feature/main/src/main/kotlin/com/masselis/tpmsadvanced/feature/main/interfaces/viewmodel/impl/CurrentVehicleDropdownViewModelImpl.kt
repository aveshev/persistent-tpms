package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.State
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleAlertLevelsUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
internal class CurrentVehicleDropdownViewModelImpl(
    private val currentVehicleUseCase: CurrentVehicleUseCase,
    vehicleListUseCase: VehicleListUseCase,
    vehicleAlertLevelsUseCase: VehicleAlertLevelsUseCase,
    @Assisted savedStateHandle: SavedStateHandle,
) : ViewModel(), CurrentVehicleDropdownViewModel {

    @AssistedFactory
    interface Factory {
        operator fun invoke(savedStateHandle: SavedStateHandle): CurrentVehicleDropdownViewModelImpl
    }

    private val mutableStateFlow = savedStateHandle.getMutableStateFlow<State>(
        "STATE",
        State.Loading
    )
    override val stateFlow = mutableStateFlow.asStateFlow()

    private val mutableEventChannel = Channel<Event>(Channel.BUFFERED)
    override val eventChannel: ReceiveChannel<Event> = mutableEventChannel

    // Only while shown, as the current vehicle's tyres: listening to the others' scans them too
    override val alerts = vehicleAlertLevelsUseCase
        .levels
        .map { levels -> levels.filterValues { it >= RED } }
        .stateIn(viewModelScope, WhileSubscribed(), emptyMap())

    init {
        combine(
            currentVehicleUseCase.flatMapLatest { it.vehicleStateFlow },
            vehicleListUseCase.vehicleListFlow
        ) { current, list ->
            State.Vehicles(current, list.sortedBy { it.name })
        }.onEach { mutableStateFlow.value = it }
            .launchIn(viewModelScope)
    }

    override fun setCurrent(vehicle: Vehicle) {
        viewModelScope.launch {
            currentVehicleUseCase.setAsCurrent(vehicle)
        }
    }

    override fun insert(carName: String, kind: Vehicle.Kind) {
        viewModelScope.launch {
            currentVehicleUseCase
                .insertAsCurrent(carName, kind)
                .also { mutableEventChannel.send(Event.VehicleAdded(it)) }
        }
    }
}
