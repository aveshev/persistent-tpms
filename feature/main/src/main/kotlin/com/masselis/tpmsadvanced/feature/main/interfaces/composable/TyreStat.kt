// One preview per state variant
@file:Suppress("TooManyFunctions")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.ui.Orange
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A sensor id the way the readout shows it: its 3 bytes in order when it fits, in hex otherwise */
@Suppress("MagicNumber")
public fun Int.asSensorId(): String =
    if ((this ushr 24) == 0) "%02X%02X%02X".format(this and 0xFF, (this shr 8) and 0xFF, (this shr 16) and 0xFF)
    else "0x%08X".format(this)

@Composable
internal fun TyreStat(
    location: Location,
    modifier: Modifier = Modifier,
    /** Only the pressure, temperature and time since update, whatever the settings and alerts */
    isBasic: Boolean = false,
    /** Its lines centered, rather than against the tyre */
    isCentered: Boolean = false,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreStatsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreStatsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showSensorFlags by viewModel.showSensorFlags.collectAsState()
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    val showBatteryVoltage by viewModel.showBatteryVoltage.collectAsState()
    // "-:-" until its reading's read, not worth fitting the text to
    HoldTextFit(viewModel.isLoading.collectAsState().value)
    if (isBasic) TyreStat(location, state, isBasic = true, isCentered = isCentered, modifier = modifier)
    else TyreStat(
        location, state, showSensorId, showTimeSinceUpdate, showBatteryVoltage, showSensorFlags,
        isCentered = isCentered,
        modifier = modifier,
    )
}

/** A readout of [state], also shown as a sample of the text size, see [TextSize] */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun TyreStat(
    location: Location,
    state: State,
    showSensorId: Boolean = false,
    showTimeSinceUpdate: Boolean = true,
    showBatteryVoltage: Boolean = false,
    showSensorFlags: Boolean = false,
    isBasic: Boolean = false,
    isCentered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val detected = state as? State.Detected
    val levels = detected?.levels.orEmpty()
    val sensorId = detected?.sensorId
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val alignment = remember(isBasic, isCentered) {
        // Centered inside its outline on the sensors' page, under the spare, or magnified, against
        // the tyre otherwise
        if (isBasic || isCentered || location == Location.Spare) Alignment.CenterHorizontally
        // Against the tyre, on whichever side of it the readout is
        else when (location.readoutSide) {
            LEFT -> Alignment.End
            RIGHT -> Alignment.Start
        }
    }
    Column(modifier = modifier) {
        Reading(
            detected
                ?.pressure
                ?.string(detected.pressureUnit)
                // Explained on the vehicle's calibration page
                ?.let { if (detected.isPressureCalibrated) "$it*" else it }
                ?: "-.--",
            levels[PRESSURE],
            Modifier.align(alignment),
            // Still on the sensors' page, where they're only checked
            blinks = isBasic.not(),
            criticalLabel = if (detected?.isHighPressure == true) "HIGH!" else "LOW!",
        )

        Reading(
            detected?.temperature?.string(detected.temperatureUnit) ?: "-.-",
            levels[TEMPERATURE],
            Modifier.align(alignment),
            fontSize = 16.sp,
            blinks = isBasic.not(),
            // Only a high temperature alerts
            criticalLabel = "HIGH!",
        )

        if (showTimeSinceUpdate && detected != null) {
            Text(
                elapsedSinceUpdateLabel(detected.timestamp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 16.sp,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }

        // The sensor's own alarm, its meaning isn't documented but a leak is the likely one. The
        // pressure and temperature above stay as the sensor read them. The same goes for the
        // pressure falling while riding.
        if (isBasic.not() && (PRESSURE_LOSS in levels || SENSOR_ALARM in levels)) {
            Reading("Leak?", AMBER, Modifier.align(alignment), fontSize = 16.sp)
        }

        // A battery getting low shows whatever the setting
        detected
            ?.let { it.batteryVoltage?.string() ?: it.batteryPercent?.let { percent -> "$percent%" } }
            ?.takeIf { showBatteryVoltage || (isBasic.not() && BATTERY in levels) }
            ?.also { Reading(it, levels[BATTERY], Modifier.align(alignment), fontSize = 16.sp) }

        if (sensorId != null && showSensorId) {
            Text(
                text = sensorId.asSensorId(),
                fontSize = 9.sp,
                lineHeight = SMALL_LINE_HEIGHT,
                maxLines = 1,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }

        // A line per status byte, bit 7 first like the byte written in binary (0x80 lights the
        // leftmost digit), the bits which are not set in the last packet greyed out
        if (showSensorFlags) detected?.flags?.forEach { flag ->
            Text(
                text = buildAnnotatedString {
                    repeat(Byte.SIZE_BITS) { index ->
                        val bit = Byte.SIZE_BITS - 1 - index
                        withStyle(
                            if ((flag.toInt() shr bit) and 1 == 1) SpanStyle(fontWeight = FontWeight.Bold)
                            else SpanStyle(color = onSurfaceColor.copy(alpha = UNSET_FLAG_ALPHA))
                        ) { append("$bit") }
                    }
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = SMALL_LINE_HEIGHT,
                maxLines = 1,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }
    }
}

/**
 * A value in the colour of its alert [level]: blinking while red, alternating with [criticalLabel]
 * while crimson, in sync with the tyres, when it [blinks]. The label is a short word, such as
 * "LOW!", which fits beside the tyre where "CRITICAL" didn't.
 */
@Composable
private fun Reading(
    text: String,
    level: AlertLevel?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    blinks: Boolean = true,
    criticalLabel: String = "",
) = Text(
    if (blinks && level == CRIMSON && isFirstBlinkPhase(CRITICAL_LABEL_BLINK).not()) criticalLabel else text,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    fontSize = fontSize,
    color = when (level) {
        null -> MaterialTheme.colorScheme.onSurface
        AMBER -> Orange
        RED, CRIMSON -> MaterialTheme.colorScheme.error
    },
    modifier = modifier.alpha(if (blinks && level == RED && isFirstBlinkPhase(BLINK).not()) 0f else 1f),
)

private const val UNSET_FLAG_ALPHA = 0.3f

/**
 * The smaller lines' height, in proportion to their own size like the readings' 24 sp for 16 sp,
 * rather than the readings' height, which spaced them out
 */
private val SMALL_LINE_HEIGHT = 1.5.em

// Re-emits on every tier boundary crossed (each minute, then each hour, then each day) so the
// label stays live without waiting for a new sensor packet. A new `timestamp` (new packet)
// restarts this from scratch via the `key1` change, cancelling any pending delay.
@Composable
private fun elapsedSinceUpdateLabel(timestamp: Double): String {
    val label by produceState(initialValue = elapsedLabel(timestamp, now()), key1 = timestamp) {
        // produceState's underlying mutableStateOf survives across key1 changes, only the
        // producer coroutine restarts - so `value` must be set immediately here (not after the
        // first delay) or a new packet would leave the stale label showing until the next tick.
        while (true) {
            value = elapsedLabel(timestamp, now())
            delay(nextElapsedTick(timestamp, now()))
        }
    }
    return label
}

private fun elapsedLabel(timestamp: Double, now: Double): String {
    val totalMinutes = totalMinutesSince(timestamp, now)
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    return when {
        totalMinutes == 0L -> "<1 min"
        totalMinutes < HOUR_TIER_START_MINUTES -> "$totalMinutes min"
        totalHours < DAY_TIER_START_HOURS -> "$totalHours hours"
        totalHours / HOURS_PER_DAY > MAX_DAYS -> "$MAX_DAYS+ days"
        else -> "${totalHours / HOURS_PER_DAY} days"
    }
}

// Delay until the label's next tier boundary: every minute while under 2 hours, every hour
// while under 2 days, every day after that.
private fun nextElapsedTick(timestamp: Double, now: Double): Duration {
    val totalMinutes = totalMinutesSince(timestamp, now)
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    val nextBoundarySeconds = when {
        totalMinutes < HOUR_TIER_START_MINUTES ->
            (totalMinutes + 1) * SECONDS_PER_MINUTE

        totalHours < DAY_TIER_START_HOURS ->
            (totalHours + 1) * MINUTES_PER_HOUR * SECONDS_PER_MINUTE

        else ->
            (totalHours / HOURS_PER_DAY + 1) * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
    }
    return (timestamp + nextBoundarySeconds - now).seconds.coerceAtLeast(Duration.ZERO)
}

private fun totalMinutesSince(timestamp: Double, now: Double): Long =
    ((now - timestamp) / SECONDS_PER_MINUTE).toLong().coerceAtLeast(0L)

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val HOUR_TIER_START_MINUTES = 120L
private const val DAY_TIER_START_HOURS = 48L

// Keeps the label within the width `Vehicle` reserves for readouts
private const val MAX_DAYS = 99L


@Preview
@Composable
internal fun TyreStatNotDetectedPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.NotDetected,
        showTimeSinceUpdate = false,
    )
}

/** 2 bar and 30 °C read by a sensor of a vehicle using bar and degrees Celsius */
@Suppress("LongParameterList")
private fun detected(
    pressure: Pressure = 2f.bar,
    temperature: Temperature = 30f.celsius,
    timestamp: Double = 0.0,
    sensorId: Int = 0,
    isPressureCalibrated: Boolean = false,
    batteryVoltage: Voltage? = null,
    flags: List<UByte>? = null,
    levels: Map<AlertClass, AlertLevel> = emptyMap(),
    batteryPercent: Int? = null,
) = State.Detected(
    timestamp,
    sensorId,
    pressure,
    PressureUnit.BAR,
    temperature,
    TemperatureUnit.CELSIUS,
    isPressureCalibrated,
    batteryVoltage,
    flags,
    levels,
    batteryPercent,
)

@Preview
@Composable
internal fun TyreStatNormalPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatCalibratedPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(isPressureCalibrated = true),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(0.5f.bar, 150f.celsius, levels = mapOf(PRESSURE to CRIMSON, TEMPERATURE to CRIMSON)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatPressureAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(1.6f.bar, levels = mapOf(PRESSURE to RED)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatPressureWarningPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(2.05f.bar, levels = mapOf(PRESSURE to AMBER)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatPressureCriticalPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(0.5f.bar, levels = mapOf(PRESSURE to CRIMSON)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatTemperatureAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(temperature = 95f.celsius, levels = mapOf(TEMPERATURE to RED)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatTemperatureWarningPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(temperature = 85f.celsius, levels = mapOf(TEMPERATURE to AMBER)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatTimeSinceUpdateMinutesPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(timestamp = now() - 5 * SECONDS_PER_MINUTE),
        showTimeSinceUpdate = true,
    )
}

@Preview
@Composable
internal fun TyreStatTimeSinceUpdateHoursPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(timestamp = now() - 5 * MINUTES_PER_HOUR * SECONDS_PER_MINUTE),
        showTimeSinceUpdate = true,
    )
}

@Preview
@Composable
internal fun TyreStatTimeSinceUpdateDaysPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(timestamp = now() - 5 * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE),
        showTimeSinceUpdate = true,
    )
}

@Preview
@Composable
internal fun TyreStatBatteryNormalPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(batteryVoltage = 3f.volts),
        showTimeSinceUpdate = false,
        showBatteryVoltage = true,
    )
}

@Preview
@Composable
internal fun TyreStatBatteryLowSoonPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(batteryVoltage = 2.7f.volts, levels = mapOf(BATTERY to AMBER)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatBatteryLowPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(batteryVoltage = 2.6f.volts, levels = mapOf(BATTERY to RED)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatBatteryPercentLowPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(batteryPercent = 10, levels = mapOf(BATTERY to RED)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatSensorAlarmPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(levels = mapOf(SENSOR_ALARM to AMBER)),
        showTimeSinceUpdate = false,
    )
}

@Preview
@Composable
internal fun TyreStatFlagsPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(sensorId = 0x562D00, flags = listOf(0x83u.toUByte())),
        showSensorId = true,
        showTimeSinceUpdate = false,
        showSensorFlags = true,
    )
}

@Preview
@Composable
internal fun TyreStatPressureLossPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(levels = mapOf(PRESSURE_LOSS to AMBER)),
        showTimeSinceUpdate = false,
    )
}

// Wicarlink's four candidate status bytes, from its only captured packet
@Preview
@Composable
internal fun TyreStatSeveralFlagsPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = detected(sensorId = 0x562D00, flags = listOf(0xACu, 0x00u, 0x00u, 0x08u).map { it.toUByte() }),
        showTimeSinceUpdate = false,
        showSensorFlags = true,
    )
}
