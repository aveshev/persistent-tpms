package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
internal class BekubeeKyTest {

    private fun String.asRaw() = RawBekubeeKy(AdvertisingPacket(hexToByteArray()))

    // No real sensor's capture is available: encoded by MockSensor before the CRC moved to AdvertisementCrc
    @Test
    fun `decodes an advertisement with its tables and pressure offset`() {
        val raw = assertNotNull("0303a52703084b5908ff101c1501e0b125".asRaw())
        assertEquals(BEKUBEE_KY, raw.brand)
        assertEquals(230.293f, raw.pressure().kpa, 0.001f)
        assertEquals(21f, raw.temperature().celsius)
        assertEquals(2.8f, raw.voltage().volts, 0.001f)
    }

    @Test
    fun `rejects a bad CRC`() {
        assertNull("0303A52703084B5908FF801B1B02A584EA".asRaw())
    }
}
