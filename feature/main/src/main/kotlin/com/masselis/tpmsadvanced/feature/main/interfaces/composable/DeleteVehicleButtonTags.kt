package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.text.buildAnnotatedString
import com.masselis.tpmsadvanced.core.ui.appendBold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DeleteVehicleButtonTags.Button.tag
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DeleteVehicleButtonTags.Dialog.cancel
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DeleteVehicleButtonTags.Dialog.delete
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DeleteVehicleViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DeleteVehicleViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DeleteVehicleViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.DeleteVehicleViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/** A [SettingsGroup] item deleting the vehicle once confirmed, the last one cannot be */
@Composable
internal fun DeleteVehicleButton(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
    viewModel: DeleteVehicleViewModel = component.viewModel(component.key()) { it.DeleteVehicleViewModel() }
) {
    val navController = LocalHomeNavController.current
    val state by viewModel.stateFlow.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    TextSettingsItem(
        headline = stringResource(CommonR.string.delete_vehicle),
        supporting = when (state) {
            is State.DeletableVehicle -> null
            is State.NotDeletableVehicle -> stringResource(CommonR.string.the_only_vehicle_cannot_be_deleted)
        },
        onClick = { showDeleteDialog = true },
        enabled = state is State.DeletableVehicle,
        headlineColor = MaterialTheme.colorScheme.error,
        modifier = modifier.testTag(tag),
    )
    if (showDeleteDialog) DeleteVehicleDialog(
        vehicle = state.vehicle,
        onDismissRequest = { showDeleteDialog = false },
        onDelete = { viewModel.delete(); showDeleteDialog = false; })
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                Event.Leave -> navController.popBackStack()
            }
        }
    }
}

@Composable
private fun DeleteVehicleDialog(
    vehicle: Vehicle,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        text = {
            Text(
                buildAnnotatedString {
                    append(stringResource(CommonR.string.do_you_really_want_to_delete))
                    appendBold(vehicle.name)
                    append(stringResource(CommonR.string.this_action_cannot_be_undone))
                }
            )
        }, onDismissRequest = onDismissRequest, dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text(stringResource(CommonR.string.cancel)) },
                modifier = Modifier.testTag(cancel)
            )
        }, confirmButton = {
            TextButton(
                onClick = onDelete,
                content = { Text(stringResource(CommonR.string.delete, vehicle.name)) },
                modifier = Modifier.testTag(delete)
            )
        }, modifier = modifier.testTag(DeleteVehicleButtonTags.Dialog.root)
    )
}

@Suppress("ConstPropertyName")
internal object DeleteVehicleButtonTags {
    object Button {
        const val tag = "DeleteVehicleButton_Button_tag"
    }

    object Dialog {
        const val root = "DeleteVehicleButtonTags_Dialog_root"
        const val cancel = "DeleteVehicleButton_Dialog_cancel"
        const val delete = "DeleteVehicleButton_Dialog_delete"
    }
}
