package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Clear
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Conflict
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Unchecked
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.UnexpectedStop
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
internal class PersistentScanningViewModel(
    private val appPreferences: AppPreferences,
    private val scanPolicyUseCase: ScanPolicyUseCase,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
    unexpectedStopUseCase: UnexpectedStopUseCase,
    private val controller: MonitoringController,
    private val otherApp: OtherApp,
    private val tier: AppTier = AppTier.current,
) : ViewModel() {

    private val logger = Logger.withTag("PersistentScanningViewModel")

    val persistentScanning = appPreferences.persistentScanning

    /** The main screen's menu only offers its switch once the settings page turned it on */
    val persistentScanningSetUp: StateFlow<Boolean> = appPreferences.persistentScanningSetUp

    /**
     * The switch of the main screen's menu. Turning it on is enough: PersistentScanningHost walks
     * the user through whatever is missing and starts the service as soon as it sees it on.
     */
    fun setPersistentScanning(enabled: Boolean) {
        appPreferences.persistentScanning.value = enabled
        if (enabled.not()) controller.stop()
    }

    /**
     * Lite without any Bluetooth device or beacon to activate it: turned on, it would scan all the
     * time. Read when the switch is tapped, so the current values are enough.
     */
    fun scansAlwaysOnceOn(): Boolean = with(appPreferences) {
        tier == LITE &&
            (activateOnBluetooth.value && activateBluetoothDevices.value.isNotEmpty()).not() &&
            (activateOnBeacon.value && beacons.value.isNotEmpty()).not()
    }

    // Read before anything restarts the service. Consumed even when persistent scanning is off,
    // so that an old marker can't be reported once it is turned on again.
    private val mutableUnexpectedStop = MutableStateFlow(
        unexpectedStopUseCase
            .consume()
            ?.takeIf { persistentScanning.value }
            ?.also { logger.w { "The persistent scanning service was stopped unexpectedly: $it" } }
    )

    /** Set when the service died behind the user's back since the app was last opened */
    val unexpectedStop: StateFlow<UnexpectedStop?> = mutableUnexpectedStop.asStateFlow()

    fun acknowledgeUnexpectedStop() {
        mutableUnexpectedStop.value = null
    }

    val decision: Flow<ScanDecision> = scanPolicyUseCase.decision

    /** Known right away most of the time, so that the bell doesn't start with a wrong colour */
    val currentDecision: ScanDecision? get() = scanPolicyUseCase.currentDecision

    /** Whether the settings in place need to read the connected WiFi's name, so the location permission */
    val wifiExceptionActive: Flow<Boolean> = combine(
        appPreferences.persistentScanning,
        appPreferences.suspendConditions,
        appPreferences.suspendScanningOnWifi,
        appPreferences.wifiExceptionEnabled,
    ) { persistent, suspendConditions, suspendOnWifi, exception ->
        // Lite has no WiFi suspend condition, whatever was imported from Pro
        tier != LITE && persistent && suspendConditions && suspendOnWifi && exception
    }

    fun requiredWifiPermissions(): List<String> = wifiConnectionUseCase.requiredPermissions()

    fun disableWifiException() {
        appPreferences.wifiExceptionEnabled.value = false
    }

    private val mutableOtherAppCheck = MutableStateFlow<OtherAppCheck>(Unchecked)

    /**
     * Only one of Lite and Pro may scan persistently: checked before the service starts here,
     * whenever persistent scanning is turned on or the app is opened while it's on
     */
    val otherAppCheck: StateFlow<OtherAppCheck> = mutableOtherAppCheck.asStateFlow()

    fun checkOtherApp() {
        mutableOtherAppCheck.value = Unchecked
        viewModelScope.launch {
            mutableOtherAppCheck.value = otherApp
                .status()
                ?.takeIf { it.persistentScanning }
                ?.let { Conflict(otherApp.label() ?: "the other version of this app") }
                ?: Clear
        }
    }

    /** Turns persistent scanning off in the other app, so that it runs here */
    fun switchFromOtherApp() {
        viewModelScope.launch {
            if (otherApp.turnOffPersistentScanning().not()) {
                logger.w { "Cannot turn persistent scanning off in ${otherApp.packageName}" }
            }
            mutableOtherAppCheck.value = Clear
        }
    }

    /** Leaves persistent scanning to the other app */
    fun keepOtherApp() {
        setPersistentScanning(false)
        mutableOtherAppCheck.value = Unchecked
    }

    sealed interface OtherAppCheck {
        /** Not checked yet, since persistent scanning was turned on or the app was opened */
        data object Unchecked : OtherAppCheck

        /** The other app isn't installed, can't be reached, or doesn't scan persistently */
        data object Clear : OtherAppCheck

        data class Conflict(val otherApp: String) : OtherAppCheck
    }

    /** Persistent scanning is on, so the service is expected to run whenever the app is opened */
    fun ensureRunning() {
        if (controller.isRunning.value) return
        // Never take the app down for this: the next app opening will try again
        runCatching { controller.start() }
            .onFailure { logger.e(it) { "Cannot start the persistent scanning service" } }
    }
}
