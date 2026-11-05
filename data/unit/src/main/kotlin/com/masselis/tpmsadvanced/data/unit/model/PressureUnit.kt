package com.masselis.tpmsadvanced.data.unit.model

import java.util.Locale

public enum class PressureUnit {
    KILO_PASCAL,
    BAR,
    PSI;

    public fun string(): String = when (this) {
        KILO_PASCAL -> if (isRussian) "кПа" else "kpa"
        BAR -> if (isRussian) "бар" else "bar"
        PSI -> "psi"
    }

    /** The unit as written after a value, e.g. "kPa" in "250 kPa" */
    public fun symbol(): String = when (this) {
        KILO_PASCAL -> if (isRussian) "кПа" else "kPa"
        BAR -> if (isRussian) "бар" else "bar"
        PSI -> "psi"
    }

    /**
     * The default locale rather than a string resource: it follows the app's language, as for the
     * value's decimal separator, and psi has no Russian symbol. Read every time, the language can
     * change while the process runs.
     */
    private val isRussian get() = Locale.getDefault().language == "ru"
}
