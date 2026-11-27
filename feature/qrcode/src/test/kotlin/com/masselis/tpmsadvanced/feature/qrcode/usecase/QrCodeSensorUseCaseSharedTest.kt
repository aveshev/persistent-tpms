package com.masselis.tpmsadvanced.feature.qrcode.usecase

import android.net.Uri
import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/** The kit formats are covered by the instrumented `QrCodeSensorUseCaseTest` */
internal class QrCodeSensorUseCaseSharedTest {

    private lateinit var cameraAnalyser: CameraAnalyser

    @Before
    fun setup() {
        cameraAnalyser = mockk()
    }

    private fun test() = QrCodeSensorUseCase(cameraAnalyser)

    @Test
    fun `a shared code holding a kit's id is read as shared`() = runTest {
        // A Wicarlink id is 6 hex characters, a code's base64 often holds such a run
        val shared = generateSequence(0) { it + 1 }
            .map { id -> SharedSensor(Sensor(id, Wheel(FRONT_LEFT), WICARLINK), SensorCalibration.None) }
            .map { SharedSensors(CAR, "Car", listOf(it)) }
            .first { "[0-9a-fA-F]{6}".toRegex().containsMatchIn(it.uri.substringAfter("d=")) }
        every { cameraAnalyser.analyse(any()) } returns MutableStateFlow(
            CameraAnalyser.Seen(listOf(shared.uri), emptyList())
        )
        test().analyse(mockk()).test {
            assertEquals(QrCodeSensorUseCase.Sight.Code(QrCodeResult.Shared(shared)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a damaged shared code isn't mistaken for a kit's`() = runTest {
        every { cameraAnalyser.analyse(any()) } returns MutableStateFlow(
            CameraAnalyser.Seen(listOf("tpmsadvanced://sensors?v=1&d=AB12CD"), emptyList())
        )
        test().analyse(mockk()).test {
            assertEquals(QrCodeSensorUseCase.Sight.Code(QrCodeResult.SharedUnreadable), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a typed ID is read as a Wicarlink sensor`() {
        assertEquals(QrCodeResult.Wicarlink(0x2221C5), test().decode("2221C5"))
    }

    @Test
    fun `a code with the same wheel twice tells which one`() {
        assertEquals(
            QrCodeResult.DuplicateWheelLocation(listOf(Wheel(FRONT_LEFT))),
            test().decode("11AD8B&21563D&11A4F0&41A552"),
        )
    }

    @Test
    fun `a code with the same sensor twice tells which one`() {
        // Not starting with a wheel number, the two are put on the wheels by rank
        assertEquals(QrCodeResult.DuplicateId(listOf(0x8BADAA00.toInt())), test().decode("AAAD8B&AAAD8B"))
    }

    @Test
    fun `the pictures' codes are read once each, without the unknown ones`() = runTest {
        val first = mockk<Uri>()
        val second = mockk<Uri>()
        coEvery { cameraAnalyser.read(first) } returns CameraAnalyser.Seen(listOf("2221C5", "hello"), emptyList())
        coEvery { cameraAnalyser.read(second) } returns CameraAnalyser.Seen(listOf("ABCDEF", "2221C5"), emptyList())
        assertEquals(
            listOf(QrCodeResult.Wicarlink(0x2221C5), QrCodeResult.Wicarlink(0xABCDEF)),
            test().read(listOf(first, second)),
        )
    }

    @Test
    fun `a frame gives its codes, then the IDs printed on it once each`() = runTest {
        every { cameraAnalyser.analyse(any()) } returns MutableStateFlow(
            CameraAnalyser.Seen(listOf("hello"), listOf("224822", "Wicarlink 224822", "224822", "ID"))
        )
        test().analyse(mockk()).test {
            assertEquals(QrCodeSensorUseCase.Sight.Code(null), awaitItem())
            assertEquals(QrCodeSensorUseCase.Sight.PrintedId("224822"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a printed ID is 6 hexadecimal characters, the look-alike letters read as digits`() {
        test().apply {
            assertEquals("224822", printedId("224822"))
            assertEquals("224822", printedId("224 822"))
            assertEquals("2221C5", printedId("2221c5"))
            assertEquals("201110", printedId("2O1lI0"))
            assertNull(printedId("22482"))
            assertNull(printedId("2248222"))
            assertNull(printedId("22G822"))
            assertNull(printedId("ID 224822"))
        }
    }

    @Test
    fun `a picture without a TPMS code gives the IDs printed on it`() = runTest {
        val uri = mockk<Uri>()
        coEvery { cameraAnalyser.read(uri) } returns CameraAnalyser.Seen(listOf("hello"), listOf("224822", "ID"))
        assertEquals(listOf(QrCodeResult.Wicarlink(0x224822)), test().read(listOf(uri)))
    }

    @Test
    fun `a picture's TPMS code wins over the ID printed on it`() = runTest {
        val uri = mockk<Uri>()
        // A misread ID next to the code isn't offered as another sensor
        coEvery { cameraAnalyser.read(uri) } returns CameraAnalyser.Seen(listOf("224822"), listOf("224B22"))
        assertEquals(listOf(QrCodeResult.Wicarlink(0x224822)), test().read(listOf(uri)))
    }
}
