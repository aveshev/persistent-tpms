package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The page opened from [TimeSinceUpdateDetails]: how often each sensor brand sends an update, and
 * how to tell a silent sensor from a broken one. Only the schedules and pressure steps measured on
 * real sensors, see RawWicarlink.pressure.
 */
@Suppress("MaxLineLength")
@Composable
public fun SensorReadingsUpdateDetails(modifier: Modifier = Modifier): Unit = Column(modifier) {
    SettingsIntro(
        stringResource(CommonR.string.sensors_from_different_manufacturers_differ_in_how)
    )
    SettingsSectionHeader(stringResource(CommonR.string.known_schedules))
    SettingsGroup {
        TextSettingsItem(
            headline = "WiCarLink",
            supporting = stringResource(CommonR.string.every_5_5_minutes_whether_the_vehicle),
        )
        TextSettingsItem(
            headline = "Pecham",
            supporting = stringResource(CommonR.string.every_minute_while_the_wheel_rotates_while),
        )
    }
    SettingsSectionHeader(stringResource(CommonR.string.when_updates_seem_to_be_missing))
    SettingsIntro(
        stringResource(CommonR.string.if_the_app_does_not_show_the)
    )
}

@Preview
@Composable
internal fun SensorReadingsUpdateDetailsPreview() {
    SensorReadingsUpdateDetails()
}
