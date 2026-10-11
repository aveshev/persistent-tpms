package com.masselis.tpmsadvanced.feature.background.interfaces.composable

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
import com.masselis.tpmsadvanced.core.ui.RadioSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.TONES
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.AlertsSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/** The page opened from the "Alert sound" item of [AlertsSettings], choosing the sound once it's on */
@Composable
public fun AlertSoundSettings(modifier: Modifier = Modifier): Unit = AlertSoundSettings(
    modifier,
    viewModel { Bindings.featureBackgroundInternal.alertsSettingsViewModel() },
)

@Composable
internal fun AlertSoundSettings(
    modifier: Modifier = Modifier,
    viewModel: AlertsSettingsViewModel,
) {
    val alertSound by viewModel.alertSound.collectAsState()
    AlertSoundSettings(alertSound, { viewModel.alertSound.value = it }, modifier)
}

@Composable
private fun AlertSoundSettings(
    alertSound: AlertSound,
    onAlertSound: (AlertSound) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Plays in addition to the red and critical alerts' notifications, and repeats while those alerts stay active: every 10 minutes for red, every 20 seconds at first for critical, then every 10 minutes.\n\nStopping or pausing monitoring stops the repeats too, until a new alerting sensor update is received.",
        Modifier.padding(bottom = 16.dp),
    )
    SettingsGroup {
        RadioSettingsItem(
            headline = "Speech",
            supporting = "Says \"Tyre pressure\", \"Tyre hot\" or \"Sensor battery\" out loud",
            selected = alertSound == SPEECH,
            onClick = { onAlertSound(SPEECH) },
            modifier = Modifier.testTag("${AlertsSettingsTags.alertSoundOption}_$SPEECH"),
        )
        RadioSettingsItem(
            headline = "Tones",
            supporting = "A tone pattern, faster and higher pitched for a critical alert",
            selected = alertSound == TONES,
            onClick = { onAlertSound(TONES) },
            modifier = Modifier.testTag("${AlertsSettingsTags.alertSoundOption}_$TONES"),
        )
    }
}

@Preview
@Composable
private fun AlertSoundSettingsPreview() = AlertSoundSettings(
    alertSound = TONES,
    onAlertSound = {},
)
