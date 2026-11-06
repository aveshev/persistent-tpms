package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

/**
 * What closes the advertisements of [RawPecham] and [RawBekubeeKy]: a CRC-16 of their first 15
 * bytes, whose 2 bytes each go through a table of their own, the only thing differing between them.
 */
@Suppress("MagicNumber")
internal class AdvertisementCrc(
    private val highBytes: ByteArray,
    private val lowBytes: ByteArray,
) {
    fun isValid(dataWithCRC: ByteArray): Boolean = of(dataWithCRC)
        .let { (high, low) -> high == dataWithCRC[15] && low == dataWithCRC[16] }

    /** The 2 CRC bytes for the first 15 bytes of an advertisement, used by MockSensor */
    fun of(dataWithoutCRC: ByteArray): ByteArray = calculate(dataWithoutCRC)
        .let { byteArrayOf(highBytes[it shr 8 and 0xFF], lowBytes[it and 0xFF]) }

    // Reverse engineered with the help of ChatGPT: the polynomial is 0x8005, the initial value 0xFFFF
    private fun calculate(data: ByteArray): Int = data
        .take(15)
        .fold(0xFFFF) { crc, byte ->
            (7 downTo 0).fold(crc) { bits, bit ->
                (byte.toInt() shr bit and 1 == 1).xor(bits shr 15 and 1 == 1)
                    .let { flipped -> if (flipped) bits shl 1 xor 0x8005 else bits shl 1 }
            }
        }
        .and(0xFFFF)
}
