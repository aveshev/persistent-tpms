package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MAX
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSnoozeUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSpeaker
import com.masselis.tpmsadvanced.feature.background.usecase.StoredTyreAlertsUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.StoredTyreAlertsUseCase.Update
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Posts a notification per vehicle, tyre and class of alert, separate from the monitor service's,
 * whatever scans: see docs/alerts.md.
 */
@SuppressLint("MissingPermission")
internal class AlertNotifier(
    storedTyreAlertsUseCase: StoredTyreAlertsUseCase,
    vehicleListUseCase: VehicleListUseCase,
    private val snoozeUseCase: AlertSnoozeUseCase,
    private val speaker: AlertSpeaker,
    private val unitPreferences: UnitPreferences,
    /** Single threaded: the readings and the end of the holds take turns */
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("AlertNotifier")
    private val notificationManager = NotificationManagerCompat.from(appContext)

    /**
     * The notifications held at their level, by tag, see [HOLD]. Kept in memory: after the process
     * died, a notification keeps its level until its next reading.
     */
    private val holds = mutableMapOf<String, Hold>()

    /** [pending] is the latest reading which would lower the notification, applied when it ends */
    private data class Hold(val until: TimeMark, val job: Job, val pending: Update? = null)

    /** The updates hold the vehicles as they were when the tyres started being followed */
    private val latestVehicles = vehicleListUseCase
        .vehicleListFlow
        .stateIn(scope, Eagerly, emptyList())

    init {
        notificationManager.createNotificationChannelsCompat(
            AlertLevel.entries.map { level ->
                NotificationChannelCompat
                    .Builder(level.channel, if (level == CRIMSON) IMPORTANCE_MAX else IMPORTANCE_HIGH)
                    .setName(
                        when (level) {
                            AMBER -> "Tyre warnings"
                            RED -> "Tyre alerts"
                            CRIMSON -> "Critical tyre alerts"
                        }
                    )
                    .setDescription(
                        when (level) {
                            AMBER -> "To address when convenient"
                            RED -> "To address at the earliest possibility, ride on with caution"
                            CRIMSON -> "To fix before riding on"
                        }
                    )
                    .build()
            }
        )

        storedTyreAlertsUseCase
            .updates
            .onEach { update ->
                shown().let { shown ->
                    AlertClass.entries.forEach {
                        if (update.isReevaluation) update.reevaluate(it, shown) else update.notify(it, shown)
                    }
                }
            }
            .catch { logger.e("Failed to follow the tyres' alerts", it) }
            .launchIn(scope)
    }

    /** What the shown notifications show, by tag */
    private fun shown(): Map<String?, Shown> = notificationManager
        .activeNotifications
        .filter { it.id == NOTIFICATION_ID }
        .associate { notification ->
            notification.tag to notification.notification.extras.let {
                Shown(it.getInt(EXTRA_SENSOR_ID), AlertLevel.entries[it.getInt(EXTRA_LEVEL)])
            }
        }

    /** [isSpoken] is false for a reading which was already spoken, held until now */
    @Suppress("MaxLineLength", "CyclomaticComplexMethod", "LongMethod")
    private fun Update.notify(alertClass: AlertClass, shown: Map<String?, Shown>, isSpoken: Boolean = true) {
        val tag = tag(vehicle, location, alertClass)
        val sensorId = requireNotNull(alerts.latest).sensorId
        val current = shown[tag]
        val isHeld = holds[tag]?.until?.hasNotPassedNow() == true
        val action = action(alerts, alertClass, current, isHeld) { snoozeUseCase.isSnoozed(sensorId, alertClass, it) }
        when (action) {
            Action.None -> Unit
            Action.Cancel -> {
                notificationManager.cancel(tag, NOTIFICATION_ID)
                holds.remove(tag)?.job?.cancel()
            }

            is Action.Post -> {
                notificationManager.notify(tag, NOTIFICATION_ID, notification(tag, alertClass, action))
                holds.remove(tag)?.job?.cancel()
                holds[tag] = Hold(
                    TimeSource.Monotonic.markNow() + HOLD,
                    scope.launch {
                        delay(HOLD)
                        // The latest reading which would have lowered it goes through now
                        holds.remove(tag)?.pending?.notify(alertClass, shown(), isSpoken = false)
                    },
                )
            }

            is Action.Hold -> holds[tag] = requireNotNull(holds[tag]).copy(pending = this)
        }
        // Unlike the notification, the speech follows the reading right away: it stops, or turns red
        if (isSpoken) when {
            action is Action.Post && action.level == CRIMSON -> if (action.sound) speaker.crimson(tag, vehicle.uuid, alertClass)

            else -> {
                speaker.stop(tag)
                val level = (action as? Action.Post)?.level ?: (action as? Action.Hold)?.level
                val isSounding = (action as? Action.Post)?.sound == true
                // Coming down from crimson, said as red although its notification is held silently
                if (level == RED && (isSounding || current?.level == CRIMSON)) speaker.red(tag, vehicle.uuid, alertClass, sounded = isSounding)
            }
        }
    }

    /**
     * The thresholds changed: the notification, its hold notwithstanding, and its speech come down
     * to the latest reading's level at once, see [reevaluation]
     */
    private fun Update.reevaluate(alertClass: AlertClass, shown: Map<String?, Shown>) {
        val tag = tag(vehicle, location, alertClass)
        val current = shown[tag] ?: return
        val sensorId = requireNotNull(alerts.latest).sensorId
        val action = reevaluation(alerts, alertClass, current) { snoozeUseCase.isSnoozed(sensorId, alertClass, it) }
        when (action) {
            Action.Cancel -> {
                notificationManager.cancel(tag, NOTIFICATION_ID)
                holds.remove(tag)?.job?.cancel()
                speaker.stop(tag, isDismissed = true)
            }

            is Action.Post -> {
                notificationManager.notify(tag, NOTIFICATION_ID, notification(tag, alertClass, action))
                // A reading held back is older than the one just re-evaluated
                holds.remove(tag)?.job?.cancel()
                if (action.level < current.level) {
                    speaker.stop(tag, isDismissed = action.level < RED)
                    // Coming down from crimson, said as red, as a reading lowering it would be
                    if (action.level == RED) speaker.red(tag, vehicle.uuid, alertClass, sounded = false)
                }
            }

            Action.None, is Action.Hold -> Unit
        }
    }

    @Suppress("LongMethod")
    private fun Update.notification(tag: String, alertClass: AlertClass, action: Action.Post) = NotificationCompat
        .Builder(appContext, action.level.channel)
        .setSmallIcon(R.drawable.car_tire_alert)
        .setColor(
            when (action.level) {
                AMBER -> AMBER_COLOR
                RED -> RED_COLOR
                CRIMSON -> CRIMSON_COLOR
            }
        )
        .setPriority(if (action.level == CRIMSON) PRIORITY_MAX else PRIORITY_HIGH)
        .apply {
            // Through Do Not Disturb when it lets alarms through, as when driving
            if (action.level == CRIMSON) setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        .setOnlyAlertOnce(action.sound.not())
        .setSubText((latestVehicles.value.firstOrNull { it.uuid == vehicle.uuid } ?: vehicle).name)
        .setContentTitle(title(alertClass, action.level))
        .setContentText(text(alertClass))
        // The reading's own time, a notification left over from earlier looks its age
        .setWhen(requireNotNull(alerts.latest).timestamp.times(MILLIS_PER_SECOND).roundToLong())
        .setShowWhen(true)
        .setContentIntent(
            Intent(Intent.ACTION_VIEW, "tpmsadvanced://vehicle/${vehicle.uuid}".toUri()).let {
                TaskStackBuilder
                    .create(appContext)
                    .addNextIntentWithParentStack(it)
                    .getPendingIntent(0, FLAG_IMMUTABLE)
            }
        )
        .apply {
            action.level.snoozes.forEach { duration ->
                addAction(0, "Dismiss ${duration.label}", snooze(tag, alertClass, action.level, duration))
            }
        }
        // Swiping it away dismisses it for the shorter period
        .setDeleteIntent(snooze(tag, alertClass, action.level, action.level.snoozes.min()))
        .setAutoCancel(false)
        .addExtras(
            bundleOf(
                EXTRA_SENSOR_ID to requireNotNull(alerts.latest).sensorId,
                EXTRA_LEVEL to action.level.ordinal,
            )
        )
        .build()

    private fun Update.snooze(tag: String, alertClass: AlertClass, level: AlertLevel, duration: Duration) =
        getBroadcast(
            appContext,
            0,
            AlertActionReceiver.intent(tag, requireNotNull(alerts.latest).sensorId, alertClass, level, duration),
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT,
        )

    /** Short enough to fit beside the vehicle's name, the tyre is told in the [text] */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun Update.title(alertClass: AlertClass, level: AlertLevel) = when (alertClass) {
        PRESSURE -> isLowPressure.let { isLow ->
            when (level) {
                AMBER -> if (isLow) "Pressure getting low" else "Pressure getting high"
                RED -> if (isLow) "Pressure low" else "Pressure high"
                CRIMSON -> if (isLow) "Pressure critically low" else "Pressure critically high"
            }
        }

        TEMPERATURE -> when (level) {
            AMBER -> "Tyre getting hot"
            RED -> "Tyre hot"
            CRIMSON -> "Tyre critically hot"
        }

        BATTERY -> if (level == AMBER) "Sensor battery getting low" else "Sensor battery low"
        PRESSURE_LOSS -> "Losing pressure"
        SENSOR_ALARM -> "May be leaking"
    }

    private fun Update.text(alertClass: AlertClass): String = buildString {
        appendLoc(location, capitalized = true)
        append(": ")
        append(details(alertClass))
    }

    @Suppress("CyclomaticComplexMethod")
    private fun Update.details(alertClass: AlertClass): String {
        val reading = requireNotNull(alerts.latest)
        val pressureUnit = unitPreferences.pressure.value
        return when (alertClass) {
            PRESSURE -> buildString {
                append("${reading.pressure.string(pressureUnit)}, ")
                append(
                    if (isLowPressure) "minimum ${thresholds.lowPressure.string(pressureUnit)}"
                    else "maximum ${thresholds.highPressure.string(pressureUnit)}"
                )
                // Not notified on their own then, see action()
                if (alerts.foldsLeaks) {
                    loss?.also { append(", ${it.summary(pressureUnit)}") }
                    if (SENSOR_ALARM in alerts.levels) append(", sensor alarm")
                }
            }

            TEMPERATURE -> unitPreferences.temperature.value.let { unit ->
                "${reading.temperature.string(unit)}, hot from ${thresholds.highTemp.string(unit)}"
            }

            BATTERY -> reading
                .batteryVoltage
                ?.let { voltage ->
                    "${voltage.string()}, alarm at ${
                        thresholds.lowBatteryVoltage.alarmAt(reading.temperature).string()
                    }"
                }
                ?: "${reading.batteryPercent} %, alarm at ${thresholds.lowBatteryPercent} %"
            PRESSURE_LOSS -> loss
                ?.let { "${it.summary(pressureUnit)}, now ${reading.pressure.string(pressureUnit)}" }
                ?: reading.pressure.string(pressureUnit)

            SENSOR_ALARM -> "the sensor raised its own alarm, at ${reading.pressure.string(pressureUnit)}"
        }
    }

    private fun PressureLoss.summary(unit: PressureUnit) =
        "down ${drop.string(unit)} in ${((until - since) / SECONDS_PER_MINUTE).roundToLong().coerceAtLeast(1)} min"

    /** Which side of the range the pressure alerts for, the closer one */
    private val Update.isLowPressure
        get() = requireNotNull(alerts.latest).pressure.kpa <
            (thresholds.lowPressure.kpa + thresholds.highPressure.kpa) / 2

    /** What the notification of a class shows */
    internal data class Shown(val sensorId: Int, val level: AlertLevel)

    /** What a tyre's new reading does to the notification of one of its classes */
    internal sealed interface Action {
        data object None : Action
        data object Cancel : Action
        data class Post(val level: AlertLevel, val sound: Boolean) : Action

        /** Keeps the notification as it is until its hold ends, the reading would lower it to [level] */
        data class Hold(val level: AlertLevel?) : Action
    }

    internal companion object {
        const val NOTIFICATION_ID = 2
        private const val EXTRA_SENSOR_ID = "ALERT_SENSOR_ID"
        private const val EXTRA_LEVEL = "ALERT_LEVEL"
        private const val MILLIS_PER_SECOND = 1000.0
        private const val SECONDS_PER_MINUTE = 60.0
        private const val AMBER_COLOR = 0xFFFF9800.toInt()
        private const val RED_COLOR = 0xFFBA1A1A.toInt()
        private const val CRIMSON_COLOR = 0xFF7A0010.toInt()

        /**
         * See docs/alerts.md: a level (see [TyreAlerts.notifiable]) is posted unless dismissed for a
         * while, and holds the notification at it [isHeld] for [HOLD]: a reading lowering it or
         * clearing it meanwhile waits until the hold ends, a reading at that level or above starts it
         * over. It sounds when it's new, higher than shown, or red or crimson again: a lower level
         * updates silently. A leak isn't notified on its own while the tyre's pressure is red or
         * crimson, it's told in the pressure's notification, see [foldsLeaks].
         */
        @Suppress("CyclomaticComplexMethod", "MaxLineLength")
        fun action(
            alerts: TyreAlerts,
            alertClass: AlertClass,
            shown: Shown?,
            isHeld: Boolean,
            isSnoozed: (AlertLevel) -> Boolean,
        ): Action {
            val level = alerts.notifiable[alertClass]
            return when {
                // At once rather than after the hold, the pressure's notification tells it now
                alertClass in LEAKS && alerts.foldsLeaks -> if (shown == null) Action.None else Action.Cancel
                shown == null -> if (level == null || isSnoozed(level)) Action.None else Action.Post(level, sound = true)
                // Another sensor was bound, whatever the previous one alerted for was likely seen to
                shown.sensorId != alerts.latest?.sensorId ->
                    if (level == null || isSnoozed(level)) Action.Cancel else Action.Post(level, sound = true)

                level != null && level >= shown.level ->
                    if (isSnoozed(level)) Action.None
                    else Action.Post(level, sound = level > shown.level || level >= RED)

                isHeld -> Action.Hold(level)
                level == null || isSnoozed(level) -> Action.Cancel
                else -> Action.Post(level, sound = false)
            }
        }

        /**
         * What re-evaluating the latest reading under other thresholds does to a [shown]
         * notification: lowered silently or cleared, the hold notwithstanding, see docs/alerts.md.
         * Never raised nor posted, a threshold change doesn't alert about a reading already stored.
         */
        fun reevaluation(
            alerts: TyreAlerts,
            alertClass: AlertClass,
            shown: Shown,
            isSnoozed: (AlertLevel) -> Boolean,
        ): Action {
            val level = alerts.notifiable[alertClass]
            return when {
                // About another sensor than the latest reading's, left to the next reading
                shown.sensorId != alerts.latest?.sensorId -> Action.None
                alertClass in LEAKS && alerts.foldsLeaks -> Action.Cancel
                level == null || isSnoozed(level) -> Action.Cancel
                level > shown.level -> Action.None
                else -> Action.Post(level, sound = false)
            }
        }

        /** What tells the tyre may be leaking, "Leaking?" on the main screen */
        private val LEAKS = setOf(PRESSURE_LOSS, SENSOR_ALARM)

        /**
         * The pressure being red or crimson already says more than a leak's amber: another
         * notification would only be one more sound and one more thing to dismiss
         */
        val TyreAlerts.foldsLeaks
            get() = notifiable[PRESSURE]?.let { it >= RED } == true

        /** A value hovering on a boundary while riding, read about once a minute, doesn't flicker */
        val HOLD = 3.minutes

        fun tag(vehicle: Vehicle, location: Vehicle.Kind.Location, alertClass: AlertClass) =
            "alert/${vehicle.uuid}/$location/${alertClass.name}"

        private val AlertLevel.channel
            get() = "ALERT_${name}"

        private val AlertLevel.snoozes
            get() = when (this) {
                AMBER -> listOf(1.days, 7.days)
                RED, CRIMSON -> listOf(10.minutes, 1.days)
            }

        private val Duration.label
            get() = when {
                this < 1.days -> "$inWholeMinutes min"
                this == 1.days -> "1 day"
                this == 7.days -> "1 week"
                else -> "$inWholeDays days"
            }
    }
}
