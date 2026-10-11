package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibrations
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class TyreStatsStateFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tyreAtmosphereUseCase: TyreAtmosphereUseCase
    private lateinit var vehicleRangesUseCase: VehicleRangesUseCase
    private lateinit var vehicleCalibrationUseCase: VehicleCalibrationUseCase
    private lateinit var unitPreferences: UnitPreferences
    private lateinit var pressureLoss: MutableStateFlow<PressureLoss?>

    @Before
    fun setup() {
        tyreAtmosphereUseCase = mockk {
            every { listen() } returns emptyFlow()
        }
        vehicleRangesUseCase = mockk {
            every { alertThresholds(Wheel(FRONT_LEFT)) } returns
                flowOf(AlertThresholds(1f.bar, 3f.bar, 90f.celsius, 2.6f.volts))
        }
        vehicleCalibrationUseCase = mockk {
            every { calibrations } returns MutableStateFlow(PressureCalibrations.None)
        }
        unitPreferences = mockk {
            every { pressure } returns MutableStateFlow(BAR)
            every { temperature } returns MutableStateFlow(CELSIUS)
        }
        pressureLoss = MutableStateFlow(null)
    }

    context(scope: TestScope)
    private fun test() = TyreStatsStateFlow(
        TyreAlertsUseCase(tyreAtmosphereUseCase, vehicleRangesUseCase, pressureLoss, Wheel(FRONT_LEFT)),
        vehicleCalibrationUseCase,
        unitPreferences,
        SensorBindingUseCase.NoOp,
        scope.backgroundScope,
    )

    private fun setAtmosphere(
        pressure: Pressure,
        temperature: Temperature,
        timestamp: Double = now(),
        sensorId: Int = 0,
        batteryVoltage: Voltage? = null,
        isSensorAlarm: Boolean = false,
        flags: List<UByte>? = null,
    ) = every { tyreAtmosphereUseCase.listen() }.returns(
        flowOf(TyreAtmosphere(timestamp, sensorId, pressure, temperature, batteryVoltage, isSensorAlarm, flags))
    )

    context(scope: TestScope)
    private suspend fun firstDetected(): State.Detected {
        lateinit var detected: State.Detected
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            detected = assertIs<State.Detected>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        return detected
    }

    @Test
    fun notDetected(): Unit = runTest {
        assertIs<State.NotDetected>(test().value)
    }

    @Test
    fun normalPressure(): Unit = runTest {
        setAtmosphere(2f.bar, 45f.celsius)
        assertEquals(emptyMap(), firstDetected().levels)
    }

    @Test
    fun lowPressure(): Unit = runTest {
        setAtmosphere(0.8f.bar, 45f.celsius)
        assertEquals(mapOf(PRESSURE to RED), firstDetected().levels)
    }

    @Test
    fun `pressure at the minimum is red`(): Unit = runTest {
        setAtmosphere(1f.bar, 45f.celsius)
        assertEquals(mapOf(PRESSURE to RED), firstDetected().levels)
    }

    @Test
    fun `pressure close to the minimum is amber`(): Unit = runTest {
        setAtmosphere(1.02f.bar, 45f.celsius)
        assertEquals(mapOf(PRESSURE to AMBER), firstDetected().levels)
    }

    @Test
    fun `half the minimum is crimson`(): Unit = runTest {
        setAtmosphere(0.5f.bar, 45f.celsius)
        assertEquals(mapOf(PRESSURE to CRIMSON), firstDetected().levels)
    }

    @Test
    fun highTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, 95f.celsius)
        assertEquals(mapOf(TEMPERATURE to RED), firstDetected().levels)
    }

    @Test
    fun `marks the pressure of a calibrated sensor`() = runTest {
        every { vehicleCalibrationUseCase.calibrations } returns MutableStateFlow(
            PressureCalibrations(mapOf(0x562D00 to PressureCalibration(0.1f.bar, 1f)))
        )
        setAtmosphere(2f.bar, 45f.celsius, sensorId = 0x562D00)
        assertTrue(firstDetected().isPressureCalibrated)
        setAtmosphere(2f.bar, 45f.celsius, sensorId = 0x562E00)
        assertFalse(firstDetected().isPressureCalibrated)
    }

    @Test
    fun `shows a pressure loss as amber`() = runTest {
        pressureLoss.value = PressureLoss(0.6f.bar, 0.1f.bar, 0.0, 600.0)
        setAtmosphere(2f.bar, 45f.celsius)
        assertEquals(mapOf(PRESSURE_LOSS to AMBER), firstDetected().levels)
    }

    @Test
    fun `preserves sensor id and timestamp`() = runTest {
        val timestamp = 1_726_483_200.0
        val sensorId = 0x562D00
        setAtmosphere(2f.bar, 25f.celsius, timestamp, sensorId)
        firstDetected().also {
            assertEquals(timestamp, it.timestamp)
            assertEquals(sensorId, it.sensorId)
        }
    }

    @Test
    fun `a sensor without a voltage has no battery`() = runTest {
        setAtmosphere(2f.bar, 45f.celsius)
        assertNull(firstDetected().batteryVoltage)
    }

    @Test
    fun `a voltage above the warning margin is normal`() = runTest {
        setAtmosphere(2f.bar, 45f.celsius, batteryVoltage = 2.8f.volts)
        firstDetected().also {
            assertEquals(2.8f.volts, it.batteryVoltage)
            assertEquals(emptyMap(), it.levels)
        }
    }

    @Test
    fun `a voltage within 0,1 V of the alarm is amber`() = runTest {
        setAtmosphere(2f.bar, 45f.celsius, batteryVoltage = 27.toFloat().div(10f).volts)
        assertEquals(mapOf(BATTERY to AMBER), firstDetected().levels)
    }

    @Test
    fun `a voltage at the alarm is red`() = runTest {
        setAtmosphere(2f.bar, 45f.celsius, batteryVoltage = 2.6f.volts)
        assertEquals(mapOf(BATTERY to RED), firstDetected().levels)
    }

    @Test
    fun `a sensor alarm is amber, the read pressure kept`() = runTest {
        setAtmosphere(2f.bar, 45f.celsius, isSensorAlarm = true)
        firstDetected().also {
            assertEquals(mapOf(SENSOR_ALARM to AMBER), it.levels)
            assertEquals(2f.bar, it.pressure)
        }
    }

    @Test
    fun `preserves the flags, alerting or not`() = runTest {
        setAtmosphere(2f.bar, 25f.celsius, flags = listOf(0x83u))
        assertEquals(listOf<UByte>(0x83u), firstDetected().flags)
        setAtmosphere(0.5f.bar, 25f.celsius, flags = listOf(0xACu, 0x00u, 0x00u, 0x08u))
        assertEquals(listOf<UByte>(0xACu, 0x00u, 0x00u, 0x08u), firstDetected().flags)
    }
}
