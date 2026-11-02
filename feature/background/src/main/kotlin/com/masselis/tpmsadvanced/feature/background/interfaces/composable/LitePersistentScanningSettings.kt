package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * Lite's "Background scanning" group: its only conditions are right under the persistent scanning
 * switch, rather than on pages of their own like [PersistentScanningSettings]. They stay usable while
 * persistent scanning is off, so that a trigger can be set up before turning it on. Without any
 * trigger it scans all the time, which [AlwaysOnWarning] tells when that's about to happen.
 */
@Composable
public fun LitePersistentScanningSettings(
    openPersistentScanning: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = LitePersistentScanningSettings(
    openPersistentScanning,
    openBluetoothDevices,
    openBeacons,
    openStayActiveDuration,
    modifier,
    viewModel { PersistentScanningSettingsViewModel() },
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun LitePersistentScanningSettings(
    openPersistentScanning: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    // The toggle only turns on once everything the service needs was granted; refusing leaves it off
    val permissions = rememberMonitoringPermissions(
        confirmBeforeStart = false,
        onGranted = viewModel::enablePersistentScanning,
    )
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val bluetoothEnabled by viewModel.activateBluetooth.enabled.collectAsState()
    val bluetoothDevices by viewModel.activateBluetooth.devices.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState(initial = emptyList())
    val beaconEnabled by viewModel.activateOnBeacon.collectAsState()
    val beacons by viewModel.beacons.collectAsState()
    val stayActive by viewModel.stayActive.collectAsState()
    val stayActiveMinutes by viewModel.stayActiveMinutes.collectAsState()
    val suspendInDoze by viewModel.suspendScanningInDoze.collectAsState()
    // Like ScanPolicyUseCase: a switch without any device or beacon to look for doesn't count
    val bluetooth = bluetoothEnabled && bluetoothDevices.isNotEmpty()
    val beacon = beaconEnabled && beacons.isNotEmpty()
    val anyTrigger = bluetooth || beacon

    var warning by rememberSaveable { mutableStateOf<AlwaysOnWarning?>(null) }
    // The last trigger can go from any page: its switch here, but also the device picker or the
    // beacons page left without any. Told once back here, where the choice can be made: saved, so
    // that it outlives the trip to those pages.
    var hadTrigger by rememberSaveable { mutableStateOf(anyTrigger) }
    LaunchedEffect(anyTrigger) {
        if (hadTrigger && anyTrigger.not() && persistentScanning) warning = AlwaysOnWarning.LastTriggerRemoved
        hadTrigger = anyTrigger
    }

    LitePersistentScanningSettings(
        persistentScanning = persistentScanning,
        onPersistentScanning = { enabled ->
            when {
                enabled.not() -> viewModel.disablePersistentScanning()
                anyTrigger.not() -> warning = AlwaysOnWarning.EnablingWithoutTrigger
                else -> permissions.request()
            }
        },
        status = viewModel.status(),
        openPersistentScanning = openPersistentScanning,
        bluetooth = bluetoothEnabled,
        bluetoothSummary = bluetoothDevicesSummary(paired, bluetoothDevices),
        // Turned on without any device to look for, the only sensible next step is to pick one
        onBluetooth = { enabled ->
            viewModel.activateBluetooth.enabled.value = enabled
            if (enabled && paired.orEmpty().none { it.address in bluetoothDevices }) openBluetoothDevices()
        },
        openBluetoothDevices = openBluetoothDevices,
        beacon = beaconEnabled,
        beaconSummary = beaconsSummary(beacons),
        // Turned on without any beacon to look for, the only sensible next step is to add one
        onBeacon = { enabled ->
            viewModel.activateOnBeacon.value = enabled
            if (enabled && beacons.isEmpty()) openBeacons()
        },
        openBeacons = openBeacons,
        anyTrigger = anyTrigger,
        stayActive = stayActive,
        stayActiveMinutes = stayActiveMinutes,
        onStayActive = { viewModel.stayActive.value = it },
        openStayActiveDuration = openStayActiveDuration,
        suspendInDoze = suspendInDoze,
        onSuspendInDoze = { viewModel.suspendScanningInDoze.value = it },
        modifier = modifier,
    )

    warning?.also { shown ->
        AlwaysOnWarning(
            onKeepAlwaysOn = {
                warning = null
                // Already on when the last trigger went, nothing more to do then
                if (shown == AlwaysOnWarning.EnablingWithoutTrigger) permissions.request()
            },
            onDisable = {
                warning = null
                // Never turned on when enabling, nothing to undo then
                if (shown == AlwaysOnWarning.LastTriggerRemoved) viewModel.disablePersistentScanning()
            },
            // Leaves things as they were: off when enabling, on when the last trigger went
            onDismiss = { warning = null },
        )
    }
}

/** Why the [AlwaysOnWarning] dialog is shown */
internal enum class AlwaysOnWarning { EnablingWithoutTrigger, LastTriggerRemoved }

/** Persistent scanning is, or is about to be, on without any trigger: it would scan all the time */
@Suppress("MaxLineLength")
@Composable
internal fun AlwaysOnWarning(onKeepAlwaysOn: () -> Unit, onDisable: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    text = {
        Text(
            stringResource(CommonR.string.when_no_bluetooth_connections_or_beacons_are)
        )
    },
    onDismissRequest = onDismiss,
    confirmButton = {
        TextButton(onClick = onKeepAlwaysOn, Modifier.testTag(PersistentScanningSettingsTags.keepAlwaysOn)) {
            Text(stringResource(CommonR.string.keep_always_on))
        }
    },
    dismissButton = {
        TextButton(onClick = onDisable, Modifier.testTag(PersistentScanningSettingsTags.disablePersistent)) {
            Text(stringResource(CommonR.string.disable_persistent_scanning))
        }
    },
)

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun LitePersistentScanningSettings(
    persistentScanning: Boolean,
    onPersistentScanning: (Boolean) -> Unit,
    status: BellState?,
    openPersistentScanning: () -> Unit,
    bluetooth: Boolean,
    bluetoothSummary: List<AnnotatedString>,
    onBluetooth: (Boolean) -> Unit,
    openBluetoothDevices: () -> Unit,
    beacon: Boolean,
    beaconSummary: List<AnnotatedString>,
    onBeacon: (Boolean) -> Unit,
    openBeacons: () -> Unit,
    anyTrigger: Boolean,
    stayActive: Boolean,
    stayActiveMinutes: Int,
    onStayActive: (Boolean) -> Unit,
    openStayActiveDuration: () -> Unit,
    suspendInDoze: Boolean,
    onSuspendInDoze: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsGroup {
        PersistentScanningItem(persistentScanning, onPersistentScanning, status, openPersistentScanning)
    }
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
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.stay_active),
            supporting = listOf(
                stringResource(CommonR.string.for_min_after_the_last_condition_ends, stayActiveMinutes),
                stringResource(CommonR.string.for_min, stayActiveMinutes),
            ).map(::AnnotatedString),
            checked = stayActive,
            // Only extends the conditions above, meaningless without any of them
            enabled = anyTrigger,
            onCheckedChange = onStayActive,
            onClick = openStayActiveDuration,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.stayActive),
        )
    }
    SettingsSectionHeader(stringResource(CommonR.string.suspend))
    SettingsGroup {
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.suspend_when_idle),
            supporting = stringResource(CommonR.string.screen_off_or_locked_not_charging_no),
            checked = suspendInDoze,
            onCheckedChange = onSuspendInDoze,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendInDoze),
        )
    }
}

@Suppress("MagicNumber")
@Preview
@Composable
internal fun LitePersistentScanningSettingsPreview() {
    LitePersistentScanningSettings(
        persistentScanning = true,
        onPersistentScanning = {},
        status = BellState.Always,
        openPersistentScanning = {},
        bluetooth = false,
        bluetoothSummary = listOf(AnnotatedString("None selected")),
        onBluetooth = {},
        openBluetoothDevices = {},
        beacon = false,
        beaconSummary = listOf(AnnotatedString("None added")),
        onBeacon = {},
        openBeacons = {},
        anyTrigger = false,
        stayActive = false,
        stayActiveMinutes = 10,
        onStayActive = {},
        openStayActiveDuration = {},
        suspendInDoze = true,
        onSuspendInDoze = {},
    )
}

@Preview
@Composable
internal fun AlwaysOnWarningPreview() {
    AlwaysOnWarning(onKeepAlwaysOn = {}, onDisable = {}, onDismiss = {})
}
