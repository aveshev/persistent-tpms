package com.masselis.tpmsadvanced.feature.unlocated.interfaces.ui

import com.masselis.tpmsadvanced.core.ui.appendBold
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.asSensorId
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import com.masselis.tpmsadvanced.feature.main.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Preconditions
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Dialog
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Event
import com.masselis.tpmsadvanced.feature.unlocated.ioc.Bindings.Companion.BluetoothAssignViewModel
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep
import java.util.UUID

/**
 * Assigns [location] a sensor found by Bluetooth: the user puts it on the wheel, takes it off and
 * puts it back, see [AssignStep]. [onLeave] once assigned.
 */
@Suppress("LongMethod")
@Composable
public fun BluetoothAssign(
    vehicleUuid: UUID,
    location: Location,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BluetoothAssignViewModel = viewModel { BluetoothAssignViewModel(vehicleUuid, location) }
    val state by viewModel.stateFlow.collectAsState()
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) when (event) {
            Event.Leave -> onLeave()
        }
    }
    // Bluetooth on and its permissions granted, like the main screen
    // Its modifier only goes to what it shows in place of the steps
    Preconditions(modifier) { Steps(location, state, viewModel, modifier) }
    Dialogs(state.vehicle, state.dialog, viewModel)
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun Steps(
    location: Location,
    state: BluetoothAssignViewModel.State,
    viewModel: BluetoothAssignViewModel,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    // The step's instruction in the middle, its actions anchored at the bottom
    Column(modifier = modifier.fillMaxSize().testTag(BluetoothAssignTags.root)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.bluetooth_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            // Once assigned, the page tells it in full
            if (state.isAssigned.not()) Text(
                text = buildAnnotatedString {
                    append("Assigning to ")
                    appendWheelOn(location, state.vehicle)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val dialog = state.dialog
            if (state.isAssigned) Instruction(
                buildAnnotatedString {
                    (step as? AssignStep.Found)?.also { append("Sensor ${it.sensorId.asSensorId()} assigned to ") }
                    appendWheelOn(location, state.vehicle)
                }
            )
            else if (dialog == Dialog.OneFound) Instruction(
                "One sensor found",
                buildString {
                    append("Assign it to the ")
                    appendLoc(location)
                    append(", or check again to make sure?")
                },
            )
            else if (dialog is Dialog.ManyFound) Instruction(
                "${dialog.count} on-wheel sensors found",
                "Let's figure out which one to assign",
            )
            else when (step) {
                AssignStep.Ask -> Instruction("Is the sensor you want to assign on the wheel right now?")

                is AssignStep.PutOn -> {
                    Instruction("Put the sensor on the wheel", FORCES_UPDATE)
                    Listening("Listening for on-wheel sensors…", step.found.size.sensors("on-wheel"))
                }

                is AssignStep.TakeOff -> {
                    Instruction("Remove the sensor from the wheel", FORCES_UPDATE)
                    Listening("Listening for off-wheel sensors…", step.off.size.sensors("off-wheel"))
                }

                is AssignStep.PutBack -> {
                    Instruction("Put the sensor back on the wheel", FORCES_UPDATE)
                    Listening("Listening for the sensor to come back on…", null)
                }

                is AssignStep.Found -> Instruction("Sensor found")
            }
        }
        HorizontalDivider()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val dialog = state.dialog
            if (state.isAssigned) Button(
                onClick = viewModel::done,
                modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.done),
            ) { Text("Done") }
            else if (dialog == Dialog.OneFound) {
                OutlinedButton(
                    onClick = viewModel::checkAgain,
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.checkAgain),
                ) { Text("Check again") }
                Button(
                    onClick = viewModel::assignFound,
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.assign),
                ) { Text("Assign") }
            }
            else if (dialog is Dialog.ManyFound) Button(
                onClick = viewModel::checkAgain,
                modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.checkAgain),
            ) { Text("Continue") }
            else when (step) {
                AssignStep.Ask -> {
                    OutlinedButton(
                        onClick = { viewModel.answer(isOnWheel = false) },
                        modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.inHand),
                    ) { Text("No, not yet") }
                    Button(
                        onClick = { viewModel.answer(isOnWheel = true) },
                        modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.onWheel),
                    ) { Text("Yes, it's on the wheel") }
                }

                is AssignStep.PutOn -> {
                    // Below 10 kPa, a sensor isn't told on a wheel from in the hand
                    if (state.offersLowPressure && step.found.isEmpty() && step.low.isNotEmpty()) TextButton(
                        onClick = viewModel::lowPressure,
                        modifier = Modifier.testTag(BluetoothAssignTags.lowPressure),
                    ) { Text("Low pressure tyre?") }
                    Continue(enabled = step.found.isNotEmpty(), onClick = viewModel::next)
                }

                is AssignStep.TakeOff -> Continue(enabled = step.off.isNotEmpty(), onClick = viewModel::next)

                // Assigned by itself once back on the wheel
                is AssignStep.PutBack, is AssignStep.Found -> Continue(enabled = false, onClick = {})
            }
        }
    }
}

@Composable
private fun Continue(enabled: Boolean, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.next),
) { Text("Continue") }

private const val FORCES_UPDATE = "This will force it to send an update"

@Suppress("LongMethod")
@Composable
private fun Dialogs(vehicle: Vehicle?, dialog: Dialog?, viewModel: BluetoothAssignViewModel) {
    when (dialog) {
        null -> {}

        // Asked on the page, see Steps
        Dialog.OneFound, is Dialog.ManyFound -> {}

        is Dialog.BoundElsewhere -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Already assigned") },
            text = {
                Text(buildAnnotatedString {
                    append("Warning: this sensor is already assigned to ")
                    appendBold(dialog.vehicle.name)
                    append(".\n\nReassign it to ")
                    appendBold(vehicle?.name.orEmpty())
                    append("?")
                })
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmBoundElsewhere,
                    modifier = Modifier.testTag(BluetoothAssignTags.assign),
                ) { Text("Reassign") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Instruction(text: String, detail: String? = null) = Instruction(AnnotatedString(text), detail)

@Composable
private fun Instruction(text: AnnotatedString, detail: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        detail?.also {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * "the **front left** wheel on **My car**", the location and the vehicle in bold. A monowheel's only
 * wheel is just "the wheel".
 */
private fun AnnotatedString.Builder.appendWheelOn(location: Location, vehicle: Vehicle?) {
    append("the ")
    if (location == Location.Single) append("wheel")
    else {
        appendBold(buildString { appendLoc(location, withType = false) })
        append(" wheel")
    }
    vehicle?.also {
        append(" on ")
        appendBold(it.name)
    }
}

/** A pulsing bar while listening, [found] under it once there's any */
@Composable
private fun Listening(text: String, found: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Its line kept while nothing is found yet, so the page doesn't move when something is
        Text(found.orEmpty(), style = MaterialTheme.typography.titleMedium)
    }
}

/** "1 on-wheel sensor found", "2 on-wheel sensors found", nothing until one is */
private fun Int.sensors(kind: String): String? =
    takeIf { it > 0 }?.let { "$it $kind sensor${if (it > 1) "s" else ""} found" }

@Suppress("ConstPropertyName")
public object BluetoothAssignTags {
    public const val root: String = "BluetoothAssignTags_root"
    public const val onWheel: String = "BluetoothAssignTags_onWheel"
    public const val inHand: String = "BluetoothAssignTags_inHand"
    public const val next: String = "BluetoothAssignTags_next"
    public const val lowPressure: String = "BluetoothAssignTags_lowPressure"
    public const val assign: String = "BluetoothAssignTags_assign"
    public const val checkAgain: String = "BluetoothAssignTags_checkAgain"
    public const val done: String = "BluetoothAssignTags_done"
}
