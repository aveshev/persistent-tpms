package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.PressureLossSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.PressureLossSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.VehiclePressureLossUseCase

/**
 * The page of the experimental pressure loss warning, opened from [DeveloperOptionsSettings]. It
 * applies to every vehicle, each against its own low pressure alert.
 */
@Composable
public fun PressureLossSettings(
    modifier: Modifier = Modifier,
): Unit = PressureLossSettings(modifier, viewModel { PressureLossSettingsViewModel() })

@Composable
internal fun PressureLossSettings(
    modifier: Modifier = Modifier,
    viewModel: PressureLossSettingsViewModel = viewModel { PressureLossSettingsViewModel() },
) {
    val enabled by viewModel.enabled.collectAsState()
    val minDrop by viewModel.minDrop.collectAsState()
    val unit by viewModel.pressureUnit.collectAsState()
    PressureLossSettings(
        enabled = enabled,
        minDrop = minDrop.kpa,
        unit = unit,
        onEnabled = { viewModel.enabled.value = it },
        onMinDrop = { viewModel.minDrop.value = it.kpa },
        modifier = modifier,
    )
}

@Suppress("MaxLineLength")
@Composable
private fun PressureLossSettings(
    enabled: Boolean,
    minDrop: Pressure,
    unit: PressureUnit,
    onEnabled: (Boolean) -> Unit,
    onMinDrop: (Pressure) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Experimental. Shows \"Leak?\" under a tyre losing pressure while riding, and notifies about it: its pressure falling from its highest reading of the ride by at least the minimum drop, on two readings in a row, while the tyre isn't cooling down.\n\nRiding warms a tyre up, which only raises its pressure, so a fall is air getting out. A leak too slow to show within a ride is left to the low pressure alert, once the tyre is cold. Letting air out shows as a loss too."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Pressure loss warning",
            checked = enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.testTag(PressureLossSettingsTags.enabled),
        )
    }
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SegmentedSettingsItem(
            headline = "Minimum drop, in ${unit.symbol()}",
            options = VehiclePressureLossUseCase.MIN_DROPS,
            selected = minDrop,
            onSelect = onMinDrop,
            label = { it.numberString(unit) },
        )
    }
}

@Suppress("ConstPropertyName")
internal object PressureLossSettingsTags {
    const val enabled = "PressureLossSettingsTags_enabled"
}

@Preview
@Composable
internal fun PressureLossSettingsPreview() {
    PressureLossSettings(
        enabled = true,
        minDrop = 7f.kpa,
        unit = PSI,
        onEnabled = {},
        onMinDrop = {},
    )
}

@Preview
@Composable
internal fun PressureLossSettingsOffPreview() {
    PressureLossSettings(
        enabled = false,
        minDrop = 10f.kpa,
        unit = BAR,
        onEnabled = {},
        onMinDrop = {},
    )
}
