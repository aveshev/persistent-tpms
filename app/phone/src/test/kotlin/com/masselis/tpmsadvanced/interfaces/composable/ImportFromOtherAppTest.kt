package com.masselis.tpmsadvanced.interfaces.composable

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

internal class ImportFromOtherAppTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val paparazzi = Paparazzi(theme = "android:Theme.Material3.DayNight.NoActionBar")

    @Before
    fun setup() {
        // The offers tell when the latest readings of each app are from
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun snapshot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, content)
    }

    @Test
    fun emptyAppOffer() {
        snapshot { EmptyAppOfferPreview() }
    }

    @Test
    fun replacingOffer() {
        snapshot { ReplacingOfferPreview() }
    }
}
