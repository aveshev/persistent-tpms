package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsSectionNote
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * The page opened from the "Sensor readings text size" item of [DisplaySettings], like the system's font size
 * page: sample readouts, in the units chosen, above a slider stepping through [TEXT_SCALES]
 */
@Composable
public fun TextSize(modifier: Modifier = Modifier): Unit =
    TextSize(modifier, viewModel { TyreDisplaySettingsViewModel() })

@Composable
internal fun TextSize(
    modifier: Modifier = Modifier,
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val scale by viewModel.readoutTextScale.collectAsState()
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    TextSize(
        scale = scale,
        onScale = { viewModel.readoutTextScale.value = it },
        pressureUnit = pressureUnit,
        temperatureUnit = temperatureUnit,
        showTimeSinceUpdate = showTimeSinceUpdate,
        modifier = modifier,
    )
}

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSize(
    scale: Float,
    onScale: (Float) -> Unit,
    pressureUnit: PressureUnit,
    temperatureUnit: TemperatureUnit,
    showTimeSinceUpdate: Boolean,
    modifier: Modifier = Modifier,
) = Column(modifier.padding(top = 16.dp)) {
    // A front left and a front right readout, against their tyre like on the main screen
    val samples = remember(pressureUnit, temperatureUnit) {
        val timestamp = now()
        listOf(
            SensorLocation.FRONT_LEFT to sample(2.4f, 21f, timestamp, pressureUnit, temperatureUnit),
            SensorLocation.FRONT_RIGHT to sample(2.3f, 23f, timestamp, pressureUnit, temperatureUnit),
        )
    }
    val density = LocalDensity.current

    @Composable
    fun Samples(sampleScale: Float, modifier: Modifier = Modifier) = CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * sampleScale)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            samples.forEach { (location, state) ->
                TyreStat(Location.Wheel(location), state, showTimeSinceUpdate = showTimeSinceUpdate)
            }
        }
    }

    val index = TEXT_SCALES.indices.minBy { (TEXT_SCALES[it] - scale).absoluteValue }
    SettingsGroup {
        SettingsItem {
            // As tall as the largest size takes, so the slider stays put while it's dragged
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                Samples(TEXT_SCALES.last(), Modifier.alpha(0f))
                Samples(TEXT_SCALES[index])
            }
        }
        SettingsItem {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { onScale(TEXT_SCALES[it.roundToInt()]) },
                        valueRange = 0f..TEXT_SCALES.lastIndex.toFloat(),
                        // The notches between both ends
                        steps = TEXT_SCALES.size - 2,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .semantics { stateDescription = scale.textSizeLabel }
                            .testTag(TyreDisplaySettingsTags.textSizeSlider),
                    )
                    Text("A", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    TEXT_SCALES[index].textSizeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    @Suppress("MaxLineLength")
    SettingsSectionNote(
        "Applies only to sensor readouts, on top of the system font size. If the chosen font size does not fit on the screen, it will be reduced to fit.",
        Modifier.padding(top = 8.dp),
    )
}

private fun sample(
    bar: Float,
    celsius: Float,
    timestamp: Double,
    pressureUnit: PressureUnit,
    temperatureUnit: TemperatureUnit,
) = State.Detected(timestamp, 0, bar.bar, pressureUnit, celsius.celsius, temperatureUnit)

/** The readouts' text sizes offered, the same steps as the system's font size from Android 14 */
@Suppress("MagicNumber")
private val TEXT_SCALES = listOf(.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)

/** How a text size reads in the settings: "Default", or a percentage */
@Suppress("MagicNumber")
internal val Float.textSizeLabel: String
    get() = if (this == 1f) "Default" else "${(this * 100).roundToInt()}%"

@Preview
@Composable
internal fun TextSizePreview() {
    TextSize(
        scale = 1.3f,
        onScale = {},
        pressureUnit = PressureUnit.BAR,
        temperatureUnit = TemperatureUnit.CELSIUS,
        showTimeSinceUpdate = true,
    )
}
