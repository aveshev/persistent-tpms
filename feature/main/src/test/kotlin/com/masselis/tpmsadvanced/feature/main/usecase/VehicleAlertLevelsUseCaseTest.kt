package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.previewVehicle
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit.SECONDS

@OptIn(ExperimentalCoroutinesApi::class)
internal class VehicleAlertLevelsUseCaseTest {

    private lateinit var vehicles: MutableStateFlow<List<Vehicle>>
    private lateinit var tyres: Map<UUID, List<MutableStateFlow<State>>>

    @Before
    fun setup() {
        vehicles = MutableStateFlow(listOf(car, motorcycle))
        tyres = listOf(car, motorcycle).associate { vehicle ->
            vehicle.uuid to vehicle.kind.locations.map { MutableStateFlow<State>(State.NotDetected) }
        }
    }

    private fun test() = VehicleAlertLevelsUseCase(
        mockk { every { vehicleListFlow } returns vehicles },
        { vehicle -> tyres.getValue(vehicle.uuid) },
    )

    private fun detected(vararg levels: Pair<AlertClass, AlertLevel>, timestamp: Double = now()) = State.Detected(
        timestamp = timestamp,
        sensorId = 1,
        pressure = 2f.bar,
        pressureUnit = BAR,
        temperature = 20f.celsius,
        temperatureUnit = CELSIUS,
        levels = levels.toMap(),
    )

    @Test
    fun `a vehicle alerts with the worst level of its tyres' readings`() = runTest {
        test().levels.test {
            assertEquals(emptyMap(), awaitItem())
            tyres.getValue(car.uuid)[0].value = detected(PRESSURE to RED)
            assertEquals(mapOf(car.uuid to RED), awaitItem())
            tyres.getValue(car.uuid)[1].value = detected(TEMPERATURE to AMBER, BATTERY to CRIMSON)
            assertEquals(mapOf(car.uuid to CRIMSON), awaitItem())
            tyres.getValue(motorcycle.uuid)[1].value = detected(PRESSURE to AMBER)
            assertEquals(mapOf(car.uuid to CRIMSON, motorcycle.uuid to AMBER), awaitItem())
        }
    }

    @Test
    fun `a vehicle stops alerting once none of its tyres does`() = runTest {
        tyres.getValue(car.uuid)[0].value = detected(PRESSURE to RED)
        test().levels.test {
            assertEquals(emptyMap(), awaitItem())
            assertEquals(mapOf(car.uuid to RED), awaitItem())
            tyres.getValue(car.uuid)[0].value = detected()
            assertEquals(emptyMap(), awaitItem())
            tyres.getValue(car.uuid)[0].value = detected(PRESSURE to CRIMSON)
            assertEquals(mapOf(car.uuid to CRIMSON), awaitItem())
            tyres.getValue(car.uuid)[0].value = State.NotDetected
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun `a reading stops alerting once the tyre no longer shows it`() = runTest {
        tyres.getValue(car.uuid)[0].value = detected(PRESSURE to RED)
        tyres.getValue(motorcycle.uuid)[0].value =
            detected(PRESSURE to CRIMSON, timestamp = now() - 3.days.toDouble(SECONDS))
        test().levels.test {
            assertEquals(emptyMap(), awaitItem())
            assertEquals(mapOf(car.uuid to RED), awaitItem())
            advanceTimeBy(4.minutes)
            expectNoEvents()
            advanceTimeBy(2.minutes)
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun `editing a vehicle keeps listening to the same tyres`() = runTest {
        tyres.getValue(motorcycle.uuid)[0].value = detected(PRESSURE to RED)
        test().levels.test {
            assertEquals(emptyMap(), awaitItem())
            assertEquals(mapOf(motorcycle.uuid to RED), awaitItem())
            vehicles.value = listOf(car.copy(name = "RENAMED"), motorcycle)
            expectNoEvents()
            vehicles.value = listOf(car)
            assertEquals(emptyMap(), awaitItem())
        }
    }

    private companion object {
        val car = previewVehicle
        val motorcycle = previewVehicle.copy(uuid = UUID.randomUUID(), kind = Vehicle.Kind.MOTORCYCLE)
    }
}
