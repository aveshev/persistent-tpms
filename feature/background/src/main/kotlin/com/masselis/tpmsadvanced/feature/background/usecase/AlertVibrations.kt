package com.masselis.tpmsadvanced.feature.background.usecase

import android.media.AudioAttributes
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.VibrationAttributes
import android.os.VibrationAttributes.USAGE_ALARM
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * The vibration patterns played along with the alerts' sound, see docs/alerts.md. The same rhythm
 * as [AlertTones]: 3 pulses twice for a red alert, a burst of 3 and 2 pulses twice for a crimson
 * one. A crimson pulse is longer, closer to the next and at full strength, a red one at half
 * strength: phones without amplitude control vibrate every pulse at the same strength, they still
 * tell the two apart by how long the motor runs.
 */
@Suppress("MagicNumber")
internal object AlertVibrations {

    private val vibrator by lazy { appContext.getSystemService<Vibrator>()!! }

    /** Vibrates the crimson or the red pattern, returning once it's vibrated */
    suspend fun vibrate(isCrimson: Boolean) {
        val pattern = if (isCrimson) crimson else red
        // The alarm usage vibrates even with the ringer on silent, as the alarm audio usage plays
        // with the media volume muted: the user turned the vibration on to feel it while riding
        if (SDK_INT >= TIRAMISU) vibrator.vibrate(pattern.effect, VibrationAttributes.createForUsage(USAGE_ALARM))
        else @Suppress("DEPRECATION") vibrator.vibrate(
            pattern.effect,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
        try {
            delay(pattern.durationMillis)
        } catch (e: CancellationException) {
            // Only this app's vibration, a notification's own isn't cut. Not once it's done, the
            // delay can end a few milliseconds before the motor does
            vibrator.cancel()
            throw e
        }
    }

    private class Pattern(val effect: VibrationEffect, val durationMillis: Long)

    /** 3 pulses, twice */
    private val red by lazy {
        listOf(0L, RED_SPACING, 2 * RED_SPACING)
            .let { pulses -> pulses + pulses.map { it + RED_REPEAT } }
            .pattern(RED_PULSE, RED_AMPLITUDE)
    }

    /** 3 pulses and 2 after a pause, twice */
    private val crimson by lazy {
        listOf(0L, 1L, 2L)
            .map { it * CRIMSON_SPACING }
            .plus(listOf(3L, 4L).map { it * CRIMSON_SPACING + CRIMSON_PAUSE })
            .let { pulses -> pulses + pulses.map { it + CRIMSON_REPEAT } }
            .pattern(CRIMSON_PULSE, MAX_AMPLITUDE)
    }

    /** The pulses starting at these offsets, in milliseconds, as alternating off and on timings */
    private fun List<Long>.pattern(pulseMillis: Long, amplitude: Int) = this
        .zip(listOf(0L) + map { it + pulseMillis }) { start, previousEnd -> listOf(start - previousEnd, pulseMillis) }
        .flatten()
        .let { timings ->
            Pattern(
                VibrationEffect.createWaveform(
                    timings.toLongArray(),
                    IntArray(timings.size) { if (it % 2 == 0) 0 else amplitude },
                    -1,
                ),
                timings.sum(),
            )
        }

    private const val MAX_AMPLITUDE = 255

    private const val RED_AMPLITUDE = 128
    private const val RED_PULSE = 150L
    private const val RED_SPACING = 400L
    private const val RED_REPEAT = 1600L

    private const val CRIMSON_PULSE = 250L
    private const val CRIMSON_SPACING = 330L
    private const val CRIMSON_PAUSE = 160L
    private const val CRIMSON_REPEAT = 2000L
}
