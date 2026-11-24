package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController

/** The "Going for a ride?" notification's settings, see `RideNotifier` */
internal class RideNotificationSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val controller: MonitoringController,
) : ViewModel() {
    val rideNotification = appPreferences.rideNotification
    val absentMinutes = appPreferences.rideAbsentMinutes
    val staleMinutes = appPreferences.rideStaleMinutes

    /**
     * Called once the monitoring permissions are granted, the switch only turns on after that.
     * PersistentScanningHost starts the service as soon as it sees it on, see `BackgroundMode`.
     */
    fun enable() {
        appPreferences.rideNotification.value = true
    }

    fun disable() {
        appPreferences.rideNotification.value = false
        // Without persistent scanning, the service only ran for the ride notification
        if (appPreferences.persistentScanning.value.not()) controller.stop()
    }
}
