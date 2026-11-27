package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant.V1
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant.V2
import com.masselis.tpmsadvanced.data.vehicle.model.variant
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
    fun `the pressure is in units of 3_144 kPa, not rounded to 0_1 bar`() {
        listOf(
            // Two older sensors, from 140 to 590 kPa: only ever the nearest unit to each 0.1 bar
            "0201060303B0FB12FFAC00C22D51000DC2001C10FF1100C52122" to 141.48f,
            "0201060303B0FB12FFAC00C73056000DAE00FA10FF1100C52122" to 150.912f,
            "0201060303B0FB12FFAC00C63355000D6400BA10FF1100C52122" to 160.344f,
            "0201060303B0FB12FFAC00C35350010D9A00D710FF1100C42122" to 260.952f,
            "0201060303B0FB12FFAC00C36353000D3F007C10FF1100C42122" to 311.256f,
            "0201060303B0FB12FFAC00C47350010D3800E610FF1100C42122" to 361.56f,
            "0201060303B0FB12FFAC00C48657020DB2006E10FF1100C42122" to 421.296f,
            "0201060303B0FB12FFAC00C68F54000DB900EB10FF1100C42122" to 449.592f,
            "0201060303B0FB12FFAC00C6A253020DE2008C10FF1100C42122" to 509.328f,
            "0201060303B0FB12FFAC00C7BC52000D7900AC10FF1100C52122" to 591.072f,
            // Two newer sensors on a ride: every unit, which 0.1 bar steps turned into 170, 180,
            // 180, then 240 three times
            "0201060303B0FB12FFAC00B63757030AF800272811111101555B" to 172.92f,
            "0201060303B0FB12FFAC00B73858000A0B00942811111101555B" to 176.064f,
            "0201060303B0FB12FFAC00B63959000ABF00ED2811111101555B" to 179.208f,
            "0201060303B0FB12FFAC00B84B5F000A33008D2811111151535B" to 235.8f,
            "0201060303B0FB12FFAC00B84C59020AAF00E82811111151535B" to 238.944f,
            "0201060303B0FB12FFAC00B94D5E000AAE00492811111151535B" to 242.088f,
        ).forEach { (sample, kpa) ->
            assertEquals(kpa, assertNotNull(sample.hexToByteArray().decodedTyre(), sample).pressure.kpa, 0.01f, sample)
        }
    }

    @Test
    fun `a pressure drop is an alarm`() {
        // A real sensor on a test stand: a scheduled packet, then the one sent at once when some
        // air was let out
        assertFalse(assertNotNull("0201060303B0FB12FFAC00C78952000D3800D510FF1100C42122".hexToByteArray().decodedTyre()).isAlarm)
        assertTrue(assertNotNull("0201060303B0FB12FFAC00C67C53010D0400F010FF1100C42122".hexToByteArray().decodedTyre()).isAlarm)
    }

    @Test
    fun `the older and newer sensors are told apart, from what they send`() {
        assertEquals(V1, assertNotNull("0201060303B0FB12FFAC00C22D51000DC2001C10FF1100C52122".hexToByteArray().decodedTyre()).variant)
        assertEquals(V2, assertNotNull("0201060303B0FB12FFAC00B63757030AF800272811111101555B".hexToByteArray().decodedTyre()).variant)
    }
}
