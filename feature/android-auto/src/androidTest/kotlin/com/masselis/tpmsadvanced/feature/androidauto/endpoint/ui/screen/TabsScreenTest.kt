package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.model.Row
import androidx.car.app.model.ListTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ContributesTo(AppScope::class)
internal interface VehicleDatabaseExtractor {
    val vehicleDatabase: VehicleDatabase
}

// The vehicle switch only opens once 2+ vehicles exist. Each @Test starts from the single "My car"
// vehicle seeded on database creation (Android Test Orchestrator clears app data between tests),
// so these insert extra vehicles into the real on-device database rather than mocking
// TabScreenViewModel's use cases, exercising the same Metro-provided AppGraph as
// VehicleTemplateTest.
internal class TabsScreenTest {

    private lateinit var vehicleDatabase: VehicleDatabase

    @Before
    fun setup() {
        vehicleDatabase = (appGraph as VehicleDatabaseExtractor).vehicleDatabase
    }

    private inline fun <reified T> runOnMain(crossinline block: () -> T): T {
        var result: T? = null
        getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun buildScreen(): Screen = runOnMain {
        TabsScreen(TestCarContext.createCarContext(getApplicationContext()))
    }

    /**
     * TabScreenViewModel builds its state asynchronously from a coroutine flow, so the first
     * templates read off a freshly built screen may still be loading, or show the vehicle that was
     * current before the test inserted one
     */
    private fun awaitVehicleRow(screen: Screen, name: String, timeoutMs: Long = 5_000): List<Row> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            runOnMain { screen.onGetTemplate() }
                .let { it as? ListTemplate }
                ?.singleList
                ?.items
                ?.map { assertIs<Row>(it) }
                ?.takeIf { it.first().title.toString() == name }
                ?.let { return it }
            Thread.sleep(20)
        }
        error("Timed out waiting for $name's list")
    }

    @Test
    fun aSingleVehicleIsNamedWithoutASwitch() {
        val rows = awaitVehicleRow(buildScreen(), "My car")

        // The vehicle, its 4 tyres, then the alert speech's item, there while the alerts make sound
        assertEquals(6, rows.size)
        assertEquals(false, rows.first().isBrowsable)
        assertEquals("Alert speech", rows.last().title.toString())
    }

    @Test
    fun withOtherVehiclesTheCurrentOneOpensTheSwitch() {
        val otherCar = mockVehicle()
        runBlocking { vehicleDatabase.insert(otherCar.uuid, otherCar.kind, otherCar.name, true) }

        // "MOCK" was inserted as current, so the list is its own, not "My car"'s
        val vehicleRow = awaitVehicleRow(buildScreen(), otherCar.name).first()

        assertEquals(true, vehicleRow.isBrowsable)
        assertEquals("Change vehicle", vehicleRow.texts.single().toString())
    }
}
