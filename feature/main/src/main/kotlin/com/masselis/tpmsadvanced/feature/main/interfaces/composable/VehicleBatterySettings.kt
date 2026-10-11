package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            BatteryLevelSamples(
                normal = (lowVoltage + BATTERY_AMBER_MARGIN + BATTERY_AMBER_MARGIN).string(),
                gettingLow = (lowVoltage + BATTERY_AMBER_MARGIN).string(),
                low = lowVoltage.string(),
                isVisible = isVisible,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Most sensors report the voltage of their battery. A fresh coin cell reads around 3 V and stays close to it for most of its life, then drops quickly near the end.\n\n" +
                    "Once a sensor's voltage is within 0.1 V of the low voltage alarm, it is shown in orange below that tyre's readings, so you can plan a battery change. At or below the alarm, it blinks red, and you get a notification once it stays there for 10 minutes: the voltage dips in the cold and while the sensor sends.\n\n" +
                    "The alarm is meant for 20°C. A battery's voltage drops in the cold without it being any emptier, so below 20°C the alarm comes down by 0.05 V every 10°C. It never comes down more than 0.1 V: that close to the end, the cold can make the sensor stop sending.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            BatteryLevelSamples(
                normal = "${lowPercent + BATTERY_AMBER_MARGIN_PERCENT * 2}%",
                gettingLow = "${lowPercent + BATTERY_AMBER_MARGIN_PERCENT}%",
                low = "$lowPercent%",
                isVisible = isVisible,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Sysgration sensors report their battery as a percentage instead of a voltage.\n\n" +
                    "Once it is within $BATTERY_AMBER_MARGIN_PERCENT points of the low battery alarm, it is shown in orange below that tyre's readings. At or below the alarm, it blinks red, and you get a notification once it stays there for 10 minutes.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
