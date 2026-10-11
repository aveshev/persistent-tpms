package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/**
 * The "Display" group of the app settings, [openTimeSinceUpdate] opening the page explaining it.
 * [additionalItems] are appended to the group, for display settings owned by other features.
 */
@Composable
public fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
): Unit =
    TyreDisplaySettings(
        openTimeSinceUpdate,
        modifier,
        additionalItems,
        viewModel { TyreDisplaySettingsViewModel() },
    )

@Composable
internal fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    val showBatteryVoltage by viewModel.showBatteryVoltage.collectAsState()
    val magnifierSeconds by viewModel.magnifierSeconds.collectAsState()
    TyreDisplaySettings(
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        showBatteryVoltage = showBatteryVoltage,
        onShowBatteryVoltage = { viewModel.showBatteryVoltage.value = it },
        magnifierSeconds = magnifierSeconds,
        onMagnifierSeconds = { viewModel.magnifierSeconds.value = it },
        openTimeSinceUpdate = openTimeSinceUpdate,
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
    magnifierSeconds: Int,
    onMagnifierSeconds: (Int) -> Unit,
    openTimeSinceUpdate: () -> Unit,
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
    var isMagnifierExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextSettingsItem(
            headline = "Magnify on tap",
            supporting = magnifierSeconds
                .takeIf { it > 0 }
                ?.let { "Tap near a tyre to show its reading large for $it s" }
                ?: "Off",
            onClick = { isMagnifierExpanded = true },
            modifier = Modifier.testTag(TyreDisplaySettingsTags.magnifier),
        )
        DropdownMenu(expanded = isMagnifierExpanded, onDismissRequest = { isMagnifierExpanded = false }) {
            MAGNIFIER_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option > 0) "$option s" else "Off") },
                    onClick = {
                        onMagnifierSeconds(option)
                        isMagnifierExpanded = false
                    },
                    trailingIcon = {
                        if (option == magnifierSeconds) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${TyreDisplaySettingsTags.magnifierOption}_$option"),
                )
            }
        }
    }
    additionalItems()
}

/** In seconds, 0 being off */
@Suppress("MagicNumber")
private val MAGNIFIER_OPTIONS = listOf(0, 3, 5, 10, 20, 30)

@Preview
@Composable
internal fun TyreDisplaySettingsPreview() {
    TyreDisplaySettings(
        showTimeSinceUpdate = true,
        onShowTimeSinceUpdate = {},
        showBatteryVoltage = false,
        onShowBatteryVoltage = {},
        magnifierSeconds = 5,
        onMagnifierSeconds = {},
        openTimeSinceUpdate = {},
    )
}

@Suppress("ConstPropertyName")
internal object TyreDisplaySettingsTags {
    const val showTimeSinceUpdate = "TyreDisplaySettingsTags_showTimeSinceUpdate"
    const val showBatteryVoltage = "TyreDisplaySettingsTags_showBatteryVoltage"
    const val magnifier = "TyreDisplaySettingsTags_magnifier"
    const val magnifierOption = "TyreDisplaySettingsTags_magnifierOption"
    const val timeSinceUpdateDetails = "TyreDisplaySettingsTags_timeSinceUpdateDetails"
}
