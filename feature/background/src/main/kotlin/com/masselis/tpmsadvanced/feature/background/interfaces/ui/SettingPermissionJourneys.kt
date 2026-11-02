package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import androidx.compose.runtime.Composable
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * Location must be "Allow all the time" (API 29+, where that option exists): that is the only way
 * the service can read the connected WiFi's name while the app is not visible, so it cannot be
 * granted from a system popup. "Use precise location" only exists from API 31.
 */
@Composable
internal fun rememberWifiExceptionJourney(
    permissions: List<String>,
    isSettingsOnScreen: () -> Boolean,
    onDenied: () -> Unit,
): PermissionJourney = rememberPermissionJourney(
    permissions = permissions,
    systemPopupFirst = false,
    rationale = stringResource(CommonR.string.wifi_rationale_start) +
            when {
                SDK_INT >= S -> stringResource(CommonR.string.wifi_rationale_s)

                SDK_INT >= Q -> stringResource(CommonR.string.wifi_rationale_q)
                else -> stringResource(CommonR.string.wifi_rationale_old)
            } +
            if (SDK_INT >= Q) {
                stringResource(CommonR.string.wifi_rationale_only_way)
            } else "",
    disabledInfo = stringResource(CommonR.string.wifi_disabled_start) +
            when {
                SDK_INT >= S -> stringResource(CommonR.string.wifi_disabled_s)
                SDK_INT >= Q -> stringResource(CommonR.string.wifi_disabled_q)
                else -> ""
            } +
            stringResource(CommonR.string.wifi_disabled_end),
    isSettingsOnScreen = isSettingsOnScreen,
    onDenied = onDenied,
)
