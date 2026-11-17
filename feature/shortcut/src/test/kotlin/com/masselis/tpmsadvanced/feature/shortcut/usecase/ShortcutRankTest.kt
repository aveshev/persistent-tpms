package com.masselis.tpmsadvanced.feature.shortcut.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.feature.shortcut.usecase.ShortcutUseCase.Companion.rankedForShortcuts
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class ShortcutRankTest {

    private val car = vehicle("car")
    private val bike = vehicle("bike")
    private val trailer = vehicle("trailer")
    private val van = vehicle("van")

    private fun vehicle(name: String) = Vehicle(
        UUID.randomUUID(),
        CAR,
        name,
        Pressure(200f),
        Pressure(300f),
        Temperature(-10f),
        Temperature(90f),
    )

    @Test
    fun `the latest read comes first`() {
        assertEquals(
            listOf(bike, car, trailer),
            listOf(car, trailer, bike).rankedForShortcuts(
                alerting = emptyMap(),
                latestReadings = mapOf(car.uuid to 100.0, bike.uuid to 200.0, trailer.uuid to 50.0),
            ),
        )
    }

    @Test
    fun `an alerting vehicle comes before the latest read`() {
        assertEquals(
            listOf(trailer, bike, car),
            listOf(car, trailer, bike).rankedForShortcuts(
                alerting = mapOf(trailer.uuid to RED),
                latestReadings = mapOf(car.uuid to 100.0, bike.uuid to 200.0, trailer.uuid to 50.0),
            ),
        )
    }

    @Test
    fun `the worst alert comes first, then the latest read`() {
        assertEquals(
            listOf(van, bike, car, trailer),
            listOf(car, trailer, bike, van).rankedForShortcuts(
                alerting = mapOf(car.uuid to RED, bike.uuid to RED, van.uuid to CRIMSON),
                latestReadings = mapOf(car.uuid to 100.0, bike.uuid to 200.0, trailer.uuid to 300.0),
            ),
        )
    }

    @Test
    fun `vehicles never read come last, by name`() {
        assertEquals(
            listOf(car, bike, trailer, van),
            listOf(van, trailer, bike, car).rankedForShortcuts(
                alerting = emptyMap(),
                latestReadings = mapOf(car.uuid to 100.0),
            ),
        )
    }
}
