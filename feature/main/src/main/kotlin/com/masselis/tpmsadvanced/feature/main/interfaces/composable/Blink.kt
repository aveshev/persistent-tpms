package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** How long each of a blinking element's two alternating phases lasts, the [first] then the [second] */
internal data class Blink(val first: Duration, val second: Duration = first) {
    val cycle get() = first + second
}

/** A red alert's blinking, shown longer than hidden, at 1.25 flashes a second */
internal val BLINK = Blink(600.milliseconds, 200.milliseconds)

/** An outline waiting for its tyre's sensor, as long shown as hidden */
internal val WAITING_BLINK = Blink(400.milliseconds)

/** A crimson alert's blinking */
internal val CRITICAL_BLINK = Blink(200.milliseconds)

/** A crimson reading's alternation with "LOW!" or "HIGH!" */
internal val CRITICAL_LABEL_BLINK = Blink(800.milliseconds)

// Every blink phase is a multiple of it
private val TICK = 200.milliseconds

// The count of ticks since the epoch, shared by every blinking element of every screen so they all
// switch together, in the same frame. Cycles start on multiples of their length since the epoch, so
// a shorter cycle always starts along with the longer ones.
// Nothing is replayed once no screen blinks, so a screen opened later starts from the current tick.
private val ticks: Flow<Long> = flow {
    while (true) {
        emit(currentTick())
        delay(TICK.inWholeMilliseconds - System.currentTimeMillis() % TICK.inWholeMilliseconds)
    }
}.shareIn(CoroutineScope(Dispatchers.Main.immediate), WhileSubscribed(replayExpirationMillis = 0), replay = 1)

private fun currentTick() = System.currentTimeMillis() / TICK.inWholeMilliseconds

/**
 * Whether a blinking element is in the first of its [blink]'s two alternating phases, in sync with
 * all the other blinking elements. Always the first one under [LocalInspectionMode], as in previews and
 * the screenshot tests providing it, which would otherwise depend on the time they're drawn at.
 */
@Composable
internal fun isFirstBlinkPhase(blink: Blink): Boolean {
    require(listOf(blink.first, blink.second).all { it.inWholeMilliseconds % TICK.inWholeMilliseconds == 0L }) {
        "$blink isn't made of multiples of $TICK"
    }
    if (LocalInspectionMode.current)
        return true
    val tick by ticks.collectAsState(currentTick())
    val isFirst by remember(blink) {
        derivedStateOf { tick % (blink.cycle / TICK).toLong() < (blink.first / TICK).toLong() }
    }
    return isFirst
}
