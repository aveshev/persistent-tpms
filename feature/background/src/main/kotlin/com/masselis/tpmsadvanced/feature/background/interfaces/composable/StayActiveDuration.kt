package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.RadioSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localizedPlural

/** The page opened from the "Stay active" item of [ActivateScanConditions] */
@Composable
public fun StayActiveDuration(modifier: Modifier = Modifier): Unit =
    StayActiveDuration(modifier, viewModel { PersistentScanningSettingsViewModel() })

@Composable
internal fun StayActiveDuration(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    val minutes by viewModel.stayActiveMinutes.collectAsState()
    StayActiveDuration(
        minutes = minutes,
        onMinutes = { viewModel.stayActiveMinutes.value = it },
        modifier = modifier,
    )
}

@Composable
private fun StayActiveDuration(
    minutes: Int,
    onMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        when (AppTier.current) {
            // Lite has no suspend scan conditions to tell about, nor charging
            LITE -> stringResource(CommonR.string.once_every_activate_condition_has_ended_for)
            PRO -> stringResource(CommonR.string.once_every_activate_condition_has_ended_for_2)
        }
    )
    SettingsSectionHeader(stringResource(CommonR.string.keep_scanning_for))
    SettingsGroup {
        STAY_ACTIVE_MINUTES.forEach { option ->
            RadioSettingsItem(
                headline = localizedPlural(CommonR.plurals.minutes, option, option),
                selected = option == minutes,
                onClick = { onMinutes(option) },
                modifier = Modifier.testTag("${PersistentScanningSettingsTags.stayActiveDuration}_$option"),
            )
        }
    }
}

@Suppress("MagicNumber")
private val STAY_ACTIVE_MINUTES = listOf(1, 2, 5, 10, 15, 30)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun StayActiveDurationPreview() {
    StayActiveDuration(minutes = 10, onMinutes = {})
}
