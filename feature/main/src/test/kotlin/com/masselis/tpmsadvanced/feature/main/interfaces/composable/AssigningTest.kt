package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** What's shown around a tyre for assigning its sensor or moving it, and the legend of the main screen */
internal class AssigningTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val paparazzi = Paparazzi(theme = "android:Theme.Material3.DayNight.NoActionBar")

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
    fun assignSensorDialog() {
        snapshot { AssignSensorDialogPreview() }
    }

    @Test
    fun assignSensorDialogDetected() {
        snapshot { AssignSensorDialogDetectedPreview() }
    }

    @Test
    fun dimmedNotice() {
        snapshot { DimmedNoticePreview() }
    }

    @Test
    fun manageSensorDialog() {
        snapshot { ManageSensorDialogPreview() }
    }

    @Test
    fun moveToVehicleDialog() {
        snapshot { MoveToVehicleDialogPreview() }
    }

    @Test
    fun swapOrReplaceDialog() {
        snapshot { SwapOrReplaceDialogPreview() }
    }

    @Test
    fun numberDialog() {
        snapshot { NumberDialogPreview() }
    }

    @Test
    fun renameVehicleDialog() {
        snapshot { RenameVehicleDialogPreview() }
    }

    @Test
    fun sysgrationBadge() {
        snapshot { SysgrationBadgePreview() }
    }

    @Test
    fun tapToAssign() {
        snapshot { TapToAssignPreview() }
    }

    @Test
    fun tapToAssignDetected() {
        snapshot { TapToAssignDetectedPreview() }
    }

    @Test
    fun tyreLegend() {
        snapshot { TyreLegendPreview() }
    }
}
