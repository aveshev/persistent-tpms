package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import kotlin.math.roundToInt

/**
 * Builds valid advertisements for every sensor the app decodes: the reverse of the `Raw*`
 * decoders. `MockSensorTest` decodes every encoded advertisement back, so a decoder change that
 * isn't mirrored here fails the tests instead of producing packets the app silently drops.
 *
 * Values are checked against what each advertisement can carry, and rounded to its resolution.
 * `flags` is the status byte each brand broadcasts, most of its bits are unknown: see each entry
 * for where it goes, and its default (what real sensors send).
 */
@Suppress("MagicNumber")
internal enum class MockSensor {

    /**
     * The sensor ID comes from the Bluetooth address, the advertisement doesn't carry one. `flags`
     * is the first data byte, 0x10 by default.
     */
    PECHAM {
        override fun advertisement(reading: Reading): ByteArray =
            reading.pechamLike(name = "BR", pressureOffset = 145, crc = RawPecham.Companion.CRC::of)
    },

    /** Same as [PECHAM] */
    BEKUBEE_KY {
        override fun advertisement(reading: Reading): ByteArray =
            reading.pechamLike(name = "KY", pressureOffset = 146, crc = RawBekubeeKy.Companion.CRC::of)
    },

    /**
     * `flags` is byte 14, the sensor's state: 0x01 "leakage" is the alarm, which real sensors set
     * on the packets they send at once when the pressure drops, 0x02 "inflating". It replaces
     * `isAlarm`. Byte 17 carries the pressure's
     * 9th bit, the decoder adding 256 to the pressure when it is exactly 1.
     */
    WICARLINK {
        // Captured from a real sensor (see WircarlinkTest), only the reading's bytes are replaced
        override fun advertisement(reading: Reading): ByteArray = "0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206"
            .hexToByteArray()
            .also { bytes ->
                (reading.kpa.within("kpa", 0f..1606f) / 3.144f)
                    .roundToInt()
                    .let { pressure ->
                        bytes[12] = pressure.toByte()
                        bytes[17] = (pressure shr 8).toByte()
                    }
                bytes[11] = reading.voltageByte(default = 33)
                bytes[13] = reading.celsius.within("celsius", -55f..200f).roundToInt().plus(55).toByte()
                bytes[14] = (
                    reading.flags
                        ?.also { require(reading.isAlarm.not()) { "Send either flags or alarm for WICARLINK, flags 1 is the alarm" } }
                        ?.within("flags", 0..255)
                        ?: if (reading.isAlarm) 0x01 else 0x00
                    ).toByte()
                reading.id.within("id", 0..0xFFFFFF).let { id ->
                    bytes[23] = id.toByte()
                    bytes[24] = (id shr 8).toByte()
                    bytes[25] = (id shr 16).toByte()
                }
                // The CRC is stored minus the pressure's 9th bit, which sits between its 2 bytes
                (RawWicarlink.Companion.CRC.of(bytes) + bytes[17])
                    .also { check(it <= 0xFFFF) { "No valid CRC for this reading, change any value" } }
                    .let { crc ->
                        bytes[16] = (crc shr 8).toByte()
                        bytes[18] = crc.toByte()
                    }
            }
    },

    /** `flags` is the low byte of the company ID: 0x02 (the default) when mounted, 0x06 unplugged */
    BEKUBEE_TPMS {
        override fun advertisement(reading: Reading): ByteArray = byteArrayOf(
            reading.voltageByte(default = 30),
            reading.celsius.within("celsius", -50f..205f).roundToInt().plus(50).toByte(),
            *reading.kpa.within("kpa", 0f..1000f).roundToInt().plus(100).let { byteArrayOf((it shr 8).toByte(), it.toByte()) },
            *reading.id.within("id", 0..0xFFFFFF).let { byteArrayOf(it.toByte(), (it shr 8).toByte(), (it shr 16).toByte()) },
            0xA8.toByte(), // Unknown, captured from a real sensor (see BekubeeTpmsTest)
            0xB4.toByte(),
        ).let { manufacturerData ->
            // Name "TPMS", manufacturer data under company ID 0x00<flags>, service UUID 0xA827
            "050854504D530CFF".hexToByteArray() +
                byteArrayOf((reading.flags?.within("flags", 0..255) ?: 0x02).toByte(), 0x00) +
                manufacturerData +
                "030327A8".hexToByteArray()
        }
    },

    /** `flags` is byte 15 of the manufacturer data, whose value 0x01 is the alarm: it replaces `isAlarm` */
    SYSGRATION {
        override fun advertisement(reading: Reading): ByteArray = byteArrayOf(
            reading.location.byte.toByte(),
            0xEA.toByte(), // Address checked by the decoder
            0xCA.toByte(),
            // The decoder reads the 3 ID bytes as the 3 upper bytes of the sensor ID
            *reading.id
                .also { require(it and 0xFF == 0) { "id must be a multiple of 256 for SYSGRATION, the decoder reads its 3 bytes shifted by one" } }
                .let { byteArrayOf((it shr 8).toByte(), (it shr 16).toByte(), (it shr 24).toByte()) },
            *reading.kpa.within("kpa", 0f..1000f).times(1000).roundToInt().littleEndian(),
            *reading.celsius.within("celsius", -100f..200f).times(100).roundToInt().littleEndian(),
            (reading.battery ?: 100).within("battery", 0..100).toByte(),
            (
                reading.flags
                    ?.also { require(reading.isAlarm.not()) { "Send either flags or alarm for SYSGRATION, flags 1 is the alarm" } }
                    ?.within("flags", 0..255)
                    ?: if (reading.isAlarm) 0x01 else 0x00
                ).toByte(),
        ).let { manufacturerData ->
            // Flags, service UUID 0xFBB0, manufacturer data under company ID 0x0001
            "0201060303B0FB13FF0100".hexToByteArray() + manufacturerData
        }
    };

    abstract fun advertisement(reading: Reading): ByteArray

    /**
     * @param battery What the app shows as the battery: decivolts (30 for 3.0 V) for every sensor
     * but SYSGRATION, a percentage for it. `null` picks a healthy value.
     * @param id The sensor ID the app shows. Ignored by PECHAM and BEKUBEE_KY, see their doc.
     * @param location Only SYSGRATION advertises its location.
     * @param isAlarm Only SYSGRATION and WICARLINK advertise an alarm. The others report their battery
     * as a voltage, which the app compares to the vehicle's low voltage alarm.
     * @param flags The raw status byte, 0 to 255. `null` sends what real sensors do, see each entry.
     */
    data class Reading(
        val kpa: Float,
        val celsius: Float,
        val battery: Int?,
        val id: Int,
        val location: SensorLocation,
        val isAlarm: Boolean,
        val flags: Int? = null,
    )

    protected fun Reading.pechamLike(name: String, pressureOffset: Int, crc: (ByteArray) -> ByteArray): ByteArray =
        (
            // Service UUID 0x27A5, name, then the data the decoder reads from bytes 10 to 16
            "0303A527".hexToByteArray() +
                byteArrayOf(0x03, 0x08) + name.toByteArray() +
                byteArrayOf(0x08, 0xFF.toByte()) +
                byteArrayOf(
                    (flags?.within("flags", 0..255) ?: 0x10).toByte(),
                    (battery ?: 30).within("battery", 0..127).toByte(),
                    celsius.within("celsius", -128f..127f).roundToInt().toByte(),
                    *Pressure(kpa.within("kpa", 0f..1000f))
                        .asPsi()
                        .times(10)
                        .roundToInt()
                        .plus(pressureOffset)
                        .let { byteArrayOf((it shr 8).toByte(), it.toByte()) },
                )
            )
            .let { it + crc(it) }

    /** Wicarlink and Bekubee TPMS: volts = byte * 0.01 + 1.22 */
    protected fun Reading.voltageByte(default: Int): Byte = (battery ?: default)
        .within("battery", 13..37)
        .times(10)
        .minus(122)
        .toByte()

    protected fun Float.within(name: String, range: ClosedFloatingPointRange<Float>): Float =
        also { require(it in range) { "$name must be within $range for ${this@MockSensor}" } }

    protected fun Int.within(name: String, range: IntRange): Int =
        also { require(it in range) { "$name must be within $range for ${this@MockSensor}" } }

    protected fun Int.littleEndian(): ByteArray = ByteBuffer.allocate(4).order(LITTLE_ENDIAN).putInt(this).array()
}
