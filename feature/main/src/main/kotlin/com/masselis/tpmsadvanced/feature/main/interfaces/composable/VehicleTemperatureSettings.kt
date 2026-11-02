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
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds.Companion.AMBER_TEMPERATURE_BELOW
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds.Companion.CRIMSON_TEMPERATURE_ABOVE
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.toTemperature
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Threshold.COLD
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Threshold.HOT
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.Companion.WARM_UP_CELSIUS
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.Companion.fineCelsius
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

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
    val high by viewModel.highTemp.collectAsState()
    VehicleTemperatureSettings(
        unit = unit,
        low = low,
        high = high,
        onLow = { viewModel.lowTemp.value = it },
        onHigh = { viewModel.highTemp.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength", "CyclomaticComplexMethod")
@Composable
private fun VehicleTemperatureSettings(
    unit: TemperatureUnit,
    low: Temperature,
    high: Temperature,
    onLow: (Temperature) -> Unit,
    onHigh: (Temperature) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    // The colours' and the alert levels' margins, in degrees of the displayed unit
    val degrees = { celsius: Float ->
        "%.0f ${unit.symbol()}".format(if (unit == CELSIUS) celsius else celsius * FAHRENHEIT_PER_CELSIUS)
    }
    TyreLegend(
        text = stringResource(CommonR.string.a_tyre_indicator_is_blue_up_to, degrees(WARM_UP_CELSIUS), degrees(AMBER_TEMPERATURE_BELOW), degrees(CRIMSON_TEMPERATURE_ABOVE)),
        entries = listOf(
            State.Normal.BlueToGreen(Fraction(0f)) to stringResource(CommonR.string.cold, low.withSymbol(unit)),
            State.Normal.BlueToGreen(Fraction(1f)) to fineCelsius(low, high).let { fine ->
                stringResource(CommonR.string.ok, fine.start.celsius.numberString(unit), fine.endInclusive.celsius.withSymbol(unit))
            },
            State.Alerting() to stringResource(CommonR.string.hot, high.withSymbol(unit)),
            State.Alerting(isCritical = true) to stringResource(CommonR.string.critical_2, (high.celsius + CRIMSON_TEMPERATURE_ABOVE).celsius.withSymbol(unit)),
        ),
    )
    SettingsSectionHeader(stringResource(CommonR.string.temperatures))
    var editing by rememberSaveable { mutableStateOf<Threshold?>(null) }
    SettingsGroup {
        TextSettingsItem(headline = stringResource(CommonR.string.cold_2), supporting = low.withSymbol(unit), onClick = { editing = COLD })
        TextSettingsItem(headline = stringResource(CommonR.string.hot_2), supporting = high.withSymbol(unit), onClick = { editing = HOT })
    }
    editing?.also { threshold ->
        NumberDialog(
            title = when (threshold) {
                COLD -> stringResource(CommonR.string.cold_temperature)
                HOT -> stringResource(CommonR.string.hot_temperature)
            },
            value = when (threshold) {
                COLD -> low
                HOT -> high
            }.convert(unit),
            // Cold stays below hot so the colours keep their order
            range = when (threshold) {
                COLD -> TemperatureLimits.start..high
                HOT -> low..TemperatureLimits.endInclusive
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
                            HOT -> onHigh
                        }
                    )
                editing = null
            },
            onDismissRequest = { editing = null },
        )
    }
}

private enum class Threshold { COLD, HOT }

private val TemperatureLimits = COLDEST_CELSIUS.celsius..150f.celsius

private fun Temperature.withSymbol(unit: TemperatureUnit) = "${numberString(unit)} ${unit.symbol()}"

private const val FAHRENHEIT_PER_CELSIUS = 1.8f

// Winter tyres stay supple far below summer ones
private const val COLDEST_CELSIUS = -40f

@Preview
@Composable
internal fun VehicleTemperatureSettingsPreview() {
    VehicleTemperatureSettings(
        unit = CELSIUS,
        low = 7f.celsius,
        high = 65f.celsius,
        onLow = {},
        onHigh = {},
    )
}
