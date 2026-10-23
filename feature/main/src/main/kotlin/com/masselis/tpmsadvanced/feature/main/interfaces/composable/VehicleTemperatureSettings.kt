package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.toTemperature
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Threshold.COLD
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Threshold.HOT
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Threshold.NORMAL
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State

/** The page editing the temperatures giving the vehicle's tyres their colour */
@Composable
public fun VehicleTemperatureSettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val unit by viewModel.temperatureUnit.collectAsState()
    val low by viewModel.lowTemp.collectAsState()
    val normal by viewModel.normalTemp.collectAsState()
    val high by viewModel.highTemp.collectAsState()
    VehicleTemperatureSettings(
        unit = unit,
        low = low,
        normal = normal,
        high = high,
        onLow = { viewModel.lowTemp.value = it },
        onNormal = { viewModel.normalTemp.value = it },
        onHigh = { viewModel.highTemp.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength", "CyclomaticComplexMethod")
@Composable
private fun VehicleTemperatureSettings(
    unit: TemperatureUnit,
    low: Temperature,
    normal: Temperature,
    high: Temperature,
    onLow: (Temperature) -> Unit,
    onNormal: (Temperature) -> Unit,
    onHigh: (Temperature) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    // The margins of the alert levels, see AlertThresholds, in degrees of the displayed unit
    val degrees = if (unit == CELSIUS) 1f else FAHRENHEIT_PER_CELSIUS
    TyreLegend(
        text = "A tyre indicator's colour will change from blue to green between cold and normal temperatures, and from green to orange to red between normal and hot. Going above hot will raise an alert and blink the affected tyre indicator in red. Going ${"%.0f".format(CRIMSON_MARGIN * degrees)} ${unit.symbol()} above hot will raise a critical alert.",
        entries = listOf(
            State.Normal.BlueToGreen(Fraction(0f)) to "Cold\n${low.withSymbol(unit)}",
            State.Normal.BlueToGreen(Fraction(1f)) to "Normal\n${normal.withSymbol(unit)}",
            State.Alerting() to "Hot\n${high.withSymbol(unit)}",
            State.Alerting(isCritical = true) to "Critical\n${(high.celsius + CRIMSON_MARGIN).celsius.withSymbol(unit)}",
        ),
    )
    SettingsSectionHeader("Temperatures")
    var editing by rememberSaveable { mutableStateOf<Threshold?>(null) }
    SettingsGroup {
        TextSettingsItem(headline = "Cold", supporting = low.withSymbol(unit), onClick = { editing = COLD })
        TextSettingsItem(headline = "Normal", supporting = normal.withSymbol(unit), onClick = { editing = NORMAL })
        TextSettingsItem(headline = "Hot", supporting = high.withSymbol(unit), onClick = { editing = HOT })
    }
    editing?.also { threshold ->
        NumberDialog(
            title = when (threshold) {
                COLD -> "Cold temperature"
                NORMAL -> "Normal temperature"
                HOT -> "Hot temperature"
            },
            value = when (threshold) {
                COLD -> low
                NORMAL -> normal
                HOT -> high
            }.convert(unit),
            // Each threshold stays between its neighbours so the colours keep their order
            range = when (threshold) {
                COLD -> TemperatureLimits.start..normal
                NORMAL -> low..high
                HOT -> normal..TemperatureLimits.endInclusive
            }.let { it.start.convert(unit)..it.endInclusive.convert(unit) },
            step = 1f,
            unit = unit.symbol(),
            format = { it.toTemperature(unit).numberString(unit) },
            onConfirm = { value ->
                value
                    .toTemperature(unit)
                    .also(
                        when (threshold) {
                            COLD -> onLow
                            NORMAL -> onNormal
                            HOT -> onHigh
                        }
                    )
                editing = null
            },
            onDismissRequest = { editing = null },
        )
    }
}

private enum class Threshold { COLD, NORMAL, HOT }

private val TemperatureLimits = COLDEST_CELSIUS.celsius..150f.celsius

private fun Temperature.withSymbol(unit: TemperatureUnit) = "${numberString(unit)} ${unit.symbol()}"

// AlertThresholds' critical margin, in degrees Celsius
private const val CRIMSON_MARGIN = 20f
private const val FAHRENHEIT_PER_CELSIUS = 1.8f

// Winter tyres stay supple far below summer ones
private const val COLDEST_CELSIUS = -40f

@Preview
@Composable
internal fun VehicleTemperatureSettingsPreview() {
    VehicleTemperatureSettings(
        unit = CELSIUS,
        low = 20f.celsius,
        normal = 45f.celsius,
        high = 90f.celsius,
        onLow = {},
        onNormal = {},
        onHigh = {},
    )
}
