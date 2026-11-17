package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
public data class TyreAtmosphere(
    val timestamp: Double,
    val sensorId: Int,
    val pressure: Pressure,
    val temperature: Temperature,
    val batteryVoltage: Voltage? = null,
    /** See [Tyre.isAlarm], the pressure and temperature are still the ones the sensor read */
    val isSensorAlarm: Boolean = false,
    // See Tyre.flags
    val flags: List<UByte>? = null,
    /** Sysgration's battery, which isn't a voltage */
    val batteryPercent: Int? = null,
    /** The signal (dBm) the record was received at, null when unknown */
    val rssi: Int? = null,
) : Parcelable
