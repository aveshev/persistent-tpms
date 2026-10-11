package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibrations
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TyreAtmosphereUseCaseTest {

    private lateinit var listenTyreUseCase: ListenTyreUseCase
    private lateinit var calibration: MutableStateFlow<PressureCalibration?>
    private lateinit var calibrationUseCase: VehicleCalibrationUseCase

    @Before
    fun setup() {
        listenTyreUseCase = mockk()
        calibration = MutableStateFlow(null)
        calibrationUseCase = mockk {
            every { calibrations } returns this@TyreAtmosphereUseCaseTest
                .calibration
                // The tyre's reading comes from sensor 0
                .map { calibration -> PressureCalibrations(listOfNotNull(calibration?.let { 0 to it }).toMap()) }
        }
    }

    private fun test() = TyreAtmosphereUseCase(listenTyreUseCase, calibrationUseCase)

    private fun setTyre(pressure: Pressure, isAlarm: Boolean = false) =
        every { listenTyreUseCase.listen() } returns flowOf(
            Tyre.Located(0.0, 0, 0, pressure, 20f.celsius, PECHAM, isAlarm, Wheel(FRONT_LEFT))
        )

    @Test
    fun `sends the read pressure while the calibration is off`() = runTest {
        setTyre(200f.kpa)
        test().listen().test {
            assertEquals(200f.kpa, awaitItem().pressure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `corrects the pressure while the calibration is on`() = runTest {
        setTyre(200f.kpa)
        calibration.value = PressureCalibration(10f.kpa, 1f)
        test().listen().test {
            assertEquals(210f.kpa, awaitItem().pressure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `follows a calibration change`() = runTest {
        setTyre(200f.kpa)
        test().listen().test {
            assertEquals(200f.kpa, awaitItem().pressure)
            calibration.value = PressureCalibration((-10f).kpa, 1f)
            assertEquals(190f.kpa, awaitItem().pressure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps the read pressure when the sensor raises an alarm`() = runTest {
        setTyre(200f.kpa, isAlarm = true)
        calibration.value = PressureCalibration(10f.kpa, 1f)
        test().listen().test {
            awaitItem().also {
                assertEquals(210f.kpa, it.pressure)
                assertTrue(it.isSensorAlarm)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `corrects the pressure by the calibration of the sensor which sent it`() = runTest {
        // The tyre's reading comes from sensor 0
        every { calibrationUseCase.calibrations } returns flowOf(
            PressureCalibrations(mapOf(0 to PressureCalibration(10f.kpa, 1f), 1 to PressureCalibration(50f.kpa, 1f)))
        )
        setTyre(200f.kpa)
        test().listen().test {
            assertEquals(210f.kpa, awaitItem().pressure)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
