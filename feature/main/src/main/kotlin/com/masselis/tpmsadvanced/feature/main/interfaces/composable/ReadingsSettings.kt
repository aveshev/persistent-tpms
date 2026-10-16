package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/**
 * The "Readings" group of the app settings, about what each tyre's reading shows,
 * [openTimeSinceUpdate] opening the page explaining the time since the last update, see
 * [TimeSinceUpdateDetails]. [leadingItems] open the group, for settings owned by other features
 * such as the units.
 */
@Composable
public fun ReadingsSettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    leadingItems: @Composable ColumnScope.() -> Unit = {},
): Unit = ReadingsSettings(
    openTimeSinceUpdate,
    modifier,
    leadingItems,
    viewModel { TyreDisplaySettingsViewModel() },
)

@Composable
internal fun ReadingsSettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    leadingItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    val showBatteryVoltage by viewModel.showBatteryVoltage.collectAsState()
    ReadingsSettings(
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        openTimeSinceUpdate = openTimeSinceUpdate,
        showBatteryVoltage = showBatteryVoltage,
        onShowBatteryVoltage = { viewModel.showBatteryVoltage.value = it },
        modifier = modifier,
        leadingItems = leadingItems,
    )
}

@Composable
private fun ReadingsSettings(
    showTimeSinceUpdate: Boolean,
    onShowTimeSinceUpdate: (Boolean) -> Unit,
    openTimeSinceUpdate: () -> Unit,
    showBatteryVoltage: Boolean,
    onShowBatteryVoltage: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingItems: @Composable ColumnScope.() -> Unit = {},
) = SettingsGroup(modifier) {
    leadingItems()
    SwitchNavigationSettingsItem(
        headline = "Time since last update",
        checked = showTimeSinceUpdate,
        onCheckedChange = onShowTimeSinceUpdate,
        onClick = openTimeSinceUpdate,
        // The page explains why a sensor can stay silent, which matters whether shown or not
        openableWhenOff = true,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.showTimeSinceUpdate),
    )
    SwitchSettingsItem(
        headline = "Always show battery",
        supporting = "(even when not low)",
        checked = showBatteryVoltage,
        onCheckedChange = onShowBatteryVoltage,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.showBatteryVoltage),
    )
}

@Preview
@Composable
internal fun ReadingsSettingsPreview() {
    ReadingsSettings(
        showTimeSinceUpdate = true,
        onShowTimeSinceUpdate = {},
        openTimeSinceUpdate = {},
        showBatteryVoltage = false,
        onShowBatteryVoltage = {},
    )
}
