package com.masselis.tpmsadvanced.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Failed
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Idle
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Importing
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.NothingToImport
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Offer
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Unreachable
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.UpdateNeeded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * Imports the data of the other version of the app (Lite or Pro). Imported data replaces this app's,
 * it's never merged. Offered once, when the app opens, and from the settings any time after:
 * - this app has no data: offered whenever the other app has some;
 * - this app has data, restored by Auto Backup for instance: only offered when the other app's
 *   latest reading is newer.
 */
@Suppress("LongParameterList")
internal class ImportViewModel(
    private val otherApp: OtherApp,
    private val databaseExport: DatabaseExport,
    /** Exists once the import was offered when the app opened, kept out of the backups */
    private val offeredMarker: File,
    /** Stages the snapshot, @return the version of its database */
    private val stage: suspend (InputStream) -> Long,
    /** Stops monitoring, has the staged data replace this app's, and restarts the app */
    private val commitAndRestart: suspend () -> Unit,
    private val discardStaged: () -> Unit,
) : ViewModel() {

    private val logger = Logger.withTag("ImportViewModel")

    private val mutableState = MutableStateFlow<State>(Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Null while the other app isn't installed */
    fun otherAppLabel(): String? = otherApp.label()

    /** When the app opens: offers the import if the rules allow, once */
    fun offerOnce() {
        if (offeredMarker.exists()) return
        val label = otherApp.label() ?: return
        viewModelScope.launch {
            // Asked again on the next opening when the other app can't tell, an older build maybe
            val status = otherApp.status() ?: return@launch
            offeredMarker.parentFile?.mkdirs()
            offeredMarker.createNewFile()
            val ours = databaseExport.latestReading()
            val oursHasData = databaseExport.hasData()
            mutableState.value = when {
                status.hasData.not() -> Idle
                oursHasData && status.latestReading.isNewerThan(ours).not() -> Idle
                status.databaseVersion > DatabaseExport.version -> UpdateNeeded(label)
                else -> Offer(label, status.latestReading, ours, oursHasData, fromSettings = false)
            }
        }
    }

    /** From the settings: always offered, if there is anything to import */
    fun requestImport() {
        val label = otherApp.label() ?: return
        viewModelScope.launch {
            val status = otherApp.status()
            val ours = databaseExport.latestReading()
            mutableState.value = when {
                status == null -> Unreachable(label)
                status.hasData.not() -> NothingToImport(label)
                status.databaseVersion > DatabaseExport.version -> UpdateNeeded(label)
                else -> Offer(label, status.latestReading, ours, databaseExport.hasData(), fromSettings = true)
            }
        }
    }

    fun confirm() {
        val offer = mutableState.value as? Offer ?: return
        mutableState.value = Importing(offer.otherApp)
        viewModelScope.launch {
            runCatching { otherApp.openSnapshot()?.use { stage(it) } }
                .onFailure { logger.e(it) { "Cannot stage the snapshot of ${otherApp.packageName}" } }
                .getOrNull()
                .let { version ->
                    when {
                        version == null -> mutableState.value = Failed(offer.otherApp).also { discardStaged() }
                        // The other app was updated since its status was read
                        version > DatabaseExport.version ->
                            mutableState.value = UpdateNeeded(offer.otherApp).also { discardStaged() }

                        else -> commitAndRestart()
                    }
                }
        }
    }

    fun dismiss() {
        mutableState.value = Idle
    }

    private fun Double?.isNewerThan(other: Double?) = this != null && (other == null || this > other)

    sealed interface State {
        data object Idle : State

        data class Offer(
            val otherApp: String,
            /** In seconds since the epoch, null without any reading */
            val theirLatestReading: Double?,
            val ourLatestReading: Double?,
            /** This app has data, which the import replaces */
            val replacing: Boolean,
            val fromSettings: Boolean,
        ) : State

        data class Importing(val otherApp: String) : State

        /** The other app's database is of a newer version than this build's */
        data class UpdateNeeded(val otherApp: String) : State

        data class Unreachable(val otherApp: String) : State

        data class NothingToImport(val otherApp: String) : State

        data class Failed(val otherApp: String) : State
    }
}
