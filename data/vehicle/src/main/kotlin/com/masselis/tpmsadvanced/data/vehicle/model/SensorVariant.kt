package com.masselis.tpmsadvanced.data.vehicle.model

/**
 * Which of a brand's kinds of sensors sent a packet, for the brands which make several that behave
 * differently: only Wicarlink so far, see RawWicarlink.variant. Read from each packet rather than
 * stored with the sensor, so it applies to the sensors bound before it was known.
 */
public enum class SensorVariant {
    /** Wicarlink's older sensors: in steps of 0.1 bar, every 5.5 minutes */
    V1,

    /** Wicarlink's newer sensors: in steps of 3.1 kPa, every 10 minutes, every 2 riding */
    V2,
}
