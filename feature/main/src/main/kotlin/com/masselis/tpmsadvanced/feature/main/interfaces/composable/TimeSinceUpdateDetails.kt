package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/** The page opened from the "Time since last update" item of [ReadingsSettings] */
@Composable
public fun TimeSinceUpdateDetails(
    openSensorReadingsUpdateDetails: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = TimeSinceUpdateDetails(
    openSensorReadingsUpdateDetails,
    modifier,
    viewModel { TyreDisplaySettingsViewModel() },
)

@Composable
internal fun TimeSinceUpdateDetails(
    openSensorReadingsUpdateDetails: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    TimeSinceUpdateDetails(
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        openSensorReadingsUpdateDetails = openSensorReadingsUpdateDetails,
        modifier = modifier,
    )
}

@Composable
private fun TimeSinceUpdateDetails(
    showTimeSinceUpdate: Boolean,
    onShowTimeSinceUpdate: (Boolean) -> Unit,
    openSensorReadingsUpdateDetails: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        stringResource(CommonR.string.most_tpms_sensors_send_updates_at_regular)
    )
    SettingsGroup(Modifier.padding(top = 16.dp)) {
        TextSettingsItem(
            headline = stringResource(CommonR.string.sensor_readings_update_details),
            supporting = stringResource(CommonR.string.how_often_each_sensor_updates_and_how),
            onClick = openSensorReadingsUpdateDetails,
        )
    }
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.time_since_last_update),
            checked = showTimeSinceUpdate,
            onCheckedChange = onShowTimeSinceUpdate,
            modifier = Modifier.testTag(TyreDisplaySettingsTags.timeSinceUpdateDetails),
        )
    }
}

@Preview
@Composable
internal fun TimeSinceUpdateDetailsPreview() {
    TimeSinceUpdateDetails(
        showTimeSinceUpdate = true,
        onShowTimeSinceUpdate = {},
        openSensorReadingsUpdateDetails = {},
    )
}
