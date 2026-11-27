package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SY_4300
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.YC_0001
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
internal class Sy4300Test {

    // Full raw advertisements captured with nRF Connect from 2 "SY-4300" sensors, 12:30:AF:00:04:5A
    // on a tyre and 12:30:AF:00:09:A1 at 0 psi:
    // 0x08: Shortened Local Name ("SY-4300")
    // 0x02: Incomplete List of 16-bit Service Class UUIDs (0xCC13)
    // 0xFF: Manufacturer Specific Data: status, battery, temperature, pressure, checksum
    private fun capture(hex: String) = assertNotNull(hex.hexToByteArray().decodedTyre(), hex)

    @Test
    fun `on a tyre`() {
        capture("080853592D34333030030213CC07FF001B1B0277D8").also { tyre ->
            assertEquals(SY_4300, tyre.brand)
            assertEquals(48.5f, tyre.pressure.asPsi(), 0.01f)
            assertEquals(27f, tyre.temperature.celsius)
            assertEquals(2.7f, assertNotNull(tyre.batteryVoltage).volts, 0.01f)
            assertEquals(ADDRESS_SENSOR_ID, tyre.sensorId)
            assertFalse(tyre.isAlarm)
        }
    }

    @Test
    fun `off a tyre, it reads 0 psi`() {
        capture("080853592D34333030030213CC07FF001E1A00929E").also { tyre ->
            assertEquals(0f, tyre.pressure.asPsi(), 0.01f)
            assertEquals(26f, tyre.temperature.celsius)
            assertEquals(3f, assertNotNull(tyre.batteryVoltage).volts, 0.01f)
        }
    }

    // 0x04, pressurizing, on the first packets sent from a tyre
    @Test
    fun `the status byte is the first manufacturer data byte`() {
        assertEquals(
            listOf(0x04u.toUByte()),
            AdvertisingPacket("080853592D34333030030213CC07FF041B1B027ACD".hexToByteArray()).statusBytes,
        )
        assertFalse(capture("080853592D34333030030213CC07FF041B1B027ACD").isAlarm)
    }

    @Test
    fun `the air leak bit is the alarm`() {
        assertTrue(capture("080853592D34333030030213CC07FF021B1B027ACD").isAlarm)
    }

    // SY-4300's CRC isn't known: the vendor's app doesn't check it either
    @Test
    fun `an SY-4300 sensor is decoded whatever its last byte and its address`() {
        AdvertisingPacket("080853592D34333030030213CC07FF001B1B027700".hexToByteArray())
            .decode("12:30:AF:00:04:5A")
            .let(::assertNotNull)
    }

    @Test
    fun `another name or too short a manufacturer data isn't decoded as this one`() {
        assertNull("080853592D34333031030213CC07FF001B1B0277D8".hexToByteArray().decoded())
        assertNull("080853592D34333030030213CC06FF001B1B0277".hexToByteArray().decoded())
    }

    // No YC-0001 was captured yet: these are built with the vendor app's CRC, over the frame header
    // 0x02 0x1B, the address in reverse and the first 5 data bytes
    @Test
    fun `a YC-0001 sensor is decoded when its CRC covers its address`() {
        val packet = AdvertisingPacket("08085943 2D30303031 07FF001B1B027748".replace(" ", "").hexToByteArray())
        assertEquals(YC_0001, assertNotNull(packet.decode("12:30:AF:00:04:5A")).brand)
        assertNull(packet.decode("12:30:AF:00:04:5B"))
        // A stored reading has no address, it was checked when received
        assertEquals(YC_0001, assertNotNull(packet.decode()).brand)
    }

    @Test
    fun `the YC-0001 CRC is the vendor's`() {
        assertEquals(0xF2.toByte(), RawSy4300.yc0001Crc("021B1B0092".hexToByteArray(), "12:30:AF:00:04:5A"))
    }
}
