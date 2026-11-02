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
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localizedPlural

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
        stringResource(CommonR.string.lowers_the_screen_s_brightness_on_the)
    )
    SettingsSectionHeader(stringResource(CommonR.string.dim_after))
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
    SettingsSectionHeader(stringResource(CommonR.string.brightness))
    SettingsGroup {
        SegmentedSettingsItem(
            headline = stringResource(CommonR.string.divide_brightness_by),
            options = DIVISORS,
            selected = divisor,
            onSelect = onDivisor,
            label = { "$it×" },
            modifier = Modifier.testTag(TyreDisplaySettingsTags.dimDisplayDivisor),
        )
    }
    SettingsSectionHeader(stringResource(CommonR.string.in_a_pocket))
    SettingsGroup {
        SwitchSettingsItem(
            headline = stringResource(CommonR.string.block_touches_in_a_pocket),
            supporting = stringResource(CommonR.string.turns_the_screen_black_and_ignores_touches),
            checked = pocketGuard,
            onCheckedChange = onPocketGuard,
            modifier = Modifier.testTag(TyreDisplaySettingsTags.pocketGuard),
        )
    }
}

/** "30 seconds", "1 minute", "2 minutes", for a delay in seconds */
@Suppress("MagicNumber")
internal fun Int.asDimDelay(): String = when {
    this < 60 -> localizedPlural(CommonR.plurals.seconds, this, this)
    else -> (this / 60).let { localizedPlural(CommonR.plurals.minutes, it, it) }
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
