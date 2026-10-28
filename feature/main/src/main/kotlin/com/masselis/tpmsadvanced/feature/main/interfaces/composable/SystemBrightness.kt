package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.ContentResolver
import android.content.Context
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_LIGHT
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
import android.provider.Settings.System.getFloat
import android.provider.Settings.System.getInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * The brightness the system would give the screen, from 0 to 1, our best guess at it between
 * [start] and [stop], while a window of ours sets it instead.
 *
 * It's the brightness setting at [start], which auto-brightness keeps up to date with what it
 * applies, read before ours takes over. With auto-brightness on, the system no longer watches the
 * light meanwhile, it's guessed from then on from the light sensor: scaled by the square root of
 * the light's change, auto-brightness raising the brightness far less than the light rises. Set by
 * hand, the brightness doesn't follow the light, it stays as it was.
 *
 * [level] moves towards that guess while [follow] runs, gradually, so a flickering light doesn't
 * flicker the screen: not within [DEAD_ZONE] of it, slowly, faster the further it is, at most
 * [SLOW_MAX] a second. Once it's twice the guess or half of it for [FAST_AFTER], a lasting change of
 * light, it moves fast, [FAST] of the way a second.
 *
 * Before Android 11, auto-brightness doesn't write the setting, which keeps the brightness last
 * set by hand: there, with auto-brightness on, it's the phone's own curve at the light instead,
 * see [BrightnessCurve], from the first reading of the light sensor, when the phone has one. It's
 * divided in nits there too, where the backlight's level can be far from them.
 */
internal class SystemBrightness(context: Context) : SensorEventListener {

    private val resolver = context.contentResolver
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(TYPE_LIGHT)
    private val curve = if (SDK_INT < R) BrightnessCurve.read() else null

    var level by mutableFloatStateOf(resolver.brightness)
        private set

    /**
     * [level] divided by [divisor], in nits where the phone says how, see [BrightnessCurve.divide],
     * rounded down to the setting's steps, so it can reach 0
     */
    fun dimmedBy(divisor: Int): Float = (floor((curve?.divide(level, divisor) ?: (level / divisor)) * STEPS) / STEPS)
        // 0 turns the backlight off on some phones before Android 11, it's clamped since
        .let { if (SDK_INT >= R) it else it.coerceAtLeast(OLD_LOWEST) }

    /** So low that [dimmedBy] can barely, if at all, be told apart from it on the screen */
    val isLow: Boolean
        get() = level <= LOW

    private var atStart = level
    private var startLux: Float? = null
    private var target = level
    // Since when the level is twice the target, or half of it
    private var farSince: Long? = null

    fun start() {
        atStart = resolver.brightness
        level = atStart
        target = atStart
        startLux = null
        farSince = null
        sensor
            ?.takeIf { getInt(resolver, SCREEN_BRIGHTNESS_MODE, -1) == SCREEN_BRIGHTNESS_MODE_AUTOMATIC }
            ?.also { sensors?.registerListener(this, it, SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // + 1 so darkness, 0 lux, still compares
        val lux = event.values[0] + 1
        val isFirst = startLux == null
        val start = startLux ?: lux.also { startLux = it }
        target = curve
            ?.at(lux - 1, resolver.adjustment, BrightnessCurve.maxGamma())
            // From the phone's curve, the setting being stale, the level starts right there
            ?.also { if (isFirst) level = it }
            ?: (atStart * sqrt(lux / start)).coerceIn(0f, 1f)
    }

    /** Moves [level] towards the light's, as said above, until cancelled */
    suspend fun follow(): Nothing {
        while (true) {
            delay(TICK)
            // How far, as a ratio, so a dim screen moves as smoothly as a bright one
            val distance = ln(target.coerceAtLeast(DARKEST) / level.coerceAtLeast(DARKEST))
            val now = SystemClock.elapsedRealtime()
            farSince = if (abs(distance) >= ln(2f)) farSince ?: now else null
            if (abs(distance) < ln(1 + DEAD_ZONE)) continue
            val seconds = (TICK / 1.seconds).toFloat()
            val move = if (farSince?.let { now - it >= FAST_AFTER.inWholeMilliseconds } == true)
                distance * (1 - (1 - FAST).pow(seconds))
            else (distance * SLOW * seconds).coerceIn(-ln(1 + SLOW_MAX) * seconds, ln(1 + SLOW_MAX) * seconds)
            level = (level.coerceAtLeast(DARKEST) * exp(move)).coerceIn(0f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int): Unit = Unit

    private companion object {
        /** The setting's steps above its lowest, from 1 to 255 */
        const val STEPS = 254f
        const val OLD_LOWEST = 0.01f
        const val LOW = 0.02f
        val TICK = 100.milliseconds
        /** How close to the light's the level stays as it is */
        const val DEAD_ZONE = 0.1f
        /** How much of the ratio to the light's the level moves a second, slowly */
        const val SLOW = 0.5f
        /** The most the level moves a second, slowly, by */
        const val SLOW_MAX = 0.5f
        /** How much of the way to the light's the level moves a second, fast */
        const val FAST = 0.9f
        val FAST_AFTER = 2.seconds
        /** Below it, ratios are taken as from it, so 0 still moves */
        const val DARKEST = 0.001f

        // Missing, a mid brightness: dimmed, still lit
        val ContentResolver.brightness: Float
            get() = ((getInt(this, SCREEN_BRIGHTNESS, 128) - 1) / STEPS).coerceIn(0f, 1f)

        // The auto-brightness slider's position before Android 11, from -1 to 1, hidden from the SDK
        val ContentResolver.adjustment: Float
            get() = getFloat(this, "screen_auto_brightness_adj", 0f)
    }
}
