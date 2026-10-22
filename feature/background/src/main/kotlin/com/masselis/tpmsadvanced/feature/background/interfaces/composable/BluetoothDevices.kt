package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import android.content.Intent
import android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.CheckboxSettingsItem
import com.masselis.tpmsadvanced.core.ui.ExpandSettingsItem
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SettingsSectionNote
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.rememberBluetoothState
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import kotlinx.coroutines.flow.map

/** The page opened from the "Bluetooth device connected" item of [ActivateScanConditions] */
@Composable
public fun ActivateBluetoothDevices(modifier: Modifier = Modifier): Unit =
    BluetoothDevices(activate = true, modifier = modifier)

/** The page opened from the "Bluetooth device connected" item of [SuspendScanConditions] */
@Composable
public fun SuspendBluetoothDevices(modifier: Modifier = Modifier): Unit =
    BluetoothDevices(activate = false, modifier = modifier)

/**
 * Picks the paired devices of the activate ([activate]) or the suspend Bluetooth condition. A device
 * can't be in both: the ones selected for the other condition are listed last, not selectable. Lite
 * has no suspend condition, the devices left in it (imported from Pro) are free to pick there.
 */
@Composable
internal fun BluetoothDevices(
    activate: Boolean,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
    tier: AppTier = AppTier.current,
) {
    SettingsOnScreenEffect()
    val condition = if (activate) viewModel.activateBluetooth else viewModel.suspendBluetooth
    val other = if (activate) viewModel.suspendBluetooth else viewModel.activateBluetooth
    // Wrapped, so that "not read yet" isn't mistaken for "Bluetooth is off" (null devices)
    val paired by remember(viewModel) { viewModel.pairedDevices.map(::Paired) }
        .collectAsState(initial = null)
    OnLeaveEffect { paired?.let { viewModel.disableBluetoothIfNoneSelected(condition, it.devices) } }
    val selected by condition.devices.collectAsState()
    val otherSelected by other.devices.collectAsState()
    val connected by viewModel.connectedDevices.collectAsState(initial = emptySet())
    // Taken once: ticking a device doesn't move it under the finger, the order is updated next time
    val selectedAtEntry = rememberSaveable { condition.devices.value.toList() }.toSet()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bluetoothState = rememberBluetoothState()
    val context = LocalContext.current
    BluetoothDevices(
        activate = activate,
        paired = paired,
        selected = selected,
        selectedAtEntry = selectedAtEntry,
        otherSelected = otherSelected.takeIf { tier != LITE }.orEmpty(),
        connected = connected,
        expanded = expanded,
        onToggle = { address ->
            viewModel.toggleBluetoothDevice(condition, address)
            // Picked in Lite, it leaves the suspend condition it can't see, so that it never ends in both
            if (tier == LITE) other.devices.value -= address
        },
        onExpand = { expanded = expanded.not() },
        onTurnOnBluetooth = bluetoothState::askEnable,
        openBluetoothSettings = { context.startActivity(Intent(ACTION_BLUETOOTH_SETTINGS)) },
        modifier = modifier,
    )
}

// Devices moved from another phone (e.g. a Samsung restore) are listed by the system settings, yet
// not paired until they connect once: the system doesn't tell them to apps meanwhile
@Suppress("MaxLineLength")
private const val PAIRING_HINT =
    "Devices moved from another phone appear here once connected to this one. Pair or connect one from the Bluetooth settings."

/** What the system tells about the paired devices, null [devices] while Bluetooth is off */
internal data class Paired(val devices: List<PairedDevice>?)

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MaxLineLength")
@Composable
private fun BluetoothDevices(
    activate: Boolean,
    paired: Paired?,
    selected: Set<String>,
    selectedAtEntry: Set<String>,
    otherSelected: Set<String>,
    connected: Set<String>,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    onExpand: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    openBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        if (activate) "Background scanning activates while any of the checked devices is connected: your car stereo, helmet intercom, etc. The devices paired with this phone are listed here."
        else "Background scanning is suspended while any of the checked devices is connected: a home speaker, your office headset, etc. The devices paired with this phone are listed here."
    )
    SettingsSectionHeader(
        if (activate) "Activate scanning when connected to" else "Suspend scanning when connected to"
    )
    val devices = paired?.devices
    when {
        // Not read yet, a moment only
        paired == null -> return@Column
        devices == null -> SettingsGroup {
            TextSettingsItem(
                headline = "Bluetooth is off",
                supporting = "Turn it on to see your paired devices",
                onClick = onTurnOnBluetooth,
                modifier = Modifier.testTag(BluetoothDevicesTags.bluetoothOff),
            )
        }

        devices.isEmpty() -> SettingsGroup {
            TextSettingsItem(
                headline = "No paired device",
                supporting = PAIRING_HINT,
                onClick = openBluetoothSettings,
                modifier = Modifier.testTag(BluetoothDevicesTags.noDevice),
            )
        }

        else -> {
            // The connected ones first, selected or not: the device at hand is the likeliest pick,
            // a newly paired one included. Unlike the selected ones, they move as soon as they
            // connect.
            val (others, selectable) = devices
                .sortedWith(compareBy({ it.address !in connected }, { it.name.lowercase() }))
                .partition { it.address in otherSelected && it.address !in selectedAtEntry }
            val (first, rest) = selectable.partition { it.address in connected || it.address in selectedAtEntry }
            val (audio, nonAudio) = rest.partition { it.isAudio }
            @Composable
            fun Device(device: PairedDevice, enabled: Boolean = true) = CheckboxSettingsItem(
                headline = if (device.address in connected) "${device.name} (connected)" else device.name,
                checked = device.address in selected || enabled.not(),
                enabled = enabled,
                onCheckedChange = { onToggle(device.address) },
                modifier = Modifier.testTag("${BluetoothDevicesTags.device}_${device.address}"),
            )
            if (selectable.isNotEmpty()) SettingsGroup {
                (first + audio).forEach { Device(it) }
                if (nonAudio.isEmpty()) return@SettingsGroup
                ExpandSettingsItem(
                    headline = "Other devices (${nonAudio.size})",
                    expanded = expanded,
                    onClick = onExpand,
                    modifier = Modifier.testTag(BluetoothDevicesTags.otherDevices),
                )
                if (expanded) nonAudio.forEach { Device(it) }
            }
            if (others.isNotEmpty()) {
                SettingsSectionHeader(
                    if (activate) "Already selected to suspend scanning" else "Already selected to activate scanning"
                )
                SettingsSectionNote(
                    if (activate) "The devices below have already been selected in the other settings page to suspend scanning."
                    else "The devices below have already been selected in the other settings page to activate scanning."
                )
                SettingsGroup { others.forEach { Device(it, enabled = false) } }
            }
            // Separated from the lists above, so that it doesn't read as one more device
            SettingsGroup(Modifier.padding(top = 24.dp)) {
                TextSettingsItem(
                    headline = "Missing a device?",
                    supporting = PAIRING_HINT,
                    onClick = openBluetoothSettings,
                    modifier = Modifier.testTag(BluetoothDevicesTags.missingDevice),
                )
            }
        }
    }
}

/**
 * The supporting text of a "Bluetooth device connected" item, from the most detailed to the
 * shortest. Only the paired devices are told, the other ones selected are hidden until paired again.
 */
internal fun bluetoothDevicesSummary(paired: List<PairedDevice>?, selected: Set<String>): List<AnnotatedString> =
    when (paired) {
        null -> listOf("Bluetooth is off")
        else -> paired
            .filter { it.address in selected }
            .map { it.name }
            .sortedBy { it.lowercase() }
            .let { names ->
                when (names.size) {
                    0 -> listOf("No device selected")
                    1 -> names
                    else -> listOf(names.joinToString(", "), "${names.size} devices")
                }
            }
    }.map(::AnnotatedString)

@Suppress("ConstPropertyName")
internal object BluetoothDevicesTags {
    const val device = "BluetoothDevicesTags_device"
    const val otherDevices = "BluetoothDevicesTags_otherDevices"
    const val bluetoothOff = "BluetoothDevicesTags_bluetoothOff"
    const val noDevice = "BluetoothDevicesTags_noDevice"
    const val missingDevice = "BluetoothDevicesTags_missingDevice"
}

private val previewDevices = listOf(
    PairedDevice("00:00:00:00:00:01", "ZEEKR-9DFD", isAudio = true),
    PairedDevice("00:00:00:00:00:02", "soundcore Liberty 5", isAudio = true),
    PairedDevice("00:00:00:00:00:03", "Galaxy Buds FE", isAudio = true),
    PairedDevice("00:00:00:00:00:04", "ZeekrVehicle522", isAudio = false),
    PairedDevice("00:00:00:00:00:05", "Galaxy Fit3 (3C9C)", isAudio = false),
    PairedDevice("00:00:00:00:00:06", "Redmi TV Soundbar", isAudio = true),
)

@Composable
private fun BluetoothDevicesPreview(paired: Paired?, expanded: Boolean = false) = BluetoothDevices(
    activate = true,
    paired = paired,
    selected = setOf("00:00:00:00:00:01"),
    selectedAtEntry = setOf("00:00:00:00:00:01"),
    otherSelected = setOf("00:00:00:00:00:06"),
    connected = setOf("00:00:00:00:00:02", "00:00:00:00:00:06"),
    expanded = expanded,
    onToggle = {},
    onExpand = {},
    onTurnOnBluetooth = {},
    openBluetoothSettings = {},
)

@Preview
@Composable
internal fun BluetoothDevicesListPreview() = BluetoothDevicesPreview(Paired(previewDevices))

@Preview
@Composable
internal fun BluetoothDevicesExpandedPreview() = BluetoothDevicesPreview(Paired(previewDevices), expanded = true)

@Preview
@Composable
internal fun BluetoothDevicesOffPreview() = BluetoothDevicesPreview(Paired(null))

@Preview
@Composable
internal fun BluetoothDevicesEmptyPreview() = BluetoothDevicesPreview(Paired(emptyList()))
