package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibrations
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

internal class TyrePressureLossStateFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var stored: List<Tyre.Located>
    private lateinit var listened: List<Tyre.Located>
    private lateinit var calibration: MutableStateFlow<PressureCalibration?>
    private lateinit var rule: MutableStateFlow<PressureLoss.Rule?>

    @Before
    fun setup() {
        stored = emptyList()
        listened = emptyList()
        calibration = MutableStateFlow(null)
        rule = MutableStateFlow(PressureLoss.Rule())
    }

    context(scope: TestScope)
    private fun test() = TyrePressureLossStateFlow(
        mockk<Vehicle> { every { uuid } returns VEHICLE_UUID },
        Wheel(FRONT_LEFT),
        mockk<ReadingDatabase> {
            // any() can't match the Location value classes
            every { allByLocation(Wheel(FRONT_LEFT), VEHICLE_UUID) } returns mockk {
                every { execute() } returns stored
            }
        },
        mockk<ListenTyreUseCase> { every { listen() } returns flowOf(*listened.toTypedArray()) },
        mockk<VehicleCalibrationUseCase> {
            every { calibrations } returns this@TyrePressureLossStateFlowTest
                .calibration
                // Every sensor of the records below
                .map { calibration ->
                    PressureCalibrations(listOf(1, 2).mapNotNull { id -> calibration?.let { id to it } }.toMap())
                }
        },
        mockk<VehiclePressureLossUseCase> {
            every { rule } returns this@TyrePressureLossStateFlowTest.rule
        },
        scope.backgroundScope,
    )

    private fun record(minute: Int, kpa: Float, sensorId: Int = 1) = Tyre.Located(
        START + minute * SECONDS_PER_MINUTE,
        -50,
        sensorId,
        kpa.kpa,
        20f.celsius,
        PECHAM,
        false,
        Wheel(FRONT_LEFT),
    )

    /** The flow computes on other dispatchers, the wait is in real time */
    private suspend fun TyrePressureLossStateFlow.awaitLoss(): PressureLoss? =
        withContext(Dispatchers.Default) { withTimeoutOrNull(1.seconds) { first { it != null } } }

    @Test
    fun `detects a loss started before the app was opened`() = runTest {
        stored = listOf(record(0, 214f), record(2, 206f))
        listened = listOf(record(4, 206f))
        val loss = test().awaitLoss()
        assertNotNull(loss)
        assertEquals(START, loss.since)
    }

    @Test
    fun `a drop under the minimum isn't a loss`() = runTest {
        listened = listOf(record(0, 214f), record(2, 211f), record(3, 211f))
        assertNull(test().awaitLoss())
    }

    @Test
    fun `forgets the readings of the previously bound sensor`() = runTest {
        stored = listOf(record(0, 214f, sensorId = 2), record(1, 214f, sensorId = 2))
        listened = listOf(record(2, 200f), record(3, 200f))
        assertNull(test().awaitLoss())
    }

    @Test
    fun `stays quiet while the warning is off`() = runTest {
        rule.value = null
        listened = listOf(record(0, 214f), record(2, 200f), record(3, 200f))
        assertNull(test().awaitLoss())
    }

    private companion object {
        const val START = 1_726_483_200.0
        const val SECONDS_PER_MINUTE = 60.0
        val VEHICLE_UUID: UUID = UUID.randomUUID()
    }
}
