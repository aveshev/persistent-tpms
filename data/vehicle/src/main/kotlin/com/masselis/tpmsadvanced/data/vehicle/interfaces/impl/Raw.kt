package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre

/** A decoded advertisement, see [AdvertisingPacket.decode] */
internal interface Raw {
    val brand: SensorBrand

    /** null for the sensors identified by their Bluetooth address, the advertisement not carrying it */
    val sensorId: Int?

    /** Which of the brand's kinds of sensors sent it, null when it makes a single one or can't tell */
    val variant: SensorVariant?
        get() = null

    /**
     * [raw] is the advertisement in hexadecimal, kept with the reading. [sensorId] is the ID read
     * from the advertisement, or the one the sensor was given from its Bluetooth address.
     */
    fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String): Tyre.SensorInput
}

/** The ID of a sensor identified by its Bluetooth address, see [Raw.sensorId] */
internal fun sensorIdOf(address: String): Int = address.hashCode()
