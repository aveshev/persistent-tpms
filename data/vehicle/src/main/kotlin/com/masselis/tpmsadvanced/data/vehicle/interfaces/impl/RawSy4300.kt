package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SY_4300
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.YC_0001
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import java.util.UUID.fromString

/**
 * The sensors advertising as "SY-4300" and "YC-0001", both read by the vendor's "Ble tpms" app
 * (`com.csc.tpms`, from the QR code on the SY-4300's box). The two share a format, taken from that
 * app's code (`BluetoothScanForegroundService.parseTpmsDataFromScanRecord`), and SY-4300's was
 * checked against nRF Connect captures of 2 sensors, one on a tyre and one at 0 psi:
 *
 * `080853592D34333030 030213CC 07FF 00 1B 1B 0277 D8`
 *
 * The name, the service UUID 0xCC13 (in an incomplete list) and 6 bytes of manufacturer data,
 * without a company ID: the status, the battery in decivolts, the temperature in °C, the pressure
 * and a checksum. Like Pecham's, whose data has the same layout, the advertisement carries no ID:
 * the sensor is identified by its Bluetooth address.
 *
 * The status bits, as the vendor's app names them, from the lowest: low voltage, air leak,
 * pressurizing and low pressure. The air leak is the alarm, the others are left to the app's own
 * thresholds. A sensor on a tyre sent 0x04 (pressurizing) for its first packets, then 0x00.
 *
 * The pressure is absolute, in steps of 0.1 psi, 146 (14.6 psi, the atmosphere) at 0 psi, like
 * [RawBekubeeKy]. The vendor's app reads the temperature unsigned, it is read signed here, as
 * Pecham's is: no capture shows a temperature below 0 °C yet.
 *
 * The last byte is a CRC-8 (polynomial 0x39, from 0x7E, then XORed with 0x48) over a frame header,
 * the Bluetooth address in reverse and the first 5 data bytes. The vendor's app only checks it for
 * YC-0001, with the header 0x02 0x1B, see [isValidYc0001Crc]. SY-4300's uses the same polynomial but
 * not that header, and isn't checked, as in the vendor's app.
 *
 * YC-0001 is experimental, its label says so: no capture of one exists, it is read as the vendor's
 * app reads it. Its service UUID is unknown, so Android finds it by its name, which iOS can't scan
 * for: the iOS app only finds one if it advertises SY-4300's 0xCC13.
 */
@Suppress("MagicNumber")
internal class RawSy4300 private constructor(
    override val brand: SensorBrand,
    private val data: ByteArray,
) : Raw {

    // The advertisement doesn't carry an ID, the sensor is identified by its Bluetooth address
    override val sensorId: Int? = null

    fun pressure() = (((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF))
        .minus(146)
        .toFloat()
        .div(10)
        .psi

    fun voltage() = (data[1].toInt() and 0xFF).toFloat().div(10f).volts // Returns 2.7 for 27

    fun temperature() = data[2].toFloat().celsius

    fun isAlarm() = data[0].toInt() and AIR_LEAK != 0

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String) = Tyre.Unlocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        isAlarm(),
        voltage(),
        raw,
    )

    companion object {
        internal const val SY_4300_NAME = "SY-4300"
        internal const val YC_0001_NAME = "YC-0001"
        internal val SERVICE_UUID = ParcelUuid(fromString("0000cc13-0000-1000-8000-00805f9b34fb"))

        private const val AIR_LEAK = 0x02
        private const val DATA_LENGTH = 6
        private val YC_0001_HEADER = byteArrayOf(0x02, 0x1B)

        /**
         * [address] is the Bluetooth address the scan reports, which YC-0001's CRC covers: null for
         * a stored reading, which was checked when it was received.
         */
        operator fun invoke(packet: AdvertisingPacket, address: String?): RawSy4300? = packet
            .let {
                when (it.name) {
                    SY_4300_NAME -> SY_4300
                    YC_0001_NAME -> YC_0001
                    else -> null
                }
            }
            ?.let { brand ->
                // The 2 first bytes aren't a company ID, the 6 bytes are the data
                packet
                    .manufacturer
                    ?.let { (companyId, data) -> byteArrayOf(companyId.toByte(), (companyId shr 8).toByte()) + data }
                    ?.takeIf { it.size >= DATA_LENGTH }
                    ?.takeIf { data -> brand != YC_0001 || address == null || isValidYc0001Crc(data, address) }
                    ?.let { RawSy4300(brand, it) }
            }

        internal fun isValidYc0001Crc(data: ByteArray, address: String): Boolean =
            yc0001Crc(data, address) == data[5]

        /** The CRC byte YC-0001 sends after its first 5 data bytes, from the sensor at [address] */
        internal fun yc0001Crc(data: ByteArray, address: String): Byte = YC_0001_HEADER
            .plus(address.split(':').map { it.toInt(16).toByte() }.reversed())
            .plus(data.copyOfRange(0, 5))
            .fold(0x7E) { crc, byte ->
                (0 until 8).fold(crc xor (byte.toInt() and 0xFF)) { bits, _ ->
                    (if (bits and 0x80 != 0) (bits shl 1) xor 0x39 else bits shl 1) and 0xFF
                }
            }
            .xor(0x48)
            .toByte()
    }
}
