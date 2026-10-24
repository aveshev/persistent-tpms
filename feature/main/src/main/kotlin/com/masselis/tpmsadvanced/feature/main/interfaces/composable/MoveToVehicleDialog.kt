package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase.OtherVehicle
import java.util.UUID

/**
 * Picks which of the [otherVehicles] the sensor at [location] moves to. One with several wheels
 * [open]s, where the user taps the wheel, see [MoveSensorToVehicle]. A mono-wheel takes it straight
 * away by [move], once asked whether to swap or replace the sensor it already has.
 */
@Suppress("LongMethod")
@Composable
internal fun MoveToVehicleDialog(
    location: Location,
    otherVehicles: List<OtherVehicle>,
    open: (Vehicle) -> Unit,
    move: (Vehicle, Location, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // A mono-wheel whose wheel has a sensor, asking whether to swap them or replace it
    var askingUuid by rememberSaveable { mutableStateOf<UUID?>(null) }
    // Gone if it was deleted meanwhile, back to the vehicles
    otherVehicles
        .firstOrNull { it.vehicle.uuid == askingUuid }
        ?.let { it.vehicle to it.vehicle.kind.locations.single() }
        ?.also { (vehicle, wheel) ->
            SwapOrReplaceDialog(
                vehicleName = vehicle.name,
                from = location,
                to = wheel,
                move = { swap -> move(vehicle, wheel, swap) },
                back = { askingUuid = null },
                onDismissRequest = onDismissRequest,
            )
        }
        ?: OptionsDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Column {
                    Text("Move to another vehicle")
                    Text(
                        buildString {
                            append("The ")
                            appendLoc(location)
                            append("'s sensor, with its calibration")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column {
                    otherVehicles.forEach { other ->
                        DialogOption(
                            icon = { Icon(ImageVector.vectorResource(R.drawable.directions_car_24px), null) },
                            title = other.vehicle.name,
                            subtitle = when (val count = other.occupied.size) {
                                0 -> "No sensors yet"
                                1 -> "1 sensor"
                                else -> "$count sensors"
                            },
                            onClick = {
                                other.vehicle.kind.locations.singleOrNull()
                                    ?.also { wheel ->
                                        if (wheel in other.occupied) askingUuid = other.vehicle.uuid
                                        else move(other.vehicle, wheel, false)
                                    }
                                    ?: open(other.vehicle)
                            },
                            modifier = Modifier.testTag(MoveToVehicleDialogTags.vehicle(other.vehicle.uuid)),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag(MoveToVehicleDialogTags.cancel),
                ) { Text("Cancel") }
            },
            modifier = Modifier.testTag(MoveToVehicleDialogTags.vehicleDialog),
        )
}

/**
 * The sensor at [from] going to [to] of the vehicle [vehicleName], which already has one: [move]
 * swaps them when `true`, replaces it otherwise
 */
@Composable
internal fun SwapOrReplaceDialog(
    vehicleName: String,
    from: Location,
    to: Location,
    move: (Boolean) -> Unit,
    back: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    OptionsDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                buildString {
                    append(vehicleName)
                    append("'s ")
                    appendLoc(to)
                    append(" already has a sensor")
                }
            )
        },
        text = {
            Column {
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.swap_horizontal_24px), null) },
                    title = "Swap them",
                    subtitle = buildString {
                        append("Its sensor comes to the other vehicle's ")
                        appendLoc(from)
                    },
                    onClick = { move(true) },
                    modifier = Modifier.testTag(MoveToVehicleDialogTags.swap),
                )
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.delete_24px), null) },
                    title = "Replace it",
                    subtitle = "Its sensor is removed from $vehicleName",
                    onClick = { move(false) },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(MoveToVehicleDialogTags.replace),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = back) { Text("Back") }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag(MoveToVehicleDialogTags.cancel),
            ) { Text("Cancel") }
        },
        modifier = Modifier.testTag(MoveToVehicleDialogTags.askDialog),
    )
}

/** What the snackbar tells once a sensor went to [location] of [vehicle] */
internal fun movedMessage(vehicle: Vehicle, location: Location, swap: Boolean): String = buildString {
    append(if (swap) "Swapped with " else "Moved to ")
    append(vehicle.name)
    append("'s ")
    appendLoc(location)
}

@Preview
@Composable
internal fun MoveToVehicleDialogPreview() {
    MoveToVehicleDialog(
        location = Location.Wheel(FRONT_LEFT),
        otherVehicles = listOf(
            OtherVehicle(
                previewVehicle.copy(name = "Van"),
                setOf(Location.Wheel(FRONT_LEFT), Location.Wheel(FRONT_RIGHT)),
            ),
            OtherVehicle(
                previewVehicle.copy(uuid = UUID.randomUUID(), kind = Vehicle.Kind.MONOWHEEL, name = "Monowheel"),
                emptySet(),
            ),
        ),
        open = {},
        move = { _, _, _ -> },
        onDismissRequest = {},
    )
}

@Preview
@Composable
internal fun SwapOrReplaceDialogPreview() {
    SwapOrReplaceDialog(
        vehicleName = "Van",
        from = Location.Wheel(FRONT_LEFT),
        to = Location.Wheel(FRONT_RIGHT),
        move = {},
        back = {},
        onDismissRequest = {},
    )
}

@Suppress("ConstPropertyName")
internal object MoveToVehicleDialogTags {
    fun vehicle(uuid: UUID) = "MoveToVehicleDialogTags_vehicle_$uuid"
    const val vehicleDialog = "MoveToVehicleDialogTags_vehicleDialog"
    const val askDialog = "MoveToVehicleDialogTags_askDialog"
    const val swap = "MoveToVehicleDialogTags_swap"
    const val replace = "MoveToVehicleDialogTags_replace"
    const val cancel = "MoveToVehicleDialogTags_cancel"
}
