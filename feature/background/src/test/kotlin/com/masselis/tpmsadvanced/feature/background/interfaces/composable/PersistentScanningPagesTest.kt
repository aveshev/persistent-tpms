package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.test.mockLocalizedAsEnglish
import io.mockk.unmockkAll
import org.junit.After
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.DetectedActivitiesIndicatorPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.PersistentScanningBellPreview
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.PersistentScanningMenuItemPreview
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

internal class PersistentScanningPagesTest {

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
        mockLocalizedAsEnglish()
        // They decide how numbers and times are written
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() = unmockkAll()

    // Shows what blinks in its first phase, as previews do, instead of the current one
    private fun snapshot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, content)
    }

    @Test
    fun alwaysOnWarning() {
        snapshot { AlwaysOnWarningPreview() }
    }

    @Test
    fun beaconScanList() {
        snapshot { BeaconScanListPreview() }
    }

    @Test
    fun beaconScanSearching() {
        snapshot { BeaconScanSearchingPreview() }
    }

    @Test
    fun beaconsBluetoothOff() {
        snapshot { BeaconsBluetoothOffPreview() }
    }

    @Test
    fun beaconsEmpty() {
        snapshot { BeaconsEmptyPreview() }
    }

    @Test
    fun beaconsList() {
        snapshot { BeaconsListPreview() }
    }

    @Test
    fun currentWifiExceptionItem() {
        snapshot { CurrentWifiExceptionItemPreview() }
    }

    @Test
    fun detectedActivitiesIndicator() {
        snapshot { DetectedActivitiesIndicatorPreview() }
    }

    @Test
    fun exceptedWifis() {
        snapshot { ExceptedWifisPreview() }
    }

    @Test
    fun litePersistentScanningSettings() {
        snapshot { LitePersistentScanningSettingsPreview() }
    }

    @Test
    fun persistentScanningBell() {
        snapshot { PersistentScanningBellPreview() }
    }

    @Test
    fun persistentScanningDetails() {
        snapshot { PersistentScanningDetailsPreview() }
    }

    @Test
    fun persistentScanningMenuItem() {
        snapshot { PersistentScanningMenuItemPreview() }
    }

    @Test
    fun persistentScanningSettings() {
        snapshot { PersistentScanningSettingsPreview() }
    }

    @Test
    fun stayActiveDuration() {
        snapshot { StayActiveDurationPreview() }
    }
}
