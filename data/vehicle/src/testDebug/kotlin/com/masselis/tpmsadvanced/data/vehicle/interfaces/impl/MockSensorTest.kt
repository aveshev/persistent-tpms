package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.SY_4300
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.TC_TPMS
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.MockSensor.YC_0001
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("MagicNumber")
internal class MockSensorTest {

    private val readings = listOf(
        reading(kpa = 230f, celsius = 21f),
        reading(kpa = 0f, celsius = -20f, battery = 25),
        reading(kpa = 310.5f, celsius = 95f, battery = 37, id = 0x062200),
        reading(kpa = 1000f, celsius = 0f, battery = 13, id = 0xFFFF00),
    )

    private fun reading(
        kpa: Float,
        celsius: Float,
        battery: Int? = null,
        id: Int = 0x123400,
        location: SensorLocation = FRONT_LEFT,
        isAlarm: Boolean = false,
        flags: Int? = null,
    ) = MockSensor.Reading(kpa, celsius, battery, id, location, isAlarm, flags)

    /** Pressure resolution of each advertisement, in kPa */
    private val resolution = mapOf(
        PECHAM to 0.6895f,
        BEKUBEE_KY to 0.6895f,
        WICARLINK to 3.144f,
        BEKUBEE_TPMS to 1f,
        SYSGRATION to 0.001f,
        TC_TPMS to 0.6895f,
        SY_4300 to 0.6895f,
        YC_0001 to 0.6895f,
    )

    /**
     * The 16-bit service UUID the scan filters must find in each advertisement, in a complete or an
     * incomplete list. YC-0001 has none, the scan filters find it by its name.
     */
    private val serviceUuid = mapOf(
        PECHAM to 0x27A5,
        BEKUBEE_KY to 0x27A5,
        WICARLINK to 0xFBB0,
        BEKUBEE_TPMS to 0xA827,
        SYSGRATION to 0xFBB0,
        TC_TPMS to 0xA828,
        SY_4300 to 0xCC13,
    )

    @Test
    fun `every encoded reading decodes back to itself through the scanner's decoder chain`() {
        MockSensor.entries.forEach { sensor ->
            readings.forEach { reading ->
                val bytes = sensor.advertisement(reading)
                val structures = bytes.adStructures()
                assertTrue(
                    (structures[0x03] ?: structures[0x02])?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) } ==
                        serviceUuid[sensor],
                    "$sensor must advertise its service UUID, the scan filters drop it otherwise"
                )
                // With the address the scanner would give, which YC-0001's CRC covers
                val raw = AdvertisingPacket(bytes).decode(reading.address)
                assertEquals(
                    when (sensor) {
                        PECHAM -> RawPecham::class
                        BEKUBEE_KY -> RawBekubeeKy::class
                        WICARLINK -> RawWicarlink::class
                        BEKUBEE_TPMS -> RawBekubeeTpms::class
                        SYSGRATION -> RawSysgration::class
                        TC_TPMS -> RawTcTpms::class
                        SY_4300, YC_0001 -> RawSy4300::class
                    },
                    raw?.let { it::class },
                    "$sensor $reading"
                )
                val tyre = raw!!.let { it.asTyre(0.0, -60, it.sensorId ?: ADDRESS_SENSOR_ID, "") }
                assertTrue(
                    abs(tyre.pressure.kpa - reading.kpa) <= resolution.getValue(sensor) / 2 + 0.01f,
                    "$sensor $reading decoded ${tyre.pressure}"
                )
                assertEquals(reading.celsius, tyre.temperature.celsius, 0.01f, "$sensor $reading")
                if (sensor == SYSGRATION) {
                    assertNull(tyre.batteryVoltage, "$sensor $reading")
                    assertEquals(reading.battery ?: 100, tyre.batteryPercent, "$sensor $reading")
                } else {
                    assertNull(tyre.batteryPercent, "$sensor $reading")
                    reading.battery?.let {
                        assertEquals(it / 10f, assertNotNull(tyre.batteryVoltage).volts, 0.05f, "$sensor $reading")
                    }
                }
                if (sensor !in listOf(PECHAM, BEKUBEE_KY, SY_4300, YC_0001)) assertEquals(reading.id, tyre.sensorId, "$sensor $reading")
            }
        }
    }

    @Test
    fun `sysgration advertises its wheel and alarm`() {
        SYSGRATION
            .advertisement(reading(kpa = 150f, celsius = 30f, location = REAR_RIGHT, isAlarm = true))
            .decodedTyre()
            .let { assertIs<Tyre.SensorLocated>(it) }
            .also { assertEquals(REAR_RIGHT, it.location) }
            .also { assertTrue(it.isAlarm) }
    }

    @Test
    fun `a low battery is reported as a voltage, not as an alarm`() {
        listOf(PECHAM, BEKUBEE_KY, WICARLINK, BEKUBEE_TPMS, TC_TPMS, SY_4300, YC_0001).forEach { sensor ->
            sensor
                .advertisement(reading(kpa = 200f, celsius = 20f, battery = 20))
                .decodedTyre()!!
                .also { assertFalse(it.isAlarm, "$sensor") }
                .also { assertEquals(2f, assertNotNull(it.batteryVoltage, "$sensor").volts, 0.05f, "$sensor") }
        }
    }

    @Test
    fun `sysgration's battery above 100 isn't a percentage`() {
        SYSGRATION
            .advertisement(reading(kpa = 200f, celsius = 20f))
            .also { it[it.size - 2] = 0x7F }
            .decodedTyre()!!
            .also { assertNull(it.batteryPercent) }
    }

    @Test
    fun `flags go to the status byte the tyre shows`() {
        reading(kpa = 200f, celsius = 20f, flags = 0x5A).let { reading ->
            listOf(PECHAM, BEKUBEE_KY, WICARLINK, BEKUBEE_TPMS, SYSGRATION, TC_TPMS, SY_4300, YC_0001).forEach { sensor ->
                val advertisement = sensor.advertisement(reading)
                assertNotNull(AdvertisingPacket(advertisement).decode(reading.address), "$sensor with flags")
                assertEquals(listOf<UByte>(0x5Au), AdvertisingPacket(advertisement).statusBytes, "$sensor")
            }
        }
        // Wicarlink's 9th pressure bit is byte 17 being 1
        assertEquals(1.toByte(), WICARLINK.advertisement(reading(kpa = 900f, celsius = 20f))[17])
        assertEquals(0.toByte(), WICARLINK.advertisement(reading(kpa = 200f, celsius = 20f))[17])
        // Sysgration's and Wicarlink's alarm is their flags byte being 1, SY-4300's and YC-0001's its bit 0x02
        listOf(SYSGRATION, WICARLINK, SY_4300, YC_0001).forEach { sensor ->
            val alarm = if (sensor == SY_4300 || sensor == YC_0001) 2 else 1
            assertTrue(sensor.advertisement(reading(kpa = 200f, celsius = 20f, flags = alarm)).decodedTyre()!!.isAlarm, "$sensor")
            assertTrue(sensor.advertisement(reading(kpa = 200f, celsius = 20f, isAlarm = true)).decodedTyre()!!.isAlarm, "$sensor")
            assertFalse(sensor.advertisement(reading(kpa = 200f, celsius = 20f)).decodedTyre()!!.isAlarm, "$sensor")
        }
    }

    @Test
    fun `values an advertisement cannot carry are rejected`() {
        assertFailsWith<IllegalArgumentException> { PECHAM.advertisement(reading(kpa = 200f, celsius = 20f, flags = 256)) }
        assertFailsWith<IllegalArgumentException> { WICARLINK.advertisement(reading(kpa = 200f, celsius = 20f, flags = 256)) }
        assertFailsWith<IllegalArgumentException> { SYSGRATION.advertisement(reading(kpa = 200f, celsius = 20f, isAlarm = true, flags = 3)) }
        assertFailsWith<IllegalArgumentException> { WICARLINK.advertisement(reading(kpa = 200f, celsius = 20f, isAlarm = true, flags = 3)) }
        assertFailsWith<IllegalArgumentException> { PECHAM.advertisement(reading(kpa = -1f, celsius = 20f)) }
        assertFailsWith<IllegalArgumentException> { WICARLINK.advertisement(reading(kpa = 200f, celsius = -60f)) }
        assertFailsWith<IllegalArgumentException> { BEKUBEE_TPMS.advertisement(reading(kpa = 200f, celsius = 20f, battery = 40)) }
        assertFailsWith<IllegalArgumentException> { WICARLINK.advertisement(reading(kpa = 200f, celsius = 20f, id = 0x1000000)) }
        assertFailsWith<IllegalArgumentException> { SYSGRATION.advertisement(reading(kpa = 200f, celsius = 20f, id = 0x123456)) }
        assertFailsWith<IllegalArgumentException> { TC_TPMS.advertisement(reading(kpa = 200f, celsius = 20f, id = 0x1000000)) }
        assertFailsWith<IllegalArgumentException> { SY_4300.advertisement(reading(kpa = 200f, celsius = 20f, isAlarm = true, flags = 2)) }
    }

    @Test
    fun `a YC-0001 packet is only valid from the address it was built for`() {
        YC_0001
            .advertisement(reading(kpa = 200f, celsius = 20f).copy(address = "12:30:AF:00:04:5A"))
            .let(::AdvertisingPacket)
            .also { assertNotNull(it.decode("12:30:AF:00:04:5A")) }
            .also { assertNull(it.decode("00:11:22:33:44:55")) }
    }

    /** AD structures by type, as `ScanRecord.parseFromBytes` splits them */
    private fun ByteArray.adStructures(): Map<Int, ByteArray> = generateSequence(0) { it + (this[it].toInt() and 0xFF) + 1 }
        .takeWhile { it < size && this[it] != 0.toByte() }
        .associate { (this[it + 1].toInt() and 0xFF) to copyOfRange(it + 2, it + 1 + (this[it].toInt() and 0xFF)) }
}
