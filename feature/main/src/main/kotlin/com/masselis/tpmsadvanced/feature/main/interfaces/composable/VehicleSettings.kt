@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import kotlinx.coroutines.delay
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localized

/**
 * The vehicle's settings, the alerts being summarised here and edited on their own pages opened by
 * [openPressure], [openTemperature] and [openBattery]. [openManageSensors] manages the
 * sensors on the main screen, where each sensor is calibrated. [openShareSensors] shows them as a
 * QR code for another phone, scanned from a wheel's assignment.
 */
@Suppress("LongMethod", "MaxLineLength", "CyclomaticComplexMethod")
@Composable
public fun VehicleSettings(
    openPressure: () -> Unit,
    openTemperature: () -> Unit,
    openBattery: () -> Unit,
    openManageSensors: () -> Unit,
    openShareSensors: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundSettings: @Composable (VehicleComponent) -> Unit = backgroundSettingsPlaceholder,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val vehicle by viewModel.vehicle.collectAsState()
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val low by viewModel.lowPressure.collectAsState()
    val high by viewModel.highPressure.collectAsState()
    val rearLow by viewModel.rearLowPressure.collectAsState()
    val rearHigh by viewModel.rearHighPressure.collectAsState()
    val separateRear by viewModel.separateRearPressure.collectAsState()
    val spareLow by viewModel.spareLowPressure.collectAsState()
    val spareHigh by viewModel.spareHighPressure.collectAsState()
    val separateSpare by viewModel.separateSparePressure.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val lowTemp by viewModel.lowTemp.collectAsState()
    val highTemp by viewModel.highTemp.collectAsState()
    val lowBatteryVoltage by viewModel.lowBatteryVoltage.collectAsState()
    val lowBatteryPercent by viewModel.lowBatteryPercent.collectAsState()
    val batteryKinds by viewModel.batteryKinds.collectAsState()
    var showRename by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        SettingsSectionHeader(stringResource(CommonR.string.alert_thresholds))
        SettingsGroup {
            fun ClosedFloatingPointRange<Pressure>.summary() =
                "${start.numberString(pressureUnit)} – ${endInclusive.numberString(pressureUnit)} ${pressureUnit.symbol()}"
            TextSettingsItem(
                headline = localized(CommonR.string.pressure),
                supporting = listOfNotNull(
                    rearLow
                        ?.takeIf { separateRear }
                        ?.let { start -> rearHigh?.let { start..it } }
                        ?.let { localized(CommonR.string.rear_2, it.summary()) },
                    spareLow
                        ?.takeIf { separateSpare }
                        ?.let { start -> spareHigh?.let { start..it } }
                        ?.let { localized(CommonR.string.spare_2, it.summary()) },
                )
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(localized(CommonR.string.front_2, (low..high).summary())) + it }
                    ?.joinToString(" · ")
                    ?: (low..high).summary(),
                onClick = openPressure,
            )
            TextSettingsItem(
                headline = localized(CommonR.string.temperature),
                supporting = localized(CommonR.string.cold_hot, lowTemp.numberString(temperatureUnit), highTemp.numberString(temperatureUnit), temperatureUnit.symbol()),
                onClick = openTemperature,
            )
            TextSettingsItem(
                headline = localized(CommonR.string.battery),
                // Like the battery page, both while nothing is known about the sensors
                supporting = when {
                    batteryKinds.hasVoltage && batteryKinds.hasPercent.not() ->
                        localized(CommonR.string.low_voltage_alarm_2, lowBatteryVoltage.string())

                    batteryKinds.hasPercent && batteryKinds.hasVoltage.not() ->
                        localized(CommonR.string.low_battery_alarm_2, lowBatteryPercent)

                    else -> localized(CommonR.string.low_alarms, lowBatteryVoltage.string(), lowBatteryPercent)
                },
                onClick = openBattery,
                modifier = Modifier.testTag(VehicleSettingsTags.battery),
            )
        }
        if (backgroundSettings !== backgroundSettingsPlaceholder) {
            SettingsSectionHeader(localized(CommonR.string.background))
            backgroundSettings(component)
        }
        SettingsSectionHeader(localized(CommonR.string.sensors))
        SettingsGroup {
            TextSettingsItem(
                headline = localized(CommonR.string.manage_sensors),
                supporting = localized(CommonR.string.move_calibrate_delete),
                onClick = openManageSensors,
                modifier = Modifier.testTag(VehicleSettingsTags.manageSensors),
            )
            TextSettingsItem(
                headline = localized(CommonR.string.share_sensors),
                supporting = localized(CommonR.string.share_with_another_phone_via_qr),
                onClick = openShareSensors,
                modifier = Modifier.testTag(VehicleSettingsTags.shareSensors),
            )
            ClearBoundSensorsButton()
        }
        SettingsSectionHeader(localized(CommonR.string.vehicle))
        SettingsGroup {
            TextSettingsItem(
                headline = localized(CommonR.string.edit_vehicle_name),
                onClick = { showRename = true },
            )
            DeleteVehicleButton()
        }
    }
    if (showRename) RenameVehicleDialog(
        name = vehicle.name,
        onRename = { viewModel.rename(it); showRename = false },
        onDismissRequest = { showRename = false },
    )
}

@Composable
private fun RenameVehicleDialog(
    name: String,
    onRename: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Starts fully selected so typing replaces the name
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(name, TextRange(0, name.length)))
    }
    val canRename = text.text.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(CommonR.string.vehicle_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canRename) onRename(text.text) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text.text) }, enabled = canRename) { Text(stringResource(CommonR.string.rename_2)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(CommonR.string.cancel)) }
        },
        modifier = modifier,
    )
    LaunchedEffect(Unit) {
        // Same delay as the add vehicle dialog: the dialog's window must be shown to take the focus
        delay(200)
        focusRequester.requestFocus()
    }
}

@Preview
@Composable
internal fun RenameVehicleDialogPreview() {
    RenameVehicleDialog(name = "My car", onRename = {}, onDismissRequest = {})
}

@Suppress("ConstPropertyName")
internal object VehicleSettingsTags {
    const val manageSensors = "VehicleSettingsTags_manageSensors"
    const val shareSensors = "VehicleSettingsTags_shareSensors"
    const val battery = "VehicleSettingsTags_battery"
}

private val backgroundSettingsPlaceholder: @Composable (VehicleComponent) -> Unit = {}
