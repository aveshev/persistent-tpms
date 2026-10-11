package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR_WITH_SPARE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.SINGLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.AddVehicle.addButton
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.AddVehicle.cancelButton
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.AddVehicle.kindRadio
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.AddVehicle.textField
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.currentVehicleTest
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.dropdownEntry
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdownTags.dropdownEntryAddVehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.CurrentVehicleDropdownViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.CurrentVehicleDropdownViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * The vehicle picker, [onVehicleAdded] being called with the uuid of a vehicle once added. The
 * vehicles' red or crimson [alerts], by their uuid, blink: the other vehicles' behind the arrow,
 * every vehicle's in the list.
 */
@Composable
public fun CurrentVehicleDropdown(
    modifier: Modifier = Modifier,
    alerts: Map<UUID, AlertLevel> = emptyMap(),
    onVehicleAdded: (UUID) -> Unit = {},
) {
    val component = LocalVehicleComponent.current
    CurrentVehicleDropdown(
        // Collected anew for another vehicle, rather than keeping the previous one's name a few frames
        shown = key(component.vehicle.uuid) { component.vehicleStateFlow.collectAsState().value },
        modifier = modifier,
        alerts = alerts,
        onVehicleAdded = onVehicleAdded,
        viewModel = viewModel { CurrentVehicleDropdownViewModel(createSavedStateHandle()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentVehicleDropdown(
    /**
     * The vehicle the screen shows, named from the start rather than once the list is loaded, and
     * until the screen is gone rather than once another one is picked: nothing moves around it
     */
    shown: Vehicle,
    modifier: Modifier = Modifier,
    alerts: Map<UUID, AlertLevel> = emptyMap(),
    onVehicleAdded: (UUID) -> Unit = {},
    viewModel: CurrentVehicleDropdownViewModel = viewModel {
        CurrentVehicleDropdownViewModel(createSavedStateHandle())
    }
) {
    // Before the early return below, the events must be collected whatever the state
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                is Event.VehicleAdded -> onVehicleAdded(event.uuid)
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.scanVehicles() }
    val vehicles = viewModel.stateFlow.collectAsState().value as? State.Vehicles
    var expanded by remember { mutableStateOf(false) }
    var askNewVehicle by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        ) {
            // On one line, giving way to the arrow when the room's short
            Text(
                text = shown.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag(currentVehicleTest),
            )
            // Another vehicle alerting is told before the list is opened, by a red disc behind the
            // arrow, the list's entries telling which once open. Drawn behind the icon, nothing
            // moves while it blinks; the padding keeps it off the name.
            val isAlertOn = expanded.not() &&
                isAlertBlinkOn(alerts.filterKeys { it != shown.uuid }.values.maxOrNull())
            val discColor = MaterialTheme.colorScheme.error
            val arrowColor = if (isAlertOn) MaterialTheme.colorScheme.onError else LocalContentColor.current
            CompositionLocalProvider(LocalContentColor provides arrowColor) {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .drawBehind { if (isAlertOn) drawCircle(discColor, radius = ALERT_DISC_RADIUS.toPx()) }
                )
            }
        }
        // Once the list is loaded
        vehicles?.also {
            VehicleListDropdownMenu(
                isExpanded = expanded,
                vehicleList = it.list.toImmutableList(),
                alerts = alerts.toImmutableMap(),
                onDismissRequest = { expanded = false },
                onNewCurrent = { viewModel.setCurrent(it) },
                onAskNewVehicle = { askNewVehicle = true }
            )
        }
    }
    if (askNewVehicle)
        AddVehicle(
            onDismissRequest = { askNewVehicle = false },
            onVehicleAdd = { name, kind -> viewModel.insert(name, kind); askNewVehicle = false }
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownMenuBoxScope.VehicleListDropdownMenu(
    isExpanded: Boolean,
    vehicleList: ImmutableList<Vehicle>,
    alerts: ImmutableMap<UUID, AlertLevel>,
    onDismissRequest: () -> Unit,
    onNewCurrent: (Vehicle) -> Unit,
    onAskNewVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.exposedDropdownSize(false)
    ) {
        vehicleList.forEach { vehicle ->
            DropdownMenuItem(
                text = {
                    Text(
                        vehicle.name,
                        Modifier.weight(1f),
                        color = if (isAlertBlinkOn(alerts[vehicle.uuid])) MaterialTheme.colorScheme.error
                        else Color.Unspecified,
                    )
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                onClick = {
                    onNewCurrent(vehicle)
                    onDismissRequest()
                },
                modifier = Modifier.testTag(dropdownEntry(vehicle.name))
            )
        }
        DropdownMenuItem(
            text = { Text(text = "Add a vehicle", Modifier.weight(1f)) },
            trailingIcon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.add_circle_24px),
                    contentDescription = ""
                )
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            onClick = { onAskNewVehicle(); onDismissRequest() },
            modifier = Modifier.testTag(dropdownEntryAddVehicle)
        )
    }
}

/**
 * Whether a red or crimson [level] is in its red phase, alternating in sync with the tyres and as
 * fast as they blink for it. Never for another level.
 */
@Composable
private fun isAlertBlinkOn(level: AlertLevel?): Boolean = when (level) {
    RED -> BLINK
    CRIMSON -> CRITICAL_BLINK
    else -> null
}
    ?.let { isFirstBlinkPhase(it) }
    ?: false

private val ALERT_DISC_RADIUS = 10.dp

@Suppress("LongMethod")
@Composable
private fun AddVehicle(
    onDismissRequest: () -> Unit,
    onVehicleAdd: (name: String, kind: Vehicle.Kind) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var vehicleName by remember { mutableStateOf("") }
    var currentKind by remember { mutableStateOf<Vehicle.Kind?>(null) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(CurrentVehicleDropdownTags.AddVehicle.root),
        title = { Text(text = "Add a new vehicle") },
        text = {
            Column {
                OutlinedTextField(
                    label = { Text(text = "Vehicle name") },
                    value = vehicleName,
                    onValueChange = { vehicleName = it },
                    singleLine = true,
                    modifier = Modifier
                        .testTag(textField)
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // In landscape only a few kinds fit, they scroll under the name. The name stays out
                // of it: shorter than the name with the keyboard up, the scroll would jump to it at
                // each letter typed.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    listOf(
                        CAR,
                        CAR_WITH_SPARE,
                        MOTORCYCLE,
                        TADPOLE_THREE_WHEELER,
                        DELTA_THREE_WHEELER,
                        SINGLE_AXLE_TRAILER,
                        MONOWHEEL,
                    ).forEach { kind ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .testTag(kindRadio(kind))
                                .padding(8.dp)
                                .selectable(
                                    selected = currentKind == kind,
                                    onClick = { currentKind = kind },
                                    role = Role.RadioButton
                                )
                        ) {
                            RadioButton(
                                selected = currentKind == kind,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (kind) {
                                    CAR -> "Car"
                                    CAR_WITH_SPARE -> "Car with a spare tyre"
                                    MOTORCYCLE -> "Motorcycle"
                                    TADPOLE_THREE_WHEELER -> "Tadpole three-wheeler"
                                    DELTA_THREE_WHEELER -> "Delta three-wheeler"
                                    SINGLE_AXLE_TRAILER -> "Single axle trailer"
                                    MONOWHEEL -> "Monowheel"
                                }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text("Cancel") },
                modifier = Modifier.testTag(cancelButton)
            )
        },
        confirmButton = {
            TextButton(
                enabled = vehicleName.isBlank().not() && currentKind != null,
                onClick = { onVehicleAdd(vehicleName, currentKind!!) },
                content = { Text(text = "Add") },
                modifier = Modifier.testTag(addButton)
            )
        }
    )
    LaunchedEffect(key1 = Unit) {
        delay(200)
        focusRequester.requestFocus()
    }
}

private val previewOtherVehicle = previewVehicle.copy(
    uuid = UUID.randomUUID(),
    kind = Vehicle.Kind.MOTORCYCLE,
    name = "PREVIEW 2"
)

@Preview
@Composable
private fun CurrentVehicleDropdownPreview() {
    CurrentVehicleDropdown(
        shown = previewVehicle,
        viewModel = MockCurrentVehicleDropdownViewModel(
            State.Vehicles(previewVehicle, listOf(previewVehicle, previewOtherVehicle))
        )
    )
}

@Preview
@Composable
private fun OtherVehicleAlertingPreview() {
    CurrentVehicleDropdown(
        shown = previewVehicle,
        alerts = mapOf(previewOtherVehicle.uuid to CRIMSON),
        viewModel = MockCurrentVehicleDropdownViewModel(
            State.Vehicles(previewVehicle, listOf(previewVehicle, previewOtherVehicle))
        ),
    )
}

private class MockCurrentVehicleDropdownViewModel(state: State) : CurrentVehicleDropdownViewModel {
    override val stateFlow: StateFlow<State> = MutableStateFlow(state)
    override val eventChannel: ReceiveChannel<Event> = Channel()
    override suspend fun scanVehicles() = Unit
    override fun setCurrent(vehicle: Vehicle) = error("")
    override fun insert(carName: String, kind: Vehicle.Kind) = error("")
}

@Suppress("ConstPropertyName")
internal object CurrentVehicleDropdownTags {
    const val currentVehicleTest = "dropdown_current_vehicle_test"
    fun dropdownEntry(vehicleName: String) = "dropdown_entry_$vehicleName"
    const val dropdownEntryAddVehicle = "dropdown_item_add_vehicle"

    object AddVehicle {
        const val root = "CurrentVehicleDropdownTags_AddVehicle_root"
        const val textField = "dialog_add_vehicle_text_field"
        fun kindRadio(kind: Vehicle.Kind) = "dialog_add_vehicle_kind_radio_${kind.name}"

        const val addButton = "dialog_add_vehicle_add_button"
        const val cancelButton = "dialog_add_vehicle_cancel_button"

    }
}
