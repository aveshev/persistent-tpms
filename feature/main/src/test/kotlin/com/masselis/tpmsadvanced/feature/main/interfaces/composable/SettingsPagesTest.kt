package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

internal class SettingsPagesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material3.DayNight.NoActionBar",
        // As tall as the content, so that the end of the longer pages isn't cut
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    @Before
    fun setup() {
        // Another test of this module may have changed them, and they decide how numbers are written
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    // Shows what blinks in its first phase, as previews do, instead of the current one
    private fun snapshot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, content)
    }

    @Test
    fun debugSettings() {
        snapshot { DebugSettingsPreview() }
    }

    @Test
    fun dimDisplayDelay() {
        snapshot { DimDisplayDelayPreview() }
    }

    @Test
    fun displaySettings() {
        snapshot { DisplaySettingsPreview() }
    }

    @Test
    fun magnifierDuration() {
        snapshot { MagnifierDurationPreview() }
    }

    @Test
    fun miscSettings() {
        snapshot { MiscSettingsPreview() }
    }

    @Test
    fun pressureLossSettings() {
        snapshot { PressureLossSettingsPreview() }
    }

    @Test
    fun pressureLossSettingsOff() {
        snapshot { PressureLossSettingsOffPreview() }
    }

    @Test
    fun readingsSettings() {
        snapshot { ReadingsSettingsPreview() }
    }

    @Test
    fun sensorCalibrationSettings() {
        snapshot { SensorCalibrationSettingsPreview() }
    }

    @Test
    fun sensorCalibrationSettingsOff() {
        snapshot { SensorCalibrationSettingsOffPreview() }
    }

    @Test
    fun sensorReadingsUpdateDetails() {
        snapshot { SensorReadingsUpdateDetailsPreview() }
    }

    @Test
    fun textSize() {
        snapshot { TextSizePreview() }
    }

    @Test
    fun timeSinceUpdateDetails() {
        snapshot { TimeSinceUpdateDetailsPreview() }
    }

    @Test
    fun vehicleBatterySettings() {
        snapshot { VehicleBatterySettingsPreview() }
    }

    @Test
    fun vehicleBatterySettingsSysgration() {
        snapshot { VehicleBatterySettingsSysgrationPreview() }
    }

    @Test
    fun vehiclePressureSettingsShared() {
        snapshot { VehiclePressureSettingsSharedPreview() }
    }

    @Test
    fun vehiclePressureSettingsSeparate() {
        snapshot { VehiclePressureSettingsSeparatePreview() }
    }

    @Test
    fun vehiclePressureSettingsSpare() {
        snapshot { VehiclePressureSettingsSparePreview() }
    }

    @Test
    fun vehiclePressureSettingsTrailer() {
        snapshot { VehiclePressureSettingsTrailerPreview() }
    }

    @Test
    fun vehicleTemperatureSettings() {
        snapshot { VehicleTemperatureSettingsPreview() }
    }
}
