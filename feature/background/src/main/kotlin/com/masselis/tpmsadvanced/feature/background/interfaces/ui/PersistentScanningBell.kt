package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.Blue
import com.masselis.tpmsadvanced.core.ui.Orange
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.rationale

@Composable
internal fun PersistentScanningBell(
    viewModel: PersistentScanningViewModel,
    modifier: Modifier = Modifier,
) {
    val decision by viewModel.decision.collectAsState(initial = viewModel.currentDecision ?: ScanDecision.Idle)
    val permissions = LocalMonitoringPermissions.current
    var showRationale by remember { mutableStateOf(false) }
    val state = BellState.of(permissions.status == MonitoringPermissions.Status.Ok, decision)

    PersistentScanningBell(
        state = state,
        onClick = {
            if (state == BellState.NeedsPermission) permissions.request()
            else showRationale = true
        },
        modifier = modifier,
    )
    if (showRationale) {
        AlertDialog(
            text = { Text(text = decision.rationale()) },
            onDismissRequest = { showRationale = false },
            confirmButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(text = "OK")
                }
            }
        )
    }
}

@Composable
private fun PersistentScanningBell(
    state: BellState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.testTag("persistent_scanning_bell")) {
        Icon(
            imageVector = ImageVector.vectorResource(
                if (state == BellState.NeedsPermission) R.drawable.notifications_off
                else R.drawable.notifications
            ),
            contentDescription = when (state) {
                BellState.NeedsPermission -> "Persistent scanning needs permissions, tap to grant them"
                BellState.Active -> "Background scanning is active"
                BellState.Always -> "Background scanning is always active"
                BellState.Suspended -> "Background scanning is suspended"
                BellState.Idle -> "Background scanning is idle"
            },
            tint = state.color,
        )
    }
}

/** Also colours the status shown in the settings, so that it reads like the bell */
internal val BellState.color: Color
    @Composable
    get() = when (this) {
        BellState.NeedsPermission -> MaterialTheme.colorScheme.error
        BellState.Active -> MaterialTheme.colorScheme.primary
        BellState.Always -> Blue
        BellState.Suspended -> Orange
        // The theme's content colour, a plain white would vanish on the light theme
        BellState.Idle -> LocalContentColor.current
    }

@Preview
@Composable
internal fun PersistentScanningBellPreview() {
    Row {
        BellState.entries.forEach { PersistentScanningBell(state = it, onClick = {}) }
    }
}
