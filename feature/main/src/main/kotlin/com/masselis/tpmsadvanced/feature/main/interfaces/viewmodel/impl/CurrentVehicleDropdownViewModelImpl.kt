package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
internal class CurrentVehicleDropdownViewModelImpl(
    private val currentVehicleUseCase: CurrentVehicleUseCase,
    private val vehicleListUseCase: VehicleListUseCase,
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

    init {
        combine(
            currentVehicleUseCase.flatMapLatest { it.vehicleStateFlow },
            vehicleListUseCase.vehicleListFlow
        ) { current, list ->
            State.Vehicles(current, list.sortedBy { it.name })
        }.onEach { mutableStateFlow.value = it }
            .launchIn(viewModelScope)
    }

    override suspend fun scanVehicles() = vehicleListUseCase
        .vehicleListFlow
        // Editing a vehicle (ranges, name...) re-emits the list; only a change in the set of
        // vehicles resubscribes, otherwise the scans restart
        .distinctUntilChanged { old, new -> old.map(Vehicle::uuid) == new.map(Vehicle::uuid) }
        .flatMapLatest { vehicles ->
            vehicles
                .flatMap { vehicle ->
                    VehicleComponent(vehicle).let { component ->
                        vehicle.kind.locations.map { component.TyreComponent(it).tyreAtmosphereUseCase.listen() }
                    }
                }
                .merge()
        }
        .catch { Logger.withTag("CurrentVehicleDropdownViewModel").e("Failed to scan the vehicles", it) }
        .collect {}

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
