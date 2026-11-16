package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibrations
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import com.masselis.tpmsadvanced.feature.background.usecase.StoredTyreAlertsUseCase.Event
import com.masselis.tpmsadvanced.feature.background.usecase.StoredTyreAlertsUseCase.Update
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
internal class StoredTyreAlertsUseCaseTest {

    private val location = Location.Single
    private val sensor = Sensor(1, location, PECHAM)
    private val thresholds = AlertThresholds(Pressure(200f), Pressure(300f), Temperature(90f), Voltage(2.6f))
    private val rule = PressureLoss.Rule()

    private lateinit var vehicle: Vehicle
    private lateinit var vehicles: MutableStateFlow<List<Vehicle>>

    /** Each vehicle's readings, in the order they're stored */
    private lateinit var stored: MutableStateFlow<List<Pair<UUID, Tyre.Located>>>

    /** How many times a tyre has started listening to the new readings */
    private lateinit var listening: MutableStateFlow<Int>

    /** What the sensor's query answers when it's executed, and what it tells its collectors */
    private var boundNow: Sensor? = null
    private lateinit var bound: MutableStateFlow<Sensor?>
    private lateinit var calibrationFlow: MutableStateFlow<PressureCalibrations>
    private lateinit var ruleFlow: MutableStateFlow<PressureLoss.Rule?>
    private lateinit var thresholdsFlow: MutableStateFlow<AlertThresholds>
    private lateinit var readingDatabase: ReadingDatabase

    @Before
    fun setup() {
        vehicle = vehicle("Unicycle")
        vehicles = MutableStateFlow(listOf(vehicle))
        stored = MutableStateFlow(emptyList())
        listening = MutableStateFlow(0)
        boundNow = sensor
        bound = MutableStateFlow(sensor)
        calibrationFlow = MutableStateFlow(PressureCalibrations.None)
        ruleFlow = MutableStateFlow(null)
        thresholdsFlow = MutableStateFlow(thresholds)
        readingDatabase = mockk {
            every { allByLocation(location, any()) } answers {
                secondArg<UUID>().let { vehicle ->
                    mockk { every { execute() } answers { readings(vehicle) } }
                }
            }
            every { afterByLocation(location, any(), any()) } answers {
                val vehicle = secondArg<UUID>()
                val since = thirdArg<Double>()
                mockk { every { execute() } answers { readings(vehicle).filter { it.timestamp > since } } }
            }
            // Every change of the readings wakes it, of any tyre
            every { latestByLocation(location, any()) } answers {
                secondArg<UUID>().let { vehicle ->
                    mockk {
                        every { asFlow(any()) } returns stored
                            .map { readings(vehicle).lastOrNull() }
                            .onStart { listening.update { it + 1 } }
                    }
                }
            }
        }
        mockkObject(VehicleComponent.Companion)
        every { VehicleComponent(any()) } returns mockk {
            every { vehicleCalibrationUseCase } returns mockk { every { calibrations } returns calibrationFlow }
            every { vehiclePressureLossUseCase } returns mockk { every { rule } returns ruleFlow }
            every { vehicleRangesUseCase } returns mockk { every { alertThresholds(location) } returns thresholdsFlow }
        }
    }

    @After
    fun tearDown() {
        unmockkObject(VehicleComponent.Companion)
    }

    private fun vehicle(name: String) = Vehicle(
        UUID.randomUUID(),
        MONOWHEEL,
        name,
        Pressure(200f),
        Pressure(300f),
        Temperature(-10f),
        Temperature(90f),
    )

    private fun test() = StoredTyreAlertsUseCase(
        mockk<VehicleListUseCase> { every { vehicleListFlow } returns vehicles },
        readingDatabase,
        mockk<SensorDatabase> {
            every { selectByVehicleAndLocation(any(), location) } returns mockk {
                every { asFlow(any()) } returns bound
                every { execute() } answers { boundNow }
            }
        },
    )

    private fun readings(vehicle: UUID) = stored.value.filter { it.first == vehicle }.map { it.second }

    private fun reading(minute: Int, kpa: Float = 230f, sensorId: Int = sensor.id) = Tyre.Located(
        minute * SECONDS_PER_MINUTE,
        -50,
        sensorId,
        Pressure(kpa),
        Temperature(20f),
        PECHAM,
        false,
        location,
    )

    private fun store(vararg readings: Tyre.Located, of: Vehicle = vehicle) {
        stored.update { list -> list + readings.map { of.uuid to it } }
    }

    private suspend fun listeningTo(tyres: Int = 1) {
        listening.first { it >= tyres }
    }

    private fun Tyre.Located.atmosphere() = toAtmosphere(calibrationFlow.value)

    /** Readings falling fast, to be told as a pressure loss */
    private val leak = (0..LEAK_MINUTES).map { reading(it, 230f - it * LEAK_KPA_PER_MINUTE) }

    /** What the loss should be at the end of [readings], once through the tracker in order */
    private fun lossAfter(readings: List<Tyre.Located>) = readings
        .fold(PressureLoss.Tracker()) { tracker, reading -> tracker.next(reading.atmosphere(), rule) }
        .loss

    @Test
    fun `a new reading is told with the tyre and its thresholds`() = runTest {
        test().events.test {
            listeningTo()
            store(reading(1, kpa = 150f))
            assertIs<Update>(awaitItem()).apply {
                assertEquals(vehicle, this.vehicle)
                assertEquals(location, this.location)
                assertEquals(reading(1, kpa = 150f).atmosphere(), alerts.latest)
                assertTrue(PRESSURE in alerts.levels)
                assertEquals(thresholds, this.thresholds)
                assertNull(loss)
                assertFalse(isReevaluation)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the readings stored before listening aren't told`() = runTest {
        store(reading(1), reading(2))
        test().events.test {
            listeningTo()
            store(reading(3))
            assertEquals(reading(3).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the readings stored before listening are its history`() = runTest {
        ruleFlow.value = rule
        store(*leak.dropLast(1).toTypedArray())
        assertNotNull(lossAfter(leak), "These readings should be a loss")
        test().events.test {
            listeningTo()
            store(leak.last())
            assertEquals(lossAfter(leak), assertIs<Update>(awaitItem()).loss)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `several readings stored at once are told in order`() = runTest {
        test().events.test {
            listeningTo()
            store(reading(1), reading(2))
            assertEquals(reading(1).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            assertEquals(reading(2).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reading is told once, whatever wakes the tyre again`() = runTest {
        val other = vehicle("Other")
        test().events.test {
            listeningTo()
            store(reading(1))
            assertEquals(reading(1).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            // Another vehicle's reading wakes this tyre too, as its query is told of any change
            store(reading(2), of = other)
            store(reading(3))
            assertEquals(reading(3).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reading goes through its sensor's calibration`() = runTest {
        calibrationFlow.value = PressureCalibrations(mapOf(sensor.id to PressureCalibration(Pressure(-5f), 1f)))
        test().events.test {
            listeningTo()
            store(reading(1, kpa = 230f))
            assertEquals(Pressure(225f), assertNotNull(assertIs<Update>(awaitItem()).alerts.latest).pressure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without a rule a falling pressure isn't a loss`() = runTest {
        store(*leak.dropLast(1).toTypedArray())
        test().events.test {
            listeningTo()
            store(leak.last())
            assertNull(assertIs<Update>(awaitItem()).loss)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning the rule on starts it over from the stored readings`() = runTest {
        store(*leak.dropLast(1).toTypedArray())
        test().events.test {
            listeningTo()
            ruleFlow.value = rule
            store(leak.last())
            assertEquals(lossAfter(leak), assertNotNull(assertIs<Update>(awaitItem()).loss))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning the rule off ends the loss`() = runTest {
        ruleFlow.value = rule
        store(*leak.dropLast(1).toTypedArray())
        test().events.test {
            listeningTo()
            ruleFlow.value = null
            store(leak.last())
            assertNull(assertIs<Update>(awaitItem()).loss)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new thresholds go through the latest reading again`() = runTest {
        val strict = thresholds.copy(lowPressure = Pressure(240f))
        test().events.test(timeout = TIMEOUT) {
            listeningTo()
            store(reading(1, kpa = 230f))
            assertFalse(PRESSURE in assertIs<Update>(awaitItem()).alerts.levels)
            thresholdsFlow.value = strict
            assertIs<Update>(awaitItem()).apply {
                assertTrue(isReevaluation)
                assertEquals(strict, thresholds)
                assertEquals(reading(1, kpa = 230f).atmosphere(), alerts.latest)
                assertTrue(PRESSURE in alerts.levels)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new thresholds settle first`() = runTest {
        val last = thresholds.copy(lowPressure = Pressure(240f))
        test().events.test(timeout = TIMEOUT) {
            listeningTo()
            store(reading(1))
            awaitItem()
            thresholdsFlow.value = thresholds.copy(lowPressure = Pressure(210f))
            thresholdsFlow.value = thresholds.copy(lowPressure = Pressure(220f))
            thresholdsFlow.value = last
            assertEquals(last, assertIs<Update>(awaitItem()).thresholds)
            waitForTheThresholdsToSettle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new thresholds without a reading tell nothing, the next reading goes through them`() = runTest {
        val strict = thresholds.copy(lowPressure = Pressure(240f))
        test().events.test(timeout = TIMEOUT) {
            listeningTo()
            thresholdsFlow.value = strict
            waitForTheThresholdsToSettle()
            expectNoEvents()
            store(reading(1, kpa = 230f))
            assertIs<Update>(awaitItem()).apply {
                assertFalse(isReevaluation)
                assertEquals(strict, thresholds)
                assertTrue(PRESSURE in alerts.levels)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the same thresholds again tell nothing`() = runTest {
        test().events.test(timeout = TIMEOUT) {
            listeningTo()
            store(reading(1))
            awaitItem()
            thresholdsFlow.value = thresholds.copy()
            waitForTheThresholdsToSettle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `another sensor on the tyre ends what the previous one alerted for`() = runTest {
        val next = sensor.copy(id = 2)
        test().events.test {
            listeningTo()
            boundNow = next
            bound.value = next
            assertEquals(Event.SensorChanged(vehicle, location), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the sensor unbound ends what it alerted for`() = runTest {
        test().events.test {
            listeningTo()
            boundNow = null
            bound.value = null
            assertEquals(Event.SensorChanged(vehicle, location), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the first sensor isn't a change, nor is the same one again`() = runTest {
        test().events.test {
            listeningTo()
            bound.value = sensor.copy(brand = sensor.brand)
            expectNoEvents()
            store(reading(1))
            assertIs<Update>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `readings moved in with a sensor aren't told, they're stored from before`() = runTest {
        val next = sensor.copy(id = 2)
        test().events.test {
            listeningTo()
            // In the same transaction: the tyre is woken by the readings, and already has the sensor
            boundNow = next
            store(reading(1, sensorId = next.id))
            bound.value = next
            assertEquals(Event.SensorChanged(vehicle, location), awaitItem())
            listeningTo(tyres = 2)
            store(reading(2, sensorId = next.id))
            assertEquals(reading(2, sensorId = next.id).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new sensor's history holds the readings moved in`() = runTest {
        ruleFlow.value = rule
        val next = sensor.copy(id = 2)
        test().events.test {
            listeningTo()
            boundNow = next
            store(*leak.dropLast(1).toTypedArray())
            bound.value = next
            assertEquals(Event.SensorChanged(vehicle, location), awaitItem())
            listeningTo(tyres = 2)
            store(leak.last())
            assertEquals(lossAfter(leak), assertIs<Update>(awaitItem()).loss)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing a vehicle doesn't start its tyres' history over`() = runTest {
        test().events.test {
            listeningTo()
            store(reading(1))
            awaitItem()
            vehicles.value = listOf(vehicle.copy(highTemp = Temperature(80f)))
            store(reading(2))
            assertEquals(reading(2).atmosphere(), assertIs<Update>(awaitItem()).alerts.latest)
            verify(exactly = 1) { readingDatabase.allByLocation(location, vehicle.uuid) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every vehicle's tyres are followed`() = runTest {
        val other = vehicle("Other")
        vehicles.value = listOf(vehicle, other)
        test().events.test {
            listeningTo(tyres = 2)
            store(reading(1), of = other)
            assertEquals(other, assertIs<Update>(awaitItem()).vehicle)
            store(reading(2))
            assertEquals(vehicle, assertIs<Update>(awaitItem()).vehicle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a vehicle added is followed too`() = runTest {
        val other = vehicle("Other")
        test().events.test {
            listeningTo()
            vehicles.value = listOf(vehicle, other)
            listeningTo(tyres = 3)
            store(reading(1), of = other)
            assertEquals(other, assertIs<Update>(awaitItem()).vehicle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a vehicle removed isn't followed anymore`() = runTest {
        val other = vehicle("Other")
        vehicles.value = listOf(vehicle, other)
        test().events.test {
            listeningTo(tyres = 2)
            vehicles.value = listOf(vehicle)
            listeningTo(tyres = 3)
            store(reading(1), of = other)
            store(reading(2))
            assertEquals(vehicle, assertIs<Update>(awaitItem()).vehicle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Longer than the thresholds take to settle, for real: this runs on Dispatchers.IO */
    private suspend fun waitForTheThresholdsToSettle() = withContext(Dispatchers.Default) {
        delay(1.5.seconds)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val LEAK_MINUTES = 15
        const val LEAK_KPA_PER_MINUTE = 5f
        val TIMEOUT = 5.seconds
    }
}
