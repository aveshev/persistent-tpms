package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TC_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import java.util.UUID.fromString

/**
 * An AOOCCI sensor of the Bekubee family, advertising as "TC.TPMS" with the service UUID 0xA828
 * (the "TPMS" one of [RawBekubeeTpms] uses 0xA827). Reverse engineered from nRF Connect captures of
 * one sensor at 0, about 23 and about 53 psi:
 *
 * `080854432E54504D53 0CFF 01 1E 20 017A 34300710809C 030328A8`
 *
 * The manufacturer data's 2 first bytes aren't a company ID: they are a status byte and the battery
 * in decivolts. The status byte follows the pressure's last change: 0x01 after it rose, 0x02 after
 * it fell, 0x00 once steady (seen mounted on a tyre). Shown for debugging only, no alarm is read
 * from it.
 *
 * Then come the temperature in °C, the pressure and the sensor's Bluetooth address. The pressure is
 * absolute, in steps of 0.1 psi, reading 146 (14.6 psi, the atmosphere) at 0 psi, the same encoding
 * as [RawBekubeeKy].
 */
@Suppress("MagicNumber")
internal class RawBekubeeTcTpms private constructor(
    private val companyId: Int,
    private val data: ByteArray,
) : Raw {

    override val brand = BEKUBEE_TC_TPMS

    // The last 3 bytes of the Bluetooth address the advertisement carries, the 3 first ones being
    // the vendor's. Read from the advertisement rather than the address the scan reports, so it
    // doesn't depend on the platform giving it.
    override val sensorId: Int = (data[6].toInt() and 0xFF shl 16) or
        (data[7].toInt() and 0xFF shl 8) or
        (data[8].toInt() and 0xFF)

    fun pressure() = (((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF))
        .minus(146)
        .toFloat()
        .div(10)
        .psi

    // Returns 3.0 for 30
    fun voltage() = (companyId shr 8 and 0xFF).toFloat().div(10f).volts

    fun temperature() = data[0].toFloat().celsius

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String) = Tyre.Unlocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        // The low battery is reported through batteryVoltage, no alarm is known for these sensors
        false,
        voltage(),
        raw,
    )

    companion object {
        internal const val NAME = "TC.TPMS"
        internal val SERVICE_UUID = ParcelUuid(fromString("0000a828-0000-1000-8000-00805f9b34fb"))

        private const val MANUFACTURER_DATA_LENGTH = 9

        operator fun invoke(packet: AdvertisingPacket): RawBekubeeTcTpms? = packet
            .takeIf { it.name == NAME && 0xA828 in it.serviceUuids16 }
            ?.manufacturer
            ?.takeIf { (_, data) -> data.size >= MANUFACTURER_DATA_LENGTH }
            ?.let { (companyId, data) -> RawBekubeeTcTpms(companyId, data) }
    }
}
