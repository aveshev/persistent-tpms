package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Companion.DEFAULT_BEACON_MIN_RSSI
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement.Companion.RSSI_UNAVAILABLE
import java.util.UUID
import kotlin.time.Duration

/**
 * Decides when something heard counts as being back near the vehicle, for the "Going for a ride?"
 * notification, ported from the iOS app's `RideTriggers`. Times are the wall clock's, in
 * milliseconds: they're stored, and must still mean something after the phone restarted.
 *
 * Unlike on iOS, a beacon needs no confirming here, [BeaconPresenceUseCase] already takes two
 * packets, and a stale notification needs no expiring, Android removes it by itself.
 */
internal data class RideTriggers(
    /** When each trigger was last heard nearby */
    val lastHeard: Map<String, Long> = emptyMap(),
    /** When a trigger last fired, whichever one */
    val lastFired: Long? = null,
) {

    /**
     * Whether hearing [source] at [now] counts as back near the vehicle: it had never been heard,
     * or not for [absentFor]
     */
    fun isBack(source: String, now: Long, absentFor: Duration): Boolean =
        lastHeard[source]?.let { now - it >= absentFor.inWholeMilliseconds } ?: true

    /** [source] was there at [now]: heard, or known to be there until now, like a connection ending */
    fun heard(source: String, now: Long): RideTriggers = copy(lastHeard = lastHeard + (source to now))

    /**
     * Whether a trigger firing at [now] posts a new notification: not while one [isShowing] is
     * still current, so two triggers firing for one return, or one coming back and forth, make a
     * single notification. Each firing keeps it current for another [staleAfter], see [fired].
     */
    fun posts(now: Long, isShowing: Boolean, staleAfter: Duration): Boolean =
        isShowing.not() || lastFired?.let { now - it < staleAfter.inWholeMilliseconds }?.not() ?: true

    fun fired(now: Long): RideTriggers = copy(lastFired = now)

    /** A trigger turned off and on again starts over, its first report counting as back near the vehicle */
    fun forget(source: String): RideTriggers = copy(lastHeard = lastHeard - source)

    fun forgetSensors(): RideTriggers = copy(lastHeard = lastHeard.filterKeys { it.startsWith(SENSORS).not() })

    companion object {
        /**
         * The signal (dBm) from which a sensor counts as heard, like the beacons by default: a bike
         * parked out of reach of the house reads as absent, and going to it as coming back
         */
        const val MIN_RSSI = DEFAULT_BEACON_MIN_RSSI

        const val BEACON = "beacon"
        const val BLUETOOTH = "bluetooth"
        private const val SENSORS = "sensors:"

        /** Each vehicle has its own absence: a neighbouring car's sensor heard all day doesn't hide the bike */
        fun sensors(vehicle: UUID): String = "$SENSORS$vehicle"

        /** Whether a packet at [rssi] counts as hearing its sensor at all */
        fun isHeard(rssi: Int): Boolean = rssi != RSSI_UNAVAILABLE && rssi >= MIN_RSSI
    }
}
