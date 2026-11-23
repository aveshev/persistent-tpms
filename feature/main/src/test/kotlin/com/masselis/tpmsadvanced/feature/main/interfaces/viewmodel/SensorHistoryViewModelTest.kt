package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.test.mockkQueryList
import com.masselis.tpmsadvanced.core.test.mockkQueryOneOrNull
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorHistoryViewModel.State
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class SensorHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var calibration: MutableStateFlow<SensorCalibration>
    private lateinit var readings: MutableStateFlow<List<Tyre.Located>>
    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var readingDatabase: ReadingDatabase
    private lateinit var calibrationUseCase: VehicleCalibrationUseCase
    private lateinit var unitPreferences: UnitPreferences
    private lateinit var locale: Locale
    private lateinit var timeZone: TimeZone

    @Before
    fun setup() {
        locale = Locale.getDefault()
        timeZone = TimeZone.getDefault()
        // Writes 2,5 rather than 2.5, the CSV must not
        Locale.setDefault(Locale.GERMANY)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        calibration = MutableStateFlow(SensorCalibration.None)
        readings = MutableStateFlow(listOf(newest, oldest))
        sensorDatabase = mockk {
            every { selectById(SENSOR) } returns mockkQueryOneOrNull(Sensor(SENSOR, Location.Wheel(FRONT_LEFT), PECHAM))
        }
        readingDatabase = mockk {
            every { allBySensor(SENSOR) } returns mockkQueryList(readings)
        }
        calibrationUseCase = mockk {
            every { of(SENSOR) } returns calibration
        }
        unitPreferences = mockk {
            every { pressure } returns MutableStateFlow(KILO_PASCAL)
            every { temperature } returns MutableStateFlow(CELSIUS)
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(locale)
        TimeZone.setDefault(timeZone)
    }

    private fun test() = SensorHistoryViewModel(
        SENSOR,
        sensorDatabase,
        readingDatabase,
        calibrationUseCase,
        unitPreferences,
    )

    private suspend fun SensorHistoryViewModel.loaded(): State.Loaded {
        var loaded: State.Loaded? = null
        stateFlow.test {
            while (loaded == null) loaded = awaitItem() as? State.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return assertIs<State.Loaded>(loaded)
    }

    @Test
    fun `every reading of the sensor is shown, wherever it was read`() = runTest {
        assertEquals(listOf(newest, oldest), test().loaded().readings)
    }

    @Test
    fun `the pressures are corrected by the sensor's current calibration`() = runTest {
        calibration.value = SensorCalibration(true, PressureCalibration(10f.kpa, 1f))
        assertEquals(listOf(210f.kpa, 230f.kpa), test().loaded().readings.map { it.pressure })
    }

    @Test
    fun `a calibration turned off corrects nothing`() = runTest {
        calibration.value = SensorCalibration(false, PressureCalibration(10f.kpa, 1f))
        assertEquals(listOf(200f.kpa, 220f.kpa), test().loaded().readings.map { it.pressure })
    }

    @Test
    fun `the csv lists the readings oldest first, in the units shown, with dots`() {
        val csv = State.Loaded(
            sensorId = SENSOR,
            sensor = Sensor(SENSOR, Location.Wheel(FRONT_LEFT), PECHAM),
            calibration = null,
            readings = listOf(newest, oldest),
            pressureUnit = BAR,
            temperatureUnit = CELSIUS,
        ).csv
        assertEquals(
            """
            time,pressure (bar),temperature (°C),battery (V),alarm,rssi (dBm)
            2025-11-17T10:00:00Z,2.20,18.0,2.9,false,-70
            2025-11-17T11:00:00Z,2.00,21.5,2.8,true,-60

            """.trimIndent(),
            csv,
        )
    }

    @Test
    fun `the file is named after the sensor id as the app shows it`() {
        val state = State.Loaded(
            sensorId = 0x0C0B0A,
            sensor = Sensor(0x0C0B0A, Location.Wheel(FRONT_LEFT), PECHAM),
            calibration = null,
            readings = emptyList(),
            pressureUnit = BAR,
            temperatureUnit = CELSIUS,
        )
        assertEquals("sensor-0A0B0C", state.fileName)
    }

    @Test
    fun `a sensor reporting a percentage has its battery as one`() {
        val csv = State.Loaded(
            sensorId = SENSOR,
            sensor = null,
            calibration = null,
            readings = listOf(
                newest.copy(brand = SYSGRATION, batteryVoltage = null, batteryPercent = 80),
            ),
            pressureUnit = KILO_PASCAL,
            temperatureUnit = CELSIUS,
        ).csv
        assertEquals(
            """
            time,pressure (kPa),temperature (°C),battery (%),alarm,rssi (dBm)
            2025-11-17T11:00:00Z,200,21.5,80,true,-60

            """.trimIndent(),
            csv,
        )
    }

    private companion object {
        const val SENSOR = 42

        // 2025-11-17T11:00:00Z, read on another vehicle's wheel
        val newest = Tyre.Located(
            1_763_377_200.0, -60, SENSOR, 200f.kpa, 21.5f.celsius, PECHAM, true, Location.Wheel(REAR_RIGHT),
            batteryVoltage = 2.8f.volts,
        )
        val oldest = Tyre.Located(
            1_763_373_600.0, -70, SENSOR, 220f.kpa, 18f.celsius, PECHAM, false, Location.Wheel(FRONT_LEFT),
            batteryVoltage = 2.9f.volts,
        )
    }
}
