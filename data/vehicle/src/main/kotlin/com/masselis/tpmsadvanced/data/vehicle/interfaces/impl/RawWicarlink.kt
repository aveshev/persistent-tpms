package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.os.ParcelUuid
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import java.util.UUID.fromString
import kotlin.math.roundToInt

@Suppress("MagicNumber")
internal class RawWicarlink private constructor(private val data: ByteArray) : Raw {

    override val brand = WICARLINK

    override val sensorId: Int = (data[23].toInt() and 0xFF) or
        ((data[24].toInt() and 0xFF) shl 8) or
        ((data[25].toInt() and 0xFF) shl 16)

    // The sensor reads steps of 0.1 bar, sent rounded to units of 3.144 kPa: 48 units are its 150
    // kPa, not 150.9. Every value of real sensors is the nearest unit to a step.
    fun pressure() = (data[12].toInt() and 0xFF)
        .let { raw -> if ((data[17].toInt() and 0xFF) == 1) raw + 256 else raw }
        .times(KPA_PER_UNIT)
        .div(KPA_PER_STEP)
        .roundToInt()
        .times(KPA_PER_STEP)
        .kpa

    fun voltage() = (data[11].toInt() and 0xFF) * 0.01f + 1.22f // Returns 2.7 for 2.7 Volts

    fun temperature() = ((data[13].toInt() and 0xFF) - 55).toFloat().celsius

    // The state the official app shows as "leakage": set on the packets sent at once when the
    // pressure drops, which a fall in temperature may cause too. 0x02, "inflating", isn't an alarm.
    fun isAlarm() = data[14] == LEAKAGE_STATE

    override fun asTyre(timestamp: Double, rssi: Int, sensorId: Int, raw: String): Tyre.SensorInput = Tyre.Unlocated(
        timestamp,
        rssi,
        sensorId,
        pressure(),
        temperature(),
        brand,
        isAlarm(),
        voltage().volts,
        raw,
    )

    companion object {
        private const val LEAKAGE_STATE: Byte = 0x01
        internal const val KPA_PER_UNIT = 3.144f

        /** 0.1 bar */
        internal const val KPA_PER_STEP = 10f

        internal val SERVICE_UUID = ParcelUuid(fromString("0000fbb0-0000-1000-8000-00805f9b34fb"))

        operator fun invoke(packet: AdvertisingPacket): RawWicarlink? = packet
            .takeIf { it.flags == 0x06 && 0xFBB0 in it.serviceUuids16 }
            ?.bytes
            ?.takeIf { it.size >= 26 && CRC.validate(it) }
            ?.let(::RawWicarlink)

        // Reversed engineered from the official LYTPMS app with the help of Claude
        internal object CRC {
            fun validate(bytes: ByteArray): Boolean {
                val expected = (((bytes[16].toInt() and 0xFF) shl 8) or (bytes[18].toInt() and 0xFF)) - bytes[17]
                return of(bytes) == expected
            }

            /** The CRC of an advertisement, it covers bytes 9 to 15 and 19 to 25 */
            fun of(bytes: ByteArray): Int = ByteArray(14)
                .also { bytes.copyInto(it, destinationOffset = 0, startIndex = 9, endIndex = 16) }
                .also { bytes.copyInto(it, destinationOffset = 7, startIndex = 19, endIndex = 26) }
                .let { crc16XMODEM(it) }

            private fun crc16XMODEM(bytes: ByteArray): Int {
                var crc = 0
                for (byte in bytes) {
                    crc = crc xor ((byte.toInt() and 0xFF) shl 8)
                    repeat(8) {
                        crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                    }
                }
                return crc and 0xFFFF
            }
        }
    }
}
