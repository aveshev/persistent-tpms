package com.masselis.tpmsadvanced.feature.shortcut.interfaces

import android.content.Context
import androidx.startup.Initializer
import com.masselis.tpmsadvanced.core.common.AppGraphReadyInitializer
import com.masselis.tpmsadvanced.core.common.CoreCommonInitializer
import com.masselis.tpmsadvanced.feature.shortcut.ioc.Bindings.Companion.ShortcutUseCase

/** Publishes a launcher shortcut per vehicle, from the app's start */
public class FeatureShortcutInitializer : AppGraphReadyInitializer<Unit> {

    // Building the use case opens the database, which reads the app's context that
    // CoreCommonInitializer sets. androidx.startup runs the initializers in no particular order.
    override fun dependencies(): List<Class<out Initializer<*>>> =
        super.dependencies() + CoreCommonInitializer::class.java

    override fun create(context: Context) {
        ShortcutUseCase()
    }
}
