package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.core.content.res.use
import kotlin.math.pow

/**
 * The phone's own brightness curves, as the system's resources configure them, the screen's
 * brightness being its backlight's level from 0 to 1:
 * - auto-brightness's, from the light in lux to the brightness, through nits, or straight to the
 *   backlight's level on phones configured the older way. It's the curve as shipped: some phones
 *   learn their own from the user's changes, which apps can't read, it tends to stay close.
 * - the screen's, between the backlight's level and nits, far from linear on some phones: a tenth
 *   of a Galaxy S9's backlight is 6 of its 400 nits.
 *
 * Either can be missing, or both, the phone's resources not having them.
 */
internal class BrightnessCurve private constructor(
    private val autoLux: List<Float>?,
    private val autoBrightness: List<Float>?,
    private val screenBrightness: List<Float>?,
    private val screenNits: List<Float>?,
) {

    /**
     * Auto-brightness's brightness at [lux], shifted by its adjustment, from -1 to 1, as the system
     * does: a gamma, up to [maxGamma] at either end. Null without the curve.
     */
    fun at(lux: Float, adjustment: Float, maxGamma: Float): Float? = autoLux
        ?.interpolate(autoBrightness ?: return null, lux)
        ?.coerceIn(0f, 1f)
        ?.pow(maxGamma.pow(-adjustment.coerceIn(-1f, 1f)))

    /** [brightness] divided by [divisor] in nits, as the eye sees it, or as it is without them */
    fun divide(brightness: Float, divisor: Int): Float = screenNits
        ?.let { nits ->
            screenBrightness
                ?.interpolate(nits, brightness)
                ?.div(divisor)
                ?.let { nits.interpolate(screenBrightness, it) }
        }
        ?: (brightness / divisor)

    internal companion object {

        private const val BACKLIGHT_MAX = 255f
        private const val DEFAULT_MAX_GAMMA = 3f

        fun read(resources: Resources = Resources.getSystem()): BrightnessCurve {
            val screenNits = resources.floats("config_screenBrightnessNits")
            val screenBrightness = resources
                .floats("config_screenBrightnessBacklight")
                ?.takeIf { it.size == screenNits?.size }
                ?.map { it / BACKLIGHT_MAX }
            // The first value is for below the first level, the curve starts at 0 lux
            val autoLux = resources.floats("config_autoBrightnessLevels")?.let { listOf(0f) + it }
            val autoBrightness = screenNits
                ?.let { nits ->
                    screenBrightness?.let { brightness ->
                        resources
                            .floats("config_autoBrightnessDisplayValuesNits")
                            ?.map { nits.interpolate(brightness, it) }
                    }
                }
                ?: resources.floats("config_autoBrightnessLcdBacklightValues")?.map { it / BACKLIGHT_MAX }
            return BrightnessCurve(
                autoLux = autoLux?.takeIf { it.size == autoBrightness?.size },
                autoBrightness = autoBrightness?.takeIf { it.size == autoLux?.size },
                screenBrightness = screenBrightness?.takeIf { screenNits != null },
                screenNits = screenNits?.takeIf { screenBrightness != null },
            )
        }

        /** The adjustment's strongest gamma, see [at] */
        @SuppressLint("DiscouragedApi")
        fun maxGamma(resources: Resources = Resources.getSystem()): Float = resources
            .getIdentifier("config_autoBrightnessAdjustmentMaxGamma", "fraction", "android")
            .takeIf { it != 0 }
            ?.let { resources.getFraction(it, 1, 1) }
            ?: DEFAULT_MAX_GAMMA

        // Integer arrays too, which are arrays as resources: a typed array reads integers as floats
        @SuppressLint("DiscouragedApi")
        private fun Resources.floats(name: String): List<Float>? = getIdentifier(name, "array", "android")
            .takeIf { it != 0 }
            ?.let { id ->
                obtainTypedArray(id).use { array ->
                    (0 until array.length()).map { array.getFloat(it, Float.NaN) }
                }
            }
            ?.takeIf { values -> values.isNotEmpty() && values.none { it.isNaN() } }

        /** Linear, between the points of [this] and [values], flat beyond them */
        private fun List<Float>.interpolate(values: List<Float>, at: Float): Float =
            indexOfFirst { it >= at }
                .let { index ->
                    when (index) {
                        -1 -> values.last()
                        0 -> values.first()
                        else -> (at - this[index - 1])
                            .div(this[index] - this[index - 1])
                            .let { values[index - 1] + it * (values[index] - values[index - 1]) }
                    }
                }
    }
}
