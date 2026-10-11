package com.masselis.tpmsadvanced.feature.background.interfaces

import androidx.core.content.ContextCompat.startForegroundService
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts and stops [MonitorService] for whoever needs to: the manual button, the persistent
 * scanning toggle, the app-open check and the boot receiver.
 */
internal class MonitoringController {

    val isRunning: StateFlow<Boolean> = MonitorService.isRunning

    /**
     * What the manual button shows. Turning persistent scanning off would otherwise show the
     * service as monitoring by hand, for the moment it takes to stop.
     */
    val isMonitoring: StateFlow<Boolean> = MonitorService.isMonitoring

    /**
     * Safe to call while the service already runs: it then keeps its single notifier. Throws when
     * the system refuses the start.
     */
    fun start() {
        startForegroundService(appContext, MonitorService.intent())
    }

    /** @return true if the service was running */
    fun stop(): Boolean = MonitorService.stop()
}
