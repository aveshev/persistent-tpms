package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.AdvertisingPacket
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.parcelize.Parcelize

public sealed interface Tyre : Parcelable {
    public val timestamp: Double
    public val rssi: Int
    public val sensorId: Int
    public val pressure: Pressure
    public val temperature: Temperature

    public val brand: SensorBrand

    /** An alarm raised by the sensor itself, only Sysgration sensors send one */
    public val isAlarm: Boolean

    /** Null for the sensors which don't report their battery as a voltage, see [SensorBrand.batteryUnit] */
    public val batteryVoltage: Voltage?

    /**
     * The whole advertisement as received, in hexadecimal: what the database stores, the other
     * values being decoded from it when read back. null only for the readings previews and tests
     * make up, which aren't stored.
     */
    public val raw: String?

    /** Null for the sensors which report their battery as a voltage, see [batteryVoltage] */
    public val batteryPercent: Int?

    public sealed interface SensorInput : Tyre

    @Parcelize
    public data class Unlocated(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val brand: SensorBrand,
        override val isAlarm: Boolean,
        override val batteryVoltage: Voltage? = null,
        override val raw: String? = null,
        override val batteryPercent: Int? = null,
    ) : Tyre, SensorInput

    @Parcelize
    public data class Located(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val brand: SensorBrand,
        override val isAlarm: Boolean,
        val location: Location,
        override val batteryVoltage: Voltage? = null,
        override val raw: String? = null,
        override val batteryPercent: Int? = null,
    ) : Tyre {
        public constructor(tyre: Tyre, location: Location) : this(
            tyre.timestamp,
            tyre.rssi,
            tyre.sensorId,
            tyre.pressure,
            tyre.temperature,
            tyre.brand,
            tyre.isAlarm,
            location,
            tyre.batteryVoltage,
            tyre.raw,
            tyre.batteryPercent,
        )
    }

    @Parcelize
    public data class SensorLocated(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val brand: SensorBrand,
        override val isAlarm: Boolean,
        val location: SensorLocation,
        override val batteryVoltage: Voltage? = null,
        override val raw: String? = null,
        override val batteryPercent: Int? = null,
    ) : Tyre, SensorInput
}

/**
 * The status bytes of the packet, as broadcast, read from [Tyre.raw]: shown for debugging, since
 * what most of their bits mean is unknown. One byte for most sensors, several candidates for the
 * ones whose status byte isn't found yet. null when the sensor has none, or without [Tyre.raw].
 */
@OptIn(ExperimentalStdlibApi::class)
public val Tyre.flags: List<UByte>?
    get() = raw?.let { AdvertisingPacket(it.hexToByteArray()).statusBytes }

/**
 * The atmosphere shown for this record, its pressure corrected by [calibration] when the vehicle has
 * one
 */
public fun Tyre.toAtmosphere(calibration: PressureCalibration?): TyreAtmosphere = TyreAtmosphere(
    timestamp,
    sensorId,
    pressure,
    temperature,
    batteryVoltage,
    isAlarm,
    flags,
    batteryPercent,
).let { atmosphere ->
    calibration
        ?.let { atmosphere.copy(pressure = it.applyTo(atmosphere.pressure)) }
        ?: atmosphere
}

/** The atmosphere shown for this record, corrected by the calibration of the sensor which sent it */
public fun Tyre.toAtmosphere(calibrations: PressureCalibrations): TyreAtmosphere =
    toAtmosphere(calibrations.of(sensorId))
