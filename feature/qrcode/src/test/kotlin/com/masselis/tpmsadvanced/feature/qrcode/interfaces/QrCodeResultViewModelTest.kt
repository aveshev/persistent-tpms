package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR_WITH_SPARE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeResultViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensors
import com.masselis.tpmsadvanced.feature.qrcode.usecase.BoundSensorMapUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
@OptIn(ExperimentalCoroutinesApi::class)
internal class QrCodeResultViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var vehicle: Vehicle
    private lateinit var other: Vehicle
    private lateinit var assigned: List<Pair<Sensor, Vehicle>>
    private lateinit var vehicleDatabase: VehicleDatabase
    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var boundSensorMapUseCase: BoundSensorMapUseCase

    private val calibrated = SensorCalibration(true, PressureCalibration(Pressure(-5f), 1f))

    private val shared = SharedSensors(
        CAR_WITH_SPARE,
        "Partner's car",
        listOf(
            SharedSensor(Sensor(1, Wheel(FRONT_LEFT), SYSGRATION), calibrated),
            SharedSensor(Sensor(2, Wheel(FRONT_RIGHT), PECHAM), SensorCalibration.None),
            SharedSensor(Sensor(3, Location.Spare, PECHAM), SensorCalibration.None),
        ),
    )

    @Before
    fun setup() {
        vehicle = vehicle("Car", CAR)
        other = vehicle("Van", CAR)
        assigned = emptyList()
        // Each query answers what the tests' fields hold once it's executed
        vehicleDatabase = mockk {
            every { selectByUuid(any()) } answers {
                firstArg<UUID>().let { uuid -> mockk { every { execute() } answers { listOf(vehicle, other).first { it.uuid == uuid } } } }
            }
            every { selectBySensorId(any()) } answers {
                firstArg<Int>().let { id -> mockk { every { execute() } answers { assigned.firstOrNull { it.first.id == id }?.second } } }
            }
        }
        sensorDatabase = mockk {
            every { selectListByVehicleId(any()) } answers {
                firstArg<UUID>().let { uuid ->
                    mockk { every { execute() } answers { assigned.filter { it.second.uuid == uuid }.map { it.first } } }
                }
            }
            every { selectById(any()) } answers {
                firstArg<Int>().let { id -> mockk { every { execute() } answers { assigned.firstOrNull { it.first.id == id }?.first } } }
            }
        }
        boundSensorMapUseCase = mockk(relaxUnitFun = true)
    }

    private fun test(result: QrCodeResult = QrCodeResult.Shared(shared)) = QrCodeResultViewModel(
        vehicleDatabase,
        sensorDatabase,
        boundSensorMapUseCase,
        vehicle.uuid,
        Wheel(FRONT_LEFT),
        result,
    )

    private fun vehicle(name: String, kind: Vehicle.Kind) = Vehicle(
        UUID.randomUUID(),
        kind,
        name,
        Pressure(200f),
        Pressure(300f),
        Temperature(-10f),
        Temperature(90f),
    )

    private suspend fun QrCodeResultViewModel.state() = stateFlow.first { it != State.Loading }

    @Test
    fun `the shared sensors on wheels the vehicle doesn't have are skipped`() = runTest {
        val state = assertIs<State.AskShared>(test().state())
        assertEquals(shared.sensors.take(2), state.sensors)
        assertEquals(shared.sensors.drop(2), state.skipped)
        assertEquals("Partner's car", state.from)
    }

    @Test
    fun `a shared sensor assigned to another vehicle is told before it's moved`() = runTest {
        assigned = listOf(Sensor(2, Wheel(REAR_LEFT), PECHAM) to other, Sensor(9, Wheel(FRONT_LEFT), PECHAM) to vehicle)
        val state = assertIs<State.AskShared>(test().state())
        assertEquals(mapOf(2 to State.Assigned(other, Wheel(REAR_LEFT))), state.reassigns)
        assertTrue(state.overwrites)
    }

    @Test
    fun `nothing is asked when no shared sensor fits the vehicle`() = runTest {
        vehicle = vehicle("Unicycle", MONOWHEEL)
        assertIs<State.NoSharedSensorFits>(test().state())
    }

    @Test
    fun `binding assigns the shared sensors with their calibration`() = runTest {
        test().apply { state() }.bind().join()
        coVerify {
            boundSensorMapUseCase.bind(
                vehicle.uuid,
                shared.sensors.take(2).map { it.sensor },
                mapOf(1 to calibrated, 2 to SensorCalibration.None),
            )
        }
    }

    @Test
    fun `a code from a newer app or a damaged one is told apart`() = runTest {
        assertEquals(State.SharedByNewerApp, test(QrCodeResult.SharedByNewerApp).state())
        assertEquals(State.SharedUnreadable, test(QrCodeResult.SharedUnreadable).state())
    }
}
