package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.KeepAliveInstructions
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.rationale

/** The page opened from the "Persistent scanning" item of [PersistentScanningSettings] */
@Composable
public fun PersistentScanningDetails(modifier: Modifier = Modifier): Unit =
    PersistentScanningDetails(modifier, viewModel { PersistentScanningSettingsViewModel() })

@Composable
internal fun PersistentScanningDetails(
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
    val decision = viewModel.persistentDecision()
    var showKeepAliveInstructions by rememberSaveable { mutableStateOf(false) }
    if (showKeepAliveInstructions) KeepAliveInstructions(onDismissRequest = { showKeepAliveInstructions = false })
    PersistentScanningDetails(
        status = viewModel.status(decision),
        decision = decision ?: ScanDecision.Idle,
        persistentScanning = persistentScanning,
        onPersistentScanning = { enabled ->
            if (enabled) permissions.request() else viewModel.disablePersistentScanning()
        },
        onKeepAliveInstructions = { showKeepAliveInstructions = true },
        modifier = modifier,
    )
}

@Composable
private fun PersistentScanningDetails(
    status: BellState?,
    decision: ScanDecision,
    persistentScanning: Boolean,
    onPersistentScanning: (Boolean) -> Unit,
    onKeepAliveInstructions: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Persistent scanning monitors your tyres in the background, even after the phone restarts, without having to start it by hand. To save battery it only scans when it is useful: whenever any of the activate scan conditions is true, such as the phone charging in its mount or Android Auto being connected. The suspend scan conditions, such as being connected to your home (or any other) WiFi, override activate conditions and suspend scanning whenever any of them is true. Scanning while the app is open is never affected."
    )
    SettingsGroup(Modifier.padding(top = 16.dp)) {
        TextSettingsItem(
            headline = "Keep monitoring running on this phone",
            supporting = "Some phones stop background apps on their own, see which settings prevent it",
            onClick = onKeepAliveInstructions,
        )
    }
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Persistent scanning",
            checked = persistentScanning,
            onCheckedChange = onPersistentScanning,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.persistentScanningDetails),
        )
    }
    Text(
        text = status.statusText("Current status: "),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .testTag(PersistentScanningSettingsTags.status),
    )
    when (status) {
        // The manual mode, which the app never explained anywhere else
        null -> "Tyres can still be monitored without it: the app scans whenever it is open, and " +
            "tapping the minimize button at the top of the main screen keeps scanning in the " +
            "background, whatever the conditions, until stopped from its notification or the phone restarts"

        BellState.NeedsPermission ->
            "Notifications or the unrestricted battery usage are missing, tap the bell on the main " +
                "screen to grant them"

        BellState.Active, BellState.Suspended, BellState.Idle -> decision.rationale()
    }.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
    }
}

@Preview
@Composable
internal fun PersistentScanningDetailsPreview() {
    PersistentScanningDetails(
        status = BellState.Suspended,
        decision = ScanDecision.Suspended(setOf(ScanSuspensionUseCase.Reason.WIFI)),
        persistentScanning = true,
        onPersistentScanning = {},
        onKeepAliveInstructions = {},
    )
}
