package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The page opened from [TimeSinceUpdateDetails]: why sensors update at different rates, a link to
 * each model's schedule and pressure steps on the website (docs/sensors.md), and how to tell a silent
 * sensor from a broken one.
 */
@Suppress("MaxLineLength")
@Composable
public fun SensorReadingsUpdateDetails(modifier: Modifier = Modifier): Unit = Column(modifier) {
    SettingsIntro(
        stringResource(CommonR.string.sensors_from_different_manufacturers_differ_in_how)
    )
    SettingsSectionHeader(stringResource(CommonR.string.known_schedules))
    // On the website, where each model's description is updated without a new release
    val uriHandler = LocalUriHandler.current
    stringResource(CommonR.string.sensor_models_url).also { url ->
        SettingsGroup {
            TextSettingsItem(
                headline = stringResource(CommonR.string.sensor_models),
                supporting = stringResource(CommonR.string.each_model_s_updates_and_pressure_steps_on),
                onClick = { uriHandler.openUri(url) },
            )
        }
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
