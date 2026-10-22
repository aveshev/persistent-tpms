package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Clear
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Conflict
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * How many settings screens are composed, so that the host knows the user is looking at one. A count
 * rather than a flag: while navigating between two of them, the next one enters before the
 * previous one leaves.
 */
private val LocalSettingsOnScreen = compositionLocalOf<MutableIntState> {
    error("No PersistentScanningHost above this composable")
}

/** Tells the host that a settings screen is visible, for as long as the caller is composed */
@Composable
internal fun SettingsOnScreenEffect() {
    val settingsOnScreen = LocalSettingsOnScreen.current
    DisposableEffect(settingsOnScreen) {
        settingsOnScreen.intValue += 1
        onDispose { settingsOnScreen.intValue -= 1 }
    }
}

/** The journey behind the WiFi exception toggle, so that it only turns on once its permission is held */
internal val LocalWifiExceptionJourney = compositionLocalOf<PermissionJourney> {
    error("No PersistentScanningHost above this composable")
}

/**
 * Must wrap every [MonitoringButton]. It owns the one permission journey (and its dialogs) that the
 * bell and the app-open check share: two independent journeys would both react when the user comes
 * back from Settings and show their dialogs twice.
 *
 * Every time the app is opened while persistent scanning is on, it also checks that the service
 * still has what it needs (walking the user through what is missing, once) and that it is running.
 * When the service died behind the user's back since the last opening, the user is told first.
 * Before any of that, the other version of the app (Lite or Pro) must not scan persistently too:
 * the user chooses which one does.
 */
@Composable
public fun PersistentScanningHost(content: @Composable () -> Unit): Unit =
    PersistentScanningHost(
        viewModel { Bindings.featureBackgroundInternal.persistentScanningViewModel() },
        content,
    )

@Suppress("LongMethod", "ComplexCondition")
@Composable
internal fun PersistentScanningHost(
    viewModel: PersistentScanningViewModel,
    content: @Composable () -> Unit,
) {
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val unexpectedStop by viewModel.unexpectedStop.collectAsState()
    val otherAppCheck by viewModel.otherAppCheck.collectAsState()
    // Declared before the permission journey's effect, so that it runs first once the app is
    // started: the journey reads the check's reset state rather than the one of the last opening
    LifecycleStartEffect(persistentScanning) {
        if (persistentScanning) viewModel.checkOtherApp()
        onStopOrDispose { }
    }
    (otherAppCheck as? Conflict)?.also { conflict ->
        OtherAppPersistentScanningAlert(
            otherApp = conflict.otherApp,
            onSwitch = viewModel::switchFromOtherApp,
            onCancel = viewModel::keepOtherApp,
        )
    }
    val permissions = rememberMonitoringPermissions(
        confirmBeforeStart = false,
        onGranted = viewModel::ensureRunning,
    )
    val currentPermissions by rememberUpdatedState(permissions)
    // Read here to key the effect: the service is restarted once the user read why it stopped
    val unexpectedStopShown = unexpectedStop != null
    LifecycleStartEffect(persistentScanning, unexpectedStopShown, otherAppCheck) {
        // When everything is granted this only makes sure the service runs, without any popup.
        // Coming back from Settings also starts the lifecycle again: the journey started before
        // leaving is resumed by the permissions themselves, it must not be started a second time.
        // The check is read live: the composition's value can still be the last opening's.
        if (
            persistentScanning &&
            viewModel.otherAppCheck.value == Clear &&
            unexpectedStopShown.not() &&
            currentPermissions.isInProgress().not()
        ) {
            currentPermissions.request()
        }
        onStopOrDispose { }
    }
    // Acknowledged once the instructions are closed too, so the permission journey (and its
    // dialogs) doesn't start behind them
    var showKeepAliveInstructions by rememberSaveable { mutableStateOf(false) }
    unexpectedStop?.also { stop ->
        if (showKeepAliveInstructions) {
            KeepAliveInstructions(
                onDismissRequest = {
                    showKeepAliveInstructions = false
                    viewModel.acknowledgeUnexpectedStop()
                }
            )
        } else {
            UnexpectedStopAlert(
                unexpectedStop = stop,
                onDismissRequest = viewModel::acknowledgeUnexpectedStop,
                onLearnMore = { showKeepAliveInstructions = true },
            )
        }
    }

    // The WiFi exception needs permissions of its own: they are asked for every time the app is
    // opened while the settings in place need them, not only when the settings screen is visible.
    val wifiExceptionActive by viewModel.wifiExceptionActive.collectAsState(initial = false)
    val settingsOnScreen = remember { mutableIntStateOf(0) }
    val wifiJourney = rememberWifiExceptionJourney(
        permissions = viewModel.requiredWifiPermissions(),
        isSettingsOnScreen = { settingsOnScreen.intValue > 0 },
        onDenied = viewModel::disableWifiException,
    )
    val currentWifiJourney by rememberUpdatedState(wifiJourney)
    LifecycleStartEffect(wifiExceptionActive) {
        // Both journeys start together when the app opens: the prompt queue has the monitoring one
        // go first (its effect is declared first), this one prompting once it's done
        if (wifiExceptionActive && currentWifiJourney.isInProgress().not()) currentWifiJourney.request()
        onStopOrDispose { }
    }
    CompositionLocalProvider(
        LocalMonitoringPermissions provides permissions,
        LocalSettingsOnScreen provides settingsOnScreen,
        LocalWifiExceptionJourney provides wifiJourney,
        content = content,
    )
}
