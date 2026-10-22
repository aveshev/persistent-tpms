package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DemoModeSwitchViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DemoModeSwitchViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DemoModeSwitchViewModel

/** Switches between made-up sensors and the real ones, see [MiscSettings] */
@Composable
internal fun DemoModeSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: DemoModeSwitchViewModel = viewModel { DemoModeSwitchViewModel() },
) {
    val state by viewModel.stateFlow.collectAsState()
    DemoModeSettingsItem(
        demoMode = state == State.Enabled,
        onDemoMode = { if (it) viewModel.enable() else viewModel.disable() },
        modifier = modifier,
    )
}

@Composable
internal fun DemoModeSettingsItem(
    demoMode: Boolean,
    onDemoMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SwitchSettingsItem(
    headline = "Demo mode",
    checked = demoMode,
    onCheckedChange = onDemoMode,
    supporting = "Changing restarts the app",
    modifier = modifier.testTag(DemoModeSettingsTags.demoMode),
)

@Suppress("ConstPropertyName")
internal object DemoModeSettingsTags {
    const val demoMode = "DemoModeSettingsTags_demoMode"
}
