package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehicleBatterySettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehiclePressureSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehicleSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehicleTemperatureSettings
import com.masselis.tpmsadvanced.interfaces.composable.SettingsTag.vehicle

@Composable
internal fun Settings(
    openPressure: () -> Unit,
    openTemperature: () -> Unit,
    openBattery: () -> Unit,
    openManageSensors: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehicleSettings(
        openPressure = openPressure,
        openTemperature = openTemperature,
        openBattery = openBattery,
        openManageSensors = openManageSensors,
        // backgroundSettings = { AutomaticBackgroundSettings(it) }
        modifier = Modifier.testTag(vehicle),
    )
}

@Composable
internal fun PressureSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehiclePressureSettings()
}

@Composable
internal fun TemperatureSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehicleTemperatureSettings()
}

@Composable
internal fun BatterySettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehicleBatterySettings()
}

internal object SettingsTag {
    const val vehicle = "SettingsTag_vehicle"
}
