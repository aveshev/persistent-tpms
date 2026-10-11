package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SettingsSectionNote
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.appendBold
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CalibrationField.MULTIPLIER
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CalibrationField.OFFSET
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel.Dialog
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key

/**
 * The page correcting the pressure read by [sensorId]. Its edits are only a draft until applied to
 * this sensor or to every sensor of the vehicle, back discards them. [onLeave] once done.
 */
@Suppress("LongMethod")
@Composable
public fun SensorCalibrationSettings(
    sensorId: Int,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val settings: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val viewModel: SensorCalibrationViewModel = component.viewModel(component.key() + ("sensor_id" to "$sensorId")) {
        SensorCalibrationViewModel(it.vehicleCalibrationUseCase, sensorId)
    }
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) when (event) {
            Event.Leave -> onLeave()
        }
    }
    BackHandler(onBack = viewModel::back)
    val unit by settings.pressureUnit.collectAsState()
    val vehicle by settings.vehicle.collectAsState()
    val low by settings.lowPressure.collectAsState()
    val high by settings.highPressure.collectAsState()
    val state by viewModel.stateFlow.collectAsState()
    state?.also { state ->
        // What the sensor reads, or somewhere the user expects their tyres to be: the example speaks
        // to them. A flat or removed tyre's reading wouldn't show much.
        val read = state.latestRead?.takeIf { it.kpa > MIN_EXAMPLE_KPA }
        SensorCalibrationSettings(
            unit = unit,
            vehicleName = vehicle.name,
            location = state.location,
            enabled = state.draft.isEnabled,
            calibration = state.draft.calibration,
            example = read ?: ((low.kpa + high.kpa) / 2).kpa,
            isExampleRead = read != null,
            hasOthers = state.others.isNotEmpty(),
            canApplyToAll = state.canApplyToAll,
            isChanged = state.isChanged,
            onEnabled = viewModel::setEnabled,
            onOffset = viewModel::setOffset,
            onMultiplier = viewModel::setMultiplier,
            onApplyToSensor = viewModel::applyToSensor,
            onApplyToAll = viewModel::applyToAll,
            modifier = modifier,
        )
        when (state.dialog) {
            Dialog.EraseOthers -> AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                text = { Text("Applying to all will erase other sensors' calibration, are you sure?") },
                confirmButton = { TextButton(onClick = viewModel::eraseOthers) { Text("Yes, erase them") } },
                dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
                modifier = Modifier.testTag(SensorCalibrationSettingsTags.eraseOthers),
            )

            Dialog.Discard -> AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                text = { Text("Discard your changes?") },
                confirmButton = { TextButton(onClick = viewModel::discard) { Text("Discard") } },
                dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
                modifier = Modifier.testTag(SensorCalibrationSettingsTags.discard),
            )

            null -> {}
        }
    }
}

@Suppress("LongMethod", "MaxLineLength", "LongParameterList")
@Composable
private fun SensorCalibrationSettings(
    unit: PressureUnit,
    vehicleName: String,
    location: Location?,
    enabled: Boolean,
    calibration: PressureCalibration,
    example: Pressure,
    isExampleRead: Boolean,
    hasOthers: Boolean,
    canApplyToAll: Boolean,
    isChanged: Boolean,
    onEnabled: (Boolean) -> Unit,
    onOffset: (Pressure) -> Unit,
    onMultiplier: (Float) -> Unit,
    onApplyToSensor: () -> Unit,
    onApplyToAll: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier.fillMaxSize()) {
    // The settings scroll, the actions stay anchored at the bottom like the Bluetooth assignment's
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        SettingsIntro(
            "If a sensor is reporting incorrect pressure values, you can adjust it here.\nKeep the multiplier at default (×1.00), unless you're really sure you need to change it."
        )
        SettingsGroup(Modifier.padding(top = 24.dp)) {
            SwitchSettingsItem(
                headline = "Pressure calibration",
                checked = enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.testTag(SensorCalibrationSettingsTags.enabled),
            )
        }
        SettingsSectionHeader("Correction")
        var editing by rememberSaveable { mutableStateOf<CalibrationField?>(null) }
        // Greyed out rather than hidden while the calibration is off, like Android's settings do
        SettingsGroup {
            TextSettingsItem(
                headline = "Offset",
                supporting = calibration.offset.signedWithSymbol(unit),
                onClick = { editing = OFFSET },
                enabled = enabled,
            )
            TextSettingsItem(
                headline = "Multiplier",
                supporting = calibration.multiplier.multiplierString(),
                onClick = { editing = MULTIPLIER },
                enabled = enabled,
            )
        }
        // Under the values it follows from, greyed out with them while the calibration is off
        SettingsSectionNote(
            // "read as": the corrected pressure isn't only shown, the alerts check it too
            text = "Example: ${example.withSymbol(unit)} reported by ${if (isExampleRead) "this" else "a"} sensor is read as ${calibration.applyTo(example).withSymbol(unit)}* (asterisk added when displaying corrected values)",
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        )
        when (editing) {
            OFFSET -> NumberDialog(
                title = "Offset",
                value = calibration.offset.convert(unit),
                range = OffsetLimit.let { (-it).convert(unit)..it.convert(unit) },
                step = unit.offsetStep,
                unit = unit.symbol(),
                format = { it.toPressure(unit).numberString(unit) },
                onConfirm = { onOffset(it.toPressure(unit)); editing = null },
                onDismissRequest = { editing = null },
            )

            MULTIPLIER -> NumberDialog(
                title = "Multiplier",
                value = calibration.multiplier,
                range = MultiplierLimits,
                step = MULTIPLIER_STEP,
                unit = "×",
                format = { "%.2f".format(it) },
                onConfirm = { onMultiplier(it); editing = null },
                onDismissRequest = { editing = null },
            )

            null -> {}
        }
    }
    HorizontalDivider()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Applying to all is the recommended one: the sensors of a vehicle usually read alike. A
        // vehicle with this sensor only has nothing to apply it to, applying to it is all there is.
        val applyToSensor: @Composable () -> Unit = {
            Text(
                buildAnnotatedString {
                    append("Apply to ")
                    // A mono-wheel's only wheel is just "wheel"
                    appendBold(buildString { location?.also { appendLoc(it, withType = false) } ?: append("this") })
                    append(" sensor")
                }
            )
        }
        val sensorModifier = Modifier.fillMaxWidth().testTag(SensorCalibrationSettingsTags.applyToSensor)
        if (hasOthers) {
            OutlinedButton(onClick = onApplyToSensor, enabled = isChanged, modifier = sensorModifier) { applyToSensor() }
            Button(
                onClick = onApplyToAll,
                enabled = canApplyToAll,
                modifier = Modifier.fillMaxWidth().testTag(SensorCalibrationSettingsTags.applyToAll),
            ) {
                Text(
                    buildAnnotatedString {
                        append("Apply to all ")
                        appendBold(vehicleName)
                        append(" sensors")
                    }
                )
            }
        } else Button(onClick = onApplyToSensor, enabled = isChanged, modifier = sensorModifier) { applyToSensor() }
    }
}

private const val MIN_EXAMPLE_KPA = 10f

/** Material's opacity for disabled content */
private const val DISABLED_ALPHA = .38f

private enum class CalibrationField { OFFSET, MULTIPLIER }

/** Far more than "a few units" off, a sensor that far off is broken rather than miscalibrated */
private val OffsetLimit = 20f.psi

@Suppress("MagicNumber")
private val MultiplierLimits = 0.5f..1.5f

private const val MULTIPLIER_STEP = 0.01f

private operator fun Pressure.unaryMinus() = (-kpa).kpa

private fun Pressure.withSymbol(unit: PressureUnit) = "${numberString(unit)} ${unit.symbol()}"

private fun Pressure.signedWithSymbol(unit: PressureUnit) =
    "${if (kpa > 0f) "+" else ""}${withSymbol(unit)}"

private fun Float.multiplierString() = "×%.2f".format(this)

/** Finer than the alert range's steps, a calibration corrects a few units at most */
@Suppress("MagicNumber")
private val PressureUnit.offsetStep: Float
    get() = when (this) {
        KILO_PASCAL -> 5f
        BAR -> 0.05f
        PSI -> 0.5f
    }

@Suppress("ConstPropertyName")
internal object SensorCalibrationSettingsTags {
    const val enabled = "SensorCalibrationSettingsTags_enabled"
    const val applyToSensor = "SensorCalibrationSettingsTags_applyToSensor"
    const val applyToAll = "SensorCalibrationSettingsTags_applyToAll"
    const val eraseOthers = "SensorCalibrationSettingsTags_eraseOthers"
    const val discard = "SensorCalibrationSettingsTags_discard"
}

@Preview
@Composable
internal fun SensorCalibrationSettingsPreview() {
    SensorCalibrationSettings(
        unit = BAR,
        vehicleName = "My car",
        location = Location.Wheel(FRONT_LEFT),
        enabled = true,
        calibration = PressureCalibration(0.1f.bar, 1.02f),
        example = 2.4f.bar,
        isExampleRead = true,
        hasOthers = true,
        canApplyToAll = true,
        isChanged = true,
        onEnabled = {},
        onOffset = {},
        onMultiplier = {},
        onApplyToSensor = {},
        onApplyToAll = {},
    )
}

@Preview
@Composable
internal fun SensorCalibrationSettingsOffPreview() {
    SensorCalibrationSettings(
        unit = PSI,
        vehicleName = "My car",
        location = Location.Wheel(FRONT_LEFT),
        enabled = false,
        calibration = PressureCalibration(0f.kpa, 1f),
        example = 35f.psi,
        isExampleRead = false,
        hasOthers = true,
        canApplyToAll = false,
        isChanged = false,
        onEnabled = {},
        onOffset = {},
        onMultiplier = {},
        onApplyToSensor = {},
        onApplyToAll = {},
    )
}
