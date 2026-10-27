package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.Context
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_PROXIMITY
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether something covers the screen's top (a pocket), from the proximity sensor, between [start]
 * and [stop]. [isCovered] stays `false` without one.
 */
internal class Proximity(context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(TYPE_PROXIMITY)

    var isCovered by mutableStateOf(false)
        private set

    fun start() {
        sensor?.also { sensors?.registerListener(this, it, SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensors?.unregisterListener(this)
        isCovered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Most sensors are binary: 0 when near, their maximum range when far
        isCovered = event.values[0] < event.sensor.maximumRange
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int): Unit = Unit
}
