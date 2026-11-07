package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.PressureBound.MAX
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.PressureBound.MIN
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/** The page editing the pressure range the vehicle's tyres are expected to stay within */
@Composable
public fun VehiclePressureSettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val unit by viewModel.pressureUnit.collectAsState()
    val low by viewModel.lowPressure.collectAsState()
    val high by viewModel.highPressure.collectAsState()
    val rearLow by viewModel.rearLowPressure.collectAsState()
    val rearHigh by viewModel.rearHighPressure.collectAsState()
    val separateRear by viewModel.separateRearPressure.collectAsState()
    val spareLow by viewModel.spareLowPressure.collectAsState()
    val spareHigh by viewModel.spareHighPressure.collectAsState()
    val separateSpare by viewModel.separateSparePressure.collectAsState()
    VehiclePressureSettings(
        unit = unit,
        front = low..high,
        rear = rearLow
            ?.takeIf { separateRear }
            ?.let { start -> rearHigh?.let { start..it } },
        canSeparateRear = component.vehicle.kind.hasFrontRearAxles,
        spare = spareLow
            ?.takeIf { separateSpare }
            ?.let { start -> spareHigh?.let { start..it } },
        canSeparateSpare = Location.Spare in component.vehicle.kind.locations,
        onFront = {
            viewModel.lowPressure.value = it.start
            viewModel.highPressure.value = it.endInclusive
        },
        onRear = {
            viewModel.rearLowPressure.value = it.start
            viewModel.rearHighPressure.value = it.endInclusive
        },
        onSeparateRear = viewModel::setRearOverrideEnabled,
        onSpare = {
            viewModel.spareLowPressure.value = it.start
            viewModel.spareHighPressure.value = it.endInclusive
        },
        onSeparateSpare = viewModel::setSpareOverrideEnabled,
        modifier = modifier,
    )
}

@Suppress("MaxLineLength", "LongParameterList", "LongMethod")
@Composable
private fun VehiclePressureSettings(
    unit: PressureUnit,
    front: ClosedFloatingPointRange<Pressure>,
    rear: ClosedFloatingPointRange<Pressure>?,
    canSeparateRear: Boolean,
    onFront: (ClosedFloatingPointRange<Pressure>) -> Unit,
    onRear: (ClosedFloatingPointRange<Pressure>) -> Unit,
    onSeparateRear: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    spare: ClosedFloatingPointRange<Pressure>? = null,
    canSeparateSpare: Boolean = false,
    onSpare: (ClosedFloatingPointRange<Pressure>) -> Unit = {},
    onSeparateSpare: (Boolean) -> Unit = {},
) = Column(modifier) {
    TyreLegend(
        text = stringResource(CommonR.string.when_a_sensor_reports_pressure_outside_of),
        entries = listOf(
            State.Normal.BlueToGreen(Fraction(1f)) to stringResource(CommonR.string.in_range),
            State.Alerting() to stringResource(CommonR.string.out_of_range),
            State.Alerting(isCritical = true) to stringResource(CommonR.string.critical),
        ),
    )
    if (canSeparateRear || canSeparateSpare) SettingsGroup(Modifier.padding(top = 24.dp)) {
        if (canSeparateRear) SwitchSettingsItem(
            headline = stringResource(CommonR.string.set_rear_pressure_separately),
            // Static on purpose: the section headers below already show which state is active
            supporting = stringResource(CommonR.string.turn_on_if_the_rear_tyres_need),
            checked = rear != null,
            onCheckedChange = onSeparateRear,
        )
        if (canSeparateSpare) SwitchSettingsItem(
            headline = stringResource(CommonR.string.set_spare_pressure_separately),
            supporting = stringResource(CommonR.string.turn_on_if_the_spare_needs_a),
            checked = spare != null,
            onCheckedChange = onSeparateSpare,
        )
    }
    // Every location the front range applies to: "Front, rear & spare", "Front & spare", "Front"…
    SettingsSectionHeader(
        listOfNotNull(
            stringResource(CommonR.string.front),
            "rear".takeIf { canSeparateRear && rear == null },
            "spare".takeIf { canSeparateSpare && spare == null },
        )
            .takeIf { canSeparateRear || canSeparateSpare }
            ?.let { names -> listOf(names.dropLast(1).joinToString(", "), names.last()) }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" & ")
            ?: stringResource(CommonR.string.expected_range)
    )
    PressureRangeGroup(front, unit, onFront)
    rear?.also {
        SettingsSectionHeader(stringResource(CommonR.string.rear))
        PressureRangeGroup(it, unit, onRear)
    }
    spare?.also {
        SettingsSectionHeader(stringResource(CommonR.string.spare))
        PressureRangeGroup(it, unit, onSpare)
    }
}

/** The minimum and maximum of [range], each opening a [NumberDialog] bounded by the other one */
@Composable
private fun PressureRangeGroup(
    range: ClosedFloatingPointRange<Pressure>,
    unit: PressureUnit,
    onRange: (ClosedFloatingPointRange<Pressure>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf<PressureBound?>(null) }
    SettingsGroup(modifier) {
        TextSettingsItem(
            headline = stringResource(CommonR.string.minimum_2),
            supporting = range.start.withSymbol(unit),
            onClick = { editing = MIN },
        )
        TextSettingsItem(
            headline = stringResource(CommonR.string.maximum_2),
            supporting = range.endInclusive.withSymbol(unit),
            onClick = { editing = MAX },
        )
    }
    editing?.also { bound ->
        NumberDialog(
            title = when (bound) {
                MIN -> stringResource(CommonR.string.minimum_pressure)
                MAX -> stringResource(CommonR.string.maximum_pressure)
            },
            value = when (bound) {
                MIN -> range.start
                MAX -> range.endInclusive
            }.convert(unit),
            range = when (bound) {
                MIN -> PressureLimits.start..range.endInclusive
                MAX -> range.start..PressureLimits.endInclusive
            }.let { it.start.convert(unit)..it.endInclusive.convert(unit) },
            step = unit.step,
            unit = unit.symbol(),
            format = { it.toPressure(unit).numberString(unit) },
            onConfirm = { value ->
                value
                    .toPressure(unit)
                    .let {
                        when (bound) {
                            MIN -> it..range.endInclusive
                            MAX -> range.start..it
                        }
                    }
                    .also(onRange)
                editing = null
            },
            onDismissRequest = { editing = null },
        )
    }
}

private enum class PressureBound { MIN, MAX }

private val PressureLimits = 0f.psi..150f.psi

private fun Pressure.withSymbol(unit: PressureUnit) = "${numberString(unit)} ${unit.symbol()}"

/** How much the −/+ buttons move a pressure, a common gauge resolution in each unit */
@Suppress("MagicNumber")
private val PressureUnit.step: Float
    get() = when (this) {
        KILO_PASCAL -> 10f
        BAR -> 0.1f
        PSI -> 1f
    }

@Preview
@Composable
internal fun VehiclePressureSettingsSeparatePreview() {
    VehiclePressureSettings(
        unit = BAR,
        front = 2.2f.bar..2.6f.bar,
        rear = 2.4f.bar..2.9f.bar,
        canSeparateRear = true,
        onFront = {},
        onRear = {},
        onSeparateRear = {},
    )
}

@Preview
@Composable
internal fun VehiclePressureSettingsSharedPreview() {
    VehiclePressureSettings(
        unit = BAR,
        front = 2.2f.bar..2.6f.bar,
        rear = null,
        canSeparateRear = true,
        onFront = {},
        onRear = {},
        onSeparateRear = {},
    )
}

@Preview
@Composable
internal fun VehiclePressureSettingsSparePreview() {
    VehiclePressureSettings(
        unit = BAR,
        front = 2.2f.bar..2.6f.bar,
        rear = null,
        canSeparateRear = true,
        onFront = {},
        onRear = {},
        onSeparateRear = {},
        spare = 2.8f.bar..3.2f.bar,
        canSeparateSpare = true,
    )
}

@Preview
@Composable
internal fun VehiclePressureSettingsTrailerPreview() {
    VehiclePressureSettings(
        unit = PSI,
        front = 30f.psi..40f.psi,
        rear = null,
        canSeparateRear = false,
        onFront = {},
        onRear = {},
        onSeparateRear = {},
    )
}
