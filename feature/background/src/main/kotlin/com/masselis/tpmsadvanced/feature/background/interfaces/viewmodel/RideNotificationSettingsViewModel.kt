package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase

/** The "Going for a ride?" notification's settings, see `RideNotifier` */
internal class RideNotificationSettingsViewModel(
    appPreferences: AppPreferences,
    bluetoothDevicesUseCase: BluetoothDevicesUseCase,
) : ViewModel() {
    val rideNotification = appPreferences.rideNotification
    val onSensor = appPreferences.rideOnSensor
    val onBluetooth = appPreferences.rideOnBluetooth
    val onBeacon = appPreferences.rideOnBeacon

    /** The activate condition's devices and beacons, which the triggers share */
    val bluetoothDevices = appPreferences.activateBluetoothDevices
    val beacons = appPreferences.beacons
    val pairedDevices = bluetoothDevicesUseCase.paired

    val persistentScanning = appPreferences.persistentScanning

    val absentMinutes = appPreferences.rideAbsentMinutes
    val staleMinutes = appPreferences.rideStaleMinutes
}
