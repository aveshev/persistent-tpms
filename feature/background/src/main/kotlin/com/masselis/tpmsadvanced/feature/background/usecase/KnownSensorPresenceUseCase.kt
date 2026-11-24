package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.SENSOR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tells whether a sensor bound to a vehicle is around, for [SENSOR], from the readings of the
 * service's scan. Sensors mostly send while their wheel turns, so this tells the vehicle is moving.
 *
 * A single reading at least as loud as [RideTriggers.MIN_RSSI] makes it present, for a hold after
 * each one: "Stay active"'s duration when it's on, [DEFAULT_HOLD] otherwise, so the cause holds by
 * itself. The scan lets an unchanged advertisement through once per `BluetoothLeScanner.BURST`
 * (50 s) at most, from the copy it last let through: the last reading is at most that much older
 * than the sensor's last packet, little next to the hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class KnownSensorPresenceUseCase(
    private val appPreferences: AppPreferences,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    /** The wall clock, in seconds, which the readings' timestamps are on */
    private val wallClock: () -> Double = ::now,
) {
    /** When the sensors stop being present, null if they never were */
    private val presentUntil = MutableStateFlow<TimeMark?>(null)

    val isPresent: Flow<Boolean> = presentUntil
        .transformLatest { until ->
            until
                ?.let { -it.elapsedNow() }
                ?.takeIf { it.isPositive() }
                ?.also { emit(true) }
                ?.also { delay(it) }
            emit(false)
        }
        .distinctUntilChanged()

    /** A reading of a bound sensor. The listening starts with the latest stored one, which wasn't heard now. */
    fun heard(atmosphere: TyreAtmosphere) {
        if (wallClock() - atmosphere.timestamp > RECENT.inWholeSeconds) return
        if (atmosphere.rssi?.let(RideTriggers::isHeard) != true) return
        presentUntil.value = timeSource.markNow() + hold()
    }

    private fun hold(): Duration =
        if (appPreferences.stayActive.value) appPreferences.stayActiveMinutes.value.minutes else DEFAULT_HOLD

    internal companion object {
        /** How long the sensors stay present after the last reading, when "Stay active" is off */
        val DEFAULT_HOLD = 5.minutes

        private val RECENT = 1.minutes
    }
}
