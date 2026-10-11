package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

/** How far a location's outline reaches past its tyre and readout */
internal val OUTLINE_OUTSET = 6.dp
private val SHAPE = RoundedCornerShape(12.dp)

/** A location a moving sensor went through, greyed out */
private const val PASSED_ALPHA = .38f

/**
 * Covers a location's tyre and readout. Tapping it assigns a sensor while there's none, and
 * manages the assigned one while [isManaging]. It's outlined whenever it can be tapped. While a
 * sensor is moving ([move]), tapping it sends that sensor here: the location the sensor waits at
 * blinks, those it went through are greyed out.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun TyreTapArea(
    location: Location,
    isManaging: Boolean,
    scanQrCode: (Location) -> Unit,
    scanBluetooth: (Location) -> Unit,
    move: TyreMove?,
    startMove: (Location) -> Unit,
    canMove: Boolean,
    modifier: Modifier = Modifier,
    calibrate: (Int) -> Unit = {},
    onOutlinePositioned: ((Location, Rect) -> Unit)? = null,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    // A dialog to assign a sensor, or to manage the assigned one
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val isUnassigned = state is State.Unassigned
    val isWaiting = move?.chain?.last == location
    // Gone through by the chain, unless it can close on it
    val isInChain = move?.let { location in it.chain.locations && it.canTap(location).not() } == true
    val onTap: (() -> Unit)? = when {
        move != null -> if (move.canTap(location)) ({ move.onTap(location) }) else null
        state is State.Unassigned || (state is State.Assigned && isManaging) -> ({ isOpen = true })
        else -> null
    }
    val outline = when {
        isWaiting -> MaterialTheme.colorScheme.primary.takeIf { isFirstBlinkPhase(BLINK) } ?: Color.Transparent
        isInChain -> MaterialTheme.colorScheme.onSurface.copy(alpha = PASSED_ALPHA)
        isManaging || isUnassigned -> MaterialTheme.colorScheme.primary
        else -> null
    }
    Box(
        modifier
            .outset(OUTLINE_OUTSET)
            .run {
                onOutlinePositioned
                    ?.let { report -> onGloballyPositioned { report(location, it.boundsInWindow()) } }
                    ?: this
            }
            .run { outline?.let { border(2.dp, it, SHAPE) } ?: this }
            .clip(SHAPE)
            .run { onTap?.let { clickable(onClick = it) } ?: this }
            .testTag(TyreTapAreaTags.root(location))
    )
    (state as? State.Assigned)
        ?.takeIf { isOpen }
        ?.also { assigned ->
            ManageSensorDialog(
                location = location,
                sensor = assigned.sensor,
                move = { isOpen = false; startMove(location) },
                canMove = canMove,
                calibrate = { isOpen = false; calibrate(assigned.sensor.id) },
                delete = { isOpen = false; confirmDelete = true },
                onDismissRequest = { isOpen = false },
            )
        }
    (state as? State.Unassigned)
        ?.takeIf { isOpen }
        ?.also { unassigned ->
            AssignSensorDialog(
                location = location,
                detected = unassigned.detected,
                scanQrCode = { isOpen = false; scanQrCode(location) },
                scanBluetooth = { isOpen = false; scanBluetooth(location) },
                assignDetected = { viewModel.assign(it); isOpen = false },
                onDismissRequest = { isOpen = false },
            )
        }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = {
            Text(
                buildString {
                    append("Remove the sensor from the ")
                    appendLoc(location)
                    append("?\nThis cannot be undone.")
                }
            )
        },
        dismissButton = {
            TextButton(
                onClick = { confirmDelete = false },
                modifier = Modifier.testTag(TyreTapAreaTags.cancelDelete),
            ) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.delete(); confirmDelete = false },
                modifier = Modifier.testTag(TyreTapAreaTags.confirmDelete),
            ) { Text("Remove") }
        },
        modifier = Modifier.testTag(TyreTapAreaTags.deleteDialog),
    )
}

/** Grows by [outset] on every side, around what it's laid out on */
private fun Modifier.outset(outset: Dp) = layout { measurable, constraints ->
    val extra = outset.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
            minHeight = constraints.minHeight + extra,
            maxHeight = constraints.maxHeight + extra,
        )
    )
    layout(placeable.width - extra, placeable.height - extra) {
        placeable.place(-extra / 2, -extra / 2)
    }
}

/** What can be done with the [sensor] assigned to [location], under its position and id */
@Composable
private fun ManageSensorDialog(
    location: Location,
    sensor: Sensor,
    move: () -> Unit,
    canMove: Boolean,
    calibrate: () -> Unit,
    delete: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionsDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(buildString { appendLoc(location, withType = false, capitalized = true) })
                Text(
                    "Sensor ID ${sensor.id.asSensorId()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.swap_horizontal_24px), null) },
                    title = "Move/swap",
                    subtitle = "Assign this sensor to another wheel",
                    onClick = move,
                    isEnabled = canMove,
                    modifier = Modifier.testTag(TyreTapAreaTags.move),
                )
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.tune_24px), null) },
                    title = "Calibrate",
                    subtitle = "Correct this sensor's pressure readings",
                    onClick = calibrate,
                    modifier = Modifier.testTag(TyreTapAreaTags.calibrate),
                )
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.delete_24px), null) },
                    title = "Delete",
                    subtitle = buildString {
                        append("Remove this sensor from the ")
                        appendLoc(location)
                    },
                    onClick = delete,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TyreTapAreaTags.delete),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag(TyreTapAreaTags.manageCancel),
            ) { Text("Cancel") }
        },
        modifier = modifier.testTag(TyreTapAreaTags.manageDialog),
    )
}

@Preview
@Composable
internal fun ManageSensorDialogPreview() {
    ManageSensorDialog(
        Location.Wheel(FRONT_RIGHT),
        Sensor(0x0A0B0C, Location.Wheel(FRONT_RIGHT), PECHAM),
        move = {},
        canMove = true,
        calibrate = {},
        delete = {},
        onDismissRequest = {},
    )
}

@Suppress("ConstPropertyName")
internal object TyreTapAreaTags {
    fun root(location: Location) = "TyreTapAreaTags_root_$location"
    const val manageDialog = "TyreTapAreaTags_manageDialog"
    const val move = "TyreTapAreaTags_move"
    const val calibrate = "TyreTapAreaTags_calibrate"
    const val delete = "TyreTapAreaTags_delete"
    const val manageCancel = "TyreTapAreaTags_manageCancel"
    const val deleteDialog = "TyreTapAreaTags_deleteDialog"
    const val confirmDelete = "TyreTapAreaTags_confirmDelete"
    const val cancelDelete = "TyreTapAreaTags_cancelDelete"
}
