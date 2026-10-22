package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class DebugSettingsViewModel(
    appPreferences: AppPreferences,
    private val databaseExport: DatabaseExport,
) : ViewModel() {
    val debugOptions = appPreferences.debugOptions
    val showSensorId = appPreferences.showSensorId
    val showSensorFlags = appPreferences.showSensorFlags
    val balancedAfterFirstReading = appPreferences.balancedAfterFirstReading

    // Owned by the background feature, which also puts their items on the debug page
    // Never on in Lite, which has no item for it: it can't detect activities
    val showDetectedActivities = appPreferences
        .showDetectedActivities
        .takeIf { AppTier.current != LITE }
        ?: MutableStateFlow(false)
    val announceScanStatus = appPreferences.announceScanStatus

    /** A copy of the database in the cache folder the FileProvider shares, named after now */
    suspend fun exportDatabase(): File = LocalDateTime
        .now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        .let { File(appContext.cacheDir, "database_export/tpms-advanced-$it.db") }
        .also { databaseExport.exportTo(it) }

    fun disableIfNoneSelected() {
        listOf(showSensorId, showSensorFlags, showDetectedActivities, announceScanStatus, balancedAfterFirstReading)
            .none { it.value }
            .also { if (it) debugOptions.value = false }
    }
}
