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
import android.provider.Settings.System.getInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The brightness the system would give the screen, from 0 to 1, our best guess at it between
 * [start] and [stop], while a window of ours sets it instead.
 *
 * It's the brightness setting at [start], which auto-brightness keeps up to date with what it
 * applies, read before ours takes over. With auto-brightness on, the system no longer watches the
 * light meanwhile, it's guessed from then on from the light sensor: scaled by the square root of
 * the light's change, auto-brightness raising the brightness far less than the light rises. Only
 * once the light doubles or halves, so a flickering sensor doesn't keep changing it. Set by hand,
 * the brightness doesn't follow the light, it stays as it was.
 */
internal class SystemBrightness(context: Context) : SensorEventListener {

    private val resolver = context.contentResolver
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(TYPE_LIGHT)

    var level by mutableFloatStateOf(resolver.brightness)
        private set

    /** [level] divided by [divisor], rounded down to the setting's steps, so it can reach 0 */
    fun dimmedBy(divisor: Int): Float = (floor(level * STEPS / divisor) / STEPS)
        // 0 turns the backlight off on some phones before Android 11, it's clamped since
        .let { if (SDK_INT >= R) it else it.coerceAtLeast(OLD_LOWEST) }

    /** So low that [dimmedBy] can barely, if at all, be told apart from it on the screen */
    val isLow: Boolean
        get() = level <= LOW

    private var atStart = level
    private var startLux: Float? = null
    private var appliedLux: Float? = null

    fun start() {
        atStart = resolver.brightness
        level = atStart
        startLux = null
        appliedLux = null
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
        val start = startLux ?: lux.also { startLux = it; appliedLux = it }
        (lux / (appliedLux ?: lux))
            .takeIf { it >= 2 || it <= 1f / 2 }
            ?.also {
                appliedLux = lux
                level = (atStart * sqrt(lux / start)).coerceIn(0f, 1f)
            }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int): Unit = Unit

    private companion object {
        /** The setting's steps above its lowest, from 1 to 255 */
        const val STEPS = 254f
        const val OLD_LOWEST = 0.01f
        const val LOW = 0.02f

        // Missing, a mid brightness: dimmed, still lit
        val ContentResolver.brightness: Float
            get() = ((getInt(this, SCREEN_BRIGHTNESS, 128) - 1) / STEPS).coerceIn(0f, 1f)
    }
}
