package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class AlertsSettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val alertSound = appPreferences.alertSound
    val vibrate = appPreferences.vibrateAlerts
}
