package com.masselis.tpmsadvanced.feature.qrcode.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import com.masselis.tpmsadvanced.feature.qrcode.model.sensorsFor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
internal class QrCodeSensorUseCaseTest {

    private lateinit var cameraAnalyser: CameraAnalyser

    @Before
    fun setup() {
        cameraAnalyser = mockk {
            every { findQrCode(any()) } returns MutableSharedFlow()
        }
    }

    private fun test() = QrCodeSensorUseCase(cameraAnalyser)

    private val fourWheel = QrCodeSensors.FourWheel(
        QrCodeSensor(-1951592192, Wheel(FRONT_LEFT)),
        QrCodeSensor(1029054720, Wheel(FRONT_RIGHT)),
        QrCodeSensor(-257675008, Wheel(REAR_LEFT)),
        QrCodeSensor(1386561792, Wheel(REAR_RIGHT)),
    )

    @Test
    fun fourWheels() = runTest {
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow("11AD8B&21563D&31A4F0&41A552")
        test().analyse(mockk()).test {
            assertEquals(QrCodeResult.Sensors(fourWheel), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun twoWheels() = runTest {
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow("11AD8B&41A552")
        test().analyse(mockk()).test {
            assertEquals(
                QrCodeResult.Sensors(
                    QrCodeSensors.TwoWheel(
                        QrCodeSensor(-1951592192, Wheel(FRONT_LEFT)),
                        QrCodeSensor(1386561792, Wheel(REAR_RIGHT)),
                    )
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // The cap reads 2221C5 too, the sensor advertises C5 21 22
    @Test
    fun wicarlink() = runTest {
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow("2221C5")
        test().analyse(mockk()).test {
            assertEquals(QrCodeResult.Wicarlink(0x2221C5), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unknownCode() = runTest {
        every { cameraAnalyser.findQrCode(any()) } returns MutableStateFlow("https://example.com")
        test().analyse(mockk()).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun fourWheelsForACar() {
        assertEquals(
            listOf(
                Sensor(-1951592192, Wheel(FRONT_LEFT), SYSGRATION),
                Sensor(1029054720, Wheel(FRONT_RIGHT), SYSGRATION),
                Sensor(-257675008, Wheel(REAR_LEFT), SYSGRATION),
                Sensor(1386561792, Wheel(REAR_RIGHT), SYSGRATION),
            ),
            fourWheel.sensorsFor(CAR)
        )
    }

    @Test
    fun twoWheelsForAMotorcycle() {
        assertEquals(
            listOf(
                Sensor(-1951592192, Location.Axle(FRONT), SYSGRATION),
                Sensor(1386561792, Location.Axle(REAR), SYSGRATION),
            ),
            QrCodeSensors
                .TwoWheel(
                    QrCodeSensor(-1951592192, Wheel(FRONT_LEFT)),
                    QrCodeSensor(1386561792, Wheel(REAR_RIGHT)),
                )
                .sensorsFor(MOTORCYCLE)
        )
    }

    // Three wheels, one fewer than the code's sensors: assigned via Bluetooth instead
    @Test
    fun tooManyWheelsForAThreeWheeler() {
        assertNull(fourWheel.sensorsFor(TADPOLE_THREE_WHEELER))
        assertNull(fourWheel.sensorsFor(DELTA_THREE_WHEELER))
    }

    @Test
    fun twoWheelsForATadpole() {
        assertEquals(
            listOf(
                Sensor(-1951592192, Wheel(FRONT_LEFT), SYSGRATION),
                Sensor(1386561792, Location.Axle(REAR), SYSGRATION),
            ),
            QrCodeSensors
                .TwoWheel(
                    QrCodeSensor(-1951592192, Wheel(FRONT_LEFT)),
                    QrCodeSensor(1386561792, Wheel(REAR_RIGHT)),
                )
                .sensorsFor(TADPOLE_THREE_WHEELER)
        )
    }

    @Test
    fun tooManyWheelsForAMonowheel() {
        assertNull(fourWheel.sensorsFor(MONOWHEEL))
    }
}
