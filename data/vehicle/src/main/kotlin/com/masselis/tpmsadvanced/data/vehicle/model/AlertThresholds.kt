package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts

/**
 * A tyre's thresholds, and the alert level of a reading against them, see docs/alerts.md. A value
 * exactly on a boundary is at the more severe level: a tyre exactly at its minimum pressure is red.
 */
public data class AlertThresholds(
    val lowPressure: Pressure,
    val highPressure: Pressure,
    val highTemp: Temperature,
    val lowBatteryVoltage: Voltage,
    /** Sysgration's alarm, its sensors reporting a percentage instead of a voltage */
    val lowBatteryPercent: Int = DEFAULT_LOW_BATTERY_PERCENT,
    /** Off for a spare, which isn't driven on: red at worst, never crimson */
    val allowsCritical: Boolean = true,
) {

    /**
     * The level of each class [atmosphere] alerts for, the classes it doesn't alert for being left
     * out.
     */
    public fun levels(
        atmosphere: TyreAtmosphere,
        isLeaking: Boolean,
    ): Map<AlertClass, AlertLevel> = buildMap {
        pressureLevel(atmosphere.pressure)?.also { put(PRESSURE, it) }
        temperatureLevel(atmosphere.temperature)?.also { put(TEMPERATURE, it) }
        (
            atmosphere.batteryVoltage?.let { batteryLevel(it, atmosphere.temperature) }
                ?: atmosphere.batteryPercent?.let(::batteryLevel)
            )
            ?.also { put(BATTERY, it) }
        if (isLeaking) put(PRESSURE_LOSS, AMBER)
        if (atmosphere.isSensorAlarm) put(SENSOR_ALARM, AMBER)
    }

    /** The worse of the low and the high side, null while in range */
    public fun pressureLevel(pressure: Pressure): AlertLevel? = listOfNotNull(
        when {
            pressure.kpa <= lowPressure.kpa * CRIMSON_LOW_PRESSURE + EPSILON -> CRIMSON
            pressure.kpa <= lowPressure.kpa + EPSILON -> RED
            pressure.kpa <= lowPressure.kpa * AMBER_LOW_PRESSURE + EPSILON -> AMBER
            else -> null
        },
        when {
            pressure.kpa >= highPressure.kpa * CRIMSON_HIGH_PRESSURE - EPSILON -> CRIMSON
            pressure.kpa >= highPressure.kpa - EPSILON -> RED
            pressure.kpa >= highPressure.kpa * AMBER_HIGH_PRESSURE - EPSILON -> AMBER
            else -> null
        },
    ).maxOrNull()?.capped()

    /** On the high side of the range, which tells which way a pressure alert goes */
    public fun isHighPressure(pressure: Pressure): Boolean =
        pressure.kpa * 2 >= lowPressure.kpa + highPressure.kpa

    public fun temperatureLevel(temperature: Temperature): AlertLevel? = when {
        temperature.celsius >= highTemp.celsius + CRIMSON_TEMPERATURE_ABOVE - EPSILON -> CRIMSON
        temperature.celsius >= highTemp.celsius - EPSILON -> RED
        temperature.celsius >= highTemp.celsius - AMBER_TEMPERATURE_BELOW - EPSILON -> AMBER
        else -> null
    }?.capped()

    private fun AlertLevel.capped(): AlertLevel = if (this == CRIMSON && allowsCritical.not()) RED else this

    /** Against the low voltage alarm adjusted to the sensor's [temperature], see [Voltage.alarmAt] */
    public fun batteryLevel(voltage: Voltage, temperature: Temperature): AlertLevel? = lowBatteryVoltage
        .alarmAt(temperature)
        .let { alarm ->
            when {
                voltage.isAtOrBelow(alarm) -> RED
                voltage.isAtOrBelow(alarm + BATTERY_AMBER_MARGIN) -> AMBER
                else -> null
            }
        }

    /**
     * Not adjusted to the temperature like a voltage is: how the sensor turns its voltage into a
     * percentage is unknown, it may already be
     */
    public fun batteryLevel(percent: Int): AlertLevel? = when {
        percent <= lowBatteryPercent -> RED
        percent <= lowBatteryPercent + BATTERY_AMBER_MARGIN_PERCENT -> AMBER
        else -> null
    }

    public companion object {
        /** Amber from the low voltage alarm up to this much above it */
        public val BATTERY_AMBER_MARGIN: Voltage = 0.1f.volts

        /** Amber from the low percentage alarm up to this many points above it */
        public const val BATTERY_AMBER_MARGIN_PERCENT: Int = 10

        /** The schema's default for [lowBatteryPercent] */
        public const val DEFAULT_LOW_BATTERY_PERCENT: Int = 10

        private const val AMBER_LOW_PRESSURE = 1.03f
        /** 25% below, where FMVSS 138 has cars' tyre pressure warning light turn on */
        private const val CRIMSON_LOW_PRESSURE = 0.75f
        private const val AMBER_HIGH_PRESSURE = 0.97f
        private const val CRIMSON_HIGH_PRESSURE = 1.2f

        /** In degrees Celsius whatever the displayed unit */
        private const val AMBER_TEMPERATURE_BELOW = 10f
        private const val CRIMSON_TEMPERATURE_ABOVE = 20f

        /**
         * Tolerates the float rounding of the thresholds, converted from the unit they were typed
         * in, and of the margins applied to them. Far below any sensor's resolution, in kPa or in
         * degrees Celsius.
         */
        private const val EPSILON = 0.01f
    }
}
