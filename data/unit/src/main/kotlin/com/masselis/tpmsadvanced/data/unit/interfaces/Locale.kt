package com.masselis.tpmsadvanced.data.unit.interfaces

import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.FAHRENHEIT
import java.util.Locale

/**
 * The tyre pressure unit most people use in the locale's country, at the pump and on forums,
 * rather than the official one: Canada's placards say kPa but drivers say psi, Russia's gauges
 * read kgf/cm², which is as near to a bar as a tyre needs. Without a country, Russian reads bar.
 * Keep in step with the iOS app's `PressureUnit.default(region:language:)`.
 */
internal val Locale.commonPressureUnit: PressureUnit
    get() = country
        .takeIf { it.isNotEmpty() }
        ?.let { country ->
            when (country) {
                in KILO_PASCAL_COUNTRIES -> KILO_PASCAL
                in BAR_COUNTRIES -> BAR
                else -> PSI
            }
        }
        ?: if (language == "ru") BAR else PSI

/**
 * Fahrenheit in the US and the few places that use it like the US, Celsius elsewhere.
 * Keep in step with the iOS app's `TemperatureUnit.common(region:)`.
 */
internal val Locale.commonTemperatureUnit: TemperatureUnit
    get() = if (country in FAHRENHEIT_COUNTRIES) FAHRENHEIT else CELSIUS

// The US and its territories, the Bahamas, Belize, the Cayman Islands, Liberia and the Pacific states
// in free association with the US
private val FAHRENHEIT_COUNTRIES = setOf("US", "PR", "GU", "VI", "AS", "MP", "BS", "BZ", "KY", "LR", "PW", "MH", "FM")

private val KILO_PASCAL_COUNTRIES = setOf("JP")

// Continental Europe ("150" being Europe as a whole), the former Soviet Union, Turkey, Israel, China, the
// Maghreb and South Africa. The Americas, the UK, Ireland, Oceania, most of Asia and the Gulf use psi.
private val BAR_COUNTRIES = setOf(
    "150", "AD", "AL", "AT", "BA", "BE", "BG", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FO", "FR",
    "GR", "HR", "HU", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "ME", "MK", "MT", "NL", "NO", "PL", "PT",
    "RO", "RS", "SE", "SI", "SK", "SM", "VA", "XK",
    "AM", "AZ", "BY", "GE", "KG", "KZ", "MD", "RU", "TJ", "TM", "UA", "UZ",
    "TR", "IL", "CN", "MA", "DZ", "TN", "ZA",
)
