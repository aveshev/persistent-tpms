package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.AlertsSilencedPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.OtherAppPersistentScanningAlertPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SilenceAlertsPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SilenceCriticalAlertsPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.UnexpectedStopAlertPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.UnexpectedStopAlertUnknownPreview
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

internal class AlertsPagesTest {

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
        // They decide how numbers and times are written
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    // Shows what blinks in its first phase, as previews do, instead of the current one
    private fun snapshot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, content)
    }

    @Test
    fun alertSoundSettings() {
        snapshot { AlertSoundSettingsPreview() }
    }

    @Test
    fun alertsSettings() {
        snapshot { AlertsSettingsPreview() }
    }

    @Test
    fun alertsSettingsOff() {
        snapshot { AlertsSettingsOffPreview() }
    }

    @Test
    fun otherAppPersistentScanningAlert() {
        snapshot { OtherAppPersistentScanningAlertPreview() }
    }

    @Test
    fun unexpectedStopAlert() {
        snapshot { UnexpectedStopAlertPreview() }
    }

    @Test
    fun unexpectedStopAlertUnknown() {
        snapshot { UnexpectedStopAlertUnknownPreview() }
    }

    @Test
    fun silenceAlerts() {
        snapshot { SilenceAlertsPreview() }
    }

    @Test
    fun silenceCriticalAlerts() {
        snapshot { SilenceCriticalAlertsPreview() }
    }

    @Test
    fun alertsSilenced() {
        snapshot { AlertsSilencedPreview() }
    }
}
