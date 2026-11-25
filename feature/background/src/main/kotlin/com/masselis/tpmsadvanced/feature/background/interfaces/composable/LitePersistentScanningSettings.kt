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
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Lite's background monitoring: its only conditions (Bluetooth devices, beacons, known sensors) come
 * first as the vehicle detection, then the persistent mode switch with its options, rather than on
 * pages of their own like [PersistentScanningSettings]. The ride notification and
 * [LiteSuspendSettings] follow, outside. They stay usable while
 * persistent scanning is off, so that a trigger can be set up before turning it on. Without any
 * trigger it scans all the time, which [AlwaysOnWarning] tells when that's about to happen, as it
 * tells how slow the known sensors alone are to detect the vehicle.
 */
@Composable
public fun LitePersistentScanningSettings(
    openPersistentScanning: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    openKnownSensors: () -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = LitePersistentScanningSettings(
    openPersistentScanning,
    openBluetoothDevices,
    openBeacons,
    openKnownSensors,
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
    openKnownSensors: () -> Unit,
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
    val knownSensor by viewModel.activateOnKnownSensor.collectAsState()
    val stayActive by viewModel.stayActive.collectAsState()
    val stayActiveMinutes by viewModel.stayActiveMinutes.collectAsState()
    // Like ScanPolicyUseCase: a switch without any device or beacon to look for doesn't count
    val bluetooth = bluetoothEnabled && bluetoothDevices.isNotEmpty()
    val beacon = beaconEnabled && beacons.isNotEmpty()
    val anyTrigger = bluetooth || beacon || knownSensor

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
                (bluetooth || beacon).not() -> warning = AlwaysOnWarning.EnablingWithSensorsOnly
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
        knownSensor = knownSensor,
        onKnownSensor = { viewModel.activateOnKnownSensor.value = it },
        openKnownSensors = openKnownSensors,
        anyTrigger = anyTrigger,
        stayActive = stayActive,
        stayActiveMinutes = stayActiveMinutes,
        onStayActive = { viewModel.stayActive.value = it },
        openStayActiveDuration = openStayActiveDuration,
        modifier = modifier,
    )

    warning?.also { shown ->
        AlwaysOnWarning(
            sensorsOnly = shown == AlwaysOnWarning.EnablingWithSensorsOnly,
            onConfirm = {
                warning = null
                // Already on when the last trigger went, nothing more to do then
                if (shown != AlwaysOnWarning.LastTriggerRemoved) permissions.request()
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
internal enum class AlwaysOnWarning { EnablingWithoutTrigger, EnablingWithSensorsOnly, LastTriggerRemoved }

/**
 * Persistent scanning is, or is about to be, on without any trigger: it would scan all the time.
 * Or, when [sensorsOnly], about to be on with the known sensors as its only trigger, which can take
 * a while to detect the vehicle.
 */
@Suppress("MaxLineLength")
@Composable
internal fun AlwaysOnWarning(
    onConfirm: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    sensorsOnly: Boolean = false,
) = AlertDialog(
    text = {
        Text(
            stringResource(
                if (sensorsOnly) CommonR.string.tpms_sensors_run_on_small_batteries
                else CommonR.string.when_no_bluetooth_connections_or_beacons_are
            ),
            // Scrolls when the largest font sizes make it taller than the dialog
            Modifier.verticalScroll(rememberScrollState()),
        )
    },
    onDismissRequest = onDismiss,
    confirmButton = {
        TextButton(onClick = onConfirm, Modifier.testTag(PersistentScanningSettingsTags.keepAlwaysOn)) {
            Text(stringResource(if (sensorsOnly) CommonR.string.enable_persistent_mode_2 else CommonR.string.keep_always_on))
        }
    },
    dismissButton = {
        TextButton(onClick = onDisable, Modifier.testTag(PersistentScanningSettingsTags.disablePersistent)) {
            Text(stringResource(if (sensorsOnly) CommonR.string.cancel else CommonR.string.disable_persistent_mode))
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
    knownSensor: Boolean,
    onKnownSensor: (Boolean) -> Unit,
    openKnownSensors: () -> Unit,
    anyTrigger: Boolean,
    stayActive: Boolean,
    stayActiveMinutes: Int,
    onStayActive: (Boolean) -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    // How the app tells the vehicle is near comes first: persistent mode and the ride notification
    // below both act on it
    SettingsSectionHeader(stringResource(CommonR.string.vehicle_detection))
    SettingsGroup {
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.connected_bluetooth_devices),
            supporting = bluetoothSummary,
            checked = bluetooth,
            onCheckedChange = onBluetooth,
            onClick = openBluetoothDevices,
            // Devices can be removed from the list while the condition is off
            openableWhenOff = true,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnBluetooth),
        )
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.vehicle_ble_beacons),
            supporting = beaconSummary,
            checked = beacon,
            onCheckedChange = onBeacon,
            onClick = openBeacons,
            // Beacons can be removed from the list while the condition is off
            openableWhenOff = true,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnBeacon),
        )
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.vehicle_tpms_sensors),
            checked = knownSensor,
            onCheckedChange = onKnownSensor,
            onClick = openKnownSensors,
            // Explains how unreliable it is, which matters most before turning it on
            openableWhenOff = true,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnKnownSensor),
        )
    }
    SettingsSectionHeader(stringResource(CommonR.string.persistent_mode))
    SettingsGroup {
        PersistentScanningItem(
            persistentScanning,
            onPersistentScanning,
            status,
            openPersistentScanning,
            headline = stringResource(CommonR.string.persistent_mode),
        )
        // Only keeps persistent scanning going after the vehicle detection ends: meaningless
        // without either, shown off and telling what's missing
        val stayActiveUsable = persistentScanning && anyTrigger
        SwitchNavigationSettingsItem(
            headline = stringResource(CommonR.string.stay_active),
            supporting = when {
                persistentScanning.not() -> listOf(stringResource(CommonR.string.enable_persistent_mode))
                anyTrigger.not() -> listOf(stringResource(CommonR.string.enable_vehicle_detection))
                else -> listOf(
                    stringResource(CommonR.string.for_min_after_the_last_condition_ends, stayActiveMinutes),
                    stringResource(CommonR.string.for_min, stayActiveMinutes),
                )
            }.map(::AnnotatedString),
            checked = stayActive && stayActiveUsable,
            enabled = stayActiveUsable,
            onCheckedChange = onStayActive,
            onClick = openStayActiveDuration,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.stayActive),
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
        knownSensor = false,
        onKnownSensor = {},
        openKnownSensors = {},
        anyTrigger = false,
        stayActive = false,
        stayActiveMinutes = 10,
        onStayActive = {},
        openStayActiveDuration = {},
    )
}

@Preview
@Composable
internal fun AlwaysOnWarningPreview() {
    AlwaysOnWarning(onConfirm = {}, onDisable = {}, onDismiss = {})
}

@Preview
@Composable
internal fun SensorsOnlyWarningPreview() {
    AlwaysOnWarning(onConfirm = {}, onDisable = {}, onDismiss = {}, sensorsOnly = true)
}
