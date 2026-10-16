package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class TyreStatsViewModelImpl(
    override val stateFlow: TyreStatsStateFlow,
    appPreferences: AppPreferences,
) : ViewModel(), TyreStatsViewModel {
    override val isLoading = stateFlow.isLoading
    // The debug options are only shown while their switch is on
    override val showSensorId = appPreferences.debugOptions
        .combine(appPreferences.showSensorId, Boolean::and)
        .stateIn(viewModelScope, Eagerly, appPreferences.debugOptions.value && appPreferences.showSensorId.value)
    override val showSensorFlags = appPreferences.debugOptions
        .combine(appPreferences.showSensorFlags, Boolean::and)
        .stateIn(viewModelScope, Eagerly, appPreferences.debugOptions.value && appPreferences.showSensorFlags.value)
    override val showTimeSinceUpdate = appPreferences.showTimeSinceUpdate
    override val showBatteryVoltage = appPreferences.showBatteryVoltage
}
