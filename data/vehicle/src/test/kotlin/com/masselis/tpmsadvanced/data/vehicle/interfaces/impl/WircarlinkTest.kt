package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
@OptIn(ExperimentalStdlibApi::class)
internal class WircarlinkTest {

    // 0x03: Complete List of 16-bit Service Class UUIDs
    // 0x08: Shortened Local Name
    // 0xFF: Proprietary data

    private val samples = listOf(
        // From https://github.com/VincentMasselis/TPMS-advanced/issues/428
        // Unlocated(timestamp=1.78653863791E9, rssi=-60, sensorId=402095, pressure=Pressure(kpa=210.64801), temperature=Temperature(celsius=27.0), battery=33, isAlarm=false)
        "0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206",
    )
    @Test
    fun realValue() {
        samples
            .mapNotNull { it.hexToByteArray().decodedTyre() }
            .onEach(::println)
            .also { assertEquals(samples.size, it.size) }
            .forEach { tyre ->
                assertEquals(WICARLINK, tyre.brand)
                assertEquals(402095, tyre.sensorId)
                assertFalse(tyre.isAlarm)
                assertEquals(3.3f, assertNotNull(tyre.batteryVoltage).volts, absoluteTolerance = 0.005f)
            }
    }

    @Test
    fun `a pressure drop is an alarm`() {
        // A real sensor on a test stand: a scheduled packet, then the one sent at once when some
        // air was let out
        assertFalse(assertNotNull("0201060303B0FB12FFAC00C78952000D3800D510FF1100C42122".hexToByteArray().decodedTyre()).isAlarm)
        assertTrue(assertNotNull("0201060303B0FB12FFAC00C67C53010D0400F010FF1100C42122".hexToByteArray().decodedTyre()).isAlarm)
    }
}
