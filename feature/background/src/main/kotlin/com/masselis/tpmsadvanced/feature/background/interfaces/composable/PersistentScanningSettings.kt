package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel

/**
 * The "Background scanning" group of the app settings. The details of the activate and the suspend
 * conditions live on pages of their own, opened through [openActivateConditions] and
 * [openSuspendConditions].
 */
@Composable
public fun PersistentScanningSettings(
    openPersistentScanning: () -> Unit,
    openActivateConditions: () -> Unit,
    openSuspendConditions: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = PersistentScanningSettings(
    openPersistentScanning,
    openActivateConditions,
    openSuspendConditions,
    modifier,
    viewModel { PersistentScanningSettingsViewModel() },
)

@Suppress("LongMethod")
@Composable
internal fun PersistentScanningSettings(
    openPersistentScanning: () -> Unit,
    openActivateConditions: () -> Unit,
    openSuspendConditions: () -> Unit,
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
    val activateConditions by viewModel.activateConditions.collectAsState()
    val cable by viewModel.activateOnCableCharging.collectAsState()
    val wireless by viewModel.activateOnWirelessCharging.collectAsState()
    val androidAuto by viewModel.activateOnAndroidAuto.collectAsState()
    val activateBluetooth by viewModel.activateBluetooth.enabled.collectAsState()
    val activateBluetoothDevices by viewModel.activateBluetooth.devices.collectAsState()
    val bluetooth = activateBluetooth && activateBluetoothDevices.isNotEmpty()
    val activateOnBeacon by viewModel.activateOnBeacon.collectAsState()
    val beacons by viewModel.beacons.collectAsState()
    val beacon = activateOnBeacon && beacons.isNotEmpty()
    val stayActive by viewModel.stayActive.collectAsState()
    val stayActiveMinutes by viewModel.stayActiveMinutes.collectAsState()
    val suspendConditions by viewModel.suspendConditions.collectAsState()
    val doze by viewModel.suspendScanningInDoze.collectAsState()
    val wifi by viewModel.suspendScanningOnWifi.collectAsState()
    val wifiException by viewModel.wifiExceptionEnabled.collectAsState()
    val suspendBluetooth by viewModel.suspendBluetooth.enabled.collectAsState()
    val suspendBluetoothDevices by viewModel.suspendBluetooth.devices.collectAsState()
    val suspendOnBluetooth = suspendBluetooth && suspendBluetoothDevices.isNotEmpty()
    PersistentScanningSettings(
        persistentScanning = persistentScanning,
        onPersistentScanning = { enabled ->
            if (enabled) permissions.request() else viewModel.disablePersistentScanning()
        },
        status = viewModel.status(),
        openPersistentScanning = openPersistentScanning,
        // Leaving a conditions page with nothing selected turns its switch off, which only happens
        // once that page is gone: this page shows it that way from the start rather than flashing
        // "None selected" first
        activateConditions = activateConditions && listOf(cable, wireless, androidAuto, bluetooth, beacon).any { it },
        // Turned on without any condition to use, the only sensible next step is to pick one. Leaving
        // the page without doing so turns it off again.
        onActivateConditions = { enabled ->
            viewModel.activateConditions.value = enabled
            if (enabled && listOf(cable, wireless, androidAuto, bluetooth, beacon).none { it }) openActivateConditions()
        },
        activateSummary = activateSummary(
            activateConditions,
            cable,
            wireless,
            androidAuto,
            bluetooth,
            beacon,
            stayActiveMinutes.takeIf { stayActive },
        ),
        openActivateConditions = openActivateConditions,
        suspendConditions = suspendConditions && listOf(doze, wifi, suspendOnBluetooth).any { it },
        onSuspendConditions = { enabled ->
            viewModel.suspendConditions.value = enabled
            if (enabled && listOf(doze, wifi, suspendOnBluetooth).none { it }) openSuspendConditions()
        },
        suspendSummary = suspendSummary(suspendConditions, doze, wifi, wifiException, suspendOnBluetooth),
        openSuspendConditions = openSuspendConditions,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
private fun PersistentScanningSettings(
    persistentScanning: Boolean,
    onPersistentScanning: (Boolean) -> Unit,
    status: BellState?,
    openPersistentScanning: () -> Unit,
    activateConditions: Boolean,
    onActivateConditions: (Boolean) -> Unit,
    activateSummary: List<String>,
    openActivateConditions: () -> Unit,
    suspendConditions: Boolean,
    onSuspendConditions: (Boolean) -> Unit,
    suspendSummary: List<String>,
    openSuspendConditions: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier) {
    PersistentScanningItem(persistentScanning, onPersistentScanning, status, openPersistentScanning)
    // Greyed out rather than hidden while persistent scanning is off, like Android's settings do
    SwitchNavigationSettingsItem(
        headline = "Activate scan conditions",
        supporting = activateSummary.map(::AnnotatedString),
        checked = activateConditions,
        enabled = persistentScanning,
        onCheckedChange = onActivateConditions,
        onClick = openActivateConditions,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.activateConditions),
    )
    SwitchNavigationSettingsItem(
        headline = "Suspend scan conditions",
        supporting = suspendSummary.map(::AnnotatedString),
        checked = suspendConditions,
        enabled = persistentScanning,
        onCheckedChange = onSuspendConditions,
        onClick = openSuspendConditions,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendConditions),
    )
}

/** The persistent scanning switch with its status, which opens the page explaining it */
@Composable
internal fun PersistentScanningItem(
    persistentScanning: Boolean,
    onPersistentScanning: (Boolean) -> Unit,
    status: BellState?,
    openPersistentScanning: () -> Unit,
) = SwitchNavigationSettingsItem(
    headline = "Persistent scanning",
    supporting = listOf(status.statusText("Status: ")),
    checked = persistentScanning,
    onCheckedChange = onPersistentScanning,
    onClick = openPersistentScanning,
    // The page explains the feature, which matters most before turning it on
    openableWhenOff = true,
    modifier = Modifier.testTag(PersistentScanningSettingsTags.persistentScanning),
)

/**
 * From the most detailed to the shortest, the item shows the first one that fits a single line.
 * Without any condition selected, the switch is (or is about to be) off.
 */
private fun activateSummary(
    enabled: Boolean,
    cable: Boolean,
    wireless: Boolean,
    androidAuto: Boolean,
    bluetooth: Boolean,
    beacon: Boolean,
    stayActiveMinutes: Int?,
): List<String> = listOfNotNull(
    // Same order as their page
    "Bluetooth".takeIf { bluetooth },
    "Beacon".takeIf { beacon },
    "Cable charging".takeIf { cable },
    "Wireless charging".takeIf { wireless },
    "Android Auto".takeIf { androidAuto },
)
    .takeIf { enabled && it.isNotEmpty() }
    ?.summary(then = stayActiveMinutes?.let { "then $it min" })
    ?: listOf("Always (drains battery)")

/**
 * From the most detailed to the shortest, the item shows the first one that fits a single line.
 * Without any condition selected, the switch is (or is about to be) off.
 */
private fun suspendSummary(
    enabled: Boolean,
    doze: Boolean,
    wifi: Boolean,
    wifiException: Boolean,
    bluetooth: Boolean,
): List<String> = listOfNotNull(
    // Same order as their page
    (if (wifiException) "WiFi, with exceptions" else "WiFi").takeIf { wifi },
    "Bluetooth".takeIf { bluetooth },
    "Phone idle".takeIf { doze },
)
    .takeIf { enabled && it.isNotEmpty() }
    ?.summary()
    ?: listOf("Never (drains battery)")

private fun List<String>.summary(then: String? = null): List<String> = listOf(
    (this + listOfNotNull(then)).joinToString(", "),
    // "WiFi, with exceptions and more" would be ambiguous: the details are for the long version
    "${first().substringBefore(",")} and more",
)

@Preview
@Composable
internal fun PersistentScanningSettingsPreview() {
    PersistentScanningSettings(
        persistentScanning = true,
        onPersistentScanning = {},
        status = BellState.Active,
        openPersistentScanning = {},
        activateConditions = true,
        onActivateConditions = {},
        activateSummary = listOf("Cable charging, Wireless charging, Android Auto", "Cable charging and more"),
        openActivateConditions = {},
        suspendConditions = false,
        onSuspendConditions = {},
        suspendSummary = listOf("Never (drains battery)"),
        openSuspendConditions = {},
    )
}

@Suppress("ConstPropertyName")
internal object PersistentScanningSettingsTags {
    const val persistentScanning = "PersistentScanningSettingsTags_persistentScanning"
    const val persistentScanningDetails = "PersistentScanningSettingsTags_persistentScanningDetails"
    const val status = "PersistentScanningSettingsTags_status"
    const val activateConditions = "PersistentScanningSettingsTags_activateConditions"
    const val suspendConditions = "PersistentScanningSettingsTags_suspendConditions"
    const val suspendInDoze = "PersistentScanningSettingsTags_suspendInDoze"
    const val suspendOnWifi = "PersistentScanningSettingsTags_suspendOnWifi"
    const val wifiExceptionEnabled = "PersistentScanningSettingsTags_wifiExceptionEnabled"
    const val currentWifiException = "PersistentScanningSettingsTags_currentWifiException"
    const val exceptedWifi = "PersistentScanningSettingsTags_exceptedWifi"
    const val activateOnCable = "PersistentScanningSettingsTags_activateOnCable"
    const val activateOnWireless = "PersistentScanningSettingsTags_activateOnWireless"
    const val activateOnAndroidAuto = "PersistentScanningSettingsTags_activateOnAndroidAuto"
    const val activateOnBluetooth = "PersistentScanningSettingsTags_activateOnBluetooth"
    const val activateOnBeacon = "PersistentScanningSettingsTags_activateOnBeacon"
    const val suspendOnBluetooth = "PersistentScanningSettingsTags_suspendOnBluetooth"
    const val stayActive = "PersistentScanningSettingsTags_stayActive"
    const val stayActiveDuration = "PersistentScanningSettingsTags_stayActiveDuration"
    const val keepAlwaysOn = "PersistentScanningSettingsTags_keepAlwaysOn"
    const val disablePersistent = "PersistentScanningSettingsTags_disablePersistent"
}
