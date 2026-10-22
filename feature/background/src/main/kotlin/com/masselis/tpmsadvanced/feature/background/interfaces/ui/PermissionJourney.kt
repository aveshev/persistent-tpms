package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.content.pm.PackageManager.PERMISSION_GRANTED
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.masselis.tpmsadvanced.core.common.appContext

/** The journey that gets the permissions a setting needs, see [rememberPermissionJourney] */
internal class PermissionJourney(
    private val onRequest: (onGranted: () -> Unit) -> Unit,
    private val inProgress: () -> Boolean,
) {
    /**
     * Asks for the permissions if any is missing. If the user ends up refusing them, the setting
     * is turned off and the user is told so. [onGranted] runs once they are held, right away when
     * they already are: it lets a setting turn on only after they were verified.
     */
    fun request(onGranted: () -> Unit = {}) = onRequest(onGranted)

    /** Read live, so it stays correct when this instance was captured by a long-lived effect */
    fun isInProgress() = inProgress()
}

/**
 * Also emits the dialogs of the journey, so it must be called from a place that stays in the
 * composition for as long as the journey can run.
 *
 * With [systemPopupFirst] the system permission popup comes first, and only its refusal leads to
 * the rationale. Otherwise the journey starts with the rationale: some permissions (such as
 * "Allow all the time" location) cannot be granted from a popup at all.
 *
 * The rationale sends the user to the app's Settings. Cancelling it, or coming back from Settings
 * without the permissions, ends the journey: [onDenied] must turn the setting off, and a dialog
 * with [disabledInfo] tells the user that it was, unless [isSettingsOnScreen]: the user then sees
 * the toggle turn off.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun rememberPermissionJourney(
    permissions: List<String>,
    systemPopupFirst: Boolean,
    rationale: String,
    disabledInfo: String,
    isSettingsOnScreen: () -> Boolean,
    onDenied: () -> Unit,
): PermissionJourney {
    val activity = LocalActivity.current
    val currentOnDenied by rememberUpdatedState(onDenied)
    val currentIsSettingsOnScreen by rememberUpdatedState(isSettingsOnScreen)
    var inProgress by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var waitingForSettings by remember { mutableStateOf(false) }
    var showDisabledInfo by remember { mutableStateOf(false) }
    var pendingOnGranted by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Read directly rather than through a permission state that refreshes on its own schedule
    fun isGranted() = permissions.all { checkSelfPermission(appContext, it) == PERMISSION_GRANTED }

    fun granted() {
        inProgress = false
        pendingOnGranted?.invoke()
        pendingOnGranted = null
    }

    fun denied() {
        pendingOnGranted = null
        inProgress = false
        waitingForSettings = false
        showRationale = false
        // On the settings screen the toggle turning itself off is explanation enough
        showDisabledInfo = currentIsSettingsOnScreen().not()
        currentOnDenied()
    }

    // Requesting via our own launcher gives a completion callback, which also fires when the
    // permission was permanently denied and the system shows nothing
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.isNotEmpty() && results.values.all { it }) granted()
        else showRationale = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Back from the Settings the rationale sent the user to
            if (event == ON_RESUME && waitingForSettings) {
                waitingForSettings = false
                if (isGranted()) granted() else denied()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        RationaleAlert(
            text = rationale,
            onDismissRequest = ::denied,
            onConfirm = {
                showRationale = false
                waitingForSettings = true
                activity?.openAppSettings()
            }
        )
    }
    if (showDisabledInfo) {
        DisabledInfoAlert(text = disabledInfo, onDismissRequest = { showDisabledInfo = false })
    }

    // Prompts once the other journeys are done, and holds them back until its info dialog is read
    val turn = rememberPromptTurn(
        onTurn = {
            when {
                // Granted while waiting, by another journey asking for the same permission
                isGranted() -> granted()
                systemPopupFirst -> permissionLauncher.launch(permissions.toTypedArray())
                else -> showRationale = true
            }
        }
    )
    val holdsTurn = inProgress || showDisabledInfo
    LaunchedEffect(holdsTurn) { if (holdsTurn.not()) turn.done() }

    return PermissionJourney(
        onRequest = { onGranted ->
            pendingOnGranted = onGranted
            if (isGranted()) {
                granted()
            } else {
                inProgress = true
                turn.await()
            }
        },
        // Until the user has read the info dialog too: the next journey must not open its own
        // dialogs on top of it
        inProgress = { inProgress || showDisabledInfo },
    )
}

@Composable
private fun RationaleAlert(
    text: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = { Text(text = text) },
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
private fun DisabledInfoAlert(text: String, onDismissRequest: () -> Unit) {
    AlertDialog(
        text = { Text(text = text) },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "OK")
            }
        }
    )
}
