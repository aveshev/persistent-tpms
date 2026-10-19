package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stops the monitoring service and waits for it, before the app restarts on purpose (to import the
 * other app's data): stopped this way, it isn't reported as stopped unexpectedly afterwards.
 */
public suspend fun stopMonitoring(): Unit = with(Bindings.featureBackgroundInternal) {
    controller.stop()
    withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { controller.isRunning.first { it.not() } }
    // In case the service didn't stop in time
    unexpectedStopUseCase.stopped()
}

private const val STOP_TIMEOUT_MILLIS = 2_000L
