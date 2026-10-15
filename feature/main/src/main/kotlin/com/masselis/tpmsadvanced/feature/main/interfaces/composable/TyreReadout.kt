package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

/** The readout of [location], or what to tap to assign it a sensor while it has none */
@Composable
internal fun TyreReadout(
    location: Location,
    modifier: Modifier = Modifier,
    /** See [TyreStat] */
    isBasic: Boolean = false,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    // What's centered in the outline keeps away from its outer edge, the tyre's side is far enough
    // The spare's readout is under it, centered with no outer edge. An assigned location isn't
    // outlined outside of managing its sensors, see TyreTapArea.
    val isOutlined = location != Location.Spare && (state !is State.Assigned || isBasic)
    val outlined = if (isOutlined.not()) modifier else modifier.padding(
        start = OUTLINE_PADDING.takeIf { location.readoutSide == LEFT } ?: 0.dp,
        end = OUTLINE_PADDING.takeIf { location.readoutSide == RIGHT } ?: 0.dp,
    )
    when (val state = state) {
        is State.Unassigned -> TapToAssign(location, state.detected != null, outlined)
        State.Demo, is State.Assigned ->
            TyreStat(location = location, modifier = if (isBasic) outlined else modifier, isBasic = isBasic)
    }
}

/** Space between the outer edge of a location's outline and what's centered in it */
internal val OUTLINE_PADDING = 4.dp

@Composable
private fun TapToAssign(
    location: Location,
    isDetected: Boolean,
    modifier: Modifier = Modifier,
) {
    // Centered in the readout's slot, inside the outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag(TyreReadoutTags.tapToAssign(location)),
    ) {
        Text(
            text = "Tap to\nassign",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        // A Sysgration sensor advertising this location, see AssignSensorDialog. As narrow as the
        // text above, so the outlines needn't widen for it.
        if (isDetected) SysgrationBadge(Modifier.padding(top = 4.dp))
    }
}

@Preview(showBackground = true)
@Composable
internal fun TapToAssignPreview() {
    TapToAssign(Location.Wheel(FRONT_LEFT), isDetected = false)
}

@Preview(showBackground = true)
@Composable
internal fun TapToAssignDetectedPreview() {
    TapToAssign(Location.Wheel(FRONT_LEFT), isDetected = true)
}

internal object TyreReadoutTags {
    fun tapToAssign(location: Location) = "TyreReadoutTags_tapToAssign_$location"
}
