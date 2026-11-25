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
    fun `the country's common unit is read, whatever the language`() {
        mapOf(
            "en-US" to PressureUnit.PSI, "fr-CA" to PressureUnit.PSI, "en-GB" to PressureUnit.PSI,
            "en-AU" to PressureUnit.PSI, "es-MX" to PressureUnit.PSI, "pt-BR" to PressureUnit.PSI,
            "hi-IN" to PressureUnit.PSI, "ru-US" to PressureUnit.PSI,
            "ru-RU" to PressureUnit.BAR, "en-RU" to PressureUnit.BAR, "uk-UA" to PressureUnit.BAR,
            "kk-KZ" to PressureUnit.BAR, "de-DE" to PressureUnit.BAR, "fr-FR" to PressureUnit.BAR,
            "zh-CN" to PressureUnit.BAR, "en-150" to PressureUnit.BAR,
            "ja-JP" to PressureUnit.KILO_PASCAL,
        ).forEach { (language, unit) -> assertEquals(unit, test(language).pressure.value, language) }
    }

    @Test
    fun `without a country, Russian reads bar and others psi`() {
        assertEquals(PressureUnit.BAR, test("ru").pressure.value)
        listOf("en", "de", "es-419", "en-XA").forEach { language ->
            assertEquals(PressureUnit.PSI, test(language).pressure.value, language)
        }
    }

    @Test
    fun `the US reads Fahrenheit, whatever the language`() {
        listOf("en-US", "es-US", "ru-US", "es-PR").forEach { language ->
            assertEquals(TemperatureUnit.FAHRENHEIT, test(language).temperature.value, language)
        }
    }

    @Test
    fun `elsewhere reads Celsius`() {
        listOf("en", "en-GB", "en-CA", "ru-RU", "fr-FR", "es-419").forEach { language ->
            assertEquals(TemperatureUnit.CELSIUS, test(language).temperature.value, language)
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
