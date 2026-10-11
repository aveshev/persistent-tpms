package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.TONES
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.AlertsSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * The settings of the tyres' alerts, see docs/alerts.md. [openAlertSound] opens the page choosing
 * the sound, [AlertSoundSettings].
 */
@Composable
public fun AlertsSettings(openAlertSound: () -> Unit, modifier: Modifier = Modifier): Unit = AlertsSettings(
    openAlertSound,
    modifier,
    viewModel { Bindings.featureBackgroundInternal.alertsSettingsViewModel() },
)

@Composable
internal fun AlertsSettings(
    openAlertSound: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsSettingsViewModel,
) {
    val alertSound by viewModel.alertSound.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()
    AlertsSettings(
        alertSound = alertSound,
        onAlertSoundOn = viewModel::setAlertSoundOn,
        openAlertSound = openAlertSound,
        vibrate = vibrate,
        onVibrate = { viewModel.vibrate.value = it },
        modifier = modifier,
    )
}

@Composable
private fun AlertsSettings(
    alertSound: AlertSound,
    onAlertSoundOn: (Boolean) -> Unit,
    openAlertSound: () -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier) {
    SwitchNavigationSettingsItem(
        headline = "Alert sound",
        checked = alertSound != NONE,
        onCheckedChange = onAlertSoundOn,
        onClick = openAlertSound,
        supporting = listOf(
            AnnotatedString(
                when (alertSound) {
                    SPEECH -> "Speech"
                    TONES -> "Tones"
                    NONE -> "Off"
                }
            )
        ),
        modifier = Modifier.testTag(AlertsSettingsTags.alertSound),
    )
    SwitchSettingsItem(
        headline = "Vibrate",
        checked = vibrate,
        onCheckedChange = onVibrate,
        supporting = "Along with the alert sound, harder for a critical alert",
        modifier = Modifier.testTag(AlertsSettingsTags.vibrate),
    )
}

@Suppress("ConstPropertyName")
internal object AlertsSettingsTags {
    const val alertSound = "AlertsSettingsTags_alertSound"
    const val alertSoundOption = "AlertsSettingsTags_alertSoundOption"
    const val vibrate = "AlertsSettingsTags_vibrate"
}

@Preview
@Composable
private fun AlertsSettingsPreview() = AlertsSettings(
    alertSound = SPEECH,
    onAlertSoundOn = {},
    openAlertSound = {},
    vibrate = false,
    onVibrate = {},
)

@Preview
@Composable
private fun AlertsSettingsOffPreview() = AlertsSettings(
    alertSound = NONE,
    onAlertSoundOn = {},
    openAlertSound = {},
    vibrate = true,
    onVibrate = {},
)
