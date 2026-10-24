package com.masselis.tpmsadvanced.feature.qrcode.usecase

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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

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
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow(shared.uri)
        test().analyse(mockk()).test {
            assertEquals(QrCodeResult.Shared(shared), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a damaged shared code isn't mistaken for a kit's`() = runTest {
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow("tpmsadvanced://sensors?v=1&d=AB12CD")
        test().analyse(mockk()).test {
            assertEquals(QrCodeResult.SharedUnreadable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
