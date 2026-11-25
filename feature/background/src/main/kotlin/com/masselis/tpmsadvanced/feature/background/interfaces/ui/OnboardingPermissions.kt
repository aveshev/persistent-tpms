package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.ui.OnboardingPage
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The introduction's page asking, at once, for what the app needs to scan and for its monitor
 * mode: the Bluetooth scan, the notifications and the unrestricted battery usage. Each is ticked
 * once held. Going through every request goes on to [onNext] by itself, "Not now" goes on without.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
public fun OnboardingPermissions(
    step: Int,
    stepCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The same as the scanner asks for, see BluetoothLeScanner.missingPermission()
    val scanState = rememberMultiplePermissionsState(remember { scanPermissions() })
    // The notifications and the battery exemption can't be observed, they're read again whenever
    // the app comes back, from a system popup or from Settings
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val monitoring = rememberMonitoringPermissions(confirmBeforeStart = false, onGranted = onNext)
    val notificationsOk = remember(resumes, monitoring.status) {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
    val batteryOk = remember(resumes) {
        appContext.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(appContext.packageName) != false
    }
    // The monitor mode's journey follows the scan's popup, whatever its answer: a refused scan is
    // asked for again before the main screen, see Preconditions
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { monitoring.request() }
    val allOk = scanState.allPermissionsGranted && notificationsOk && batteryOk
    OnboardingPage(
        step = step,
        stepCount = stepCount,
        primary = stringResource(if (allOk) CommonR.string.onboarding_next else CommonR.string.onboarding_allow),
        onPrimary = {
            when {
                allOk -> onNext()
                scanState.allPermissionsGranted.not() ->
                    scanLauncher.launch(scanState.permissions.map { it.permission }.toTypedArray())

                else -> monitoring.request()
            }
        },
        secondary = stringResource(if (allOk) CommonR.string.back else CommonR.string.onboarding_not_now),
        onSecondary = if (allOk) onBack else onNext,
        onSkip = onSkip,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(CommonR.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(CommonR.string.onboarding_permissions_text))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Under "Location" before Android 12, as the system's own Settings shows it
            PermissionRow(
                title = stringResource(if (SDK_INT >= S) CommonR.string.nearby_devices else CommonR.string.location),
                text = stringResource(CommonR.string.onboarding_permission_scan_text),
                isGranted = scanState.allPermissionsGranted,
            )
            PermissionRow(
                title = stringResource(CommonR.string.onboarding_permission_notifications_title),
                text = stringResource(CommonR.string.onboarding_permission_notifications_text),
                isGranted = notificationsOk,
            )
            PermissionRow(
                title = stringResource(CommonR.string.onboarding_permission_battery_title),
                text = stringResource(CommonR.string.onboarding_permission_battery_text),
                isGranted = batteryOk,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, text: String, isGranted: Boolean, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        // Its room kept while missing, the texts don't move once it's granted
        if (isGranted) Icon(
            ImageVector.vectorResource(R.drawable.check_24px),
            // Read with the row: its texts, then that it's held
            contentDescription = stringResource(CommonR.string.onboarding_allowed),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        else Spacer(Modifier.size(24.dp))
    }
}

@SuppressLint("InlinedApi")
private fun scanPermissions() = when {
    SDK_INT >= S -> listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
    SDK_INT >= Q -> listOf(ACCESS_FINE_LOCATION)
    else -> listOf(ACCESS_COARSE_LOCATION)
}
