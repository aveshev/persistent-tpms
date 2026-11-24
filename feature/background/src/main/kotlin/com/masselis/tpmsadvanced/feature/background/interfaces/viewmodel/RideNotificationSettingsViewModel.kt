package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The "Going for a ride?" notification's settings, see `RideNotifier` */
internal class RideNotificationSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val controller: MonitoringController,
    scanPolicyUseCase: ScanPolicyUseCase,
) : ViewModel() {
    val rideNotification = appPreferences.rideNotification

    /**
     * Whether it can fire at all: on a vehicle detection condition, or on the bound sensors heard by
     * persistent scanning, which scans all the time without any condition
     */
    val canFire: Flow<Boolean> = combine(
        scanPolicyUseCase.detectsVehicle,
        appPreferences.persistentScanning,
        Boolean::or,
    )
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
