package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.ioc.vehicle.ServiceComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On Android 10 and 11, a Bluetooth scan gets results only while the app has location access, and
 * the app only asks for it while in use: a scan started while the app isn't visible hears nothing,
 * the system logging "Need ACCESS_FINE_LOCATION permission to get scan results". This service's
 * location type extends that access to the background. Android 10 gives it however the service
 * was started, Android 11 only when the service was started while the app was visible, so the app
 * starts it again when opened, see `PersistentScanningViewModel.ensureRunning`.
 */
internal class MonitorService : LifecycleService() {

    private lateinit var component: ServiceComponent

    private val createdAt = System.currentTimeMillis()

    override fun onCreate() {
        isRunningMutableStateFlow.value = true
        isMonitoringMutableStateFlow.value = true
        super.onCreate()
        // Persistent scanning runs this service: the process is alive whenever there is a status
        // to announce. A single instance for the process, which outlives the service to say "off".
        Bindings.featureBackgroundInternal.scanStatusAnnouncer()
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // The service is started again while it runs (the app being opened...), a second
        // component would monitor everything twice. The intent is null after a sticky restart.
        if (::component.isInitialized.not()) component = ServiceComponent(this, lifecycleScope)
        return Bindings.featureBackgroundInternal
            .appPreferences
            .persistentScanning
            .value
            // Persistent scanning can be turned on while the service already runs
            .also { Bindings.featureBackgroundInternal.unexpectedStopUseCase.started(it, createdAt) }
            .let { persistent -> if (persistent) START_STICKY else START_NOT_STICKY }
    }

    override fun onDestroy() {
        Bindings.featureBackgroundInternal.unexpectedStopUseCase.stopped()
        super.onDestroy()
        isMonitoringMutableStateFlow.value = false
        isRunningMutableStateFlow.value = false
    }

    companion object {
        private val isRunningMutableStateFlow = MutableStateFlow(false)
        val isRunning = isRunningMutableStateFlow.asStateFlow()

        private val isMonitoringMutableStateFlow = MutableStateFlow(false)

        /**
         * [isRunning] until the service is asked to stop, rather than until the system destroys
         * it a moment later: in between, it monitors nothing anymore.
         */
        val isMonitoring = isMonitoringMutableStateFlow.asStateFlow()

        /** @return true if the service was running */
        fun stop(): Boolean = appContext
            .stopService(intent())
            .also { if (it) isMonitoringMutableStateFlow.value = false }

        fun intent() = Intent(appContext, MonitorService::class.java)
    }
}
