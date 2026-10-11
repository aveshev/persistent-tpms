package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class TyreDisplaySettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val showTimeSinceUpdate = appPreferences.showTimeSinceUpdate
    val showBatteryVoltage = appPreferences.showBatteryVoltage
    val magnifierSeconds = appPreferences.magnifierSeconds
}
