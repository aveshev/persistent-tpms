package com.masselis.tpmsadvanced.ioc

import android.content.Context
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.ui.restartApp
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppSnapshot
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.feature.background.interfaces.stopMonitoring
import com.masselis.tpmsadvanced.feature.main.usecase.NoveltyUseCase
import com.masselis.tpmsadvanced.interfaces.viewmodel.HomeViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.VehicleHomeViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("VariableNaming", "FunctionNaming", "unused")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    private fun vehicleHomeViewModel(noveltyUseCase: NoveltyUseCase): VehicleHomeViewModel =
        VehicleHomeViewModel(noveltyUseCase)

    @Provides
    private fun importViewModel(
        context: Context,
        otherApp: OtherApp,
        databaseExport: DatabaseExport,
    ): ImportViewModel = ImportViewModel(
        otherApp = otherApp,
        databaseExport = databaseExport,
        // Not backed up: an app restored by Auto Backup offers the import again, see ImportViewModel
        offeredMarker = File(context.noBackupFilesDir, "import-offered"),
        stage = { withContext(IO) { AppSnapshot.stage(context, it).let(DatabaseExport::versionOf) } },
        commitAndRestart = { from ->
            stopMonitoring()
            AppSnapshot.commitStaged(context, from)
            // AppGraph swaps the staged data in when the app starts again
            context.restartApp()
        },
        discardStaged = { AppSnapshot.discardStaged(context) },
        consumeImported = { AppSnapshot.consumeImported(context) },
    )

    public val appPhoneInternal: Internal

    @Inject
    public class Internal internal constructor(
        internal val homeViewModel: HomeViewModel.Factory,
        internal val vehicleHomeViewModel: () -> VehicleHomeViewModel,
        internal val importViewModel: () -> ImportViewModel,
        internal val appPreferences: AppPreferences,
    )

    public companion object : Bindings by appGraph as Bindings {
        internal val HomeViewModel = appPhoneInternal.homeViewModel
        internal fun VehicleHomeViewModel() = appPhoneInternal.vehicleHomeViewModel()
        internal fun ImportViewModel() = appPhoneInternal.importViewModel()
        internal val appPreferences = appPhoneInternal.appPreferences
    }
}
