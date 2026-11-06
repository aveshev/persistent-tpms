package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcel
import android.os.Parcelable
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import java.text.DecimalFormatSymbols

/* Cannot use @Parcelize here: https://issuetracker.google.com/issues/177856519 */
@Suppress("MagicNumber")
@JvmInline
public value class Pressure(public val kpa: Float) : Parcelable, Comparable<Pressure> {

    public fun asBar(): Float = convert(BAR)

    public fun asPsi(): Float = convert(PSI)

    public fun convert(unit: PressureUnit): Float = when (unit) {
        KILO_PASCAL -> kpa
        BAR -> kpa / 100f
        PSI -> kpa / 6.895f
    }

    public fun string(unit: PressureUnit, compact: Boolean = false): String = when (unit) {
        KILO_PASCAL -> (if (compact) "%.0fk" else "%.0f kPa").format(kpa)
        // One decimal less from 10 bar and 100 psi, so high pressures keep the width of "8.88 bar"
        // and "88.8 psi" (tyre readouts are laid out for those)
        BAR -> when {
            compact -> "%.1fb"
            asBar() >= 9.995f -> "%.1f bar"
            else -> "%.2f bar"
        }.format(asBar())

        PSI -> when {
            compact -> "%.0fp"
            asPsi() >= 99.95f -> "%.0f psi"
            else -> "%.1f psi"
        }.format(asPsi())
    }

    public fun numberString(unit: PressureUnit): String = when (unit) {
        KILO_PASCAL -> "%.0f".format(kpa)
        BAR -> "%.2f".format(asBar()).trimTrailingZeroDecimal()
        PSI -> "%.1f".format(asPsi()).trimTrailingZeroDecimal()
    }

    public fun hasPressure(): Boolean = kpa > 0f

    override operator fun compareTo(other: Pressure): Int = kpa.compareTo(other.kpa)

    public operator fun rangeTo(other: Pressure): ClosedFloatingPointRange<Pressure> =
        Range(this, other)

    private class Range(
        override val start: Pressure,
        override val endInclusive: Pressure
    ) : ClosedFloatingPointRange<Pressure> {
        override fun lessThanOrEquals(a: Pressure, b: Pressure): Boolean = a.kpa <= b.kpa
    }

    private constructor(parcel: Parcel) : this(parcel.readFloat())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(kpa)
    }

    override fun describeContents(): Int = 0

    public companion object CREATOR : Parcelable.Creator<Pressure> {

        public val Float.kpa: Pressure get() = Pressure(this)

        public val Float.bar: Pressure get() = Pressure(this.times(100))

        public val Float.psi: Pressure get() = Pressure(this * 6.895f)

        public fun Float.toPressure(unit: PressureUnit): Pressure = when (unit) {
            KILO_PASCAL -> kpa
            BAR -> bar
            PSI -> psi
        }

        override fun createFromParcel(parcel: Parcel): Pressure {
            return Pressure(parcel)
        }

        override fun newArray(size: Int): Array<Pressure?> {
            return arrayOfNulls(size)
        }
    }
}

private fun String.trimTrailingZeroDecimal(): String {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    return if (contains(separator)) trimEnd('0').trimEnd(separator) else this
}
