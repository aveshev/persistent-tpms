package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.test.mockkQueryList
import com.masselis.tpmsadvanced.core.test.mockkQueryOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase.OtherVehicle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SensorBindingUseCaseTest {

    private val location = Wheel(FRONT_LEFT)
    private val sensor = Sensor(1, location, PECHAM)

    private lateinit var current: Vehicle
    private lateinit var other: Vehicle
    private lateinit var vehicles: MutableStateFlow<List<Vehicle>>
    private lateinit var otherSensors: MutableStateFlow<List<Sensor>>
    private lateinit var vehicleDatabase: VehicleDatabase
    private lateinit var sensorDatabase: SensorDatabase

    @Before
    fun setup() {
        current = vehicle("Car")
        other = vehicle("Van")
        vehicles = MutableStateFlow(listOf(current, other))
        otherSensors = MutableStateFlow(emptyList())
        vehicleDatabase = mockk {
            every { selectAll() } returns mockkQueryList(vehicles)
            every { selectBySensorId(sensor.id) } returns mockkQueryOneOrNull(current)
        }
        sensorDatabase = mockk(relaxUnitFun = true) {
            every { selectByVehicleAndLocation(current.uuid, location) } returns mockkQueryOneOrNull(sensor)
            every { selectListByVehicleId(other.uuid) } returns mockkQueryList(otherSensors)
            every { selectById(sensor.id) } returns mockkQueryOneOrNull(sensor)
            every { selectById(2) } returns mockkQueryOneOrNull<Sensor>(null)
        }
    }

    private fun vehicle(name: String) = Vehicle(
        UUID.randomUUID(),
        CAR,
        name,
        Pressure(200f),
        Pressure(300f),
        Temperature(-10f),
        Temperature(90f),
    )

    private fun CoroutineScope.test() =
        SensorBindingUseCase.Impl(current, vehicleDatabase, sensorDatabase, location, this)

    @Test
    fun `the sensor bound is told once per sensor`() = runTest {
        assertEquals(
            listOf(1, null, 2),
            flowOf(sensor, sensor.copy(location = Wheel(REAR_RIGHT)), null, null, Sensor(2, location, PECHAM))
                .boundIds()
                .toList(),
        )
    }

    @Test
    fun `a sensor starts from not detected, then what it tells`() = runTest {
        flowOf<Int?>(1)
            .flatMapLatestBound("not detected") { flowOf("sensor $it") }
            .test {
                assertEquals("not detected", awaitItem())
                assertEquals("sensor 1", awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `another sensor bound starts over from not detected`() = runTest {
        val ids = MutableStateFlow<Int?>(1)
        ids
            .flatMapLatestBound("not detected") { flowOf("sensor $it") }
            .test {
                assertEquals("not detected", awaitItem())
                assertEquals("sensor 1", awaitItem())
                ids.value = 2
                assertEquals("not detected", awaitItem())
                assertEquals("sensor 2", awaitItem())
                ids.value = null
                assertEquals("not detected", awaitItem())
                assertEquals("sensor null", awaitItem())
            }
    }

    @Test
    fun `collected again for the same sensor, the state kept stays`() = runTest {
        val flow = flowOf<Int?>(1).flatMapLatestBound("not detected") { flowOf("sensor $it") }
        assertEquals(listOf("not detected", "sensor 1"), flow.toList())
        assertEquals(listOf("sensor 1"), flow.toList())
    }

    @Test
    fun `collected again for another sensor, the state kept was the other's`() = runTest {
        val ids = MutableStateFlow<Int?>(1)
        val flow = ids.flatMapLatestBound("not detected") { flowOf("sensor $it") }
        assertEquals("sensor 1", flow.take(2).toList().last())
        ids.value = 2
        assertEquals(listOf("not detected", "sensor 2"), flow.take(2).toList())
    }

    @Test
    fun `no sensor has nothing to start over from on the first collection`() = runTest {
        assertEquals(
            listOf("sensor null"),
            flowOf<Int?>(null).flatMapLatestBound("not detected") { flowOf("sensor $it") }.toList(),
        )
    }

    @Test
    fun `the sensor bound here is the one of this vehicle and location`() = runTest {
        assertEquals(sensor, test().boundSensor().value)
    }

    @Test
    fun `the vehicle of a sensor comes from the sensor`() = runTest {
        assertEquals(current, test().boundVehicle(sensor).first())
    }

    @Test
    fun `a sensor known to the database is bound, whatever its vehicle`() = runTest {
        test().apply {
            assertTrue(isBound(sensor.id))
            assertFalse(isBound(2))
        }
    }

    @Test
    fun `binding goes to the current vehicle`() = runTest {
        test().bind(sensor)
        coVerify { sensorDatabase.upsert(sensor, current.uuid) }
    }

    @Test
    fun `unbinding removes the sensor of this location`() = runTest {
        test().unbind()
        coVerify { sensorDatabase.deleteFromVehicle(current.uuid, location) }
    }

    @Test
    fun `moving goes from here to the location of the other vehicle`() = runTest {
        test().moveToVehicle(other, Wheel(REAR_RIGHT), swap = true)
        coVerify { sensorDatabase.moveToVehicle(current.uuid, location, other.uuid, Wheel(REAR_RIGHT), true) }
    }

    @Test
    fun `the other vehicles leave this one out`() = runTest {
        assertEquals(listOf(OtherVehicle(other, emptySet())), test().otherVehicles().first())
    }

    @Test
    fun `no other vehicle still answers`() = runTest {
        vehicles.value = listOf(current)
        assertEquals(emptyList(), test().otherVehicles().first())
    }

    @Test
    fun `the other vehicles tell where their sensors are`() = runTest {
        otherSensors.value = listOf(Sensor(5, Wheel(REAR_RIGHT), PECHAM), Sensor(6, location, PECHAM))
        assertEquals(
            setOf(Wheel(REAR_RIGHT), location),
            test().otherVehicles().first().single().occupied,
        )
    }

    @Test
    fun `the other vehicles follow the sensors and the vehicles`() = runTest {
        test().otherVehicles().test {
            assertEquals(listOf(OtherVehicle(other, emptySet())), awaitItem())
            otherSensors.value = listOf(Sensor(5, location, PECHAM))
            assertEquals(listOf(OtherVehicle(other, setOf(location))), awaitItem())
            vehicles.value = listOf(current)
            assertEquals(emptyList(), awaitItem())
        }
    }
}
