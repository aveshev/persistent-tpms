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
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/** The page opened from the "Tap to magnify" item of [DisplaySettings] */
@Composable
public fun MagnifierDuration(modifier: Modifier = Modifier): Unit =
    MagnifierDuration(modifier, viewModel { TyreDisplaySettingsViewModel() })

@Composable
internal fun MagnifierDuration(
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val seconds by viewModel.magnifierSeconds.collectAsState()
    MagnifierDuration(
        seconds = seconds,
        onSeconds = { viewModel.magnifierSeconds.value = it },
        modifier = modifier,
    )
}

@Composable
private fun MagnifierDuration(
    seconds: Int,
    onSeconds: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Tap near a tyre on the main screen to temporarily show its readings in a large font size, easy to read even on a rough road."
    )
    SettingsSectionHeader("Show for")
    SettingsGroup {
        MAGNIFIER_SECONDS.forEach { option ->
            RadioSettingsItem(
                headline = "$option seconds",
                selected = option == seconds,
                onClick = { onSeconds(option) },
                modifier = Modifier.testTag("${TyreDisplaySettingsTags.magnifierDuration}_$option"),
            )
        }
    }
}

@Suppress("MagicNumber")
private val MAGNIFIER_SECONDS = listOf(2, 3, 5, 7, 10)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun MagnifierDurationPreview() {
    MagnifierDuration(seconds = 5, onSeconds = {})
}
