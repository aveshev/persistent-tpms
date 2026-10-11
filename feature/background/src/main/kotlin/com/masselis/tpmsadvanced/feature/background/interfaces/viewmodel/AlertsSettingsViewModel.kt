package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE

internal class AlertsSettingsViewModel(private val appPreferences: AppPreferences) : ViewModel() {
    val alertSound = appPreferences.alertSound
    val vibrate = appPreferences.vibrateAlerts

    /** Turns the sound back on to the one chosen before it was turned off */
    fun setAlertSoundOn(on: Boolean) {
        alertSound.value = if (on) appPreferences.lastAlertSound else NONE
    }
}
