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
    fun `the pressure is the sensor's step of 0_1 bar`() {
        // Two real sensors' readings, from 140 to 590 kPa: never 1 or 2 units apart, the nearest
        // unit of 3.144 kPa to each step of 10 kPa
        listOf(
            "0201060303B0FB12FFAC00C22D51000DC2001C10FF1100C52122" to 140f,
            "0201060303B0FB12FFAC00C73056000DAE00FA10FF1100C52122" to 150f,
            "0201060303B0FB12FFAC00C63355000D6400BA10FF1100C52122" to 160f,
            "0201060303B0FB12FFAC00C35350010D9A00D710FF1100C42122" to 260f,
            "0201060303B0FB12FFAC00C36353000D3F007C10FF1100C42122" to 310f,
            "0201060303B0FB12FFAC00C47350010D3800E610FF1100C42122" to 360f,
            "0201060303B0FB12FFAC00C48657020DB2006E10FF1100C42122" to 420f,
            "0201060303B0FB12FFAC00C68F54000DB900EB10FF1100C42122" to 450f,
            "0201060303B0FB12FFAC00C6A253020DE2008C10FF1100C42122" to 510f,
            "0201060303B0FB12FFAC00C7BC52000D7900AC10FF1100C52122" to 590f,
        ).forEach { (sample, kpa) ->
            assertEquals(kpa, assertNotNull(sample.hexToByteArray().decodedTyre(), sample).pressure.kpa, sample)
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
