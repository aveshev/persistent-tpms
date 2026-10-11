package com.masselis.tpmsadvanced.data.vehicle.model

/**
 * The calibration of each of a vehicle's sensors, only the ones turned on. A reading is corrected by
 * the sensor which sent it, so a moved sensor's past readings keep its calibration.
 */
@JvmInline
public value class PressureCalibrations(private val bySensorId: Map<Int, PressureCalibration>) {

    public fun of(sensorId: Int): PressureCalibration? = bySensorId[sensorId]

    public companion object {
        public val None: PressureCalibrations = PressureCalibrations(emptyMap())
    }
}
