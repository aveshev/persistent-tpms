package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.LocalWifiExceptionJourney
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localized

/** The page opened from the "Suspend scan conditions" item of [PersistentScanningSettings] */
@Composable
public fun SuspendScanConditions(
    openExceptedWifis: () -> Unit,
    openBluetoothDevices: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = SuspendScanConditions(
    openExceptedWifis,
    openBluetoothDevices,
    modifier,
    viewModel { PersistentScanningSettingsViewModel() },
)

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
internal fun SuspendScanConditions(
    openExceptedWifis: () -> Unit,
    openBluetoothDevices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    // Without any condition selected nothing would ever suspend: same as turning them off
    OnLeaveEffect(viewModel::disableSuspendConditionsIfNoneSelected)
    val suspendInDoze by viewModel.suspendScanningInDoze.collectAsState()
    val suspendOnWifi by viewModel.suspendScanningOnWifi.collectAsState()
    val exceptionEnabled by viewModel.wifiExceptionEnabled.collectAsState()
    val exceptedSsids by viewModel.exceptedWifiSsids.collectAsState()
    val bluetooth by viewModel.suspendBluetooth.enabled.collectAsState()
    val bluetoothDevices by viewModel.suspendBluetooth.devices.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState(initial = emptyList())
    val wifiExceptionJourney = LocalWifiExceptionJourney.current
    val permissionState = rememberMultiplePermissionsState(viewModel.requiredWifiPermissions())
    // Keyed on the grant flag: WifiConnectionUseCase's NetworkCallback delivers redacted data
    // (null SSID) while ungranted, and Android doesn't re-deliver capabilities just because
    // permission was newly granted — restarting the collection forces a fresh registration, which
    // reads correctly the moment the permission is actually held.
    // null until the new registration reports, so that a stale answer isn't shown meanwhile
    val wifiState by produceState<WifiConnectionUseCase.State?>(
        initialValue = null,
        key1 = permissionState.allPermissionsGranted,
    ) {
        value = null
        viewModel.wifiConnectionState.collect { value = it }
    }

    Column(modifier) {
        @Suppress("MaxLineLength")
        SettingsIntro(
            stringResource(CommonR.string.background_scanning_is_suspended_whenever_any_of)
        )
        SettingsSectionHeader(stringResource(CommonR.string.suspend_scanning_when_wifi))
        SettingsGroup {
            SwitchSettingsItem(
                headline = stringResource(CommonR.string.connected_to_any_wifi),
                checked = suspendOnWifi,
                onCheckedChange = { viewModel.suspendScanningOnWifi.value = it },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendOnWifi),
            )
            SwitchNavigationSettingsItem(
                headline = stringResource(CommonR.string.except_some_wifis),
                supporting = listOf(AnnotatedString(stringResource(CommonR.string.car_hotspot_garage_etc))),
                checked = exceptionEnabled,
                enabled = suspendOnWifi,
                onClick = openExceptedWifis,
                // Only turns on once the permission is held, so that what depends on it appears then
                onCheckedChange = { enabled ->
                    if (enabled) wifiExceptionJourney.request { viewModel.wifiExceptionEnabled.value = true }
                    else viewModel.wifiExceptionEnabled.value = false
                },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.wifiExceptionEnabled),
            )
            // Always shown, only usable once the exception is on and the connected WiFi is known
            val granted = permissionState.allPermissionsGranted
            val ssid = (wifiState as? WifiConnectionUseCase.State.Connected)?.ssid?.takeIf { granted }
            SwitchSettingsItem(
                headline = stringResource(CommonR.string.keep_scanning_on_current_wifi),
                supporting = currentWifiSummary(wifiState, granted),
                checked = ssid != null && ssid in exceptedSsids,
                enabled = suspendOnWifi && exceptionEnabled && ssid != null,
                onCheckedChange = { ssid?.let(viewModel::toggleExceptedSsid) },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.currentWifiException),
            )
        }
        SettingsSectionHeader(stringResource(CommonR.string.suspend_scanning_when_other))
        SettingsGroup {
            SwitchNavigationSettingsItem(
                headline = stringResource(CommonR.string.bluetooth_device_connected),
                supporting = bluetoothDevicesSummary(paired, bluetoothDevices),
                checked = bluetooth,
                onClick = openBluetoothDevices,
                // Turned on without any device to look for, the only sensible next step is to pick one
                onCheckedChange = { enabled ->
                    viewModel.suspendBluetooth.enabled.value = enabled
                    if (enabled && paired.orEmpty().none { it.address in bluetoothDevices }) openBluetoothDevices()
                },
                // Devices can be removed from the list while the condition is off
                openableWhenOff = true,
                modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendOnBluetooth),
            )
            SwitchSettingsItem(
                headline = stringResource(CommonR.string.the_phone_is_idle_for_a_while),
                supporting = stringResource(CommonR.string.screen_off_or_locked_not_charging_no),
                checked = suspendInDoze,
                onCheckedChange = { viewModel.suspendScanningInDoze.value = it },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendInDoze),
            )
        }
    }
}

private fun currentWifiSummary(
    wifiState: WifiConnectionUseCase.State?,
    granted: Boolean,
): String = when {
    // The WiFi name can't be read without it, so what's connected doesn't matter
    granted.not() -> localized(CommonR.string.needs_full_location_permission)
    wifiState == null -> localized(CommonR.string.checking_wifi_status)
    wifiState == WifiConnectionUseCase.State.Disconnected -> localized(CommonR.string.not_connected_to_a_wifi)
    // Connected, but the system doesn't tell the name: permission granted, yet the Location
    // switch of the phone is off
    wifiState !is WifiConnectionUseCase.State.Connected || wifiState.ssid == null ->
        localized(CommonR.string.turn_on_location_in_phone_settings)
    else -> localized(CommonR.string.connected_3, wifiState.ssid)
}

@Preview
@Composable
internal fun CurrentWifiExceptionItemPreview() {
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Keep scanning on current WiFi:",
            supporting = currentWifiSummary(WifiConnectionUseCase.State.Connected("HomeNetwork"), true),
            checked = false,
            onCheckedChange = {},
        )
        SwitchSettingsItem(
            headline = "Keep scanning on current WiFi:",
            supporting = currentWifiSummary(WifiConnectionUseCase.State.Disconnected, true),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
    }
}
