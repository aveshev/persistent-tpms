package com.masselis.tpmsadvanced.interfaces

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.DARK
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.LIGHT
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.SYSTEM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.interfaces.screens.Home
import com.masselis.tpmsadvanced.interfaces.screens.Home.Companion.home
import com.masselis.tpmsadvanced.ioc.Bindings
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TakeScreenshotTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<RootActivity>()

    // The theme is kept by the app, it would carry over to the next tests
    @After
    fun tearDown() {
        Bindings.appPreferences.theme.value = SYSTEM
    }

    @Test
    fun lightModeScreenshots() {
        androidComposeTestRule.home {
            takeScreenshots(
                LIGHT,
                listOf(FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT).map { Location.Wheel(it) },
            )
        }
    }

    @Test
    fun darkModeScreenshots() {
        androidComposeTestRule.home {
            dropdownMenu {
                addVehicle {
                    setVehicleName("Motorcycle")
                    setKind(MOTORCYCLE)
                    add()
                }
            }
            takeScreenshots(DARK, listOf(FRONT, REAR).map { Location.Axle(it) })
        }
    }

    /**
     * The Play Store listing's screenshots, see the app's `copyScreenshot` task. The demo sensors
     * detected at [locations] are assigned first, so the screens show their readings.
     */
    private fun Home.takeScreenshots(theme: Theme, locations: List<Location>) {
        // Chosen like from the settings: RootActivity applies it, recreating itself, and would undo
        // a night mode set on AppCompatDelegate directly
        androidComposeTestRule.activityRule.scenario.onActivity {
            Bindings.appPreferences.theme.value = theme
        }
        androidComposeTestRule.waitForIdle()
        val prefix = when (theme) {
            LIGHT -> "light_"
            DARK -> "dark_"
            SYSTEM -> error("The screenshots need a theme which doesn't depend on the device")
        }
        locations.forEach { location ->
            wheel(location) {
                assign { assignDetected() }
                waitUntilAssigned()
            }
        }
        actionOverflow {
            settings {
                capture("${prefix}settings")
                manageSensors {
                    waitForReadings()
                    capture("${prefix}manage_sensors")
                    leave()
                }
                leave()
            }
        }
        // Back on the main screen, the demo scanner sends the assigned sensors' readings again
        waitForReadings()
        capture("${prefix}main")
    }

    private fun waitForReadings() = androidComposeTestRule.waitUntil(READINGS_TIMEOUT) {
        androidComposeTestRule.onAllNodes(hasText(NO_PRESSURE)).fetchSemanticsNodes().isEmpty()
    }

    private fun capture(name: String) = androidComposeTestRule
        .onRoot()
        .captureToImage()
        .asAndroidBitmap()
        .writeToTestStorage(name)

    private companion object {
        /** What a readout shows without a pressure, see TyreStat */
        const val NO_PRESSURE = "-.--"
        const val READINGS_TIMEOUT = 10_000L
    }
}
