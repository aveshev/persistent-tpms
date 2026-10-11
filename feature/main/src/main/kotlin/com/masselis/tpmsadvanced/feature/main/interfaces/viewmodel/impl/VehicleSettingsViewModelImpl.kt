package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.BatteryKinds
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.RenameVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class VehicleSettingsViewModelImpl(
    private val vehicleRangesUseCase: VehicleRangesUseCase,
    private val renameVehicleUseCase: RenameVehicleUseCase,
    override val vehicle: StateFlow<Vehicle>,
    unitPreferences: UnitPreferences,
    sensorDatabase: SensorDatabase,
) : ViewModel(), VehicleSettingsViewModel {

    override val lowPressure = vehicleRangesUseCase.lowPressure
    override val highPressure = vehicleRangesUseCase.highPressure
    override val rearLowPressure = vehicleRangesUseCase.rearLowPressure
    override val rearHighPressure = vehicleRangesUseCase.rearHighPressure
    override val separateRearPressure = vehicleRangesUseCase.separateRearPressure.asStateFlow()
    override val spareLowPressure = vehicleRangesUseCase.spareLowPressure
    override val spareHighPressure = vehicleRangesUseCase.spareHighPressure
    override val separateSparePressure = vehicleRangesUseCase.separateSparePressure.asStateFlow()

    override val pressureUnit = unitPreferences.pressure.asStateFlow()

    override val highTemp = vehicleRangesUseCase.highTemp
    override val normalTemp = vehicleRangesUseCase.normalTemp
    override val lowTemp = vehicleRangesUseCase.lowTemp

    override val temperatureUnit = unitPreferences.temperature.asStateFlow()

    override val lowBatteryVoltage = vehicleRangesUseCase.lowBatteryVoltage
    override val lowBatteryPercent = vehicleRangesUseCase.lowBatteryPercent

    override val isFullyAssigned = sensorDatabase
        .selectListByVehicleId(vehicle.value.uuid)
        // Read now rather than after the first frame, the vehicle's size depends on it
        .asStateFlow(viewModelScope, WhileSubscribed())
        .let { sensors ->
            sensors
                .map { vehicle.value.isFullyAssigned(it) }
                .stateIn(viewModelScope, WhileSubscribed(), vehicle.value.isFullyAssigned(sensors.value))
        }

    private fun Vehicle.isFullyAssigned(sensors: List<Sensor>) = sensors
        .map { it.location }
        .containsAll(kind.locations)

    override val batteryKinds = sensorDatabase
        .brandsByVehicleId(vehicle.value.uuid)
        .asFlow()
        .map { BatteryKinds.of(it) }
        .stateIn(viewModelScope, WhileSubscribed(), BatteryKinds(hasVoltage = false, hasPercent = false))

    override fun setRearOverrideEnabled(enabled: Boolean): Unit =
        vehicleRangesUseCase.setRearOverrideEnabled(enabled)

    override fun setSpareOverrideEnabled(enabled: Boolean): Unit =
        vehicleRangesUseCase.setSpareOverrideEnabled(enabled)

    override fun rename(name: String) {
        viewModelScope.launch { renameVehicleUseCase.rename(name) }
    }
}
