package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.State
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.Locale.US
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// Runs on a real device/emulator rather than as a local Robolectric test because
// TyreRow's icon tinting reads core:ui's LightColors/DarkColors, which are backed by a
// process-wide appContext only ever populated by App Startup running this module's own
// androidTest-only com.masselis.tpmsadvanced.ioc.AppGraph
@RunWith(AndroidJUnit4::class)
internal class VehicleTemplateTest {

    private lateinit var screen: Screen
    private lateinit var defaultLocale: Locale

    // androidx.car.app.CarContext's constructor registers a LifecycleObserver, which enforces
    // being called on the main thread, unlike the instrumentation test thread this class runs on.
    private inline fun <reified T> runOnMain(crossinline block: () -> T): T {
        var result: T? = null
        getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Before
    fun setup() {
        // Pressure/Temperature.string() formats with the device's default locale (e.g. "3,2b" on
        // a comma-decimal locale), pin it so the assertions below are deterministic.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(US)
        screen = runOnMain {
            TestCarContext
                .createCarContext(ApplicationProvider.getApplicationContext())
                .let { carContext ->
                    object : Screen(carContext) {
                        override fun onGetTemplate(): Template = error("Not exercised by this test")
                    }
                }
        }
    }

    private fun singleTyreRow(
        iconState: TyreIconStateFlow.State,
        statsState: TyreStatsStateFlow.State,
        // The readings below are dated 0, so the age is the time since then
        now: Double = 0.0,
    ) = Vehicle.Kind.CAR.locations
        .associateWith { location ->
            if (location == Wheel(FRONT_LEFT)) iconState to statsState
            else TyreIconStateFlow.State.NotDetected to TyreStatsStateFlow.State.NotDetected
        }
        .let { tyres -> State.Tabs.Tab.Displayed(mockVehicle(), tyres) }
        .let { tab -> runOnMain { context(screen) { VehicleTemplate(tab, now = now) } } }
        .singleList!!
        .items
        // The vehicle, then its 4 tyres
        .also { items -> assertEquals(5, items.size) }
        .map { item -> assertIs<Row>(item); item }
        .first { item -> item.title.toString() == "Front left" }

    @Test
    fun alertingTyreShowsItsLocationAndFormattedPressureWithTemperature() {
        singleTyreRow(
            TyreIconStateFlow.State.Alerting(),
            TyreStatsStateFlow.State.Detected(
                0.0,
                0,
                3.2f.bar,
                BAR,
                60f.celsius,
                CELSIUS,
                levels = mapOf(AlertClass.PRESSURE to AlertLevel.RED),
            ),
        ).also { item ->
            assertEquals("Front left", item.title.toString())
            assertEquals("3.20 bar · 60°C · <1 min", item.texts.first().toString())
            assertNotNull(item.image)
        }
    }

    @Test
    fun normalTyreShowsItsLocationAndFormattedPressureWithTemperature() {
        singleTyreRow(
            TyreIconStateFlow.State.Normal.BlueToGreen(Fraction(0.5f)),
            TyreStatsStateFlow.State.Detected(0.0, 0, 2.4f.bar, BAR, 22f.celsius, CELSIUS),
        ).also { item ->
            assertEquals("Front left", item.title.toString())
            assertEquals("2.40 bar · 22°C · <1 min", item.texts.first().toString())
            assertNotNull(item.image)
        }
    }

    @Test
    fun aReadingShowsTheTimeSinceItsUpdateAsTheMainScreenDoes() {
        listOf(
            12 * 60.0 to "12 min",
            3 * 3_600.0 to "3 hours",
            2 * 86_400.0 to "2 days",
        ).forEach { (age, text) ->
            singleTyreRow(
                TyreIconStateFlow.State.Normal.BlueToGreen(Fraction(0.5f)),
                TyreStatsStateFlow.State.Detected(0.0, 0, 2.4f.bar, BAR, 22f.celsius, CELSIUS),
                now = age,
            ).also { item -> assertEquals("2.40 bar · 22°C · $text", item.texts.first().toString()) }
        }
    }

    @Test
    fun aLeakAndALowBatteryShowOnASecondLine() {
        singleTyreRow(
            TyreIconStateFlow.State.Alerting(),
            TyreStatsStateFlow.State.Detected(
                0.0,
                0,
                2.4f.bar,
                BAR,
                22f.celsius,
                CELSIUS,
                batteryVoltage = Voltage(2.5f),
                levels = mapOf(AlertClass.SENSOR_ALARM to AlertLevel.AMBER, AlertClass.BATTERY to AlertLevel.RED),
            ),
        ).also { item ->
            assertEquals(2, item.texts.size)
            assertEquals("Leak? · 2.5 V", item.texts[1].toString())
        }
    }

    @Test
    fun aCriticalPressureIsFollowedByItsLabel() {
        singleTyreRow(
            TyreIconStateFlow.State.Alerting(),
            TyreStatsStateFlow.State.Detected(
                0.0,
                0,
                1.2f.bar,
                BAR,
                22f.celsius,
                CELSIUS,
                levels = mapOf(AlertClass.PRESSURE to AlertLevel.CRIMSON),
            ),
        ).also { item -> assertEquals("1.20 bar LOW! · 22°C · <1 min", item.texts.single().toString()) }
    }

    @Test
    fun notDetectedTyreShowsItsLocationAndAPlaceholderText() {
        singleTyreRow(
            TyreIconStateFlow.State.NotDetected,
            TyreStatsStateFlow.State.NotDetected,
        ).also { item ->
            assertEquals("Front left", item.title.toString())
            assertEquals("No reading", item.texts.first().toString())
            assertNotNull(item.image)
        }
    }

    @Test
    fun detectionIssueTyreShowsItsLocationAndAPlaceholderText() {
        singleTyreRow(
            TyreIconStateFlow.State.DetectionIssue,
            TyreStatsStateFlow.State.NotDetected,
        ).also { item ->
            assertEquals("Front left", item.title.toString())
            assertEquals("No reading", item.texts.first().toString())
            assertNotNull(item.image)
        }
    }

    @Test
    fun theVehicleThenOneRowPerTyreLocation() {
        Vehicle.Kind.CAR.locations
            .associateWith { TyreIconStateFlow.State.Alerting() to TyreStatsStateFlow.State.NotDetected }
            .let { tyres -> State.Tabs.Tab.Displayed(mockVehicle(), tyres) }
            .let { tab -> runOnMain { context(screen) { VehicleTemplate(tab) } } }
            .singleList!!
            .items
            .map { assertIs<Row>(it) }
            .map { it.title.toString() }
            .also { titles ->
                assertEquals(listOf("MOCK", "Front left", "Front right", "Rear left", "Rear right"), titles)
            }
    }

    @Test
    fun theVehicleRowOpensTheSwitchOnlyWhenGivenOne() {
        Vehicle.Kind.CAR.locations
            .associateWith { TyreIconStateFlow.State.NotDetected to TyreStatsStateFlow.State.NotDetected }
            .let { tyres -> State.Tabs.Tab.Displayed(mockVehicle(), tyres) }
            .let { tab ->
                listOf(null, {}).map { onChangeVehicle ->
                    runOnMain { context(screen) { VehicleTemplate(tab, onChangeVehicle = onChangeVehicle) } }
                        .singleList!!
                        .items
                        .first()
                        .let { assertIs<Row>(it) }
                }
            }
            .also { (alone, withOthers) ->
                assertEquals(false, alone.isBrowsable)
                assertEquals(emptyList(), alone.texts)
                assertEquals(true, withOthers.isBrowsable)
                assertEquals("Change vehicle", withOthers.texts.single().toString())
            }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }
}
