package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanSuspensionUseCase(
    appPreferences: AppPreferences,
    phoneIdleUseCase: PhoneIdleUseCase,
    wifiConnectionUseCase: WifiConnectionUseCase,
    bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    tier: AppTier = AppTier.current,
) {
    enum class Reason { IDLE, WIFI, BLUETOOTH }

    val suspensionReasons: Flow<Set<Reason>> = appPreferences.suspendConditions.flatMapLatest { conditions ->
        // Lite only suspends while the phone is idle, without a switch for the conditions as a whole
        if (tier == LITE) {
            return@flatMapLatest appPreferences.suspendScanningInDoze.flatMapLatest { enabled ->
                if (enabled) phoneIdleUseCase.isIdle.map { if (it) setOf(Reason.IDLE) else emptySet() }
                else flowOf(emptySet())
            }
        }
        // Turned off, no condition applies: none of the system listeners is needed
        if (conditions.not()) return@flatMapLatest flowOf(emptySet())
        combine(
            appPreferences.suspendScanningInDoze.flatMapLatest { enabled ->
                if (enabled) phoneIdleUseCase.isIdle else flowOf(false)
            },
            appPreferences.suspendScanningOnWifi.flatMapLatest { enabled ->
                if (enabled) {
                    combine(
                        appPreferences.wifiExceptionEnabled,
                        appPreferences.exceptedWifiSsids,
                        wifiConnectionUseCase.state,
                    ) { exceptionEnabled, exceptedSsids, connectionState ->
                        connectionState is WifiConnectionUseCase.State.Connected &&
                                (exceptionEnabled.not() || connectionState.ssid !in exceptedSsids)
                    }
                } else {
                    flowOf(false)
                }
            },
            appPreferences.suspendScanningOnBluetooth.flatMapLatest { enabled ->
                if (enabled) {
                    combine(
                        appPreferences.suspendBluetoothDevices,
                        bluetoothDevicesUseCase.connected,
                    ) { selected, connected -> connected.any { it.address in selected } }
                } else {
                    flowOf(false)
                }
            },
        ) { idle, wifi, bluetooth ->
            buildSet {
                if (idle) add(Reason.IDLE)
                if (wifi) add(Reason.WIFI)
                if (bluetooth) add(Reason.BLUETOOTH)
            }
        }
    }
}
