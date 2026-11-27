package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.LocatedTyreScannerUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase.OtherVehicle
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How one location of the vehicle can be tapped to assign, or manage, its sensor */
internal class TyreActionsViewModel(
    private val sensorBindingUseCase: SensorBindingUseCase,
    scannerUseCase: LocatedTyreScannerUseCase,
    demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
    tyreStats: StateFlow<TyreStatsStateFlow.State>,
) : ViewModel() {

    sealed interface State {
        /** The demo's readings are shown without any sensor to assign */
        data object Demo : State

        /** [variant] from the sensor's latest reading, null until there's one */
        data class Assigned(val sensor: Sensor, val variant: SensorVariant? = null) : State

        /** No sensor here yet, [detected] can be assigned without scanning anything */
        data class Unassigned(val detected: Sensor?) : State
    }

    val stateFlow: StateFlow<State> = combine(
        sensorBindingUseCase.boundSensor(),
        scannerUseCase.detectedSensor(),
        demoOrBleScannerUseCase.isDemo,
        tyreStats,
        ::state,
    ).stateIn(
        viewModelScope,
        WhileSubscribed(),
        // From the current values, so a bound tyre doesn't flash "Tap to assign"
        state(
            sensorBindingUseCase.boundSensor().value,
            null,
            demoOrBleScannerUseCase.isDemo.value,
            tyreStats.value,
        ),
    )

    private fun state(
        bound: Sensor?,
        detected: Sensor?,
        isDemo: Boolean,
        stats: TyreStatsStateFlow.State,
    ): State = when {
        isDemo -> State.Demo
        bound != null -> State.Assigned(
            bound,
            (stats as? TyreStatsStateFlow.State.Detected)?.takeIf { it.sensorId == bound.id }?.variant,
        )
        else -> State.Unassigned(detected)
    }

    /** Where the assigned sensor can be moved to, besides this vehicle */
    val otherVehicles: StateFlow<List<OtherVehicle>> = sensorBindingUseCase
        .otherVehicles()
        .stateIn(viewModelScope, WhileSubscribed(), emptyList())

    fun assign(sensor: Sensor) {
        viewModelScope.launch { sensorBindingUseCase.bind(sensor) }
    }

    fun delete() {
        viewModelScope.launch { sensorBindingUseCase.unbind() }
    }

    /** Suspends until it's moved, a page leaving once it's done would cancel it in viewModelScope */
    suspend fun moveToVehicle(vehicle: Vehicle, location: Location, swap: Boolean): Unit =
        sensorBindingUseCase.moveToVehicle(vehicle, location, swap)
}
