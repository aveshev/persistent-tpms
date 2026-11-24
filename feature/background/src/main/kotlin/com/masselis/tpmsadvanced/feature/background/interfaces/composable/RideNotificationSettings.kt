package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Companion.DEFAULT_RIDE_ABSENT_MINUTES
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Companion.DEFAULT_RIDE_STALE_MINUTES
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.RideNotificationSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.RideNotificationSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The "Going for a ride?" notification's switch, on the background monitoring page of both tiers. It
 * fires on the persistent scan's own activate conditions, and on a known sensor heard: nothing to set
 * up. Without persistent scanning it runs the service by itself, which then only watches for the
 * vehicle, so it needs the same permissions.
 */
@Composable
public fun RideNotificationSettings(modifier: Modifier = Modifier): Unit =
    RideNotificationSettings(modifier, viewModel { RideNotificationSettingsViewModel() })

@Composable
internal fun RideNotificationSettings(
    modifier: Modifier = Modifier,
    viewModel: RideNotificationSettingsViewModel = viewModel { RideNotificationSettingsViewModel() },
) {
    val rideNotification by viewModel.rideNotification.collectAsState()
    // The switch only turns on once everything the service needs was granted; refusing leaves it off
    val permissions = rememberMonitoringPermissions(
        confirmBeforeStart = false,
        onGranted = viewModel::enable,
    )
    RideNotificationSettings(
        rideNotification = rideNotification,
        onRideNotification = { enabled -> if (enabled) permissions.request() else viewModel.disable() },
        modifier = modifier,
    )
}

@Composable
private fun RideNotificationSettings(
    rideNotification: Boolean,
    onRideNotification: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tier: AppTier = AppTier.current,
) = Column(modifier) {
    SettingsSectionHeader(stringResource(CommonR.string.ride_notification))
    SettingsGroup {
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.ride_notification),
            supporting = stringResource(
                // Lite's page lists the vehicle detection options above it
                when (tier) {
                    LITE -> CommonR.string.prompts_going_for_a_ride_lite
                    PRO -> CommonR.string.prompts_going_for_a_ride_when_a_scan
                }
            ),
            checked = rideNotification,
            onCheckedChange = onRideNotification,
            modifier = Modifier.testTag(RideNotificationSettingsTags.rideNotification),
        )
    }
}

/**
 * The ride notification's times, debug options shortening them for trying it: how long a trigger
 * must stay unheard, and how long an unopened notification stays current
 */
@Composable
public fun RideNotificationTimesSettingsItems(modifier: Modifier = Modifier): Unit =
    RideNotificationTimesSettingsItems(modifier, viewModel { RideNotificationSettingsViewModel() })

@Composable
internal fun RideNotificationTimesSettingsItems(
    modifier: Modifier = Modifier,
    viewModel: RideNotificationSettingsViewModel = viewModel { RideNotificationSettingsViewModel() },
) = Column(modifier) {
    val absent by viewModel.absentMinutes.collectAsState()
    val stale by viewModel.staleMinutes.collectAsState()
    MinutesSettingsItem(
        headline = "Ride notification: time away",
        supporting = "${absent.asMinutes()} unheard before a sensor, device or beacon heard again sends it",
        minutes = absent,
        options = listOf(DEFAULT_RIDE_ABSENT_MINUTES, SHORT_MINUTES),
        onMinutes = { viewModel.absentMinutes.value = it },
        tag = RideNotificationSettingsTags.absent,
    )
    MinutesSettingsItem(
        headline = "Ride notification: replace",
        supporting = "An unopened one is replaced ${stale.asMinutes()} after the last trigger, or removed if " +
            "nothing fires",
        minutes = stale,
        options = listOf(DEFAULT_RIDE_STALE_MINUTES, SHORT_MINUTES),
        onMinutes = { viewModel.staleMinutes.value = it },
        tag = RideNotificationSettingsTags.stale,
    )
}

@Suppress("LongParameterList")
@Composable
private fun MinutesSettingsItem(
    headline: String,
    supporting: String,
    minutes: Int,
    options: List<Int>,
    onMinutes: (Int) -> Unit,
    tag: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextSettingsItem(
            headline = headline,
            supporting = supporting,
            onClick = { expanded = true },
            modifier = Modifier.testTag(tag),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.asMinutes()) },
                    onClick = {
                        onMinutes(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == minutes) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${tag}_$option"),
                )
            }
        }
    }
}

@Suppress("MagicNumber")
private fun Int.asMinutes() = if (this % 60 == 0) "${this / 60} h" else "$this min"

private const val SHORT_MINUTES = 2

@Suppress("ConstPropertyName")
internal object RideNotificationSettingsTags {
    const val rideNotification = "RideNotificationSettingsTags_rideNotification"
    const val absent = "RideNotificationSettingsTags_absent"
    const val stale = "RideNotificationSettingsTags_stale"
}

@Preview
@Composable
internal fun RideNotificationSettingsPreview() {
    RideNotificationSettings(
        rideNotification = true,
        onRideNotification = {},
    )
}
