package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.RadioSettingsItem
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/** The page opened from the "Dim when idle" item of [DisplaySettings] */
@Composable
public fun DimDisplayDelay(modifier: Modifier = Modifier): Unit =
    DimDisplayDelay(modifier, viewModel { TyreDisplaySettingsViewModel() })

@Composable
internal fun DimDisplayDelay(
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val seconds by viewModel.dimDisplaySeconds.collectAsState()
    val divisor by viewModel.dimDisplayDivisor.collectAsState()
    val pocketGuard by viewModel.pocketGuard.collectAsState()
    DimDisplayDelay(
        seconds = seconds,
        onSeconds = { viewModel.dimDisplaySeconds.value = it },
        divisor = divisor,
        onDivisor = { viewModel.dimDisplayDivisor.value = it },
        pocketGuard = pocketGuard,
        onPocketGuard = { viewModel.pocketGuard.value = it },
        modifier = modifier,
    )
}

@Composable
private fun DimDisplayDelay(
    seconds: Int,
    onSeconds: (Int) -> Unit,
    divisor: Int,
    onDivisor: (Int) -> Unit,
    pocketGuard: Boolean,
    onPocketGuard: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Lowers the screen's brightness on the main screen once nothing new was alerted or warned about for a while. A red or critical alert going on keeps it bright, a new alert or warning brings it back up. When dimmed, a first touch only brightens the screen, lift your finger and touch again to use the app."
    )
    SettingsSectionHeader("Dim after")
    SettingsGroup {
        DIM_DISPLAY_SECONDS.forEach { option ->
            RadioSettingsItem(
                headline = option.asDimDelay(),
                selected = option == seconds,
                onClick = { onSeconds(option) },
                modifier = Modifier.testTag("${TyreDisplaySettingsTags.dimDisplayDelay}_$option"),
            )
        }
    }
    SettingsSectionHeader("Brightness")
    SettingsGroup {
        SegmentedSettingsItem(
            headline = "Divide brightness by",
            options = DIVISORS,
            selected = divisor,
            onSelect = onDivisor,
            label = { "$it×" },
            modifier = Modifier.testTag(TyreDisplaySettingsTags.dimDisplayDivisor),
        )
    }
    SettingsSectionHeader("In a pocket")
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Block touches in a pocket",
            supporting = "Turns the screen black and ignores touches",
            checked = pocketGuard,
            onCheckedChange = onPocketGuard,
            modifier = Modifier.testTag(TyreDisplaySettingsTags.pocketGuard),
        )
    }
}

/** "30 seconds", "1 minute", "2 minutes", for a delay in seconds */
@Suppress("MagicNumber")
internal fun Int.asDimDelay(): String = when {
    this < 60 -> "$this seconds"
    this == 60 -> "1 minute"
    else -> "${this / 60} minutes"
}

@Suppress("MagicNumber")
private val DIVISORS = listOf(50, 25, 10)

@Suppress("MagicNumber")
private val DIM_DISPLAY_SECONDS = listOf(15, 30, 60, 120, 300)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun DimDisplayDelayPreview() {
    DimDisplayDelay(seconds = 60, onSeconds = {}, divisor = 50, onDivisor = {}, pocketGuard = true, onPocketGuard = {})
}
