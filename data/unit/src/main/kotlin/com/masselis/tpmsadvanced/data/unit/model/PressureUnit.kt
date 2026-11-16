package com.masselis.tpmsadvanced.data.unit.model

public enum class PressureUnit {
    KILO_PASCAL,
    BAR,
    PSI;

    public fun string(): String = when (this) {
        KILO_PASCAL -> "kPa"
        BAR -> "bar"
        PSI -> "psi"
    }

    /** The unit as written after a value, e.g. "kPa" in "250 kPa" */
    public fun symbol(): String = when (this) {
        KILO_PASCAL -> "kPa"
        BAR -> "bar"
        PSI -> "psi"
    }
}
