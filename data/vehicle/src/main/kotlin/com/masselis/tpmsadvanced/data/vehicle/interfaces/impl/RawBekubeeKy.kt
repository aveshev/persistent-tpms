package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import java.util.UUID.fromString

/**
 * This a sensor from the HRTPMS app, see [pull request](https://github.com/VincentMasselis/TPMS-advanced/pull/446)
 *
 * Copied from [RawPecham], the only changes are name filtering ("KY" instead of "BR"), the
 * pressure's offset and the CRC tables
 */
@Suppress("MagicNumber", "MaxLineLength")
internal class RawBekubeeKy private constructor(private val data: ByteArray) : Raw {

    override val brand = BEKUBEE_KY

    // The advertisement doesn't carry an ID, the sensor is identified by its Bluetooth address
    override val sensorId: Int? = null

    fun pressure() = (((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF))
        .minus(146)
        .toFloat()
        .div(10)
        .psi

    fun voltage() = data[1].toFloat().div(10f).volts // Returns 2.7 for 27

    fun temperature() = data[2].toFloat().celsius

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String) = Tyre.Unlocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        // The low battery is reported through batteryVoltage, these sensors send no alarm
        false,
        voltage(),
        raw,
    )

    companion object {
        internal val SERVICE_UUID = ParcelUuid(fromString("000027a5-0000-1000-8000-00805f9b34fb"))

        operator fun invoke(packet: AdvertisingPacket): RawBekubeeKy? = packet
            .takeIf { it.name == "KY" }
            // The manufacturer data's first 2 bytes aren't a company ID, its 7 bytes are read from
            // the advertisement's
            ?.bytes
            ?.takeIf { it.size >= 17 && CRC.isValid(it) }
            ?.copyOfRange(10, 17)
            ?.let(::RawBekubeeKy)

        internal val CRC = AdvertisementCrc(
            highBytes = byteArrayOf(
                63, -39, -11, -42, -111, 85, 121, -92, 75, -45, -68, -59, 54, 76, -71, -101,
                -104, -85, -35, 52, 113, 123, 1, -66, 53, 14, 112, -100, 26, -113, -36, 102,
                -103, -94, 127, -60, -44, -3, 35, 122, 93, 72, 97, -95, -46, -15, 3, 16,
                3, -36, -61, -111, 92, -56, -57, 8, -27, 72, 65, 35, -43, 45, 45, 13,
                -8, -21, 21, 111, -108, -34, 87, 49, 28, 19, 104, -21, 109, 109, 119, -42,
                23, -97, 77, 96, -58, -44, -73, -96, -40, -37, 20, -69, -103, -29, -126, 39,
                26, -18, 84, 6, 49, 58, -118, -97, -116, 44, -71, -95, 50, -1, -48, -6,
                -115, -21, -84, 29, -88, 30, 94, -45, -81, 110, 48, -105, 45, 27, 57, -39,
                37, 82, 126, 23, -67, 97, 48, 122, 118, 116, 35, 36, -103, -20, -85, 51,
                -95, 112, 27, 105, 98, -52, -90, 96, -101, 111, -80, 124, -58, 115, 61, 109,
                65, -95, 50, 86, -80, 3, 18, 101, -107, -63, 14, -101, -44, 74, 56, 0,
                55, -42, -17, 87, -9, 63, 118, 85, 95, 75, 74, -33, -60, -43, 44, -63,
                66, 58, -117, 94, -106, -71, -113, -15, -103, -61, -47, -108, -17, 96, -4, 41,
                -48, 48, -13, -19, -21, 46, 101, 58, -109, -5, 117, 10, -87, 86, -66, 33,
                -79, -37, 42, 41, -104, -35, 19, 79, -125, -32, 118, -128, 92, -121, -97, -74,
                39, -24, 69, -26, -124, 32, -20, 98, 117, -86, 3, 78, 10, -11, -74, 52,
            ),
            lowBytes = byteArrayOf(
                17, 44, 103, 9, -106, 122, -95, -80, -95, -116, 37, -14, 13, 78, -38, 84,
                119, 53, 71, -22, -63, -120, -120, -6, 8, 78, 65, 19, 86, 64, 124, -17,
                77, 106, 79, 31, -74, -83, -70, -16, 1, 20, 77, 13, -106, -86, -7, 80,
                -61, -73, 92, -22, -122, -86, -70, 94, 91, 9, 102, 0, 78, -31, -11, -1,
                -32, -78, 27, 82, 123, -35, 100, -13, -80, -50, -18, -79, 51, -112, -63, 31,
                33, 1, -31, 9, -55, 89, 75, -85, -62, -23, 36, 69, -23, 75, 59, 104,
                66, -96, -107, -119, 111, 84, 62, 106, -103, 46, -15, -82, -38, -66, 120, 122,
                -42, 55, -29, 46, -100, -79, 103, 40, 59, 32, 87, -68, -23, -72, -2, 102,
                51, 104, -49, -84, 50, -79, -82, -92, -98, -119, -66, 118, 47, -93, 75, 90,
                44, 5, 107, 114, 22, -96, 13, -120, 29, 62, 71, 18, -79, -49, -60, -39,
                84, -127, -126, 66, 87, -21, -96, -18, 25, 123, -92, 125, 57, -90, -113, 115,
                109, 16, -31, -42, 23, -50, -25, -2, 5, 39, -101, 41, -107, 51, -26, 114,
                -2, 90, -89, 123, 91, 8, 34, 119, -115, -30, -60, -82, 48, -56, -47, -47,
                64, -104, 94, -80, 91, 29, 112, -16, -39, -126, 57, 27, -8, -88, -59, -75,
                -11, 48, 107, -57, -106, 1, 23, 81, 37, -88, -102, 70, -104, -25, -127, 91,
                13, -84, 19, 17, 19, 99, 18, -38, 127, -66, 120, -128, 36, -83, 27, -16,
            ),
        )
    }
}
