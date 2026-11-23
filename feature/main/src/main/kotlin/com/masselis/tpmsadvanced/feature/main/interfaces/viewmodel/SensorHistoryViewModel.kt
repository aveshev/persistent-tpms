package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.PERCENT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.VOLT
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.asSensorId
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every stored reading of the sensor [sensorId], wherever it was read: its history follows the
 * sensor rather than a tyre. Their pressures are corrected by the sensor's current calibration.
 */
@AssistedInject
internal class SensorHistoryViewModel(
    @Assisted private val sensorId: Int,
    sensorDatabase: SensorDatabase,
    readingDatabase: ReadingDatabase,
    calibrationUseCase: VehicleCalibrationUseCase,
    unitPreferences: UnitPreferences,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(sensorId: Int): SensorHistoryViewModel
    }

    sealed interface State {
        data object Loading : State

        /**
         * [sensor] is null once it's no longer assigned. [readings]: newest first, their pressure
         * corrected by [calibration], null while it's off.
         */
        data class Loaded(
            val sensorId: Int,
            val sensor: Sensor?,
            val calibration: PressureCalibration?,
            val readings: List<Tyre.Located>,
            val pressureUnit: PressureUnit,
            val temperatureUnit: TemperatureUnit,
        ) : State {

            /** The sensor's ID the way the app shows it, see [asSensorId], before the export's time */
            val fileName: String
                get() = "sensor-${sensorId.asSensorId(sensor?.brand ?: readings.firstOrNull()?.brand)}"

            /** The [readings] as a spreadsheet, in the units shown, oldest first */
            val csv: String
                get() = buildString {
                    // Each sensor reports its battery one way, see SensorBrand.batteryUnit
                    val batteryUnit = (sensor?.brand ?: readings.firstOrNull()?.brand)?.batteryUnit ?: VOLT
                    append("time,pressure (${pressureUnit.symbol()}),temperature (${temperatureUnit.symbol()}),")
                    append(if (batteryUnit == PERCENT) "battery (%)" else "battery (V)")
                    append(",alarm,rssi (dBm)\n")
                    readings.asReversed().forEach { reading ->
                        append(reading.timestamp.isoDateTime)
                        append(',')
                        append(reading.pressure.convert(pressureUnit).format(pressureUnit.decimals))
                        append(',')
                        append(reading.temperature.convert(temperatureUnit).format(1))
                        append(',')
                        append(
                            reading.batteryPercent?.toString()
                                ?: reading.batteryVoltage?.volts?.format(1)
                                ?: ""
                        )
                        append(',')
                        append(reading.isAlarm)
                        append(',')
                        append(reading.rssi)
                        append('\n')
                    }
                }
        }
    }

    val stateFlow: StateFlow<State> = combine(
        sensorDatabase.selectById(sensorId).asFlow(),
        calibrationUseCase.of(sensorId),
        readingDatabase.allBySensor(sensorId).asFlow(),
        unitPreferences.pressure,
        unitPreferences.temperature,
    ) { sensor, calibration, readings, pressureUnit, temperatureUnit ->
        State.Loaded(
            sensorId = sensorId,
            sensor = sensor,
            calibration = calibration.applied,
            readings = calibration
                .applied
                ?.let { applied -> readings.map { it.copy(pressure = applied.applyTo(it.pressure)) } }
                ?: readings,
            pressureUnit = pressureUnit,
            temperatureUnit = temperatureUnit,
        )
    }
        // Up to thousands of readings decoded from their advertisement, off the main thread
        .flowOn(Default)
        .stateIn(viewModelScope, WhileSubscribed(), State.Loading)

    /**
     * The history as a CSV file in the cache folder the FileProvider shares, named after the
     * sensor and now. Null while it's loading.
     */
    suspend fun exportCsv(): File? = (stateFlow.value as? State.Loaded)?.let { state ->
        withContext(IO) {
            LocalDateTime
                .now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
                .let { File(appContext.cacheDir, "sensor_history/${state.fileName}-$it.csv") }
                .also { it.parentFile?.mkdirs() }
                .also { it.writeText(state.csv) }
        }
    }
}

/** A dot whatever the locale: a comma would split the CSV's columns */
private fun Float.format(decimals: Int): String = "%.${decimals}f".format(Locale.ROOT, this)

/** As finely as the app shows them */
private val PressureUnit.decimals: Int
    get() = when (this) {
        KILO_PASCAL -> 0
        BAR -> 2
        PSI -> 1
    }

/** These seconds since the epoch, in the phone's time zone with its offset */
private val Double.isoDateTime: String
    get() = Instant
        .ofEpochMilli((this * MILLIS_PER_SECOND).toLong())
        .let { OffsetDateTime.ofInstant(it, ZoneId.systemDefault()) }
        .withNano(0)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

private const val MILLIS_PER_SECOND = 1000
