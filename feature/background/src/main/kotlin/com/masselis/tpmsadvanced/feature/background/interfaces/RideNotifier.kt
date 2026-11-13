package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getActivity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.RideTriggers
import com.masselis.tpmsadvanced.feature.background.usecase.RideTriggers.Companion.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.RideTriggers.Companion.BLUETOOTH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The "Going for a ride?" notification: the open app scans at full rate, the background's scans are
 * throttled by Android (low latency forced down to low power after a few minutes), so opening the
 * app is what makes every reading reach it. Android can't open the app from the background, hence
 * a notification.
 *
 * A trigger fires when heard after [AppPreferences.rideAbsentMinutesInUse] away: a bound sensor of a
 * vehicle (each vehicle on its own), one of the activate condition's Bluetooth devices connecting,
 * or one of its beacons coming nearby. The devices and the beacons are only watched while the
 * service monitors, the sensors are heard by its scan: the whole notification needs persistent
 * scanning. Nothing while the app is open, which also removes it, nor in demo mode. Everything runs
 * on [scope], which must run one thing at a time.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
internal class RideNotifier(
    private val appPreferences: AppPreferences,
    nearbyBeacons: Flow<Map<String, Int>>,
    bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    private val isDemo: StateFlow<Boolean>,
    isAppVisible: Flow<Boolean>,
    isMonitoring: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("RideNotifier")
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val store = appContext.getSharedPreferences("RIDE_TRIGGERS", Context.MODE_PRIVATE)

    // Only touched from the single-threaded scope
    private var triggers: RideTriggers = store.asRideTriggers()
        set(value) {
            field = value
            store.save(value)
        }

    private val isVisible = isAppVisible.stateIn(scope, Eagerly, false)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                // High for a banner, but silent: only an invitation, not an alert
                .Builder(CHANNEL, IMPORTANCE_HIGH)
                .setName("Ride notification")
                .setDescription("\"Going for a ride?\", when back near a vehicle")
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build()
        )

        // The notification did its job, or is pointless now
        isVisible
            .filter { it }
            .onEach { notificationManager.cancel(NOTIFICATION_ID) }
            .launchIn(scope)

        // A trigger turned off and on again starts over
        appPreferences.rideOnSensor
            .filter { it.not() }
            .onEach { triggers = triggers.forgetSensors() }
            .launchIn(scope)
        appPreferences.rideOnBeacon
            .filter { it.not() }
            .onEach { triggers = triggers.forget(BEACON) }
            .launchIn(scope)
        appPreferences.rideOnBluetooth
            .filter { it.not() }
            .onEach { triggers = triggers.forget(BLUETOOTH) }
            .launchIn(scope)

        isMonitoring
            .distinctUntilChanged()
            .flatMapLatest { monitoring ->
                if (monitoring.not()) emptyFlow()
                else merge(
                    appPreferences.rideOnBeacon
                        .watching(appPreferences.beacons.map { it.isNotEmpty() }) {
                            // Starts a scan of its own while the activate condition doesn't look
                            // for the beacons
                            nearbyBeacons.map { it.isNotEmpty() }
                        }
                        .presence(BEACON),
                    appPreferences.rideOnBluetooth
                        .watching(appPreferences.activateBluetoothDevices.map { it.isNotEmpty() }) {
                            combine(
                                appPreferences.activateBluetoothDevices,
                                bluetoothDevicesUseCase.connected,
                            ) { selected, connected -> connected.any { it.address in selected } }
                        }
                        .presence(BLUETOOTH),
                )
            }
            .launchIn(scope)
    }

    /**
     * A record of a bound sensor of [vehicle], from the service's scan. The listening starts with
     * the latest stored record, which wasn't heard now.
     */
    fun sensorHeard(vehicle: UUID, atmosphere: TyreAtmosphere) {
        scope.launch {
            if ((appPreferences.rideNotification.value && appPreferences.rideOnSensor.value).not()) return@launch
            if ((now() - atmosphere.timestamp).seconds > RECENT) return@launch
            if (atmosphere.rssi?.let(RideTriggers::isHeard) != true) return@launch
            heard(RideTriggers.sensors(vehicle))
        }
    }

    /** What [present] says about the trigger while it and the notification are on, and [canWatch] */
    private fun Flow<Boolean>.watching(canWatch: Flow<Boolean>, present: () -> Flow<Boolean>): Flow<Boolean> =
        combine(appPreferences.rideNotification, this, canWatch) { notification, trigger, can ->
            notification && trigger && can
        }
            .distinctUntilChanged()
            .flatMapLatest { if (it) present().distinctUntilChanged() else emptyFlow() }

    /**
     * Heard when it becomes present, and every [STILL_THERE] while it stays so, so that the stored
     * time stays close to the truth if the process ends meanwhile. Its leaving counts as heard too:
     * the absence starts then.
     */
    private fun Flow<Boolean>.presence(source: String): Flow<Pair<Boolean, Boolean>> = this
        .flatMapLatest { present ->
            if (present) flow {
                while (true) {
                    emit(true)
                    delay(STILL_THERE)
                }
            }
            else flowOf(false)
        }
        .runningFold(false to false) { (_, was), now -> was to now }
        .drop(1)
        .onEach { (was, now) ->
            if (now) heard(source)
            else if (was) left(source)
        }

    private fun left(source: String) {
        triggers = triggers.heard(source, System.currentTimeMillis())
    }

    private fun heard(source: String) {
        val now = System.currentTimeMillis()
        val isBack = triggers.isBack(source, now, appPreferences.rideAbsentMinutesInUse.value.minutes)
        triggers = triggers.heard(source, now)
        if (isBack) prompt(source, now)
    }

    /** While the app is open it has nothing to invite to: only the time heard is kept */
    private fun prompt(source: String, now: Long) {
        if (isVisible.value || isDemo.value) {
            logger.i { "$source back near the vehicle, no notification: app open or demo" }
            return
        }
        val staleAfter = appPreferences.rideStaleMinutesInUse.value.minutes
        val isShowing = notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }
        val posts = triggers.posts(now, isShowing, staleAfter)
        triggers = triggers.fired(now)
        logger.i { "$source back near the vehicle, showing: $isShowing, posts: $posts" }
        if (notificationManager.areNotificationsEnabled().not()) return
        // Removed first: only a new notification makes a banner and goes up the list for sure. One
        // still current is updated quietly instead, which starts its timeout over.
        if (posts) notificationManager.cancel(NOTIFICATION_ID)
        NotificationCompat
            .Builder(appContext, CHANNEL)
            .setSmallIcon(R.drawable.car_tire)
            .setContentText("Going for a ride? Open Persistent TPMS to monitor tyre pressure")
            .setStyle(NotificationCompat.BigTextStyle())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setOnlyAlertOnce(posts.not())
            .setAutoCancel(true)
            .setTimeoutAfter(staleAfter.inWholeMilliseconds)
            .apply {
                appContext
                    .packageManager
                    .getLaunchIntentForPackage(appContext.packageName)
                    ?.let { getActivity(appContext, NOTIFICATION_ID, it, FLAG_IMMUTABLE) }
                    ?.also(::setContentIntent)
            }
            .build()
            .also { notificationManager.notify(NOTIFICATION_ID, it) }
    }

    private fun SharedPreferences.asRideTriggers() = RideTriggers(
        lastHeard = getStringSet(LAST_HEARD, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                entry
                    .substringAfterLast('=')
                    .toLongOrNull()
                    ?.let { entry.substringBeforeLast('=') to it }
            }
            .toMap(),
        lastFired = getLong(LAST_FIRED, 0).takeIf { it > 0 },
    )

    private fun SharedPreferences.save(triggers: RideTriggers) = edit {
        putStringSet(LAST_HEARD, triggers.lastHeard.map { (source, at) -> "$source=$at" }.toSet())
        triggers.lastFired?.also { putLong(LAST_FIRED, it) } ?: remove(LAST_FIRED)
    }

    private companion object {
        const val CHANNEL = "RIDE_NOTIFICATION"
        const val NOTIFICATION_ID = 0x52494445 // "RIDE"
        const val LAST_HEARD = "LAST_HEARD"
        const val LAST_FIRED = "LAST_FIRED"
        val RECENT = 1.minutes
        val STILL_THERE = 1.minutes
    }
}
