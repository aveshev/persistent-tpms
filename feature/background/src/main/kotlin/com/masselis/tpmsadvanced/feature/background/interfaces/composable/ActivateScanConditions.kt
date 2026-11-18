package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The page opened from the "Activate scan conditions" item of [PersistentScanningSettings], the
 * duration of "Stay active" being picked on its own page, opened through [openStayActiveDuration],
 * the Bluetooth devices on another one, opened through [openBluetoothDevices], and the beacons on a
 * third one, opened through [openBeacons].
 */
@Composable
public fun ActivateScanConditions(
    openStayActiveDuration: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = ActivateScanConditions(
    openStayActiveDuration,
    openBluetoothDevices,
    openBeacons,
    modifier,
    viewModel { PersistentScanningSettingsViewModel() },
)

@Composable
internal fun ActivateScanConditions(
    openStayActiveDuration: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    // Without any condition selected nothing would ever scan: leaving the page like this means
    // not wanting the conditions at all
    OnLeaveEffect(viewModel::disableActivateConditionsIfNoneSelected)
    val cable by viewModel.activateOnCableCharging.collectAsState()
    val wireless by viewModel.activateOnWirelessCharging.collectAsState()
    val androidAuto by viewModel.activateOnAndroidAuto.collectAsState()
    val bluetooth by viewModel.activateBluetooth.enabled.collectAsState()
    val bluetoothDevices by viewModel.activateBluetooth.devices.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState(initial = emptyList())
    val beacon by viewModel.activateOnBeacon.collectAsState()
    val beacons by viewModel.beacons.collectAsState()
    val stayActive by viewModel.stayActive.collectAsState()
    val stayActiveMinutes by viewModel.stayActiveMinutes.collectAsState()
    ActivateScanConditions(
        cable = cable,
        onCable = { viewModel.activateOnCableCharging.value = it },
        wireless = wireless,
        onWireless = { viewModel.activateOnWirelessCharging.value = it },
        androidAuto = androidAuto,
        onAndroidAuto = { viewModel.activateOnAndroidAuto.value = it },
        bluetooth = bluetooth,
        bluetoothSummary = bluetoothDevicesSummary(paired, bluetoothDevices),
        // Turned on without any device to look for, the only sensible next step is to pick one
        onBluetooth = { enabled ->
            viewModel.activateBluetooth.enabled.value = enabled
            if (enabled && paired.orEmpty().none { it.address in bluetoothDevices }) openBluetoothDevices()
        },
        openBluetoothDevices = openBluetoothDevices,
        bluetoothSelected = bluetooth && bluetoothDevices.isNotEmpty(),
        beacon = beacon,
        beaconSummary = beaconsSummary(beacons),
        // Turned on without any beacon to look for, the only sensible next step is to add one
        onBeacon = { enabled ->
            viewModel.activateOnBeacon.value = enabled
            if (enabled && beacons.isEmpty()) openBeacons()
        },
        openBeacons = openBeacons,
        beaconSelected = beacon && beacons.isNotEmpty(),
        stayActive = stayActive,
        stayActiveMinutes = stayActiveMinutes,
        onStayActive = { viewModel.stayActive.value = it },
        openStayActiveDuration = openStayActiveDuration,
        modifier = modifier,
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun ActivateScanConditions(
    cable: Boolean,
    onCable: (Boolean) -> Unit,
    wireless: Boolean,
    onWireless: (Boolean) -> Unit,
    androidAuto: Boolean,
    onAndroidAuto: (Boolean) -> Unit,
    bluetooth: Boolean,
    bluetoothSummary: List<AnnotatedString>,
    onBluetooth: (Boolean) -> Unit,
    openBluetoothDevices: () -> Unit,
    bluetoothSelected: Boolean,
    beacon: Boolean,
    beaconSummary: List<AnnotatedString>,
    onBeacon: (Boolean) -> Unit,
    openBeacons: () -> Unit,
    beaconSelected: Boolean,
    stayActive: Boolean,
    stayActiveMinutes: Int,
    onStayActive: (Boolean) -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        stringResource(CommonR.string.background_scanning_listens_to_your_tyre_sensors)
    )
    SettingsSectionHeader(stringResource(CommonR.string.scan_when))
    SettingsGroup {
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.bluetooth_device_connected),
            supporting = bluetoothSummary,
            checked = bluetooth,
            onCheckedChange = onBluetooth,
            onClick = openBluetoothDevices,
            // Devices can be removed from the list while the condition is off
            openableWhenOff = true,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnBluetooth),
        )
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.bluetooth_beacon_nearby),
            supporting = beaconSummary,
            checked = beacon,
            onCheckedChange = onBeacon,
            onClick = openBeacons,
            // Beacons can be removed from the list while the condition is off
            openableWhenOff = true,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnBeacon),
        )
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.charging_with_a_cable),
            checked = cable,
            onCheckedChange = onCable,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnCable),
        )
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.charging_wirelessly),
            checked = wireless,
            onCheckedChange = onWireless,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnWireless),
        )
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.android_auto_is_connected),
            checked = androidAuto,
            onCheckedChange = onAndroidAuto,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnAndroidAuto),
        )
    }
    SettingsSectionHeader(stringResource(CommonR.string.afterwards))
    SettingsGroup {
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.stay_active),
            supporting = listOf(
                stringResource(CommonR.string.for_min_after_the_last_condition_ends, stayActiveMinutes),
                stringResource(CommonR.string.for_min, stayActiveMinutes),
            ).map(::AnnotatedString),
            checked = stayActive,
            // Only extends the conditions above, meaningless without any of them
            enabled = cable || wireless || androidAuto || bluetoothSelected || beaconSelected,
            onCheckedChange = onStayActive,
            onClick = openStayActiveDuration,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.stayActive),
        )
    }
}

@Suppress("MagicNumber")
@Preview
@Composable
internal fun ActivateScanConditionsPreview() {
    ActivateScanConditions(
        cable = true,
        onCable = {},
        wireless = true,
        onWireless = {},
        androidAuto = true,
        onAndroidAuto = {},
        bluetooth = true,
        bluetoothSummary = listOf(AnnotatedString("ZEEKR-9DFD")),
        onBluetooth = {},
        openBluetoothDevices = {},
        bluetoothSelected = true,
        beacon = true,
        beaconSummary = listOf(AnnotatedString("Bike")),
        onBeacon = {},
        openBeacons = {},
        beaconSelected = true,
        stayActive = true,
        stayActiveMinutes = 10,
        onStayActive = {},
        openStayActiveDuration = {},
    )
}
