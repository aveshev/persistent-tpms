package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class BoundSensorMapUseCaseTest {

    private val vehicle = UUID.randomUUID()
    private val frontLeft = Sensor(1, Wheel(FRONT_LEFT), PECHAM)
    private val frontRight = Sensor(2, Wheel(FRONT_RIGHT), PECHAM)
    private val calibration = SensorCalibration(true, PressureCalibration(Pressure(-5f), 1f))

    private lateinit var sensorDatabase: SensorDatabase

    @Before
    fun setup() {
        sensorDatabase = mockk(relaxUnitFun = true)
    }

    private fun test() = BoundSensorMapUseCase.Impl(sensorDatabase)

    @Test
    fun `every sensor is bound to the vehicle`() = runTest {
        test().bind(vehicle, listOf(frontLeft, frontRight))
        coVerify(exactly = 1) {
            sensorDatabase.upsert(frontLeft, vehicle)
            sensorDatabase.upsert(frontRight, vehicle)
        }
    }

    @Test
    fun `without calibrations none is changed`() = runTest {
        test().bind(vehicle, listOf(frontLeft))
        coVerify(exactly = 0) { sensorDatabase.updateCalibration(any(), any()) }
    }

    // A sensor moved from another vehicle loses its calibration when it's bound
    @Test
    fun `the calibrations are given once the sensors are bound`() = runTest {
        test().bind(vehicle, listOf(frontLeft, frontRight), mapOf(frontRight.id to calibration))
        coVerifyOrder {
            sensorDatabase.upsert(frontRight, vehicle)
            sensorDatabase.updateCalibration(frontRight.id, calibration)
        }
        coVerify(exactly = 1) { sensorDatabase.updateCalibration(any(), any()) }
    }
}
