package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.featureBackgroundInternal
import com.masselis.tpmsadvanced.feature.background.usecase.BackgroundMode

/**
 * Brings the background monitoring service (persistent mode, the ride notification) back after a
 * reboot or an app update, both of which kill it. `LOCKED_BOOT_COMPLETED` is deliberately not
 * used: the preferences are not readable before the first unlock.
 */
internal class BootReceiver : BroadcastReceiver() {

    private val logger = Logger.withTag("BootReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_BOOT_COMPLETED, ACTION_MY_PACKAGE_REPLACED)) return
        featureBackgroundInternal
            // The reboot or the update killed the service, it was not stopped behind the user's back
            .also { it.unexpectedStopUseCase.stopped() }
            .takeIf { BackgroundMode.current(it.appPreferences).isInBackground }
            // A boot start must never crash the app: a permission may have been revoked meanwhile,
            // which makes the system refuse the foreground start
            ?.also { internal ->
                runCatching { internal.controller.start() }
                    .onFailure { logger.e(it) { "Cannot restart the background monitoring service" } }
            }
    }
}
