package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.core.ui.R as CoreUiR
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.BellState.NeedsPermission
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import kotlin.math.roundToInt

/**
 * The line of the main screen's menu for persistent scanning, with the top bar's bell in the same
 * colour while it is on. Laid out like the settings items that both open a page and toggle: the
 * text and the chevron run [onClick], which opens the persistent mode settings, the switch toggles.
 * The switch is only offered once those settings turned it on.
 *
 * Must be under the [PersistentScanningHost], which starts the service once the switch turns it on.
 */
@Composable
public fun PersistentScanningMenuItem(onClick: () -> Unit, modifier: Modifier = Modifier): Unit =
    PersistentScanningMenuItem(
        onClick,
        modifier,
        viewModel { Bindings.featureBackgroundInternal.persistentScanningViewModel() },
    )

@Composable
internal fun PersistentScanningMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningViewModel = viewModel {
        Bindings.featureBackgroundInternal.persistentScanningViewModel()
    },
) {
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val setUp by viewModel.persistentScanningSetUp.collectAsState()
    val decision by viewModel.decision.collectAsState(initial = viewModel.currentDecision ?: ScanDecision.Idle)
    val permissionsOk = LocalMonitoringPermissions.current.status == MonitoringPermissions.Status.Ok
    PersistentScanningMenuItem(
        state = BellState.of(permissionsOk, decision).takeIf { persistentScanning },
        setUp = setUp,
        onPersistentScanning = viewModel::setPersistentScanning,
        onClick = onClick,
        modifier = modifier,
    )
}

/** [state] is the bell's, null while persistent scanning is off */
@Composable
private fun PersistentScanningMenuItem(
    state: BellState?,
    setUp: Boolean,
    onPersistentScanning: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = DropdownMenuItem(
    text = { Text("Persistent mode") },
    onClick = onClick,
    leadingIcon = {
        Icon(
            imageVector = ImageVector.vectorResource(
                if (state == null || state == NeedsPermission) R.drawable.notifications_off
                else R.drawable.notifications
            ),
            contentDescription = null,
            // Off, it takes the neutral colour of the other icons
            tint = state?.color ?: LocalContentColor.current,
        )
    },
    trailingIcon = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ImageVector.vectorResource(CoreUiR.drawable.chevron_right_24px),
                contentDescription = "Open the persistent mode settings",
            )
            VerticalDivider(
                Modifier
                    .padding(horizontal = 8.dp)
                    .height(32.dp)
            )
            Switch(
                checked = state != null,
                onCheckedChange = onPersistentScanning,
                enabled = setUp,
                // Smaller than in the settings, laid out at its scaled size, so "Persistent mode"
                // fits on one line in a menu at most 280 dp wide
                modifier = Modifier.layout { measurable, constraints ->
                    measurable.measure(constraints).let { placeable ->
                        layout(
                            (placeable.width * SWITCH_SCALE).roundToInt(),
                            (placeable.height * SWITCH_SCALE).roundToInt(),
                        ) {
                            placeable.placeWithLayer(0, 0) {
                                scaleX = SWITCH_SCALE
                                scaleY = SWITCH_SCALE
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }
                },
            )
        }
    },
    modifier = modifier,
)

private const val SWITCH_SCALE = .75f

@Preview
@Composable
internal fun PersistentScanningMenuItemPreview() {
    Column {
        PersistentScanningMenuItem(state = null, setUp = false, onPersistentScanning = {}, onClick = {})
        PersistentScanningMenuItem(state = null, setUp = true, onPersistentScanning = {}, onClick = {})
        BellState.entries.forEach {
            PersistentScanningMenuItem(state = it, setUp = true, onPersistentScanning = {}, onClick = {})
        }
    }
}
