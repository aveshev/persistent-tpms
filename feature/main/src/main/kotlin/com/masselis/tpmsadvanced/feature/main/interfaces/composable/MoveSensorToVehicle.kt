package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.masselis.tpmsadvanced.core.ui.isWideWindow
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Moves the sensor at [location] of the vehicle of [component] to the vehicle [vehicleUuid]: its
 * page, like [ManageSensors], where the user taps the wheel the sensor goes to, asked whether to
 * swap or replace the sensor already there. [onMoved] tells what the snackbar should say, once it's
 * moved.
 */
@Suppress("LongParameterList")
@Composable
public fun MoveSensorToVehicle(
    location: Location,
    vehicleUuid: UUID,
    snackbarHostState: SnackbarHostState,
    onMoved: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** In a wide window, see [isWideWindow], the top bar makes way: the page shows its own */
    navigationIcon: @Composable () -> Unit = {},
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    // The moving sensor's tyre's
    val viewModel: TyreActionsViewModel = component
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } }
    val otherVehicles by viewModel.otherVehicles.collectAsState()
    // Kept until the move is done: leaving the page would cancel it
    val scope = rememberCoroutineScope()
    // Nothing until they're loaded, or if it was deleted meanwhile
    otherVehicles
        .firstOrNull { it.vehicle.uuid == vehicleUuid }
        ?.vehicle
        ?.also { vehicle ->
            val target = VehicleComponent(vehicle)
            // Its tyres are the other vehicle's
            CompositionLocalProvider(LocalVehicleComponent provides target) {
                SensorsPage(
                    snackbarHostState = snackbarHostState,
                    incoming = IncomingSensor(
                        location = location,
                        move = { wheel, swap ->
                            scope.launch {
                                viewModel.moveToVehicle(vehicle, wheel, swap)
                                onMoved(movedMessage(vehicle, wheel, swap))
                            }
                        },
                        cancel = onCancel,
                    ),
                    // Tapping a wheel only takes the sensor, nothing else opens
                    scanQrCode = {},
                    scanBluetooth = {},
                    calibrate = {},
                    moveToVehicle = { _, _ -> },
                    modifier = modifier,
                    navigationIcon = navigationIcon,
                    component = target,
                )
            }
        }
}

/** A sensor coming from [location] of another vehicle: [move] sends it to a wheel, swapping or not */
internal class IncomingSensor(
    val location: Location,
    val move: (Location, Boolean) -> Unit,
    val cancel: () -> Unit,
)
