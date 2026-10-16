package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.isWideWindow
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
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun AddVehicle(
    onDismissRequest: () -> Unit,
    onVehicleAdd: (name: String, kind: Vehicle.Kind) -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogFocusRequester = remember { FocusRequester() }
    val isWide = isWideWindow()
    var vehicleName by remember { mutableStateOf("") }
    var currentKind by remember { mutableStateOf<Vehicle.Kind?>(null) }
    var isNameFocused by remember { mutableStateOf(false) }
    // In landscape, the keyboard leaves room for little more than the name: while it's typed, the
    // dialog is only the name, the rest comes back once the name is done
    val isCompact = isWide && isNameFocused
    val title = @Composable {
        Text(
            text = "Add a new vehicle",
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
    val nameField = @Composable {
        OutlinedTextField(
            label = { Text(text = "Vehicle name") },
            value = vehicleName,
            onValueChange = { vehicleName = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dialogFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(textField)
                .onFocusChanged { isNameFocused = it.isFocused }
        )
    }
    val kinds = @Composable { modifier: Modifier ->
        Column(
            modifier = modifier
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
                        },
                        color = AlertDialogDefaults.textContentColor,
                    )
                }
            }
        }
    }
    val buttons = @Composable { modifier: Modifier ->
        Row(modifier = modifier) {
            TextButton(
                onClick = onDismissRequest,
                content = { Text("Cancel") },
                modifier = Modifier.testTag(cancelButton)
            )
            TextButton(
                enabled = vehicleName.isBlank().not() && currentKind != null,
                onClick = { onVehicleAdd(vehicleName, currentKind!!) },
                content = { Text(text = "Add") },
                modifier = Modifier.testTag(addButton)
            )
        }
    }
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .testTag(CurrentVehicleDropdownTags.AddVehicle.root)
            .run {
                // Material caps a dialog at 560 dp, these margins are outside of it. In landscape,
                // as wide as the assign sensor dialog, see OptionsDialog.
                if (isWide) padding(horizontal = 48.dp, vertical = 24.dp)
                else padding(horizontal = 16.dp).widthIn(max = 328.dp)
            },
        // Sized by the dialog itself, the platform's width doesn't follow the keyboard's resize
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // With the keyboard closed by its back or down arrow, the name keeps its focus
        BackHandler(enabled = isCompact) { dialogFocusRequester.requestFocus() }
        // Panned over the keyboard, the dialog moved a few pixels at each letter typed. Resized
        // instead, it stays above the keyboard.
        LocalView.current.also { view ->
            @Suppress("DEPRECATION")
            SideEffect { (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE) }
        }
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            // Ends the name's editing: cleared, the focus would come back to the name, the dialog's
            // first focusable. Also by tapping the dialog.
            modifier = Modifier
                .focusRequester(dialogFocusRequester)
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dialogFocusRequester.requestFocus() }
                )
        ) {
            if (isWide)
                // The title, the name and the buttons on the left, the kinds scrolling on the right.
                // The name keeps its place in the composition, keeping its focus and the keyboard
                // up while the dialog switches to compact.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        // The whole height but the dialog's margins, only the name's while compact
                        .run { if (isCompact) height(IntrinsicSize.Min) else fillMaxHeight() }
                        .padding(
                            // The name's outline is below its label's room, it sits centered with
                            // 8 dp less above than around it
                            if (isCompact) PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                            else PaddingValues(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                    ) {
                        // The buttons stay at the bottom whatever the font size: short of room, the
                        // title scrolls, the name keeps right under it
                        Column(modifier = Modifier.weight(1f)) {
                            if (isCompact.not())
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                ) { title() }
                            nameField()
                        }
                        if (isCompact.not()) buttons(Modifier.padding(top = 24.dp))
                    }
                    if (isCompact.not())
                        kinds(
                            Modifier
                                .weight(3f)
                                .fillMaxHeight()
                        )
                }
            else
                Column(modifier = Modifier.padding(24.dp)) {
                    title()
                    nameField()
                    kinds(
                        Modifier
                            .weight(1f, fill = false)
                            .padding(top = 16.dp)
                    )
                    buttons(
                        Modifier
                            .align(Alignment.End)
                            .padding(top = 24.dp)
                    )
                }
        }
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
