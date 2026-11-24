package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the service runs for. Persistent mode and the ride notification each keep it running in the
 * background, watching for the vehicle: only persistent mode scans the tyres once it's detected.
 */
internal enum class BackgroundMode {
    /** Neither is on: the service runs from the manual start to the manual stop, scanning all along */
    MANUAL,

    /** Scans the tyres once the vehicle is detected, and sends the ride notification if it's on */
    PERSISTENT,

    /** Only watches for the vehicle, to send the ride notification: never scans the tyres at full rate */
    RIDE_NOTIFICATION;

    /** Whether the service is expected to run all the time, coming back after a reboot */
    val isInBackground: Boolean get() = this != MANUAL

    companion object {
        fun of(persistentScanning: Boolean, rideNotification: Boolean): BackgroundMode = when {
            persistentScanning -> PERSISTENT
            rideNotification -> RIDE_NOTIFICATION
            else -> MANUAL
        }

        fun current(appPreferences: AppPreferences): BackgroundMode =
            of(appPreferences.persistentScanning.value, appPreferences.rideNotification.value)

        fun flow(appPreferences: AppPreferences): Flow<BackgroundMode> = combine(
            appPreferences.persistentScanning,
            appPreferences.rideNotification,
            ::of,
        ).distinctUntilChanged()
    }
}
