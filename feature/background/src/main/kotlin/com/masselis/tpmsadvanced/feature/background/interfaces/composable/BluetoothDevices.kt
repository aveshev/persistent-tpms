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
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localized

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
private val PAIRING_HINT = CommonR.string.devices_moved_from_another_phone_appear_here

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
        if (activate) stringResource(CommonR.string.background_scanning_activates_while_any_of_the)
        else stringResource(CommonR.string.background_scanning_is_suspended_while_any_of)
    )
    SettingsSectionHeader(
        if (activate) stringResource(CommonR.string.activate_scanning_when_connected_to) else stringResource(CommonR.string.suspend_scanning_when_connected_to)
    )
    val devices = paired?.devices
    when {
        // Not read yet, a moment only
        paired == null -> return@Column
        devices == null -> SettingsGroup {
            TextSettingsItem(
                headline = stringResource(CommonR.string.bluetooth_is_off),
                supporting = stringResource(CommonR.string.turn_it_on_to_see_your_paired),
                onClick = onTurnOnBluetooth,
                modifier = Modifier.testTag(BluetoothDevicesTags.bluetoothOff),
            )
        }

        devices.isEmpty() -> SettingsGroup {
            TextSettingsItem(
                headline = stringResource(CommonR.string.no_paired_device),
                supporting = stringResource(PAIRING_HINT),
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
                headline = if (device.address in connected) stringResource(CommonR.string.connected, device.name) else device.name,
                checked = device.address in selected || enabled.not(),
                enabled = enabled,
                onCheckedChange = { onToggle(device.address) },
                modifier = Modifier.testTag("${BluetoothDevicesTags.device}_${device.address}"),
            )
            if (selectable.isNotEmpty()) SettingsGroup {
                (first + audio).forEach { Device(it) }
                if (nonAudio.isEmpty()) return@SettingsGroup
                ExpandSettingsItem(
                    headline = stringResource(CommonR.string.other_devices, nonAudio.size),
                    expanded = expanded,
                    onClick = onExpand,
                    modifier = Modifier.testTag(BluetoothDevicesTags.otherDevices),
                )
                if (expanded) nonAudio.forEach { Device(it) }
            }
            if (others.isNotEmpty()) {
                SettingsSectionHeader(
                    if (activate) stringResource(CommonR.string.already_selected_to_suspend_scanning) else stringResource(CommonR.string.already_selected_to_activate_scanning)
                )
                SettingsSectionNote(
                    if (activate) stringResource(CommonR.string.the_devices_below_have_already_been_selected)
                    else stringResource(CommonR.string.the_devices_below_have_already_been_selected_2)
                )
                SettingsGroup { others.forEach { Device(it, enabled = false) } }
            }
            // Separated from the lists above, so that it doesn't read as one more device
            SettingsGroup(Modifier.padding(top = 24.dp)) {
                TextSettingsItem(
                    headline = stringResource(CommonR.string.missing_a_device),
                    supporting = stringResource(PAIRING_HINT),
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
        null -> listOf(localized(CommonR.string.bluetooth_is_off))
        else -> paired
            .filter { it.address in selected }
            .map { it.name }
            .sortedBy { it.lowercase() }
            .let { names ->
                when (names.size) {
                    0 -> listOf(localized(CommonR.string.no_device_selected))
                    1 -> names
                    else -> listOf(names.joinToString(", "), localized(CommonR.string.devices, names.size))
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
