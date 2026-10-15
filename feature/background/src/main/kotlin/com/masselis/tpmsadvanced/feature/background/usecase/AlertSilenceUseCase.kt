package com.masselis.tpmsadvanced.feature.background.usecase

import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.minutes

/**
 * The alerts' speech silenced for a while from the main screen, see docs/alerts.md. Kept on disk
 * as the snoozes are: the monitor service gets killed and restarted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AlertSilenceUseCase(
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Silences the alerts up to [level] until [until], in milliseconds since the epoch */
    data class Silence(val level: AlertLevel, val until: Long)

    private val preferences = appContext.getSharedPreferences("ALERT_SILENCE", MODE_PRIVATE)

    private val stored = MutableStateFlow(
        preferences
            .getString(LEVEL, null)
            ?.let { Silence(AlertLevel.valueOf(it), preferences.getLong(UNTIL, 0L)) }
    )

    /** The silence going on, null once it ended */
    val silence: StateFlow<Silence?> = stored
        .transformLatest { silence ->
            silence
                ?.takeIf { it.until > clock() }
                ?.also { emit(it) }
                // Checked against the wall clock at least every second rather than after a single
                // delay: a delay's clock stops while the phone sleeps, so the silence would outlast
                // its end, the button saying 0 min left
                ?.also { while (it.until > clock()) delay((it.until - clock()).coerceAtMost(CHECK_PERIOD_MS)) }
            emit(null)
        }
        .stateIn(scope, Eagerly, stored.value?.takeIf { it.until > clock() })

    /** Silences the alerts up to [level] for [DURATION] from now, replacing any silence going on */
    fun silence(level: AlertLevel) {
        Silence(level, clock() + DURATION.inWholeMilliseconds)
            .also { preferences.edit(commit = true) { putString(LEVEL, it.level.name).putLong(UNTIL, it.until) } }
            .also { stored.value = it }
    }

    fun unmute() {
        preferences.edit(commit = true) { clear() }
        stored.value = null
    }

    companion object {
        val DURATION = 10.minutes
        private const val LEVEL = "LEVEL"
        private const val UNTIL = "UNTIL"
        private const val CHECK_PERIOD_MS = 1_000L
    }
}
