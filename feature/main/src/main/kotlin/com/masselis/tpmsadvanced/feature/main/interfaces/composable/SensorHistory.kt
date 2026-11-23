package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.masselis.tpmsadvanced.core.common.localizedPlural
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorHistoryViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorHistoryViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.SensorHistoryViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * Every stored reading of [sensorId], newest first, wherever it was read: the history follows the
 * sensor when it's moved, not the tyre. The pressures are corrected by its current calibration.
 * They can be shared as a CSV file.
 */
@Composable
public fun SensorHistory(
    sensorId: Int,
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: SensorHistoryViewModel = component.viewModel(component.key() + ("sensor_id" to "$sensorId")) {
        it.SensorHistoryViewModel(sensorId)
    }
    val state by viewModel.stateFlow.collectAsState()
    val context = LocalContext.current
    val chooserTitle = stringResource(CommonR.string.export_to_csv)
    val scope = rememberCoroutineScope()
    SensorHistory(
        state = state,
        export = {
            scope.launch {
                viewModel
                    .exportCsv()
                    ?.let { FileProvider.getUriForFile(context, "${context.packageName}.database_export", it) }
                    ?.let { uri ->
                        Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ?.let { Intent.createChooser(it, chooserTitle) }
                    ?.also { context.startActivity(it) }
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod")
@Composable
private fun SensorHistory(
    state: State,
    export: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier.fillMaxSize().testTag(SensorHistoryTags.root)) {
    when (state) {
        State.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is State.Loaded -> {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = buildString {
                        state.sensor?.also { append("${it.brand.label} · ") }
                        append("ID: ${state.sensorId.asSensorId(state.sensor?.brand)}")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = buildString {
                        state.sensor
                            ?.also { appendLoc(it.location, capitalized = true) }
                            ?: append(stringResource(CommonR.string.no_longer_assigned))
                        append(" · ")
                        state.readings.size.also { append(localizedPlural(CommonR.plurals.readings_count, it, it)) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SensorHistoryTags.count),
                )
                if (state.calibration?.adjusts == true) Text(
                    text = stringResource(CommonR.string.pressures_corrected_by_this_sensor_s_current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (state.readings.isEmpty()) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    text = stringResource(CommonR.string.no_readings_stored_for_this_sensor_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val zone = remember { ZoneId.systemDefault() }
                val byDay = remember(state.readings) {
                    state.readings.groupBy { it.timestamp.toInstant().atZone(zone).toLocalDate() }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(SensorHistoryTags.list)) {
                    byDay.forEach { (day, readings) ->
                        stickyHeader(key = "$day") { DayHeader(day) }
                        items(readings) { reading ->
                            ReadingRow(reading, state)
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider()
    Button(
        onClick = export,
        enabled = (state as? State.Loaded)?.readings?.isNotEmpty() == true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(SensorHistoryTags.export),
    ) { Text(stringResource(CommonR.string.export_to_csv)) }
}

@Composable
private fun DayHeader(day: LocalDate) = Text(
    text = remember(day) { day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) },
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp, vertical = 8.dp),
)

/** Its time, then its pressure, flagged when the sensor raised its alarm, temperature and battery */
@Composable
private fun ReadingRow(
    reading: Tyre.Located,
    state: State.Loaded,
    leak: String = stringResource(CommonR.string.leak),
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
) {
    Text(
        text = remember(reading.timestamp) {
            reading.timestamp
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))
        },
        style = MaterialTheme.typography.bodyMedium,
        // On one line, its column wide enough for a 12-hour time
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.weight(1.6f),
    )
    Text(
        text = buildString {
            append(reading.pressure.string(state.pressureUnit))
            if (reading.isAlarm) append(" $leak")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (reading.isAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1.3f),
    )
    Text(
        text = reading.temperature.string(state.temperatureUnit),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = reading.batteryPercent?.let { "$it %" } ?: reading.batteryVoltage?.string() ?: "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
    )
}

/** These seconds since the epoch */
@Suppress("MagicNumber")
private fun Double.toInstant(): Instant = Instant.ofEpochMilli((this * 1000).toLong())

@Preview
@Composable
internal fun SensorHistoryPreview() {
    val location = Location.Wheel(FRONT_LEFT)
    SensorHistory(
        state = State.Loaded(
            sensorId = 0x0A0B0C,
            sensor = Sensor(0x0A0B0C, location, PECHAM),
            calibration = PressureCalibration(5f.kpa, 1f),
            readings = List(6) { index ->
                Tyre.Located(
                    timestamp = 1_763_380_000.0 - index * 40_000,
                    rssi = -60,
                    sensorId = 0x0A0B0C,
                    pressure = (2.4f - index * 0.02f).bar,
                    temperature = (21f - index).celsius,
                    brand = PECHAM,
                    isAlarm = index == 2,
                    location = location,
                    batteryVoltage = 2.9f.volts,
                )
            },
            pressureUnit = BAR,
            temperatureUnit = CELSIUS,
        ),
        export = {},
    )
}

@Preview
@Composable
internal fun SensorHistoryEmptyPreview() {
    SensorHistory(
        state = State.Loaded(
            sensorId = 0x0A0B0C,
            sensor = null,
            calibration = null,
            readings = emptyList(),
            pressureUnit = BAR,
            temperatureUnit = CELSIUS,
        ),
        export = {},
    )
}

@Suppress("ConstPropertyName")
internal object SensorHistoryTags {
    const val root = "SensorHistoryTags_root"
    const val count = "SensorHistoryTags_count"
    const val list = "SensorHistoryTags_list"
    const val export = "SensorHistoryTags_export"
}
