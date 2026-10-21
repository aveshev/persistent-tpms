package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.ui.Orange
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.BatteryKinds
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds.Companion.BATTERY_AMBER_MARGIN
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds.Companion.BATTERY_AMBER_MARGIN_PERCENT
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import kotlin.math.roundToInt

/**
 * The page editing the levels at which a sensor's battery alarms: its voltage for most sensors, a
 * percentage for Sysgration's. Only the ones the vehicle's sensors report are shown, both while
 * none of its sensors has been read.
 */
@Composable
public fun VehicleBatterySettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val lowVoltage by viewModel.lowBatteryVoltage.collectAsState()
    val lowPercent by viewModel.lowBatteryPercent.collectAsState()
    val batteryKinds by viewModel.batteryKinds.collectAsState()
    VehicleBatterySettings(
        lowVoltage = lowVoltage,
        onLowVoltage = { viewModel.lowBatteryVoltage.value = it },
        lowPercent = lowPercent,
        onLowPercent = { viewModel.lowBatteryPercent.value = it },
        batteryKinds = batteryKinds,
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength", "LongParameterList")
@Composable
private fun VehicleBatterySettings(
    lowVoltage: Voltage,
    onLowVoltage: (Voltage) -> Unit,
    lowPercent: Int,
    onLowPercent: (Int) -> Unit,
    batteryKinds: BatteryKinds,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    // Blinks along with the alerting values of the main screen
    val isVisible = isFirstBlinkPhase(BLINK)
    if (batteryKinds.showsVoltage) {
        BatteryLevelSamples(
            normal = (lowVoltage + BATTERY_AMBER_MARGIN + BATTERY_AMBER_MARGIN).string(),
            gettingLow = (lowVoltage + BATTERY_AMBER_MARGIN).string(),
            low = lowVoltage.string(),
            isVisible = isVisible,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        SettingsSectionHeader("Voltage alarm")
        var editing by rememberSaveable { mutableStateOf(false) }
        SettingsGroup {
            TextSettingsItem(
                headline = "Low voltage alarm",
                supporting = lowVoltage.string(),
                onClick = { editing = true },
                modifier = Modifier.testTag(VehicleBatterySettingsTags.lowVoltage),
            )
        }
        Text(
            text = "If the sensor reports battery voltage, you can set when to report it as low. Within 0.1 V of the setting, it's shown in orange. After 10 minutes of consistently low readings it will raise an alert. The app will use a slightly reduced value when temperatures are low, to account for the voltage drop.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        if (editing) NumberDialog(
            title = "Low voltage alarm",
            value = lowVoltage.volts,
            range = LowVoltageLimits,
            // The sensors report their voltage in steps of 0.1 V
            step = VOLTAGE_STEP,
            unit = "V",
            format = { it.volts.numberString() },
            onConfirm = { onLowVoltage(it.volts); editing = false },
            onDismissRequest = { editing = false },
        )
    }
    if (batteryKinds.showsPercent) {
        // Both kinds of sensors on the vehicle, the voltage part above
        if (batteryKinds.showsVoltage) HorizontalDivider(Modifier.padding(top = 24.dp))
        BatteryLevelSamples(
            normal = "${lowPercent + BATTERY_AMBER_MARGIN_PERCENT * 2}%",
            gettingLow = "${lowPercent + BATTERY_AMBER_MARGIN_PERCENT}%",
            low = "$lowPercent%",
            isVisible = isVisible,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        SettingsSectionHeader("Percentage alarm")
        var editing by rememberSaveable { mutableStateOf(false) }
        SettingsGroup {
            TextSettingsItem(
                headline = "Low battery alarm",
                supporting = "$lowPercent%",
                onClick = { editing = true },
                modifier = Modifier.testTag(VehicleBatterySettingsTags.lowPercent),
            )
        }
        Text(
            text = "Sysgration sensors report their battery state as a percentage rather than a voltage.\n\n" +
                "At or below the setting, it will blink red. After 10 minutes of consistently low readings it will raise an alarm.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        if (editing) NumberDialog(
            title = "Low battery alarm",
            value = lowPercent.toFloat(),
            range = LowPercentLimits,
            step = PERCENT_STEP,
            unit = "%",
            format = { it.roundToInt().toString() },
            onConfirm = { onLowPercent(it.roundToInt()); editing = false },
            onDismissRequest = { editing = false },
        )
    }
}

/** A normal, a getting low and a low battery, as the main screen shows them */
@Composable
private fun BatteryLevelSamples(
    normal: String,
    gettingLow: String,
    low: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) = Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = modifier.fillMaxWidth(),
) {
    listOf(
        Triple(normal, MaterialTheme.colorScheme.onSurface, "Normal"),
        Triple(gettingLow, Orange, "Getting low"),
        Triple(low, MaterialTheme.colorScheme.error, "Low alarm"),
    ).forEach { (value, color, caption) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = color,
                modifier = Modifier.alpha(if (value != low || isVisible) 1f else 0f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Both while nothing is known, so nothing is hidden from a vehicle being set up */
private val BatteryKinds.showsVoltage get() = hasVoltage || hasPercent.not()
private val BatteryKinds.showsPercent get() = hasPercent || hasVoltage.not()

private const val VOLTAGE_STEP = 0.1f

// Below 2 V the sensors stop sending before reporting it, above 3 V a fresh battery would alarm
@Suppress("MagicNumber")
private val LowVoltageLimits = 2f..3f

private const val PERCENT_STEP = 5f

// Up to half the battery, past which a fresh battery is never far from alarming
@Suppress("MagicNumber")
private val LowPercentLimits = 5f..50f

@Suppress("ConstPropertyName")
internal object VehicleBatterySettingsTags {
    const val lowVoltage = "VehicleBatterySettingsTags_lowVoltage"
    const val lowPercent = "VehicleBatterySettingsTags_lowPercent"
}

@Preview
@Composable
internal fun VehicleBatterySettingsPreview() {
    VehicleBatterySettings(
        lowVoltage = 2.6f.volts,
        onLowVoltage = {},
        lowPercent = 10,
        onLowPercent = {},
        batteryKinds = BatteryKinds(hasVoltage = true, hasPercent = false),
    )
}

@Preview
@Composable
internal fun VehicleBatterySettingsSysgrationPreview() {
    VehicleBatterySettings(
        lowVoltage = 2.6f.volts,
        onLowVoltage = {},
        lowPercent = 10,
        onLowPercent = {},
        batteryKinds = BatteryKinds(hasVoltage = false, hasPercent = true),
    )
}
