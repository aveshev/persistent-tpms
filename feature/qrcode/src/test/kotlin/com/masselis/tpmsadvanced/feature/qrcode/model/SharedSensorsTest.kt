package com.masselis.tpmsadvanced.feature.qrcode.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SharedSensorsTest {

    private val calibrated = SensorCalibration(true, PressureCalibration(Pressure(-7.5f), 1.02f))

    private val car = SharedSensors(
        CAR,
        "Family car",
        listOf(
            SharedSensor(Sensor(-1951592192, Wheel(FRONT_LEFT), SYSGRATION), SensorCalibration.None),
            SharedSensor(Sensor(1029054720, Wheel(FRONT_RIGHT), SYSGRATION), calibrated),
            SharedSensor(Sensor(0x2221C5, Wheel(REAR_LEFT), WICARLINK), calibrated.copy(isEnabled = false)),
            SharedSensor(Sensor(Int.MAX_VALUE, Wheel(REAR_RIGHT), PECHAM), SensorCalibration.None),
        ),
    )

    @Test
    fun `a code reads back as the sensors it was made of`() {
        assertEquals(car, SharedSensors.decode(car.uri))
    }

    @Test
    fun `every kind, location and brand has a code`() {
        Vehicle.Kind.entries.forEach { kind ->
            kind.locations
                .mapIndexed { index, location ->
                    SensorBrand.entries[index % SensorBrand.entries.size]
                        .let { SharedSensor(Sensor(index, location, it), calibrated) }
                }
                .let { SharedSensors(kind, kind.name, it) }
                .also { assertEquals(it, SharedSensors.decode(it.uri)) }
        }
        SensorBrand.entries
            .map { SharedSensors(CAR, "", listOf(SharedSensor(Sensor(1, Wheel(FRONT_LEFT), it), calibrated))) }
            .forEach { assertEquals(it, SharedSensors.decode(it.uri)) }
    }

    @Test
    fun `a code is small enough for a QR code scanned off a screen`() {
        assertTrue(car.uri.length < 120, car.uri)
    }

    @Test
    fun `a long name is shortened, not refused`() {
        car.copy(name = "é".repeat(200))
            .let { SharedSensors.decode(it.uri) }
            .also { assertEquals("é".repeat(40), it?.name) }
    }

    @Test
    fun `a code that isn't a shared one is left to the other formats`() {
        assertNull(SharedSensors.decode("11AD8B&21563D&31A4F0&41A552"))
        assertNull(SharedSensors.decode("2221C5"))
        assertNull(SharedSensors.decode("https://example.com/?v=1&d=AAAA"))
    }

    @Test
    fun `a code from a newer app is told apart from a damaged one`() {
        car.uri
            .replace("v=1", "v=2")
            .let { code -> assertFailsWith<SharedSensors.UnsupportedVersion> { SharedSensors.decode(code) } }
            .also { assertEquals("2", it.version) }
    }

    @Test
    fun `a damaged code is refused`() {
        listOf(
            car.uri.dropLast(4),
            car.uri + "AAAA",
            car.uri.replace("v=1&", ""),
            car.uri.substringBefore("&d="),
            car.uri.replaceAfter("d=", "not*base64"),
            // An unknown kind
            "tpmsadvanced://sensors?v=1&d=" + Base64.getUrlEncoder().encodeToString(byteArrayOf(99, 0, 0)),
        ).forEach { code ->
            assertFailsWith<IllegalArgumentException>(code) { SharedSensors.decode(code) }
        }
    }

    @Test
    fun `a code can't be mistaken for a kit's`() {
        // The kit formats look for ids anywhere in a code, joined by a `&`
        assertNull("[0-9a-fA-F]{6}&[0-9a-fA-F]{6}".toRegex().find(car.uri))
    }

    @Test
    fun `two sensors on a wheel or a sensor shared twice can't be shared`() {
        val sensor = car.sensors.first()
        assertFailsWith<IllegalArgumentException> {
            car.copy(sensors = car.sensors + sensor.copy(sensor = sensor.sensor.copy(id = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            car.copy(sensors = listOf(sensor, sensor.copy(sensor = sensor.sensor.copy(location = Wheel(REAR_RIGHT)))))
        }
    }
}
