package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.feature.background.usecase.AlertingVehiclesUseCase
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** The vehicles alerting, for the vehicle picker */
internal class AlertingVehiclesViewModel(alertingVehiclesUseCase: AlertingVehiclesUseCase) : ViewModel() {
    val stateFlow: StateFlow<Map<UUID, AlertLevel>> = alertingVehiclesUseCase
        .levels
        .stateIn(viewModelScope, WhileSubscribed(), emptyMap())
}
