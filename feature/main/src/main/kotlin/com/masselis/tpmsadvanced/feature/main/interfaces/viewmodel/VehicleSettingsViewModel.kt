package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal interface VehicleSettingsViewModel {
    val vehicle: StateFlow<Vehicle>

    val lowPressure: MutableStateFlow<Pressure>
    val highPressure: MutableStateFlow<Pressure>
    val rearLowPressure: MutableStateFlow<Pressure?>
    val rearHighPressure: MutableStateFlow<Pressure?>
    val separateRearPressure: StateFlow<Boolean>
    val spareLowPressure: MutableStateFlow<Pressure?>
    val spareHighPressure: MutableStateFlow<Pressure?>
    val separateSparePressure: StateFlow<Boolean>
    val pressureUnit: StateFlow<PressureUnit>

    val highTemp: MutableStateFlow<Temperature>
    val normalTemp: MutableStateFlow<Temperature>
    val lowTemp: MutableStateFlow<Temperature>

    val temperatureUnit: StateFlow<TemperatureUnit>

    val lowBatteryVoltage: MutableStateFlow<Voltage>
    val lowBatteryPercent: MutableStateFlow<Int>

    /** Every location of the vehicle has a sensor: none is outlined to be tapped, see TyreTapArea */
    val isFullyAssigned: StateFlow<Boolean>

    /** What the vehicle's sensors report their battery as */
    val batteryKinds: StateFlow<BatteryKinds>

    fun setRearOverrideEnabled(enabled: Boolean)

    fun setSpareOverrideEnabled(enabled: Boolean)

    fun rename(name: String)
}
