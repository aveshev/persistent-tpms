package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

/**
 * A sensor's advertisement, as broadcast: what a live scan receives and what the database stores
 * (`Reading.raw`). Both are decoded the same way, by [decode], so a stored reading reads back as it
 * did when it was received, and a decoder change applies to the stored readings as well.
 *
 * The AD structures are read the way Android's `ScanRecord` reads them, the positions the decoders
 * use are the advertisement's bytes, or its manufacturer data.
 */
@Suppress("MagicNumber")
internal class AdvertisingPacket(val bytes: ByteArray) {

    /** The AD structures, by type: the length-prefixed blocks an advertisement is made of */
    private val structures: Map<Int, ByteArray> = buildMap {
        var index = 0
        while (index < bytes.size && bytes[index] != 0.toByte()) {
            val length = bytes[index].toInt() and 0xFF
            if (index + length >= bytes.size) break
            putIfAbsent(bytes[index + 1].toInt() and 0xFF, bytes.copyOfRange(index + 2, index + 1 + length))
            index += 1 + length
        }
    }

    val name: String? = (structures[COMPLETE_NAME] ?: structures[SHORT_NAME])?.decodeToString()

    val flags: Int? = structures[FLAGS]?.firstOrNull()?.toInt()?.and(0xFF)

    /** The company ID, in little endian, then the data */
    val manufacturer: Pair<Int, ByteArray>? = structures[MANUFACTURER_DATA]
        ?.takeIf { it.size >= 2 }
        ?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) to it.copyOfRange(2, it.size) }

    val serviceUuids16: List<Int> = listOfNotNull(structures[INCOMPLETE_UUIDS_16], structures[COMPLETE_UUIDS_16])
        .flatMap { it.toList().chunked(2) { (low, high) -> (low.toInt() and 0xFF) or ((high.toInt() and 0xFF) shl 8) } }

    /** null when this isn't the advertisement of a known sensor, or it doesn't pass its checks */
    fun decode(): Raw? = RawPecham(this)
        ?: RawBekubeeKy(this)
        ?: RawWicarlink(this)
        ?: RawBekubeeTpms(this)
        ?: RawSysgration(this)

    /**
     * The status bytes: shown for debugging, since what most of their bits mean is unknown. Only
     * what is known or suspected to be a status byte is returned, so what counts as one can change
     * here and apply to every stored reading. null when the advertisement isn't one of the known
     * sensors, or has no status byte.
     */
    val statusBytes: List<UByte>? = when {
        // Status, battery, temperature, pressure then CRC: "ARSB2H1y", most significant bit first
        // (andi38/TPMS). Bekubee KY is a copy of Pecham's format, with the same byte assumed.
        name == "BR" || name == "KY" -> listOf(10)
            .takeIf { bytes.size > 10 }
            ?.map { bytes[it].toUByte() }

        // The company ID changes with the state: 0x02 mounted, 0x06 unplugged
        name == "TPMS" -> manufacturer?.let { (companyId) -> listOf((companyId and 0xFF).toUByte()) }

        // The alarm byte, 0x01 being the only known value
        manufacturer?.second?.let { it.size > 15 && it[1] == 0xEA.toByte() && it[2] == 0xCA.toByte() } == true ->
            listOf(manufacturer.second[15].toUByte())

        // Wicarlink's state, as the official app names it: 0x01 "leakage", on the packets sent at once
        // when the pressure drops, 0x02 "inflating", 0x00 on the ones it sends every 5 and a half
        // minutes (TPMS-advanced#428, bkbilly/tpms_ble#27)
        SERVICE_FBB0 in serviceUuids16 -> listOf(14)
            .takeIf { bytes.size > 14 }
            ?.map { bytes[it].toUByte() }

        else -> null
    }

    companion object {
        private const val FLAGS = 0x01
        private const val INCOMPLETE_UUIDS_16 = 0x02
        private const val COMPLETE_UUIDS_16 = 0x03
        private const val SHORT_NAME = 0x08
        private const val COMPLETE_NAME = 0x09
        private const val MANUFACTURER_DATA = 0xFF
        private const val SERVICE_FBB0 = 0xFBB0
    }
}
