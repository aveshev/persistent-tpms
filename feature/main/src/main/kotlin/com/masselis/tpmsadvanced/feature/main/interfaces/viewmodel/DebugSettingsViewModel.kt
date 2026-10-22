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
    val developer = appPreferences.developer
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

    // Taps on the version since the settings were opened, or since developer last changed
    private var versionTaps = 0

    /**
     * Tapping the version [TAPS_TO_TOGGLE] times in a row toggles [developer], counting down for
     * the last [TAPS_COUNTED_DOWN]. Returns the message to show for this tap, if any.
     */
    @Suppress("NestedBlockDepth")
    fun onVersionTap(): String? = (TAPS_TO_TOGGLE - ++versionTaps).let { left ->
        when {
            left <= 0 -> developer.value
                .not()
                .also { developer.value = it }
                .also { versionTaps = 0 }
                .let { if (it) "You are now a developer" else "You are no longer a developer" }

            left <= TAPS_COUNTED_DOWN -> "Tap $left more ${if (left == 1) "time" else "times"} to " +
                    if (developer.value) "stop being a developer" else "become a developer"

            else -> null
        }
    }

    fun disableIfNoneSelected() {
        listOf(showSensorId, showSensorFlags, showDetectedActivities, announceScanStatus, balancedAfterFirstReading)
            .none { it.value }
            .also { if (it) debugOptions.value = false }
    }

    internal companion object {
        const val TAPS_TO_TOGGLE = 10
        const val TAPS_COUNTED_DOWN = 3
    }
}
