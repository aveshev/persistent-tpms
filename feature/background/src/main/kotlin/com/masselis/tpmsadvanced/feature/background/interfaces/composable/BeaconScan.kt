@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.ActionSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.rememberBluetoothState
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Page
import java.util.Locale
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The page opened from the "Add a beacon" item of [Beacons]: the named devices advertising around,
 * to add as a beacon. Searches for a few seconds first, then lists them in an order that only
 * changes when reloading. Scans for as long as the page is seen.
 */
@Composable
public fun BeaconScan(modifier: Modifier = Modifier): Unit =
    BeaconScan(modifier, viewModel { BeaconsViewModel() })

@Composable
internal fun BeaconScan(
    modifier: Modifier = Modifier,
    viewModel: BeaconsViewModel = viewModel { BeaconsViewModel() },
) {
    SettingsOnScreenEffect()
    // Pauses scanning once the page isn't seen any more, the screen being off included
    LifecycleStartEffect(viewModel) {
        viewModel.scanPageVisible.value = true
        onStopOrDispose { viewModel.scanPageVisible.value = false }
    }
    val page by viewModel.scanPage.collectAsState()
    val beacons by viewModel.beacons.collectAsState()
    val bluetoothState = rememberBluetoothState()
    BeaconScan(
        page = page.takeIf { bluetoothState.isEnabled },
        added = beacons.map { it.address }.toSet(),
        onAdd = viewModel::add,
        onRemove = { viewModel.remove(setOf(it)) },
        onReload = viewModel::reload,
        onTurnOnBluetooth = bluetoothState::askEnable,
        modifier = modifier,
    )
}

@Suppress("MaxLineLength", "LongMethod")
@Composable
private fun BeaconScan(
    // Null while Bluetooth is off
    page: Page?,
    added: Set<String>,
    onAdd: (Device) -> Unit,
    onRemove: (String) -> Unit,
    onReload: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        stringResource(CommonR.string.come_close_to_your_vehicle_the_beacons)
    )
    SettingsSectionHeader(stringResource(CommonR.string.scanning))
    SettingsGroup {
        when (page) {
            null -> TextSettingsItem(
                headline = stringResource(CommonR.string.bluetooth_is_off),
                supporting = stringResource(CommonR.string.turn_it_on_to_look_for_beacons),
                onClick = onTurnOnBluetooth,
                modifier = Modifier.testTag(BeaconScanTags.bluetoothOff),
            )

            // The same layout while searching and once listed, so that nothing jumps in between
            else -> ScanningItem(page, onReload)
        }
    }
    if (page !is Page.Listed) return@Column
    SettingsSectionHeader(stringResource(CommonR.string.beacons_found)) {
        // Sorts again by the current signal, the new beacons included
        // Greyed out while already sorted
        IconButton(
            onClick = onReload,
            enabled = page.isSorted.not(),
            // The header's green, plain grey while disabled rather than a faded green
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            ),
            modifier = Modifier.testTag(BeaconScanTags.sort),
        ) {
            Text(
                "\u21C5",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = "Sort by signal" },
            )
        }
    }
    SettingsGroup {
        if (page.devices.isEmpty()) SettingsItem { Text(stringResource(CommonR.string.no_beacon_nearby)) }
        page.devices.forEach { device ->
            val isAdded = device.address in added
            ActionSettingsItem(
                headline = device.name,
                supporting = listOfNotNull(device.address, stringResource(CommonR.string.added).takeIf { isAdded })
                    .joinToString(" · ") + "\n" + listOfNotNull(
                    stringResource(CommonR.string.dbm, device.rssi),
                    device.period?.let { stringResource(CommonR.string.every, it.asSeconds()) },
                    stringResource(CommonR.string.not_heard_lately).takeIf { device.isQuiet },
                    stringResource(CommonR.string.weak_signal).takeIf { device.isQuiet.not() && device.isStrong.not() },
                ).joinToString(" · "),
                // Too weak or gone for now, may come back
                enabled = device.isCandidate,
                modifier = Modifier.testTag("${BeaconScanTags.device}_${device.address}"),
            ) {
                if (isAdded) {
                    IconButton(onClick = { onRemove(device.address) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.close_24px),
                            contentDescription = stringResource(CommonR.string.remove, device.name),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(onClick = { onAdd(device) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.add_24px),
                            contentDescription = stringResource(CommonR.string.add, device.name),
                        )
                    }
                }
            }
        }
    }
}

/**
 * While searching, counts down to the list with the devices found so far. Once listed, tells how
 * many devices became candidates since the list was sorted, to reload it with them.
 */
@Suppress("MagicNumber")
@Composable
private fun ScanningItem(page: Page, onReload: () -> Unit) =
    SettingsItem(Modifier.testTag(BeaconScanTags.scanning)) {
        val notShown = (page as? Page.Listed)?.notShown ?: 0
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // As tall as the reload button, which comes and goes
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (page) {
                            is Page.Searching ->
                                stringResource(CommonR.string.listing_beacons_in, ceil(page.remaining.inWholeMilliseconds / 1000.0).toInt())
                            is Page.Listed -> stringResource(CommonR.string.listening_for_new_beacons)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        when {
                            page is Page.Searching -> stringResource(CommonR.string.found_so_far, page.found)
                            notShown == 0 -> stringResource(CommonR.string.no_new_beacons)
                            notShown == 1 -> stringResource(CommonR.string.n1_additional_beacon_found)
                            else -> stringResource(CommonR.string.additional_beacons_found, notShown)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            page is Page.Searching -> MaterialTheme.colorScheme.onSurfaceVariant
                            notShown > 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                        },
                        modifier = Modifier.testTag(BeaconScanTags.notShown),
                    )
                }
                if (notShown > 0) TextButton(
                    onClick = onReload,
                    modifier = Modifier.testTag(BeaconScanTags.reload),
                ) {
                    Text(stringResource(CommonR.string.reload))
                }
            }
            val progressModifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
            when (page) {
                is Page.Searching -> LinearProgressIndicator(
                    progress = { 1f - (page.remaining / BeaconDiscoveryUseCase.SEARCH).toFloat() },
                    modifier = progressModifier,
                )
                // Listens as long as the page is seen: never done
                is Page.Listed -> LinearProgressIndicator(modifier = progressModifier)
            }
        }
    }

private const val DISABLED_ALPHA = 0.38f

/**
 * "0.3 s", "0.02 s" or "0.005 s": 0.1 s steps unless shorter, so that a fast beacon isn't "0.0 s". Chosen
 * by what each rounds to, so that 99 ms is "0.1 s" rather than "0.10 s".
 */
@Suppress("MagicNumber")
private fun Duration.asSeconds() = String.format(
    Locale.ROOT,
    when {
        this >= 95.milliseconds -> "%.1f s"
        this >= 9500.microseconds -> "%.2f s"
        else -> "%.3f s"
    },
    inWholeMicroseconds / 1_000_000.0,
)

@Suppress("ConstPropertyName")
internal object BeaconScanTags {
    const val device = "BeaconScanTags_device"
    const val bluetoothOff = "BeaconScanTags_bluetoothOff"
    const val scanning = "BeaconScanTags_scanning"
    const val notShown = "BeaconScanTags_notShown"
    const val reload = "BeaconScanTags_reload"
    const val sort = "BeaconScanTags_sort"
}

@Suppress("MagicNumber")
private val previewDevices = listOf(
    Device("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", -62, 303.milliseconds, isStrong = true, isQuiet = false),
    Device("C4:CD:82:63:55:15", "RE6603100142", -78, 104.milliseconds, isStrong = true, isQuiet = false),
    Device("4A:1B:2C:3D:4E:5F", "Galaxy Watch", -91, 52.milliseconds, isStrong = false, isQuiet = false),
    Device("F3:34:67:CA:7B:EB", "CFMOTOR_f33467ca7beb", -84, null, isStrong = true, isQuiet = true),
)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun BeaconScanListPreview() = BeaconScan(
    page = Page.Listed(previewDevices, notShown = 2),
    added = setOf("EE:64:A3:12:38:1A"),
    onAdd = {},
    onRemove = {},
    onReload = {},
    onTurnOnBluetooth = {},
)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun BeaconScanSearchingPreview() = BeaconScan(
    page = Page.Searching(2.seconds, found = 4),
    added = emptySet(),
    onAdd = {},
    onRemove = {},
    onReload = {},
    onTurnOnBluetooth = {},
)
