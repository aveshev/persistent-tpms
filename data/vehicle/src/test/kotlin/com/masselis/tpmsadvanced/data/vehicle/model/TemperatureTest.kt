package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.FAHRENHEIT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.fahrenheit
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.toTemperature
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TemperatureTest {

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `Celsius converts to Fahrenheit`() {
        assertEquals(32f, 0f.celsius.asFahrenheit(), DELTA)
        assertEquals(212f, 100f.celsius.asFahrenheit(), DELTA)
        assertEquals(-40f, (-40f).celsius.asFahrenheit(), DELTA)
    }

    @Test
    fun `Fahrenheit converts to Celsius`() {
        assertEquals(0f, 32f.fahrenheit.celsius, DELTA)
        assertEquals(100f, 212f.fahrenheit.celsius, DELTA)
        assertEquals(-40f, (-40f).fahrenheit.celsius, DELTA)
    }

    @Test
    fun `a value read in a unit gives the same value back in that unit`() {
        listOf(CELSIUS, FAHRENHEIT).forEach { unit ->
            listOf(-30f, 0f, 21f, 90f).forEach { value ->
                assertEquals(value, value.toTemperature(unit).convert(unit), DELTA, "$value $unit")
            }
        }
    }

    @Test
    fun `written in whole degrees with its unit`() {
        assertEquals("21°C", 21f.celsius.string(CELSIUS))
        assertEquals("70°F", 21f.celsius.string(FAHRENHEIT))
        assertEquals("-10°C", (-10f).celsius.string(CELSIUS))
    }

    @Test
    fun `compact doesn't change how it's written`() {
        assertEquals(21f.celsius.string(CELSIUS), 21f.celsius.string(CELSIUS, compact = true))
    }

    @Test
    fun `the number alone is in whole degrees`() {
        assertEquals("21", 21f.celsius.numberString(CELSIUS))
        assertEquals("70", 21f.celsius.numberString(FAHRENHEIT))
    }

    @Test
    fun `half a degree rounds up`() {
        assertEquals("22°C", 21.5f.celsius.string(CELSIUS))
        assertEquals("22", 21.5f.celsius.numberString(CELSIUS))
    }

    @Test
    fun `compares by value`() {
        assertTrue(1f.celsius > 0f.celsius)
        assertTrue((-1f).celsius < 0f.celsius)
        assertEquals(0, 21f.celsius.compareTo(21f.celsius))
    }

    @Test
    fun `a range holds its bounds`() {
        val range = 0f.celsius..10f.celsius
        assertTrue(0f.celsius in range)
        assertTrue(5f.celsius in range)
        assertTrue(10f.celsius in range)
        assertFalse((-1f).celsius in range)
        assertFalse(11f.celsius in range)
    }

    @Test
    fun `a range ending before it starts is empty`() {
        assertTrue((10f.celsius..0f.celsius).isEmpty())
        assertFalse((0f.celsius..0f.celsius).isEmpty())
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
