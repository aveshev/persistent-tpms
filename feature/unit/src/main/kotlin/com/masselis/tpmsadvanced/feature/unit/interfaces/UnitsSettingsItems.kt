package com.masselis.tpmsadvanced.feature.unit.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.feature.unit.ioc.Bindings.Companion.UnitsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/** The app-wide units, as items to put in a [SettingsGroup] */
@Composable
public fun UnitsSettingsItems(): Unit = UnitsSettingsItems(viewModel { UnitsViewModel() })

@Composable
internal fun UnitsSettingsItems(
    viewModel: UnitsViewModel = viewModel { UnitsViewModel() },
) {
    val pressure by viewModel.pressure.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    SegmentedSettingsItem(
        headline = stringResource(CommonR.string.pressure_in),
        options = PressureUnit.entries,
        selected = pressure,
        onSelect = { viewModel.pressure.value = it },
        label = { it.symbol() },
    )
    SegmentedSettingsItem(
        headline = stringResource(CommonR.string.temperature_in),
        options = TemperatureUnit.entries,
        selected = temperature,
        onSelect = { viewModel.temperature.value = it },
        label = { it.symbol() },
    )
}
