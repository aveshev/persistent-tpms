package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getActivity
import android.app.PendingIntent.getBroadcast
import android.app.Service
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MAX
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
import androidx.core.app.ServiceCompat.stopForeground
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Active
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.BluetoothOff
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Idle
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.MonitoringFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanRetrying
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Starting
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Suspended
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.explanation
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * The monitor service's notification, telling the status of the scan. The tyres' alerts are
 * notified on their own, whatever scans, see [AlertNotifier].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
@Suppress("MaxLineLength")
internal class ServiceNotifier(
    scope: CoroutineScope,
    service: Service,
    vehicleListUseCase: VehicleListUseCase,
    scanPolicyUseCase: ScanPolicyUseCase,
    tyreScanStatus: Flow<ScanStatus?>,
) {
    private val logger = Logger.withTag("ServiceNotifier")
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameWhenOk, IMPORTANCE_LOW)
                .setName("Monitor service")
                .build()
        )
        // Was the alerts' channel, before they had their own notifications: a user who silenced it
        // keeps it silent
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameForFailure, IMPORTANCE_MAX)
                .setName("Monitor service failure")
                .build()
        )
        notificationManager.deleteNotificationChannel(OLD_CHANNEL_FOR_LOW_BATTERY)
        notificationManager.deleteNotificationChannel(OLD_CHANNEL_FOR_PRESSURE_LOSS)

        scanPolicyUseCase
            .decision
            // Listening to the tyres is what scans them, and stores their readings for the alerts
            .serviceStates(tyreScanStatus) { decision ->
                vehicleListUseCase
                    .vehicleListFlow
                    // Editing a vehicle (ranges, name...) re-emits the list; only a change in the
                    // set of vehicles may rebuild the collectors, otherwise the BLE scan restarts.
                    .distinctUntilChanged { old, new ->
                        old.map(Vehicle::uuid) == new.map(Vehicle::uuid)
                    }
                    .flatMapLatest { vehicles ->
                        vehicles
                            .flatMap { vehicle ->
                                VehicleComponent(vehicle).let { component ->
                                    vehicle.kind.locations.map {
                                        component.TyreComponent(it).tyreAtmosphereUseCase.listen()
                                    }
                                }
                            }
                            .merge()
                            .map<Any, State> { Active(decision) }
                            .onStart { emit(Active(decision)) }
                    }
            }
            .map { state ->
                NotificationCompat
                    .Builder(
                        appContext,
                        when (state) {
                            Starting, is Active, is Suspended, Idle, BluetoothOff, is ScanRetrying -> channelNameWhenOk
                            is ScanFailure, MonitoringFailure -> channelNameForFailure
                        }
                    )
                    .setSmallIcon(
                        when (state) {
                            Starting, is Active, is Suspended, Idle -> R.drawable.car_tire
                            BluetoothOff, is ScanRetrying, is ScanFailure, MonitoringFailure -> R.drawable.car_tire_alert
                        }
                    )
                    .setPriority(
                        when (state) {
                            Starting, is Active, is Suspended, Idle, BluetoothOff, is ScanRetrying -> PRIORITY_LOW
                            is ScanFailure, MonitoringFailure -> PRIORITY_MAX
                        }
                    )
                    .setContentText(
                        when (state) {
                            Starting -> "Background scanning is starting"
                            // Without persistent scanning, the service runs from the manual start
                            // to the manual stop
                            is Active ->
                                if (MANUAL in state.decision.causes) "Monitoring in the background"
                                else state.decision.explanation()

                            BluetoothOff -> "Background scanning is waiting for Bluetooth to be turned on"
                            is ScanRetrying -> "The Android system refused the Bluetooth scan (error " +
                                "${state.reason}), background scanning tries again shortly"

                            is ScanFailure -> "The Android system can't run the Bluetooth scan (error " +
                                "${state.reason}), Persistent TPMS must be restarted"

                            MonitoringFailure -> "Background monitoring stopped because of an unexpected " +
                                "error, Persistent TPMS must be restarted"

                            is Suspended -> state.decision.explanation()
                            Idle -> ScanDecision.Idle.explanation()
                        }
                    )
                    .apply {
                        when (state) {
                            // Opens the app on its current vehicle. Bluetooth off: the app tells it
                            // and has the button to turn it on
                            Starting, is Active, is Suspended, Idle, BluetoothOff, is ScanRetrying -> appContext
                                .packageManager
                                .getLaunchIntentForPackage(appContext.packageName)
                                ?.let { getActivity(appContext, requestCode, it, FLAG_IMMUTABLE) }
                                ?.also(::setContentIntent)

                            is ScanFailure, MonitoringFailure -> {
                                // Nothing to do, the intent does nothing when clicked
                            }
                        }
                    }
                    .apply {
                        when (state) {
                            // Persistent scanning is turned off from the app's settings, only the
                            // manual monitoring stops from here, even while waiting for Bluetooth
                            is Active, BluetoothOff, is ScanRetrying -> if (
                                (state as? Active)?.decision?.causes?.contains(MANUAL)
                                    ?: (scanPolicyUseCase.currentDecision as? ScanDecision.Active)?.causes?.contains(MANUAL)
                                    ?: false
                            ) addAction(
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Stop",
                                    getBroadcast(
                                        appContext,
                                        requestCode,
                                        DisableMonitorBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()
                            )

                            Starting, is Suspended, Idle -> Unit

                            is ScanFailure, MonitoringFailure -> addAction(
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Restart app",
                                    getBroadcast(
                                        appContext,
                                        requestCode,
                                        RestartAppBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()
                            )
                        }
                    }
                    .setAutoCancel(false)
                    .build()
            }
            .onEach {
                try {
                    ServiceCompat.startForeground(
                        service,
                        notificationId,
                        it,
                        // https://developer.android.com/about/versions/14/changes/fgs-types-required#connected-device
                        if (SDK_INT >= Q) FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                    )
                } catch (e: SecurityException) {
                    // The system refuses a connectedDevice foreground service when Bluetooth scan
                    // was revoked. That kills the process, and a sticky service is restarted right
                    // away: crashing here would loop. The next app opening walks the user through
                    // the missing permissions and starts the service again.
                    logger.w(e) { "The system refused the foreground service, stopping it" }
                    service.stopSelf()
                }
            }
            .launchIn(scope)

        callbackFlow<Nothing> {
            awaitClose {
                service.also { stopForeground(it, STOP_FOREGROUND_REMOVE) }
            }
        }.launchIn(scope)
    }

    sealed interface State {
        /** Before the scan decision is known */
        data object Starting : State

        data class Active(val decision: ScanDecision.Active) : State

        /** Scanning should be active but Bluetooth is off, it starts again once turned on */
        data object BluetoothOff : State

        /** The system refused the scan for a [reason] that can go away, it's tried again shortly */
        data class ScanRetrying(val reason: Int) : State

        /** The system can't run the scan, whatever the attempts: monitoring stopped */
        data class ScanFailure(val reason: Int) : State

        /** Something besides the scan failed (database, bug...): monitoring stopped */
        data object MonitoringFailure : State

        data class Suspended(val decision: ScanDecision.Suspended) : State

        data object Idle : State
    }

    @Suppress("ConstPropertyName")
    internal companion object {
        private const val channelNameWhenOk = "MONITOR_SERVICE_WHEN_OK"
        private const val channelNameForFailure = "MONITOR_SERVICE_FOR_ALERT"
        private const val OLD_CHANNEL_FOR_LOW_BATTERY = "MONITOR_SERVICE_FOR_LOW_BATTERY"
        private const val OLD_CHANNEL_FOR_PRESSURE_LOSS = "MONITOR_SERVICE_FOR_PRESSURE_LOSS"
        private const val notificationId = 1
        private const val requestCode = 0
    }
}

/**
 * What the service shows for each decision: the [active] states while active, unless the
 * [tyreScanStatus] tells the scan waits for Bluetooth or retries a refused start. The scanner
 * starts the scan over by itself then. Only the failures nothing can recover from end the flow,
 * with the state telling which. Each state differs from the previous one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<ScanDecision>.serviceStates(
    tyreScanStatus: Flow<ScanStatus?>,
    active: (ScanDecision.Active) -> Flow<ServiceNotifier.State>,
): Flow<ServiceNotifier.State> = flatMapLatest { decision ->
    when (decision) {
        // Not scanning: skip the scan entirely rather than emit nothing, since the service must
        // call startForeground() shortly after being started — a silent flow here would starve
        // that call if the app launches already suspended (e.g. opened while already in Doze).
        is ScanDecision.Suspended -> flowOf(Suspended(decision))
        ScanDecision.Idle -> flowOf(Idle)
        is ScanDecision.Active -> combine(active(decision), tyreScanStatus) { state, status ->
            when (status) {
                WaitingForBluetooth -> BluetoothOff
                is Retrying -> ScanRetrying(status.reason)
                Scanning, null -> state
            }
        }
    }
}
    // The service must call startForeground() shortly after being started, before the database
    // had time to answer
    .onStart { emit(Starting) }
    .catch { cause ->
        Logger.withTag("ServiceNotifier").e(cause) { "Monitoring stopped" }
        emit(if (cause is Failure.Scan) ScanFailure(cause.reason) else MonitoringFailure)
    }
    .distinctUntilChanged()
