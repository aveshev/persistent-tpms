package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.TC_TPMS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalStdlibApi::class)
internal class TcTpmsTest {

    // Full raw advertisements captured with nRF Connect from one "TC.TPMS" sensor bought from
    // Essgoo, address 34:30:07:10:80:9C, with the pressure a gauge read:
    // 0x08: Shortened Local Name ("TC.TPMS")
    // 0xFF: Manufacturer Specific Data: status, battery, temperature, pressure, address
    // 0x03: Complete List of 16-bit Service Class UUIDs (0xA828)
    private fun capture(hex: String) = assertNotNull(hex.hexToByteArray().decodedTyre(), hex)

    @Test
    fun `off the valve, it reads 0 psi`() {
        capture("080854432E54504D530CFF021E1E009234300710809C030328A8").also { tyre ->
            assertEquals(TC_TPMS, tyre.brand)
            assertEquals(0f, tyre.pressure.asPsi(), 0.01f)
            assertEquals(30f, tyre.temperature.celsius)
            assertEquals(3f, assertNotNull(tyre.batteryVoltage).volts, 0.01f)
            assertEquals(0x10809C, tyre.sensorId)
            assertFalse(tyre.isAlarm)
        }
    }

    @Test
    fun `mounted on a tyre at about 53 psi`() {
        capture("080854432E54504D530CFF011E1E02A034300710809C030328A8").also { tyre ->
            assertEquals(52.6f, tyre.pressure.asPsi(), 0.01f)
            assertEquals(0x10809C, tyre.sensorId)
        }
    }

    @Test
    fun `the temperature is the third manufacturer data byte`() {
        capture("080854432E54504D530CFF011E20017A34300710809C030328A8").also { tyre ->
            assertEquals(23.2f, tyre.pressure.asPsi(), 0.01f)
            assertEquals(32f, tyre.temperature.celsius)
            assertEquals(3f, assertNotNull(tyre.batteryVoltage).volts, 0.01f)
        }
    }

    // 0x01 after the pressure rose, 0x02 after it fell
    @Test
    fun `the status byte is the first manufacturer data byte`() {
        listOf(
            "080854432E54504D530CFF021E1E009234300710809C030328A8" to 0x02u,
            "080854432E54504D530CFF011E1E02A034300710809C030328A8" to 0x01u,
        ).forEach { (hex, status) ->
            assertEquals(listOf(status.toUByte()), AdvertisingPacket(hex.hexToByteArray()).statusBytes)
        }
    }

    @Test
    fun `the TPMS sensor's service UUID or another name isn't decoded as this one`() {
        assertEquals(null, "080854432E54504D530CFF021E1E009234300710809C030327A8".hexToByteArray().decoded())
        assertEquals(null, "080854432E58504D530CFF021E1E009234300710809C030328A8".hexToByteArray().decoded())
    }
}
