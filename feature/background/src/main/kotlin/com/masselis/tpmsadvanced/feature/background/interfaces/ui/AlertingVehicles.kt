package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.AlertingVehiclesUseCase
import java.util.UUID

/** The worst red or crimson alert going on for each vehicle alerting, see [AlertingVehiclesUseCase] */
@Composable
public fun alertingVehicles(): Map<UUID, AlertLevel> =
    viewModel { Bindings.featureBackgroundInternal.alertingVehiclesViewModel() }
        .stateFlow
        .collectAsState()
        .value

/**
 * [alertingVehicles] without the alerts silenced or ignored from the main screen's button, what keeps
 * the screen bright, see [AlertingVehiclesUseCase.unsilenced]
 */
@Composable
public fun unsilencedAlertingVehicles(): Map<UUID, AlertLevel> =
    viewModel { Bindings.featureBackgroundInternal.alertingVehiclesViewModel() }
        .unsilenced
        .collectAsState()
        .value
