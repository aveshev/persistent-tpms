package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import androidx.startup.Initializer
import com.masselis.tpmsadvanced.core.common.AppGraphReadyInitializer
import com.masselis.tpmsadvanced.core.common.CoreCommonInitializer
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * Alerts whatever scans, the main screen or the monitor service, from the app's start. The ride
 * notification is removed by opening the app, the service running or not.
 */
public class FeatureBackgroundInitializer : AppGraphReadyInitializer<Unit> {

    // The alerts read the app's context right away, which CoreCommonInitializer sets
    override fun dependencies(): List<Class<out Initializer<*>>> =
        super.dependencies() + CoreCommonInitializer::class.java

    override fun create(context: Context) {
        Bindings.featureBackgroundInternal.alertNotifier()
        Bindings.featureBackgroundInternal.rideNotifier()
    }
}
