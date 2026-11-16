package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.test.mockkQueryList
import com.masselis.tpmsadvanced.core.test.mockkQueryOne
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR_WITH_SPARE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.ShareSensorsViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensors
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ShareSensorsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calibrated = SensorCalibration(true, PressureCalibration(Pressure(-5f), 1f))
    private val frontLeft = Sensor(1, Wheel(FRONT_LEFT), SYSGRATION)
    private val rearRight = Sensor(2, Wheel(REAR_RIGHT), PECHAM)
    private val spare = Sensor(3, Location.Spare, PECHAM)

    private lateinit var vehicle: Vehicle
    private lateinit var sensors: MutableStateFlow<List<Sensor>>
    private lateinit var calibrations: MutableStateFlow<List<Pair<Int, SensorCalibration>>>

    @Before
    fun setup() {
        vehicle = Vehicle(
            UUID.randomUUID(),
            CAR_WITH_SPARE,
            "Partner's car",
            Pressure(200f),
            Pressure(300f),
            Temperature(-10f),
            Temperature(90f),
        )
        sensors = MutableStateFlow(emptyList())
        calibrations = MutableStateFlow(emptyList())
    }

    private fun test() = ShareSensorsViewModel(
        mockk<VehicleDatabase> { every { selectByUuid(vehicle.uuid) } returns mockkQueryOne(vehicle) },
        mockk<SensorDatabase> {
            every { selectListByVehicleId(vehicle.uuid) } returns mockkQueryList(sensors)
            every { selectCalibrations(vehicle.uuid) } returns mockkQueryList(calibrations)
        },
        vehicle.uuid,
    )

    private suspend fun ShareSensorsViewModel.settled() = stateFlow.first { it != State.Loading }

    private suspend fun ShareSensorsViewModel.ready() = stateFlow.first { it is State.Ready } as State.Ready

    /** Read the way another phone does, with the quiet zone the page draws around it */
    private fun BitMatrix.read(): String {
        val size = (width + 2 * QUIET_ZONE) * PIXELS_PER_MODULE
        return IntArray(size * size) { index ->
            val x = index % size / PIXELS_PER_MODULE - QUIET_ZONE
            val y = index / size / PIXELS_PER_MODULE - QUIET_ZONE
            if (x in 0 until width && y in 0 until height && get(x, y)) BLACK else WHITE
        }
            .let { RGBLuminanceSource(size, size, it) }
            .let { BinaryBitmap(HybridBinarizer(it)) }
            .let { QRCodeReader().decode(it).text }
    }

    @Test
    fun `a vehicle without a sensor has nothing to share`() = runTest {
        assertEquals(State.NoSensor(vehicle), test().settled())
    }

    @Test
    fun `the sensors are shared with the vehicle's kind and name`() = runTest {
        sensors.value = listOf(frontLeft)
        assertEquals(
            SharedSensors(CAR_WITH_SPARE, "Partner's car", listOf(SharedSensor(frontLeft, SensorCalibration.None))),
            assertIs<State.Ready>(test().settled()).shared,
        )
    }

    @Test
    fun `the sensors are in the order of the vehicle's wheels`() = runTest {
        sensors.value = listOf(spare, rearRight, frontLeft)
        assertEquals(
            listOf(frontLeft, rearRight, spare),
            test().ready().shared.sensors.map { it.sensor },
        )
    }

    @Test
    fun `each sensor comes with its own calibration`() = runTest {
        sensors.value = listOf(frontLeft, rearRight)
        calibrations.value = listOf(rearRight.id to calibrated)
        assertEquals(
            listOf(SensorCalibration.None, calibrated),
            test().ready().shared.sensors.map { it.calibration },
        )
    }

    @Test
    fun `the code holds what's shared`() = runTest {
        sensors.value = listOf(frontLeft, rearRight, spare)
        calibrations.value = listOf(frontLeft.id to calibrated)
        test().ready().apply {
            assertEquals(shared.uri, code.read())
            assertEquals(shared, SharedSensors.decode(code.read()))
        }
    }

    @Test
    fun `the code has one bit per module and no quiet zone`() = runTest {
        sensors.value = listOf(frontLeft)
        test().ready().code.apply {
            assertEquals(width, height)
            // Each corner but the bottom right one starts with a finder pattern. Versions go from 21
            // modules up by 4.
            assertEquals(1, (width - 21) % 4 + 1)
            assertTrue(get(0, 0))
            assertTrue(get(width - 1, 0))
            assertTrue(get(0, height - 1))
        }
    }

    @Test
    fun `a sensor assigned while the page is open is shared too`() = runTest {
        test().apply {
            assertIs<State.NoSensor>(settled())
            stateFlow.first { state ->
                if (state is State.NoSensor) sensors.value = listOf(frontLeft)
                state is State.Ready
            }
        }
    }

    @Test
    fun `the last sensor unassigned leaves nothing to share`() = runTest {
        sensors.value = listOf(frontLeft)
        test().apply {
            stateFlow.first { state ->
                if (state is State.Ready) sensors.value = emptyList()
                state is State.NoSensor
            }
        }
    }

    private companion object {
        const val QUIET_ZONE = 4
        const val PIXELS_PER_MODULE = 4
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
