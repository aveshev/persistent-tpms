package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class PersistentScanningSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val scanPolicyUseCase: ScanPolicyUseCase,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
    bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    private val controller: MonitoringController,
) : ViewModel() {
    /** The switch of a Bluetooth condition, and the addresses of the devices it is for */
    class BluetoothCondition(
        val enabled: MutableStateFlow<Boolean>,
        val devices: MutableStateFlow<Set<String>>,
    ) {
        /** On, with devices to look for (including unpaired ones, see [disableBluetoothIfNoneSelected]) */
        val isSelected: Boolean
            get() = enabled.value && devices.value.isNotEmpty()
    }

    val persistentScanning = appPreferences.persistentScanning

    val decision: Flow<ScanDecision> = scanPolicyUseCase.decision

    /** Known right away most of the time, so that the screen doesn't start with a wrong status */
    val currentDecision: ScanDecision? get() = scanPolicyUseCase.currentDecision

    val suspendConditions = appPreferences.suspendConditions
    val suspendScanningInDoze = appPreferences.suspendScanningInDoze
    val suspendScanningOnWifi = appPreferences.suspendScanningOnWifi
    val wifiExceptionEnabled = appPreferences.wifiExceptionEnabled
    val exceptedWifiSsids = appPreferences.exceptedWifiSsids
    val suspendBluetooth = BluetoothCondition(
        appPreferences.suspendScanningOnBluetooth,
        appPreferences.suspendBluetoothDevices,
    )

    val activateConditions = appPreferences.activateConditions
    val activateOnCableCharging = appPreferences.activateOnCableCharging
    val activateOnWirelessCharging = appPreferences.activateOnWirelessCharging
    val activateOnAndroidAuto = appPreferences.activateOnAndroidAuto
    val activateBluetooth = BluetoothCondition(
        appPreferences.activateOnBluetooth,
        appPreferences.activateBluetoothDevices,
    )
    val activateOnBeacon = appPreferences.activateOnBeacon
    val beacons = appPreferences.beacons
    val stayActive = appPreferences.stayActive
    val stayActiveMinutes = appPreferences.stayActiveMinutes

    val wifiConnectionState = wifiConnectionUseCase.state

    val pairedDevices: Flow<List<PairedDevice>?> = bluetoothDevicesUseCase.paired

    /** Addresses of the paired devices connected right now */
    val connectedDevices: Flow<Set<String>> = bluetoothDevicesUseCase
        .connected
        .map { devices -> devices.map { it.address }.toSet() }

    fun requiredWifiPermissions(): List<String> = wifiConnectionUseCase.requiredPermissions()

    fun toggleExceptedSsid(ssid: String) {
        exceptedWifiSsids.value = exceptedWifiSsids.value.let {
            if (ssid in it) it - ssid else it + ssid
        }
    }

    fun removeExceptedSsids(ssids: Set<String>) {
        exceptedWifiSsids.value -= ssids
    }

    fun toggleBluetoothDevice(condition: BluetoothCondition, address: String) {
        condition.devices.value = condition.devices.value.let {
            if (address in it) it - address else it + address
        }
    }

    /**
     * Without any of its devices paired the condition could never be fulfilled: same as turning it
     * off. Unknown while Bluetooth is off ([paired] being null), it is left as it is then.
     */
    fun disableBluetoothIfNoneSelected(condition: BluetoothCondition, paired: List<PairedDevice>?) {
        paired
            ?.none { it.address in condition.devices.value }
            ?.also { if (it) condition.enabled.value = false }
    }

    fun disableActivateConditionsIfNoneSelected() {
        listOf(activateOnCableCharging, activateOnWirelessCharging, activateOnAndroidAuto)
            .none { it.value }
            .also {
                val beaconSelected = activateOnBeacon.value && beacons.value.isNotEmpty()
                if (it && activateBluetooth.isSelected.not() && beaconSelected.not()) activateConditions.value = false
            }
    }

    fun disableSuspendConditionsIfNoneSelected() {
        listOf(suspendScanningInDoze, suspendScanningOnWifi)
            .none { it.value }
            .also { if (it && suspendBluetooth.isSelected.not()) suspendConditions.value = false }
    }

    /** Called once the monitoring permissions are granted, the toggle only turns on after that */
    fun enablePersistentScanning() {
        // Set first: the service reads it to ask for a sticky restart
        appPreferences.persistentScanning.value = true
        appPreferences.persistentScanningSetUp.value = true
        controller.start()
    }

    fun disablePersistentScanning() {
        appPreferences.persistentScanning.value = false
        controller.stop()
    }
}
