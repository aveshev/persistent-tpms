package com.masselis.tpmsadvanced.ioc

import android.content.Context
import com.masselis.tpmsadvanced.core.common.NoDependencyInitializer
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.app.interfaces.AppSnapshot
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
internal interface AppGraph {

    @DependencyGraph.Factory
    interface Factory {
        fun build(@Provides context: Context) : AppGraph
    }

    class Initializer : NoDependencyInitializer<Any> {
        override fun create(context: Context): Any = context
            // Data imported from the other version of the app (Lite or Pro) replaces this one's
            // before anything opens it. A failed import isn't tried again on the next start.
            .also { context ->
                runCatching { AppSnapshot.applyStaged(context, DatabaseExport.file(context)) }
                    .onFailure { AppSnapshot.discardStaged(context) }
            }
            .let { createGraphFactory<Factory>().build(it) }
            .also { appGraph = it }
    }
}
