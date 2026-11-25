package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.usecase.DefaultVehicleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class OnboardingVehicleViewModel(
    private val defaultVehicleUseCase: DefaultVehicleUseCase,
) : ViewModel() {

    private val mutableHasDefault = MutableStateFlow<Boolean?>(null)

    /** Whether the default vehicle is still there to replace, null until known */
    val hasDefault: StateFlow<Boolean?> = mutableHasDefault.asStateFlow()

    init {
        viewModelScope.launch { mutableHasDefault.value = defaultVehicleUseCase.untouched() != null }
    }

    fun replace(name: String, kind: Vehicle.Kind, onReplaced: () -> Unit) {
        // The dialog closes meanwhile: the screen starts over with the vehicle added, before the
        // default one is deleted
        mutableHasDefault.value = null
        viewModelScope.launch {
            defaultVehicleUseCase.replace(name, kind)
            onReplaced()
        }
    }
}
