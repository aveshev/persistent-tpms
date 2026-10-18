package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.LocalMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.MonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.color
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision

/**
 * The decision of persistent scanning, null while it is off or while the decision for having just
 * turned it on isn't known yet. Never the one of the other mode, that the screen would otherwise
 * show for a moment after the switch was flipped.
 */
@Composable
internal fun PersistentScanningSettingsViewModel.persistentDecision(): ScanDecision? {
    val persistent by persistentScanning.collectAsState()
    // Tagged with the mode it was collected for: right after the switch, the state still holds the
    // previous mode's value until the collection restarts
    val collected by produceState(persistent to currentDecision, persistent) {
        value = persistent to currentDecision
        decision.collect { value = persistent to it }
    }
    return collected
        .takeIf { (mode) -> persistent && mode == persistent }
        ?.second
}

/** What the bell of the main screen shows, null while persistent scanning is off */
@Composable
internal fun PersistentScanningSettingsViewModel.status(decision: ScanDecision? = persistentDecision()): BellState? {
    val permissions = LocalMonitoringPermissions.current
    return decision?.let { BellState.of(permissions.status == MonitoringPermissions.Status.Ok, it) }
}

/** "[prefix]<status>", the status being coloured like the bell */
@Composable
internal fun BellState?.statusText(prefix: String): AnnotatedString = buildAnnotatedString {
    append(prefix)
    val color = this@statusText?.color ?: LocalContentColor.current
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
        append(this@statusText.label)
    }
}

internal val BellState?.label: String
    get() = when (this) {
        null -> "Off"
        BellState.NeedsPermission -> "Permissions missing"
        BellState.Active -> "Active"
        BellState.Always -> "Always"
        BellState.Suspended -> "Suspended"
        BellState.Idle -> "Idle"
    }
