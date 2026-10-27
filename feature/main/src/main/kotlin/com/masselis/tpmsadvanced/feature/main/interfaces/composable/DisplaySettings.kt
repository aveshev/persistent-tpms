package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.DARK
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.LIGHT
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.SYSTEM
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/**
 * The "Display" group of the app settings, about the app and its main screen as a whole, see
 * [ReadingsSettings] for what each reading shows. [openTextSize] opens the page choosing the
 * readouts' text size, see [TextSize], [openMagnifier] the one choosing how long a reading stays
 * magnified, see [MagnifierDuration], [openDimDisplay] the one choosing how long the main screen
 * waits before dimming, see [DimDisplayDelay].
 */
@Composable
public fun DisplaySettings(
    openTextSize: () -> Unit,
    openMagnifier: () -> Unit,
    openDimDisplay: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = DisplaySettings(
    openTextSize,
    openMagnifier,
    openDimDisplay,
    modifier,
    viewModel { TyreDisplaySettingsViewModel() },
)

@Composable
internal fun DisplaySettings(
    openTextSize: () -> Unit,
    openMagnifier: () -> Unit,
    openDimDisplay: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val theme by viewModel.theme.collectAsState()
    val readoutTextScale by viewModel.readoutTextScale.collectAsState()
    val magnifier by viewModel.magnifier.collectAsState()
    val magnifierSeconds by viewModel.magnifierSeconds.collectAsState()
    val dimDisplay by viewModel.dimDisplay.collectAsState()
    val dimDisplaySeconds by viewModel.dimDisplaySeconds.collectAsState()
    DisplaySettings(
        theme = theme,
        onTheme = { viewModel.theme.value = it },
        readoutTextScale = readoutTextScale,
        openTextSize = openTextSize,
        magnifier = magnifier,
        onMagnifier = { viewModel.magnifier.value = it },
        magnifierSeconds = magnifierSeconds,
        openMagnifier = openMagnifier,
        dimDisplay = dimDisplay,
        onDimDisplay = { viewModel.dimDisplay.value = it },
        dimDisplaySeconds = dimDisplaySeconds,
        openDimDisplay = openDimDisplay,
        modifier = modifier,
    )
}

@Composable
private fun DisplaySettings(
    theme: Theme,
    onTheme: (Theme) -> Unit,
    readoutTextScale: Float,
    openTextSize: () -> Unit,
    magnifier: Boolean,
    onMagnifier: (Boolean) -> Unit,
    magnifierSeconds: Int,
    openMagnifier: () -> Unit,
    dimDisplay: Boolean,
    onDimDisplay: (Boolean) -> Unit,
    dimDisplaySeconds: Int,
    openDimDisplay: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier) {
    SegmentedSettingsItem(
        headline = "Theme",
        options = Theme.entries,
        selected = theme,
        onSelect = onTheme,
        label = {
            when (it) {
                SYSTEM -> "System"
                LIGHT -> "Light"
                DARK -> "Dark"
            }
        },
        icon = {
            when (it) {
                SYSTEM -> R.drawable.brightness_auto_24px
                LIGHT -> R.drawable.light_mode_24px
                DARK -> R.drawable.dark_mode_24px
            }.let { id -> ImageVector.vectorResource(id) }
        },
        modifier = Modifier.testTag(TyreDisplaySettingsTags.theme),
    )
    SwitchNavigationSettingsItem(
        headline = "Dim when idle",
        supporting = listOf(AnnotatedString("after ${dimDisplaySeconds.asDimDelay()}")),
        checked = dimDisplay,
        onCheckedChange = onDimDisplay,
        onClick = openDimDisplay,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.dimDisplay),
    )
    SwitchNavigationSettingsItem(
        headline = "Tap to magnify",
        supporting = listOf(AnnotatedString("for $magnifierSeconds seconds")),
        checked = magnifier,
        onCheckedChange = onMagnifier,
        onClick = openMagnifier,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.magnifier),
    )
    TextSettingsItem(
        headline = "Sensor readings text size",
        supporting = readoutTextScale.textSizeLabel,
        onClick = openTextSize,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.textSize),
    )
}

@Preview
@Composable
internal fun DisplaySettingsPreview() {
    DisplaySettings(
        theme = SYSTEM,
        onTheme = {},
        readoutTextScale = 1f,
        openTextSize = {},
        magnifier = true,
        onMagnifier = {},
        magnifierSeconds = 5,
        openMagnifier = {},
        dimDisplay = true,
        onDimDisplay = {},
        dimDisplaySeconds = 60,
        openDimDisplay = {},
    )
}

/** Test tags of [DisplaySettings], [ReadingsSettings] and the pages they open */
@Suppress("ConstPropertyName")
internal object TyreDisplaySettingsTags {
    const val showTimeSinceUpdate = "TyreDisplaySettingsTags_showTimeSinceUpdate"
    const val showBatteryVoltage = "TyreDisplaySettingsTags_showBatteryVoltage"
    const val magnifier = "TyreDisplaySettingsTags_magnifier"
    const val magnifierDuration = "TyreDisplaySettingsTags_magnifierDuration"
    const val theme = "TyreDisplaySettingsTags_theme"
    const val dimDisplay = "TyreDisplaySettingsTags_dimDisplay"
    const val dimDisplayDelay = "TyreDisplaySettingsTags_dimDisplayDelay"
    const val pocketGuard = "TyreDisplaySettingsTags_pocketGuard"
    const val textSize = "TyreDisplaySettingsTags_textSize"
    const val textSizeSlider = "TyreDisplaySettingsTags_textSizeSlider"
    const val timeSinceUpdateDetails = "TyreDisplaySettingsTags_timeSinceUpdateDetails"
}
