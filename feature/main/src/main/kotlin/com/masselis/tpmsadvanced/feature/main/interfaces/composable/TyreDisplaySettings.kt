package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/**
 * The "Display" group of the app settings, [openTimeSinceUpdate] opening the page explaining it,
 * [openMagnifier] the one choosing how long a reading stays magnified, see [MagnifierDuration].
 * [additionalItems] are appended to the group, for display settings owned by other features.
 */
@Composable
public fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    openMagnifier: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
): Unit =
    TyreDisplaySettings(
        openTimeSinceUpdate,
        openMagnifier,
        modifier,
        additionalItems,
        viewModel { TyreDisplaySettingsViewModel() },
    )

@Composable
internal fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    openMagnifier: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    val showBatteryVoltage by viewModel.showBatteryVoltage.collectAsState()
    val magnifier by viewModel.magnifier.collectAsState()
    val magnifierSeconds by viewModel.magnifierSeconds.collectAsState()
    TyreDisplaySettings(
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        showBatteryVoltage = showBatteryVoltage,
        onShowBatteryVoltage = { viewModel.showBatteryVoltage.value = it },
        magnifier = magnifier,
        onMagnifier = { viewModel.magnifier.value = it },
        magnifierSeconds = magnifierSeconds,
        openTimeSinceUpdate = openTimeSinceUpdate,
        openMagnifier = openMagnifier,
        modifier = modifier,
        additionalItems = additionalItems,
    )
}

@Composable
private fun TyreDisplaySettings(
    showTimeSinceUpdate: Boolean,
    onShowTimeSinceUpdate: (Boolean) -> Unit,
    showBatteryVoltage: Boolean,
    onShowBatteryVoltage: (Boolean) -> Unit,
    magnifier: Boolean,
    onMagnifier: (Boolean) -> Unit,
    magnifierSeconds: Int,
    openTimeSinceUpdate: () -> Unit,
    openMagnifier: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
) = SettingsGroup(modifier) {
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
        headline = "Battery voltage",
        supporting = "(even when not low)",
        checked = showBatteryVoltage,
        onCheckedChange = onShowBatteryVoltage,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.showBatteryVoltage),
    )
    SwitchNavigationSettingsItem(
        headline = "Tap to magnify",
        supporting = listOf(AnnotatedString("for $magnifierSeconds seconds")),
        checked = magnifier,
        onCheckedChange = onMagnifier,
        onClick = openMagnifier,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.magnifier),
    )
    additionalItems()
}

@Preview
@Composable
internal fun TyreDisplaySettingsPreview() {
    TyreDisplaySettings(
        showTimeSinceUpdate = true,
        onShowTimeSinceUpdate = {},
        showBatteryVoltage = false,
        onShowBatteryVoltage = {},
        magnifier = true,
        onMagnifier = {},
        magnifierSeconds = 5,
        openTimeSinceUpdate = {},
        openMagnifier = {},
    )
}

@Suppress("ConstPropertyName")
internal object TyreDisplaySettingsTags {
    const val showTimeSinceUpdate = "TyreDisplaySettingsTags_showTimeSinceUpdate"
    const val showBatteryVoltage = "TyreDisplaySettingsTags_showBatteryVoltage"
    const val magnifier = "TyreDisplaySettingsTags_magnifier"
    const val magnifierDuration = "TyreDisplaySettingsTags_magnifierDuration"
    const val timeSinceUpdateDetails = "TyreDisplaySettingsTags_timeSinceUpdateDetails"
}
