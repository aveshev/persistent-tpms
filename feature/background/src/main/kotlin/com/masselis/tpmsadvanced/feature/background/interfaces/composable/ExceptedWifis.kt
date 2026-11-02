package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.masselis.tpmsadvanced.core.ui.ActionSettingsItem
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The page opened from the "...except some WiFis" item of [SuspendScanConditions]. A removed WiFi
 * stays in the list, struck through with an undo button, and is only removed for good once the
 * page is left: a WiFi can only be added back while connected to it.
 */
@Composable
public fun ExceptedWifis(modifier: Modifier = Modifier): Unit =
    ExceptedWifis(modifier, viewModel { PersistentScanningSettingsViewModel() })

@Composable
internal fun ExceptedWifis(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    val ssids by viewModel.exceptedWifiSsids.collectAsState()
    val wifiState by viewModel.wifiConnectionState.collectAsState(initial = null)
    // Saved, so that a rotation keeps what was removed so far
    var removed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    OnLeaveEffect { viewModel.removeExceptedSsids(removed.toSet()) }
    ExceptedWifis(
        ssids = (ssids + removed).sorted(),
        removed = removed.toSet(),
        connectedSsid = (wifiState as? WifiConnectionUseCase.State.Connected)?.ssid,
        onRemove = { removed = removed + it },
        onUndo = { removed = removed - it },
        modifier = modifier,
    )
}

@Composable
private fun ExceptedWifis(
    ssids: List<String>,
    removed: Set<String>,
    connectedSsid: String?,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        stringResource(CommonR.string.background_scanning_is_not_suspended_while_connected)
    )
    SettingsSectionHeader(stringResource(CommonR.string.excepted_wifis))
    SettingsGroup {
        if (ssids.isEmpty()) SettingsItem { Text(stringResource(CommonR.string.no_wifi_excepted_yet)) }
        ssids.forEach { ssid ->
            val isRemoved = ssid in removed
            ActionSettingsItem(
                headline = ssid,
                supporting = stringResource(CommonR.string.connected_2).takeIf { ssid == connectedSsid },
                struckOut = isRemoved,
                modifier = Modifier.testTag("${PersistentScanningSettingsTags.exceptedWifi}_$ssid"),
            ) {
                if (isRemoved) {
                    TextButton(onClick = { onUndo(ssid) }) { Text(stringResource(CommonR.string.undo)) }
                } else {
                    IconButton(onClick = { onRemove(ssid) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                            contentDescription = stringResource(CommonR.string.remove_3, ssid),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
internal fun ExceptedWifisPreview() {
    ExceptedWifis(
        ssids = listOf("HomeNetwork", "Office", "Parents"),
        removed = setOf("Office"),
        connectedSsid = "HomeNetwork",
        onRemove = {},
        onUndo = {},
    )
}
