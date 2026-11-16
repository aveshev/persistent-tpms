package com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Dialog
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Event
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.PutBack
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.PutOn
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.TakeOff
import com.masselis.tpmsadvanced.feature.unlocated.usecase.BindSensorToVehicleUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
internal class BluetoothAssignViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val location = Wheel(FRONT_LEFT)

    private lateinit var vehicle: Vehicle
    private lateinit var other: Vehicle
    private lateinit var scans: MutableSharedFlow<Tyre.SensorInput>

    /** The sensors of [vehicle], left out */
    private lateinit var bound: List<Sensor>

    /** The vehicle each sensor already belongs to */
    private lateinit var boundTo: Map<Int, Vehicle>
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var vehicleDatabase: VehicleDatabase
    private lateinit var bindUseCase: BindSensorToVehicleUseCase

    @Before
    fun setup() {
        vehicle = vehicle("Car")
        other = vehicle("Van")
        scans = MutableSharedFlow(extraBufferCapacity = 64)
        bound = emptyList()
        boundTo = emptyMap()
        scanner = mockk { every { highDutyScan() } returns scans }
        val isBoundRead = CountDownLatch(1)
        sensorDatabase = mockk {
            every { selectListByVehicleId(any()) } returns mockk {
                every { asFlow(any()) } returns flow {
                    emit(bound)
                    isBoundRead.countDown()
                    awaitCancellation()
                }
            }
        }
        vehicleDatabase = mockk {
            // After the sensors left out: once the vehicle is there, so are they
            every { selectByUuid(any()) } returns mockk {
                every { execute() } answers {
                    isBoundRead.await(TIMEOUT_SECONDS, SECONDS)
                    vehicle
                }
            }
            every { selectBySensorId(any()) } answers {
                firstArg<Int>().let { id -> mockk { every { execute() } answers { boundTo[id] } } }
            }
        }
        bindUseCase = mockk(relaxUnitFun = true)
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

    private suspend fun test() = BluetoothAssignViewModel(
        scanner,
        sensorDatabase,
        vehicleDatabase,
        bindUseCase,
        vehicle.uuid,
        location,
    ).also { viewModel -> viewModel.stateFlow.first { it.vehicle != null } }

    private fun reading(sensorId: Int, kpa: Float, brand: SensorBrand = PECHAM) =
        Tyre.Unlocated(0.0, -50, sensorId, Pressure(kpa), Temperature(20f), brand, false)

    /** A reading from sensor [sensorId], once the current step listens to them */
    private suspend fun heard(sensorId: Int, kpa: Float, brand: SensorBrand = PECHAM) {
        scans.subscriptionCount.first { it > 0 }
        scans.emit(reading(sensorId, kpa, brand))
    }

    private suspend fun BluetoothAssignViewModel.assigned() = stateFlow.first { it.isAssigned }

    /**
     * Waits rather than advances the clock: the view model resumes from its database read on the IO thread, under
     * the unconfined main dispatcher, so its step collector may start the wait after the clock has moved
     */
    private suspend fun BluetoothAssignViewModel.offersLowPressure() = stateFlow.first { it.offersLowPressure }

    private val BluetoothAssignViewModel.step get() = stateFlow.value.step

    private val BluetoothAssignViewModel.dialog get() = stateFlow.value.dialog

    /** On a wheel, the flow going through [sensorId] being taken off and put back */
    private suspend fun BluetoothAssignViewModel.findsByTakingOffAndBack(sensorId: Int, brand: SensorBrand = PECHAM) {
        answer(isOnWheel = true)
        heard(sensorId, 230f, brand)
        heard(sensorId, 0f, brand)
        next()
        heard(sensorId, 230f, brand)
    }

    @Test
    fun `the vehicle is read and the question comes first, without listening`() = runTest {
        test().apply {
            assertEquals(vehicle, stateFlow.value.vehicle)
            assertEquals(AssignStep.Ask, step)
            assertEquals(null, dialog)
            assertFalse(stateFlow.value.isAssigned)
        }
        assertEquals(0, scans.subscriptionCount.value)
    }

    @Test
    fun `a sensor on the wheel is taken off first, any sensor can be it`() = runTest {
        test().apply {
            answer(isOnWheel = true)
            assertEquals(TakeOff(null), step)
        }
    }

    @Test
    fun `a sensor off the wheel is put on first`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            assertEquals(PutOn(), step)
        }
    }

    @Test
    fun `a step waiting for sensors listens to them`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            scans.subscriptionCount.first { it == 1 }
            heard(1, 230f)
            assertEquals(PutOn(found = setOf(1)), step)
        }
    }

    @Test
    fun `the sensors of this vehicle are left out`() = runTest {
        bound = listOf(Sensor(7, Wheel(REAR_RIGHT), PECHAM))
        test().apply {
            answer(isOnWheel = false)
            heard(7, 230f)
            heard(1, 230f)
            assertEquals(PutOn(found = setOf(1)), step)
        }
    }

    @Test
    fun `taken off then put back, the sensor is assigned with its last reading`() = runTest {
        test().apply {
            findsByTakingOffAndBack(1)
            assigned()
            assertEquals(AssignStep.Found(1), step)
            assertEquals(PECHAM, stateFlow.value.foundBrand)
            assertEquals(null, dialog)
        }
        coVerify(exactly = 1) {
            bindUseCase.bind(vehicle.uuid, Sensor(1, location, PECHAM), reading(1, 230f))
        }
    }

    @Test
    fun `the brand assigned is the sensor's`() = runTest {
        test().apply {
            findsByTakingOffAndBack(1, SYSGRATION)
            assigned()
            assertEquals(SYSGRATION, stateFlow.value.foundBrand)
        }
        coVerify { bindUseCase.bind(vehicle.uuid, Sensor(1, location, SYSGRATION), any()) }
    }

    @Test
    fun `once found the sensors aren't listened to anymore`() = runTest {
        test().apply {
            findsByTakingOffAndBack(1)
            assigned()
        }
        scans.subscriptionCount.first { it == 0 }
    }

    @Test
    fun `a sensor which wasn't taken off isn't the one put back`() = runTest {
        test().apply {
            answer(isOnWheel = true)
            heard(1, 230f)
            heard(1, 0f)
            heard(2, 200f)
            next()
            assertEquals(PutBack(setOf(1)), step)
            heard(2, 240f)
            assertEquals(PutBack(setOf(1)), step)
        }
        coVerify(exactly = 0) { bindUseCase.bind(any(), any(), any()) }
    }

    @Test
    fun `a single sensor on the wheel asks whether to assign it`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 230f)
            next()
            assertEquals(Dialog.OneFound, dialog)
            assertFalse(stateFlow.value.isAssigned)
        }
    }

    @Test
    fun `the single sensor on the wheel can be assigned without taking it off`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 230f)
            next()
            assignFound()
            assigned()
            assertEquals(AssignStep.Found(1), step)
            assertEquals(null, dialog)
        }
        coVerify { bindUseCase.bind(vehicle.uuid, Sensor(1, location, PECHAM), reading(1, 230f)) }
    }

    @Test
    fun `several sensors on the wheel tell how many`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 230f)
            heard(2, 240f)
            next()
            assertEquals(Dialog.ManyFound(2), dialog)
        }
    }

    @Test
    fun `checking again takes the sensors found off the wheel`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 230f)
            heard(2, 240f)
            next()
            checkAgain()
            assertEquals(TakeOff(setOf(1, 2)), step)
            assertEquals(null, dialog)
            heard(3, 0f)
            heard(2, 0f)
            assertEquals(TakeOff(setOf(1, 2), off = setOf(2)), step)
            next()
            assertEquals(PutBack(setOf(2)), step)
            heard(2, 240f)
            assigned()
            assertEquals(AssignStep.Found(2), step)
        }
    }

    @Test
    fun `dismissing the question about a sensor found keeps listening`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 230f)
            next()
            dismissDialog()
            assertEquals(null, dialog)
            assertEquals(PutOn(found = setOf(1)), step)
            heard(2, 230f)
            assertEquals(PutOn(found = setOf(1, 2)), step)
        }
    }

    @Test
    fun `a sensor of another vehicle asks before it's taken from there`() = runTest {
        boundTo = mapOf(1 to other)
        test().apply {
            findsByTakingOffAndBack(1)
            stateFlow.first { it.dialog != null }
            assertEquals(Dialog.BoundElsewhere(other), dialog)
            assertFalse(stateFlow.value.isAssigned)
        }
        coVerify(exactly = 0) { bindUseCase.bind(any(), any(), any()) }
    }

    @Test
    fun `confirming assigns the sensor of another vehicle`() = runTest {
        boundTo = mapOf(1 to other)
        test().apply {
            findsByTakingOffAndBack(1)
            stateFlow.first { it.dialog != null }
            confirmBoundElsewhere()
            assigned()
            assertEquals(null, dialog)
        }
        coVerify(exactly = 1) { bindUseCase.bind(vehicle.uuid, Sensor(1, location, PECHAM), any()) }
    }

    @Test
    fun `declining starts over, and that sensor isn't found again until it moves`() = runTest {
        boundTo = mapOf(1 to other)
        test().apply {
            findsByTakingOffAndBack(1)
            stateFlow.first { it.dialog != null }
            dismissDialog()
            assertEquals(AssignStep.Ask, step)
            assertEquals(null, dialog)
            answer(isOnWheel = false)
            heard(1, 231f)
            assertEquals(PutOn(), step)
        }
        coVerify(exactly = 0) { bindUseCase.bind(any(), any(), any()) }
    }

    @Test
    fun `a sensor already on this vehicle is assigned without asking`() = runTest {
        boundTo = mapOf(1 to vehicle)
        test().apply {
            findsByTakingOffAndBack(1)
            assigned()
        }
        coVerify(exactly = 1) { bindUseCase.bind(vehicle.uuid, Sensor(1, location, PECHAM), any()) }
    }

    @Test
    fun `nothing on a wheel for 10 seconds offers the low pressure sensors`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            offersLowPressure()
            assertEquals(10.seconds.inWholeMilliseconds, currentTime)
        }
    }

    @Test
    fun `the low pressure sensors are the ones taken off instead`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            heard(1, 2f)
            heard(2, 230f)
            assertEquals(PutOn(found = setOf(2), low = setOf(1)), step)
            lowPressure()
            assertEquals(TakeOff(setOf(1)), step)
        }
    }

    @Test
    fun `leaving the step drops the low pressure offer`() = runTest {
        test().apply {
            answer(isOnWheel = false)
            offersLowPressure()
            lowPressure()
            stateFlow.first { it.offersLowPressure.not() }
        }
    }

    @Test
    fun `the low pressure offer isn't made on a step which doesn't wait for a wheel`() = runTest {
        test().apply {
            answer(isOnWheel = true)
            advanceBy(1.seconds * 30)
            assertFalse(stateFlow.value.offersLowPressure)
        }
    }

    @Test
    fun `what only a step can do does nothing on another one`() = runTest {
        test().apply {
            next()
            lowPressure()
            checkAgain()
            assignFound()
            confirmBoundElsewhere()
            assertEquals(AssignStep.Ask, step)
            assertEquals(null, dialog)
        }
        coVerify(exactly = 0) { bindUseCase.bind(any(), any(), any()) }
    }

    @Test
    fun `done leaves`() = runTest {
        test().apply {
            done()
            assertEquals(Event.Leave, eventChannel.receive())
        }
    }

    private fun TestScope.advanceBy(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
