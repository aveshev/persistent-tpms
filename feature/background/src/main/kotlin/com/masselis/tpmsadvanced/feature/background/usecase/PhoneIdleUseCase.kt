package com.masselis.tpmsadvanced.feature.background.usecase

import android.os.SystemClock.elapsedRealtime
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.STILL
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.DEEP_DOZE
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.NO_SIGNIFICANT_MOTION
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.STANDING_STILL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the phone is idle, left somewhere rather than carried on a vehicle. Exploratory: several
 * [Mechanism]s tell it, the selected one decides and the others are only logged. None of them
 * tells it while the phone is in use (screen on and unlocked) or charges, their wait only starts
 * once both ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
internal class PhoneIdleUseCase(
    private val appPreferences: AppPreferences,
    private val deviceIdleModeUseCase: DeviceIdleModeUseCase,
    screenStateUseCase: ScreenStateUseCase,
    chargingStateUseCase: ChargingStateUseCase,
    private val significantMotionUseCase: SignificantMotionUseCase,
    private val activityRecognitionUseCase: ActivityRecognitionUseCase,
    scope: CoroutineScope,
    // Keeps counting while the phone sleeps, unlike the clock of delay()
    private val now: () -> Duration = { elapsedRealtime().milliseconds },
    private val tier: AppTier = AppTier.current,
) {
    private val logger = Logger.withTag("PhoneIdle")

    enum class Mechanism {
        /** The system's Deep Doze, which some phones enter while carried */
        DEEP_DOZE,

        /**
         * No significant motion reported by the system for [QUIET_PERIOD]. Deep Doze instead on a
         * phone without the sensor.
         */
        NO_SIGNIFICANT_MOTION,

        /** Detected as still with [STILL_MIN_CONFIDENCE] at least, for [QUIET_PERIOD] */
        STANDING_STILL,
    }

    /**
     * What the selected [Mechanism] tells. Every mechanism is tracked meanwhile, and logs when it
     * would tell otherwise, so that they can be compared on the same ride. They are only listened to
     * while the phone is neither in use nor charging, starting over each time. Shared, so
     * that a single set of listeners is registered.
     */
    val isIdle: SharedFlow<Boolean> = combine(
        screenStateUseCase.isInUse,
        chargingStateUseCase.state.map { it.cable || it.wireless },
    ) { inUse, charging -> inUse.not() && charging.not() }
        .distinctUntilChanged()
        .onEach { logger.i { if (it) "Screen off or locked, and not charging" else "In use or charging: never idle" } }
        .flatMapLatest { unattended -> if (unattended) selectedIsIdle() else flowOf(false) }
        .distinctUntilChanged()
        // A stale answer from the last time somebody listened says nothing about now
        .shareIn(scope, WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    private fun selectedIsIdle(): Flow<Boolean> = combine(
        appPreferences
            .phoneIdleMechanismInUse
            .map { it.asPhoneIdleMechanism(tier) }
            .distinctUntilChanged()
            .onEach { logger.i { "Selected: $it" } },
        deviceIdleModeUseCase.isDeviceIdle.logged(DEEP_DOZE),
        noSignificantMotion().logged(NO_SIGNIFICANT_MOTION),
        // Lite doesn't declare the physical activity permission
        if (tier == LITE) flowOf(false) else standingStill().logged(STANDING_STILL),
    ) { selected, doze, noMotion, still ->
        when (selected) {
            DEEP_DOZE -> doze
            NO_SIGNIFICANT_MOTION -> noMotion
            STANDING_STILL -> still
        }
    }

    private fun noSignificantMotion(): Flow<Boolean> = significantMotionUseCase
        .motions
        // Nothing is known from before listening: it starts the same as a motion
        ?.onStart { emit(Unit) }
        ?.transformLatest {
            emit(false)
            waitQuietPeriod(NO_SIGNIFICANT_MOTION)
            emit(true)
        }
        // As the default mechanism, it must still tell something
        ?: deviceIdleModeUseCase
            .isDeviceIdle
            .onStart { logger.w { "No significant motion sensor: $NO_SIGNIFICANT_MOTION falls back on $DEEP_DOZE" } }

    private fun standingStill(): Flow<Boolean> = flow {
        if (activityRecognitionUseCase.isPermitted().not()) {
            logger.w { "No physical activity permission: $STANDING_STILL never tells the phone is idle" }
            emit(false)
            return@flow
        }
        activityRecognitionUseCase
            .probableActivities
            .map { activities -> activities.firstOrNull { it.type == STILL }?.confidence ?: 0 }
            .onEach { logger.d { "Still confidence: $it%" } }
            // Play Services stops sending updates once the phone was still for a while: a missing
            // sample means still. Only a sample below the threshold starts the wait over.
            .map { it >= STILL_MIN_CONFIDENCE }
            .distinctUntilChanged()
            .transformLatest { still ->
                emit(false)
                if (still) {
                    waitQuietPeriod(STANDING_STILL)
                    emit(true)
                }
            }
            .onStart { emit(false) }
            .let { emitAll(it) }
    }

    /**
     * Waits for [QUIET_PERIOD] of [now]. delay() doesn't count the time the phone sleeps, so it is
     * only used in steps of [CHECK_STEP]: the wait ends at most one step of awake time late.
     */
    private suspend fun waitQuietPeriod(mechanism: Mechanism) {
        val end = now() + QUIET_PERIOD
        while (now() < end) delay((end - now()).coerceAtMost(CHECK_STEP))
        (now() - end)
            .takeIf { it > LATE_THRESHOLD }
            ?.also { logger.i { "$mechanism: the $QUIET_PERIOD wait ended $it late, the phone slept" } }
    }

    private fun Flow<Boolean>.logged(mechanism: Mechanism): Flow<Boolean> = this
        .distinctUntilChanged()
        .onEach { idle ->
            logger.i {
                "$mechanism: ${if (idle) "idle" else "not idle"}" +
                    appPreferences
                        .phoneIdleMechanismInUse
                        .value
                        .asPhoneIdleMechanism(tier)
                        .takeIf { it != mechanism }
                        ?.let { " (not selected, $it decides: would ${if (idle) "suspend" else "resume"})" }
                        .orEmpty()
            }
        }

    internal companion object {
        val QUIET_PERIOD = 5.minutes
        const val STILL_MIN_CONFIDENCE = 90
        private val CHECK_STEP = 30.seconds
        private val LATE_THRESHOLD = 5.seconds

        // Deep Doze is entered by some phones while carried on a ride (a Pixel 7), significant motion
        // was right on every ride tried
        val DEFAULT_MECHANISM = NO_SIGNIFICANT_MOTION

        /** The mechanisms [tier] offers: Lite can't detect activities, [STANDING_STILL] needs them */
        fun mechanisms(tier: AppTier = AppTier.current): List<Mechanism> =
            Mechanism.entries.filter { tier != LITE || it != STANDING_STILL }

        /**
         * The mechanism a stored name stands for, the default one for an unknown or missing name, or
         * one [tier] doesn't offer (imported from Pro)
         */
        fun String?.asPhoneIdleMechanism(tier: AppTier = AppTier.current): Mechanism =
            mechanisms(tier).firstOrNull { it.name == this } ?: DEFAULT_MECHANISM
    }
}
