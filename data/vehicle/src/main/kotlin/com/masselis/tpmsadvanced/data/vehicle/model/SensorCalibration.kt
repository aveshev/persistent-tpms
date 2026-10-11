package com.masselis.tpmsadvanced.data.vehicle.model

/**
 * A sensor's own [calibration], applied while [isEnabled]. Turning it off keeps the values, like the
 * vehicle's calibration does.
 */
public data class SensorCalibration(
    public val isEnabled: Boolean,
    public val calibration: PressureCalibration,
) {
    /** The calibration to apply, null while it's off */
    public val applied: PressureCalibration?
        get() = calibration.takeIf { isEnabled }

    /** Changes the pressures read, losing it loses something */
    public val adjusts: Boolean
        get() = applied?.adjusts == true

    public companion object {
        /** No correction, what a sensor starts with */
        public val None: SensorCalibration = SensorCalibration(false, PressureCalibration(Pressure(0f), 1f))
    }
}
