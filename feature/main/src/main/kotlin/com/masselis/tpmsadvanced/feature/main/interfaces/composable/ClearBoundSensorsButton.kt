package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.text.buildAnnotatedString
import com.masselis.tpmsadvanced.core.ui.appendBold
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.ClearBoundSensorsButtonTags.confirm
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.ClearBoundSensorsButtonTags.root
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ClearBoundSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ClearBoundSensorsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.ClearBoundSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key

/** A [SettingsGroup] item unbinding all the vehicle's sensors, once confirmed */
@Composable
internal fun ClearBoundSensorsButton(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
    viewModel: ClearBoundSensorsViewModel = component.viewModel(component.key()) {
        it.ClearBoundSensorsViewModel(createSavedStateHandle())
    }
) {
    val state by viewModel.stateFlow.collectAsState()
    val vehicle by component.vehicleStateFlow.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    TextSettingsItem(
        headline = "Clear all sensors",
        supporting = when (state) {
            State.ClearingPossible -> "Removes every sensor assigned to this vehicle"
            State.AlreadyCleared -> "No sensor is assigned to this vehicle"
        },
        onClick = { confirmClear = true },
        enabled = state is State.ClearingPossible,
        headlineColor = MaterialTheme.colorScheme.error,
        modifier = modifier.testTag(root),
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        text = {
            Text(
                buildAnnotatedString {
                    append("Clear all sensors from ")
                    appendBold(vehicle.name)
                    append("?\nThis cannot be undone.")
                }
            )
        },
        dismissButton = {
            TextButton(
                onClick = { confirmClear = false },
                modifier = Modifier.testTag(ClearBoundSensorsButtonTags.cancel),
            ) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.clear(); confirmClear = false },
                modifier = Modifier.testTag(confirm),
            ) { Text("Clear all") }
        },
    )
}

@Suppress("ConstPropertyName")
internal object ClearBoundSensorsButtonTags {
    const val root = "ClearBoundSensorsButtonTags_root"
    const val confirm = "ClearBoundSensorsButtonTags_confirm"
    const val cancel = "ClearBoundSensorsButtonTags_cancel"
}
