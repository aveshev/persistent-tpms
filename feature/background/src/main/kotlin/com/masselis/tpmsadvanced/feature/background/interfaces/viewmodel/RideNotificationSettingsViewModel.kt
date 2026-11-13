package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

/** The "Going for a ride?" notification's settings, see `RideNotifier` */
internal class RideNotificationSettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val rideNotification = appPreferences.rideNotification
    val persistentScanning = appPreferences.persistentScanning
    val absentMinutes = appPreferences.rideAbsentMinutes
    val staleMinutes = appPreferences.rideStaleMinutes
}
