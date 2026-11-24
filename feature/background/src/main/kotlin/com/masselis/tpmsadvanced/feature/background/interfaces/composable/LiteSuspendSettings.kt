package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * Lite's only suspend condition, after the ride notification on its background monitoring page: it
 * holds back persistent scanning and the ride notification's vehicle detection alike
 */
@Composable
public fun LiteSuspendSettings(modifier: Modifier = Modifier): Unit =
    LiteSuspendSettings(modifier, viewModel { PersistentScanningSettingsViewModel() })

@Composable
internal fun LiteSuspendSettings(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) = Column(modifier) {
    val suspendInDoze by viewModel.suspendScanningInDoze.collectAsState()
    SettingsSectionHeader(stringResource(CommonR.string.suspend_monitoring))
    SettingsGroup {
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.suspend_when_idle),
            supporting = stringResource(CommonR.string.screen_off_or_locked_not_charging_no),
            checked = suspendInDoze,
            onCheckedChange = { viewModel.suspendScanningInDoze.value = it },
            modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendInDoze),
        )
    }
}
