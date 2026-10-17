package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import org.junit.Test
import kotlin.test.assertEquals

internal class AsSensorIdTest {

    // Advertised as C5 21 22, its cap and its QR code read 2221C5
    @Test
    fun `a Wicarlink id reads like its cap`() {
        assertEquals("2221C5", 0x2221C5.asSensorId(WICARLINK))
    }

    // Its QR code lists the bytes in the order they're advertised
    @Test
    fun `a Sysgration id reads like its QR code`() {
        assertEquals("11AD8B", 0x8BAD11.asSensorId(SYSGRATION))
    }

    @Test
    fun `an id of an unknown brand reads in the order it's advertised`() {
        assertEquals("C52122", 0x2221C5.asSensorId(null))
    }

    @Test
    fun `an id too long for 3 bytes reads in hex`() {
        assertEquals("0x8BAD1100", 0x8BAD1100.toInt().asSensorId(WICARLINK))
    }
}
