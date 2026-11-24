package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.KeepAliveInstructions
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The item opening the instructions that keep the phone from stopping background monitoring: at the
 * bottom of Lite's background monitoring page, as it matters to persistent mode and the ride
 * notification alike. Pro has it on its persistent scanning page.
 */
@Composable
public fun KeepMonitoringRunningSettings(modifier: Modifier = Modifier) {
    var showKeepAliveInstructions by rememberSaveable { mutableStateOf(false) }
    if (showKeepAliveInstructions) KeepAliveInstructions(onDismissRequest = { showKeepAliveInstructions = false })
    SettingsGroup(modifier.padding(top = 24.dp)) {
        TextSettingsItem(
            headline = stringResource(CommonR.string.keep_monitoring_running_on_this_phone),
            supporting = stringResource(CommonR.string.some_phones_stop_background_apps_on_their),
            onClick = { showKeepAliveInstructions = true },
        )
    }
}
