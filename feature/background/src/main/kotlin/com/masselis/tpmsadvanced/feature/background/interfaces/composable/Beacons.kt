package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.ActionSettingsItem
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.rememberBluetoothState
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.BeaconsViewModel

/**
 * The page opened from the "Bluetooth beacon nearby" item of [ActivateScanConditions], new beacons
 * being picked on another page, opened through [openBeaconScan]. Like [ExceptedWifis], a removed
 * beacon stays struck through with an undo button until the page is left.
 */
@Composable
public fun Beacons(openBeaconScan: () -> Unit, modifier: Modifier = Modifier): Unit =
    Beacons(openBeaconScan, modifier, viewModel { BeaconsViewModel() })

@Composable
internal fun Beacons(
    openBeaconScan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BeaconsViewModel = viewModel { BeaconsViewModel() },
    tier: AppTier = AppTier.current,
) {
    SettingsOnScreenEffect()
    val beacons by viewModel.beacons.collectAsState()
    val overridesSuspend by viewModel.beaconOverridesSuspend.collectAsState()
    // Scans only while the page is seen, not while the scan page is opened above it
    val nearby by viewModel.nearby.collectAsStateWithLifecycle(initialValue = emptyMap())
    val wasNearby by viewModel.wasNearby.collectAsStateWithLifecycle(initialValue = emptySet())
    // Saved, so that a rotation keeps what was removed so far
    var removed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    // Opening the scan page above this one leaves the composition too, while the user is about to
    // add a beacon: the condition must not be turned off then. Not saved, back from the scan page
    // this page is composed afresh.
    var scanOpened by remember { mutableStateOf(false) }
    OnLeaveEffect {
        viewModel.remove(removed.toSet())
        if (scanOpened.not()) viewModel.disableIfNoBeacon()
    }
    val bluetoothState = rememberBluetoothState()
    Beacons(
        beacons = beacons,
        removed = removed.toSet(),
        nearby = nearby.takeIf { bluetoothState.isEnabled },
        wasNearby = wasNearby,
        // Lite only suspends while the phone is idle, which a beacon never overrides
        overridesSuspend = overridesSuspend.takeIf { tier != LITE },
        onOverridesSuspend = { viewModel.beaconOverridesSuspend.value = it },
        onRename = { renaming = it },
        onRemove = { removed = removed + it },
        onUndo = { removed = removed - it },
        onTurnOnBluetooth = bluetoothState::askEnable,
        // Removed for good first, so that the page is drawn afresh when back: the scan page may add
        // one of them again meanwhile
        openBeaconScan = {
            viewModel.remove(removed.toSet())
            removed = emptyList()
            scanOpened = true
            openBeaconScan()
        },
        modifier = modifier,
    )
    beacons
        .firstOrNull { it.address == renaming }
        ?.also { beacon ->
            RenameDialog(
                beacon = beacon,
                onRename = { viewModel.rename(beacon.address, it) },
                onDismiss = { renaming = null },
            )
        }
}

@Suppress("LongParameterList", "LongMethod", "MaxLineLength")
@Composable
private fun Beacons(
    beacons: List<Beacon>,
    removed: Set<String>,
    // Null while Bluetooth is off: unknown then
    nearby: Map<String, Int>?,
    // Still counted as nearby by the background scan, not heard by this page's
    wasNearby: Set<String>,
    // Null without the option, in Lite
    overridesSuspend: Boolean?,
    onOverridesSuspend: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    onTurnOnBluetooth: () -> Unit,
    openBeaconScan: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Some newer cars and motorcycles feature always-on or on-while-riding BLE beacons that can be used to activate scanning. These likely won't show up on your phone's Bluetooth pairing screen, but you can add them here.\n\nNOTE: AirTags and similar personal trackers use proprietary protocols, hide their names and randomize their addresses, so they aren't supported."
    )
    SettingsSectionHeader("Activate scanning when nearby")
    SettingsGroup {
        if (nearby == null) TextSettingsItem(
            headline = "Bluetooth is off",
            supporting = "Turn it on to see which beacon is nearby",
            onClick = onTurnOnBluetooth,
            modifier = Modifier.testTag(BeaconsTags.bluetoothOff),
        )
        if (beacons.isEmpty()) SettingsItem { Text("No beacon added yet") }
        // The last added first
        beacons.asReversed().forEach { beacon ->
            val isRemoved = beacon.address in removed
            ActionSettingsItem(
                headline = beacon.displayName,
                supporting = listOfNotNull(
                    // Already the headline otherwise
                    beacon.address.takeIf { beacon.displayName != it }?.let(::AnnotatedString),
                    nearby?.let { nearby ->
                        // Nearby: its signal, in the green of what's going well
                        (nearby[beacon.address]?.let { "$it dBm" } ?: "Was nearby".takeIf { beacon.address in wasNearby })
                            ?.let { text ->
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(text) }
                                }
                            }
                            ?: AnnotatedString("Not nearby")
                    },
                )
                    .takeIf { it.isNotEmpty() }
                    ?.reduce { line, part -> line + AnnotatedString(" · ") + part },
                struckOut = isRemoved,
                modifier = Modifier.testTag("${BeaconsTags.beacon}_${beacon.address}"),
            ) {
                if (isRemoved) {
                    TextButton(onClick = { onUndo(beacon.address) }) { Text("Undo") }
                } else {
                    Row {
                        IconButton(onClick = { onRename(beacon.address) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.edit_24px),
                                contentDescription = "Rename ${beacon.displayName}",
                            )
                        }
                        IconButton(onClick = { onRemove(beacon.address) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                                contentDescription = "Remove ${beacon.displayName}",
                            )
                        }
                    }
                }
            }
        }
    }
    // Separated from the list above, so that it doesn't read as one more beacon
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        TextSettingsItem(
            headline = "Add a beacon",
            supporting = "Lists the Bluetooth devices around",
            onClick = openBeaconScan,
            modifier = Modifier.testTag(BeaconsTags.addBeacon),
        )
    }
    if (overridesSuspend == null) return@Column
    SettingsSectionHeader("Options")
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Override suspend conditions",
            supporting = "(except phone idle)",
            checked = overridesSuspend,
            onCheckedChange = onOverridesSuspend,
            modifier = Modifier.testTag(BeaconsTags.overridesSuspend),
        )
    }
}

/** Renames [beacon], a blank name bringing the advertised one back */
@Composable
private fun RenameDialog(beacon: Beacon, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(beacon.label ?: beacon.advertisedName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename beacon") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text(beacon.advertisedName ?: beacon.address) },
                modifier = Modifier.testTag(BeaconsTags.renameField),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The supporting text of a "Bluetooth beacon nearby" item, from the most detailed to the shortest
 */
internal fun beaconsSummary(beacons: List<Beacon>): List<AnnotatedString> = beacons
    .map { it.displayName }
    .let { names ->
        when (names.size) {
            0 -> listOf("No beacon added")
            1 -> names
            else -> listOf(names.joinToString(", "), "${names.size} beacons")
        }
    }
    .map(::AnnotatedString)

@Suppress("ConstPropertyName")
internal object BeaconsTags {
    const val beacon = "BeaconsTags_beacon"
    const val bluetoothOff = "BeaconsTags_bluetoothOff"
    const val addBeacon = "BeaconsTags_addBeacon"
    const val overridesSuspend = "BeaconsTags_overridesSuspend"
    const val renameField = "BeaconsTags_renameField"
}

private val previewBeacons = listOf(
    Beacon("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", label = "Bike"),
    Beacon("F3:34:67:CA:7B:EB", "CFMOTOR_f33467ca7beb", label = null),
    Beacon("C4:CD:82:63:55:15", advertisedName = null, label = null),
)

@Composable
private fun BeaconsPreview(beacons: List<Beacon>, nearby: Map<String, Int>?) = Beacons(
    beacons = beacons,
    removed = setOf("C4:CD:82:63:55:15"),
    nearby = nearby,
    wasNearby = setOf("F3:34:67:CA:7B:EB"),
    overridesSuspend = true,
    onOverridesSuspend = {},
    onRename = {},
    onRemove = {},
    onUndo = {},
    onTurnOnBluetooth = {},
    openBeaconScan = {},
)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun BeaconsListPreview() = BeaconsPreview(previewBeacons, mapOf("EE:64:A3:12:38:1A" to -70))

@Preview
@Composable
internal fun BeaconsEmptyPreview() = BeaconsPreview(emptyList(), emptyMap())

@Preview
@Composable
internal fun BeaconsBluetoothOffPreview() = BeaconsPreview(previewBeacons, null)
