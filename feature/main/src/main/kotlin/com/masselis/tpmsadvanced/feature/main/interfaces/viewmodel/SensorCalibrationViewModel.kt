package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits the calibration of [sensorId] as a draft, until the user applies it to this sensor or to
 * every sensor of the vehicle. Leaving without applying discards it, see [Dialog.Discard].
 */
internal class SensorCalibrationViewModel(
    private val calibrationUseCase: VehicleCalibrationUseCase,
    private val sensorId: Int,
) : ViewModel() {

    /**
     * [saved] is the sensor's calibration, [draft] the page's edits of it. [latestRead]: the
     * pressure the sensor sent last, before its calibration.
     */
    data class State(
        val saved: SensorCalibration,
        val draft: SensorCalibration,
        val others: List<SensorCalibration>,
        val location: Location? = null,
        val latestRead: Pressure? = null,
        val dialog: Dialog? = null,
    ) {
        /**
         * What applying writes. A calibration off keeps the saved values: the ones edited while off
         * apply to nothing, they're dropped. On but adjusting nothing, it's the same as off.
         */
        val toApply: SensorCalibration
            get() = draft.takeIf { it.isEnabled && it.calibration.adjusts } ?: saved.copy(isEnabled = false)

        /**
         * Applying to this sensor would write something: an edit undone, or a calibration turned on
         * without adjusting anything, isn't one
         */
        val isChanged: Boolean
            get() = toApply != saved

        /**
         * Applying to all changes something: this sensor's edits, or another sensor reading
         * differently, even without any edit
         */
        val canApplyToAll: Boolean
            get() = others.isNotEmpty() && (isChanged || others.any { it.applied != toApply.applied })

        /** Applying to all would lose another sensor's calibration, the user is asked first */
        val applyingErases: Boolean
            get() = others.any { it.adjusts && it.applied != toApply.applied }
    }

    sealed interface Dialog {
        /** Applying to all overwrites another sensor's calibration */
        data object EraseOthers : Dialog

        /** Leaving loses the edits */
        data object Discard : Dialog
    }

    sealed interface Event {
        data object Leave : Event
    }

    private val draft = MutableStateFlow<SensorCalibration?>(null)
    private val dialog = MutableStateFlow<Dialog?>(null)

    val stateFlow: StateFlow<State?> = combine(
        calibrationUseCase.of(sensorId),
        draft.filterNotNull(),
        calibrationUseCase.othersThan(sensorId),
        combine(calibrationUseCase.locationOf(sensorId), calibrationUseCase.latestRead(sensorId), ::Pair),
        dialog,
    ) { saved, draft, others, (location, latestRead), dialog ->
        State(saved, draft, others, location, latestRead, dialog)
    }.stateIn(viewModelScope, WhileSubscribed(), null)

    private val channel = Channel<Event>(BUFFERED)
    val eventChannel: ReceiveChannel<Event> = channel

    init {
        viewModelScope.launch { draft.value = calibrationUseCase.of(sensorId).first() }
    }

    fun setEnabled(enabled: Boolean) = draft.update { it?.copy(isEnabled = enabled) }

    fun setOffset(offset: Pressure) = draft.update { it?.copy(calibration = it.calibration.copy(offset = offset)) }

    fun setMultiplier(multiplier: Float) =
        draft.update { it?.copy(calibration = it.calibration.copy(multiplier = multiplier)) }

    fun applyToSensor() {
        stateFlow.value?.also { state ->
            viewModelScope.launch {
                calibrationUseCase.set(sensorId, state.toApply)
                channel.send(Event.Leave)
            }
        }
    }

    /** Asks first when it erases another sensor's calibration, see [Dialog.EraseOthers] */
    fun applyToAll() {
        stateFlow.value?.also { state ->
            if (state.applyingErases) dialog.value = Dialog.EraseOthers
            else eraseOthers()
        }
    }

    /** [Dialog.EraseOthers]'s yes */
    fun eraseOthers() {
        dialog.value = null
        stateFlow.value?.also { state ->
            viewModelScope.launch {
                calibrationUseCase.applyToAll(state.toApply)
                channel.send(Event.Leave)
            }
        }
    }

    /** Back: leaves right away when nothing was edited, asks first otherwise */
    fun back() {
        if (stateFlow.value?.isChanged == true) dialog.value = Dialog.Discard
        else discard()
    }

    /** [Dialog.Discard]'s yes */
    fun discard() {
        dialog.value = null
        viewModelScope.launch { channel.send(Event.Leave) }
    }

    fun dismissDialog() {
        dialog.value = null
    }
}
