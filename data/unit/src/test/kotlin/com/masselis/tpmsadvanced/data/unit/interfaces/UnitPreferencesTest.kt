package com.masselis.tpmsadvanced.data.unit.interfaces

import android.content.Context
import android.content.SharedPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

internal class UnitPreferencesTest {

    private lateinit var locale: Locale
    private lateinit var stored: MutableMap<String, String>
    private lateinit var context: Context

    @Before
    fun setup() {
        locale = Locale.getDefault()
        stored = mutableMapOf()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true) {
            every { putString(any(), any()) } answers {
                stored[firstArg()] = secondArg()
                self as SharedPreferences.Editor
            }
        }
        val preferences = mockk<SharedPreferences> {
            every { getString(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
        context = mockk {
            every { getSharedPreferences("UNITS", Context.MODE_PRIVATE) } returns preferences
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(locale)
    }

    private fun test(language: String = "en-US") = Locale
        .setDefault(Locale.forLanguageTag(language))
        .let { UnitPreferences(context) }

    @Test
    fun `the US read psi and Fahrenheit`() {
        test("en-US").apply {
            assertEquals(PressureUnit.PSI, pressure.value)
            assertEquals(TemperatureUnit.FAHRENHEIT, temperature.value)
        }
    }

    @Test
    fun `the UK, Canada and Australia read psi and Celsius`() {
        listOf("en-GB", "en-CA", "fr-CA", "en-AU").forEach { language ->
            test(language).apply {
                assertEquals(PressureUnit.PSI, pressure.value, language)
                assertEquals(TemperatureUnit.CELSIUS, temperature.value, language)
            }
        }
    }

    @Test
    fun `elsewhere reads bar and Celsius`() {
        listOf("fr-FR", "de-DE", "ru-RU", "ja-JP").forEach { language ->
            test(language).apply {
                assertEquals(PressureUnit.BAR, pressure.value, language)
                assertEquals(TemperatureUnit.CELSIUS, temperature.value, language)
            }
        }
    }

    @Test
    fun `a language without a country reads bar and Celsius`() {
        test("en").apply {
            assertEquals(PressureUnit.BAR, pressure.value)
            assertEquals(TemperatureUnit.CELSIUS, temperature.value)
        }
    }

    @Test
    fun `a region without a 3-letter code reads bar and Celsius`() {
        listOf("en-XA", "ar-XB", "es-419", "en-001", "en-150").forEach { language ->
            test(language).apply {
                assertEquals(PressureUnit.BAR, pressure.value, language)
                assertEquals(TemperatureUnit.CELSIUS, temperature.value, language)
            }
        }
    }

    @Test
    fun `the units chosen win over the country's`() {
        stored["PRESSURE"] = PressureUnit.KILO_PASCAL.name
        stored["TEMPERATURE"] = TemperatureUnit.CELSIUS.name
        test("en-US").apply {
            assertEquals(PressureUnit.KILO_PASCAL, pressure.value)
            assertEquals(TemperatureUnit.CELSIUS, temperature.value)
        }
    }

    @Test
    fun `the units chosen are kept`() {
        test("fr-FR").apply {
            pressure.value = PressureUnit.PSI
            temperature.value = TemperatureUnit.FAHRENHEIT
        }
        assertEquals(mapOf("PRESSURE" to "PSI", "TEMPERATURE" to "FAHRENHEIT"), stored)
        test("fr-FR").apply {
            assertEquals(PressureUnit.PSI, pressure.value)
            assertEquals(TemperatureUnit.FAHRENHEIT, temperature.value)
        }
    }

    @Test
    fun `nothing is stored until a unit is chosen`() {
        test("en-US")
        assertEquals(emptyMap(), stored)
    }
}
