package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.Event
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class QRCodeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val code = QrCodeResult.Wicarlink(1)

    private lateinit var results: MutableSharedFlow<QrCodeResult?>

    @Before
    fun setup() {
        results = MutableSharedFlow(extraBufferCapacity = 64)
    }

    private fun test(results: Flow<QrCodeResult?> = this.results) = QRCodeViewModel(
        mockk { every { analyse(any()) } returns results },
        mockk(),
    )

    private fun failing(exception: Exception): Flow<QrCodeResult?> = flow { throw exception }

    /** What the camera sees, once the view model looks at it */
    private suspend fun seen(result: QrCodeResult?) {
        results.subscriptionCount.first { it > 0 }
        results.emit(result)
    }

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `nothing happens until a code is seen`() = runTest {
        test().apply {
            runCurrent()
            assertFalse(isUnknownCode.value)
            assertTrue(eventChannel.isEmpty)
        }
    }

    @Test
    fun `a TPMS code found closes the scan`() = runTest {
        test().apply {
            seen(code)
            assertEquals(Event.Found(code), eventChannel.receive())
            results.subscriptionCount.first { it == 0 }
        }
    }

    @Test
    fun `only the first code found is told`() = runTest {
        test().apply {
            seen(code)
            results.emit(QrCodeResult.Wicarlink(2))
            assertEquals(Event.Found(code), eventChannel.receive())
            runCurrent()
            assertTrue(eventChannel.isEmpty)
        }
    }

    @Test
    fun `a code which isn't a TPMS one is told and the scan goes on`() = runTest {
        test().apply {
            seen(null)
            assertTrue(isUnknownCode.value)
            assertTrue(eventChannel.isEmpty)
            assertEquals(1, results.subscriptionCount.value)
        }
    }

    @Test
    fun `the unknown code's flag lowers 2 seconds after it's out of view`() = runTest {
        test().apply {
            seen(null)
            advanceBy(2.seconds - 1.milliseconds)
            assertTrue(isUnknownCode.value)
            advanceBy(1.milliseconds)
            assertFalse(isUnknownCode.value)
        }
    }

    @Test
    fun `the unknown code's flag stays up while it's in view`() = runTest {
        test().apply {
            seen(null)
            advanceBy(1500.milliseconds)
            seen(null)
            advanceBy(1500.milliseconds)
            assertTrue(isUnknownCode.value)
            advanceBy(500.milliseconds)
            assertFalse(isUnknownCode.value)
        }
    }

    @Test
    fun `a TPMS code seen after an unknown one lowers its flag`() = runTest {
        test().apply {
            seen(null)
            seen(code)
            assertFalse(isUnknownCode.value)
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }

    @Test
    fun `an unavailable camera leaves`() = runTest {
        test(failing(CameraAnalyser.CameraUnavailable(IllegalArgumentException()))).apply {
            assertEquals(Event.LeaveBecauseCameraUnavailable, eventChannel.receive())
        }
    }

    @Test
    fun `a code with the same wheel twice tells which one`() = runTest {
        val wheels = listOf(Wheel(FRONT_LEFT), Wheel(FRONT_RIGHT), Wheel(FRONT_LEFT))
        test(failing(QrCodeSensors.DuplicateWheelLocation(wheels))).apply {
            assertEquals(
                Event.Found(QrCodeResult.DuplicateWheelLocation(listOf(Wheel(FRONT_LEFT)))),
                eventChannel.receive(),
            )
        }
    }

    @Test
    fun `a code with one sensor twice on a wheel tells which one`() = runTest {
        val sensor = QrCodeSensor(1, Wheel(FRONT_LEFT))
        test(flow { emit(QrCodeResult.Sensors(QrCodeSensors.TwoWheel(sensor, sensor))) }).apply {
            assertEquals(
                Event.Found(QrCodeResult.DuplicateWheelLocation(listOf(Wheel(FRONT_LEFT)))),
                eventChannel.receive(),
            )
        }
    }

    @Test
    fun `a code with the same sensor twice tells which one`() = runTest {
        test(failing(QrCodeSensors.DuplicateId(listOf(1, 2, 1, 3, 3, 3)))).apply {
            assertEquals(Event.Found(QrCodeResult.DuplicateId(listOf(1, 3))), eventChannel.receive())
        }
    }
}
