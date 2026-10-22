package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.common.appContext

/**
 * What a background monitoring service needs from the user: notifications allowed (and Bluetooth
 * scan on API 34+, required by the connectedDevice foreground service type) and an exemption from
 * battery optimization.
 */
internal class MonitoringPermissions(
    val status: Status,
    private val onRequest: () -> Unit,
    private val inProgress: () -> Boolean,
) {
    enum class Status { Ok, NotificationsMissing, BatteryOptimizationMissing }

    /**
     * Walks the user through whatever is missing, then calls the `onGranted` given to
     * [rememberMonitoringPermissions]. The dialogs are shown by [rememberMonitoringPermissions].
     */
    fun request() = onRequest()

    /**
     * Whether a [request] is still walking the user through Settings or dialogs. Read live, so it
     * stays correct when this instance was captured by a long-lived effect.
     */
    fun isInProgress() = inProgress()
}

/** Provided by [PersistentScanningHost], so that everything shares one journey and its dialogs. */
internal val LocalMonitoringPermissions = compositionLocalOf<MonitoringPermissions> {
    error("No PersistentScanningHost above this composable")
}

/**
 * Also emits the dialogs of the "grant everything" journey, so it must be called from a place that
 * stays in the composition for as long as the journey can run.
 *
 * With [confirmBeforeStart], a journey that had the user fix something ends with a dialog saying
 * monitoring starts, confirmed before [onGranted] runs: the manual monitoring closes the app right
 * away, which would be abrupt without it. Persistent scanning keeps the app open, and the WiFi
 * exception journey may still follow with its own permission, so it goes on without the dialog.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun rememberMonitoringPermissions(
    confirmBeforeStart: Boolean,
    onGranted: () -> Unit,
): MonitoringPermissions {
    val activity = LocalActivity.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val permissions = remember {
        mutableListOf<String>()
            .apply { if (SDK_INT >= TIRAMISU) add(POST_NOTIFICATIONS) }
            .apply { if (SDK_INT >= UPSIDE_DOWN_CAKE) add(BLUETOOTH_SCAN) }
            .toList()
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    val powerManager = appContext.getSystemService<PowerManager>()
    fun isBatteryOptimizationMissing() =
        powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == false
    // The runtime permission only exists from API 33, but the user can block the notifications of
    // an app on any version, which hides the service's notification: the user could neither see
    // nor stop what runs
    fun areNotificationsBlocked() = NotificationManagerCompat.from(appContext)
        .areNotificationsEnabled()
        .not()
    var showBatteryOptimizationAlert by remember { mutableStateOf(false) }
    var showNotificationPermissionAlert by remember { mutableStateOf(false) }
    var showReadyToMonitorAlert by remember { mutableStateOf(false) }
    // Tracks the multi-step "enable monitoring" journey across the trip to Settings and back, so
    // each precondition that becomes satisfied automatically advances to the next one instead of
    // requiring the user to tap the button again after every fix.
    var flowInProgress by remember { mutableStateOf(false) }
    var remediationWasNeeded by remember { mutableStateOf(false) }
    // Requested, but other journeys are still prompting: this one hasn't shown anything yet
    var waitingForTurn by remember { mutableStateOf(false) }
    // The battery exemption can't be observed, it is read again every time the app is resumed
    var resumeCount by remember { mutableIntStateOf(0) }

    fun openAppSettings() = activity?.openAppSettings()

    fun openNotificationSettings() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .apply { putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName) }
        .apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) }
        .apply { addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) }
        .also { activity?.startActivity(it) }

    // Requesting via our own launcher (rather than permissionState.launchMultiplePermissionRequest())
    // gives us a completion callback: shouldShowRationale can't tell "never asked" apart from
    // "permanently denied", but this fires exactly once the request is settled either way, letting
    // us react to "still not granted" uniformly regardless of whether the system dialog appeared.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }.not()) showNotificationPermissionAlert = true
    }

    fun proceedEnablingFlow() {
        when {
            permissionState.allPermissionsGranted.not() -> {
                remediationWasNeeded = true
                permissionLauncher.launch(permissions.toTypedArray())
            }

            // Nothing to request when the permission is held (or does not exist), the user has to
            // allow the notifications in the settings
            areNotificationsBlocked() -> {
                remediationWasNeeded = true
                showNotificationPermissionAlert = true
            }

            // Unrestricted battery usage isn't a runtime permission, so it can't be requested
            // directly; explain why it's needed before sending the user to the app's settings
            // page. Without this, the foreground service can be killed shortly after starting.
            isBatteryOptimizationMissing() -> {
                remediationWasNeeded = true
                showBatteryOptimizationAlert = true
            }

            // Only interrupt with a confirmation when the user actually had to go fix something;
            // otherwise monitoring starts directly, as it always did for an already-configured app.
            remediationWasNeeded && confirmBeforeStart -> showReadyToMonitorAlert = true

            else -> {
                flowInProgress = false
                currentOnGranted()
            }
        }
    }

    // Prompts once the other journeys are done, and holds the others back until this one is
    val turn = rememberPromptTurn(
        onTurn = {
            waitingForTurn = false
            proceedEnablingFlow()
        }
    )
    LaunchedEffect(flowInProgress) { if (flowInProgress.not()) turn.done() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == ON_RESUME) resumeCount++
            // Resuming means the user came back from Settings (or from the system permission
            // dialog); re-check where the flow stands, unless one of our own alerts is already
            // waiting for an explicit answer, or the journey hasn't started prompting yet.
            val readyToCheck = showNotificationPermissionAlert.not() &&
                    showBatteryOptimizationAlert.not() &&
                    showReadyToMonitorAlert.not() &&
                    waitingForTurn.not()
            if (event == ON_RESUME && flowInProgress && readyToCheck) {
                proceedEnablingFlow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBatteryOptimizationAlert) {
        BatteryOptimizationAlert(
            onDismissRequest = {
                showBatteryOptimizationAlert = false
                flowInProgress = false
            },
            onConfirm = {
                showBatteryOptimizationAlert = false
                openAppSettings()
            }
        )
    }
    if (showNotificationPermissionAlert) {
        NotificationPermissionAlert(
            onDismissRequest = {
                showNotificationPermissionAlert = false
                flowInProgress = false
            },
            onConfirm = {
                showNotificationPermissionAlert = false
                openNotificationSettings()
            }
        )
    }
    if (showReadyToMonitorAlert) {
        ReadyToMonitorAlert(
            onConfirm = {
                showReadyToMonitorAlert = false
                flowInProgress = false
                currentOnGranted()
            }
        )
    }

    val notificationsBlocked = remember(resumeCount) { areNotificationsBlocked() }
    val batteryOptimizationMissing = remember(resumeCount) { isBatteryOptimizationMissing() }
    val status = when {
        permissionState.allPermissionsGranted.not() || notificationsBlocked ->
            MonitoringPermissions.Status.NotificationsMissing
        batteryOptimizationMissing -> MonitoringPermissions.Status.BatteryOptimizationMissing
        else -> MonitoringPermissions.Status.Ok
    }
    return MonitoringPermissions(
        status = status,
        onRequest = {
            flowInProgress = true
            remediationWasNeeded = false
            // Nothing to prompt for: no need to wait for the other journeys. Read live, the status
            // is only refreshed when the app is resumed.
            if (permissionState.allPermissionsGranted && areNotificationsBlocked().not() &&
                isBatteryOptimizationMissing().not()
            ) {
                proceedEnablingFlow()
            } else {
                waitingForTurn = true
                turn.await()
            }
        },
        inProgress = { flowInProgress },
    )
}

@Composable
private fun BatteryOptimizationAlert(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = "To keep monitoring your tyres reliably in the background, this app " +
                        "needs to be exempted from battery optimization (sometimes labelled " +
                        "\"unrestricted\" battery usage in your device's settings). Without " +
                        "it, the system is likely to stop background monitoring shortly after " +
                        "it starts."
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun NotificationPermissionAlert(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = "This app needs notifications enabled to alert you when a tyre goes " +
                        "out of range while background monitoring is running. Please enable " +
                        "notifications for this app to continue."
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun ReadyToMonitorAlert(
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = { Text(text = "All permissions granted, starting monitoring") },
        onDismissRequest = onConfirm,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "OK")
            }
        }
    )
}
