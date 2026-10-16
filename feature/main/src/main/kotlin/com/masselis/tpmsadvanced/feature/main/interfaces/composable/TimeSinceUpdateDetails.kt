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
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/** The page opened from the "Time since last update" item of [ReadingsSettings] */
@Composable
public fun TimeSinceUpdateDetails(modifier: Modifier = Modifier): Unit =
    TimeSinceUpdateDetails(modifier, viewModel { TyreDisplaySettingsViewModel() })

@Composable
internal fun TimeSinceUpdateDetails(
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    TimeSinceUpdateDetails(
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        modifier = modifier,
    )
}

@Composable
private fun TimeSinceUpdateDetails(
    showTimeSinceUpdate: Boolean,
    onShowTimeSinceUpdate: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Many tyre sensors only send an update when the pressure changes. This can mean no update reaching your phone for hours or even days, especially from the non-driven tyres (driven tyres heat up at the start of a ride and therefore update more often).\n\nA tyre that is quickly losing air changes pressure, so its sensor updates often and you get alerted: in that sense, no update is good news. Unfortunately, a very slow leak or a faulty sensor can look exactly like that \"all good\" case.\n\nIf a sensor hasn't updated for a long while, you can briefly remove it while the app is scanning, and put it back once it shows zero pressure. If the sensor still works and its battery isn't dead, this refreshes the displayed values, at the cost of a little air."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Time since last update",
            checked = showTimeSinceUpdate,
            onCheckedChange = onShowTimeSinceUpdate,
            modifier = Modifier.testTag(TyreDisplaySettingsTags.timeSinceUpdateDetails),
        )
    }
}

@Preview
@Composable
internal fun TimeSinceUpdateDetailsPreview() {
    TimeSinceUpdateDetails(showTimeSinceUpdate = true, onShowTimeSinceUpdate = {})
}
