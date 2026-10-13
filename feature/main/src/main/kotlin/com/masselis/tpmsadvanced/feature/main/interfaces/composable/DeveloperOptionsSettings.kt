package com.masselis.tpmsadvanced.feature.main.interfaces.composable

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
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.PressureLossSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.PressureLossSettingsViewModel

/**
 * The "Developer options" group of the app settings: the demo mode, the switch of the debug
 * options, whose page [openDebug] opens, and the experimental pressure loss warning, whose page
 * [openPressureLoss] opens.
 */
@Composable
public fun DeveloperOptionsSettings(
    openDebug: () -> Unit,
    openPressureLoss: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = DeveloperOptionsSettings(
    openDebug,
    openPressureLoss,
    modifier,
    viewModel { DebugSettingsViewModel() },
    viewModel { PressureLossSettingsViewModel() },
)

@Composable
internal fun DeveloperOptionsSettings(
    openDebug: () -> Unit,
    openPressureLoss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugSettingsViewModel = viewModel { DebugSettingsViewModel() },
    pressureLossViewModel: PressureLossSettingsViewModel = viewModel { PressureLossSettingsViewModel() },
) {
    val pressureLoss by pressureLossViewModel.enabled.collectAsState()
    val debugOptions by viewModel.debugOptions.collectAsState()
    val sensorId by viewModel.showSensorId.collectAsState()
    val sensorFlags by viewModel.showSensorFlags.collectAsState()
    val activities by viewModel.showDetectedActivities.collectAsState()
    val announcements by viewModel.announceScanStatus.collectAsState()
    val balanced by viewModel.balancedAfterFirstReading.collectAsState()
    val selected = listOfNotNull(
        // Same order as their page
        "Sensor ID".takeIf { sensorId },
        "Sensor flags".takeIf { sensorFlags },
        "Detected activities".takeIf { activities },
        "Status announcements".takeIf { announcements },
        "Balanced scanning".takeIf { balanced },
    )
    SettingsGroup(modifier) {
        DemoModeSettingsItem()
        SwitchNavigationSettingsItem(
            headline = "Debug",
            // Also while off: what turning it on would show
            supporting = selected
                .takeIf { it.isNotEmpty() }
                ?.let { listOf(it.joinToString(", "), "${it.first()} and more") }
                .orEmpty()
                .map(::AnnotatedString),
            // Leaving the page with nothing selected turns the switch off, which only happens once
            // the page is gone: shown that way from the start rather than as on with nothing to show
            checked = debugOptions && selected.isNotEmpty(),
            // Turned on without anything to show, the only sensible next step is to pick something.
            // Leaving the page without doing so turns it off again.
            onCheckedChange = { enabled ->
                viewModel.debugOptions.value = enabled
                if (enabled && selected.isEmpty()) openDebug()
            },
            onClick = openDebug,
            modifier = Modifier.testTag(DeveloperOptionsSettingsTags.debug),
        )
        SwitchNavigationSettingsItem(
            headline = "Pressure loss",
            supporting = listOf(AnnotatedString("Experimental")),
            checked = pressureLoss,
            onCheckedChange = { pressureLossViewModel.enabled.value = it },
            onClick = openPressureLoss,
            // The page explains what counts as a loss, which matters before turning it on
            openableWhenOff = true,
            modifier = Modifier.testTag(DeveloperOptionsSettingsTags.pressureLoss),
        )
    }
}

@Preview
@Composable
internal fun DeveloperOptionsSettingsPreview() {
    SettingsGroup {
        DemoModeSettingsItem(demoMode = false, onDemoMode = {})
        SwitchNavigationSettingsItem(
            headline = "Debug",
            supporting = listOf(AnnotatedString("Sensor ID, Sensor flags")),
            checked = true,
            onCheckedChange = {},
            onClick = {},
        )
        SwitchNavigationSettingsItem(
            headline = "Pressure loss",
            supporting = listOf(AnnotatedString("Experimental")),
            checked = false,
            onCheckedChange = {},
            onClick = {},
            openableWhenOff = true,
        )
    }
}

@Suppress("ConstPropertyName")
internal object DeveloperOptionsSettingsTags {
    const val debug = "DeveloperOptionsSettingsTags_debug"
    const val pressureLoss = "DeveloperOptionsSettingsTags_pressureLoss"
}
