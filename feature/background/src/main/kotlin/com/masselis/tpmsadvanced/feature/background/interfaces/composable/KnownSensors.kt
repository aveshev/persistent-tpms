package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The page opened from the known TPMS sensors condition: why it's the least reliable way to detect
 * the vehicle, with the condition's switch under it, like the other single switch pages
 */
@Composable
public fun KnownSensors(modifier: Modifier = Modifier): Unit =
    KnownSensors(modifier, viewModel { PersistentScanningSettingsViewModel() })

@Composable
internal fun KnownSensors(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    val knownSensor by viewModel.activateOnKnownSensor.collectAsState()
    KnownSensors(
        knownSensor = knownSensor,
        onKnownSensor = { viewModel.activateOnKnownSensor.value = it },
        modifier = modifier,
    )
}

@Composable
private fun KnownSensors(
    knownSensor: Boolean,
    onKnownSensor: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tier: AppTier = AppTier.current,
) = Column(modifier) {
    SettingsIntro(
        stringResource(
            // Told in terms of each tier's conditions
            when (tier) {
                LITE -> CommonR.string.tpms_sensors_run_on_small_batteries
                PRO -> CommonR.string.tpms_sensors_run_on_small_batteries_2
            }
        )
    )
    SettingsGroup(Modifier.padding(top = 16.dp)) {
        SwitchSettingsItem(
            headline = stringResource(
                when (tier) {
                    LITE -> CommonR.string.vehicle_tpms_sensors
                    PRO -> CommonR.string.known_tpms_sensors
                }
            ),
            checked = knownSensor,
            onCheckedChange = onKnownSensor,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.knownSensorsDetails),
        )
    }
}

@Preview
@Composable
internal fun KnownSensorsPreview() {
    KnownSensors(knownSensor = true, onKnownSensor = {})
}
