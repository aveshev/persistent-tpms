package com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.Readings
import com.masselis.tpmsadvanced.feature.unlocated.usecase.BindSensorToVehicleUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Assigns [location] of the vehicle [vehicleUuid] a sensor found by Bluetooth, see [AssignStep].
 * Sensors already bound to this vehicle are ignored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
internal class BluetoothAssignViewModel(
    private val scanner: BluetoothLeScanner,
    private val sensorDatabase: SensorDatabase,
    private val vehicleDatabase: VehicleDatabase,
    private val bindSensorToVehicleUseCase: BindSensorToVehicleUseCase,
    @Assisted private val vehicleUuid: UUID,
    @Assisted val location: Location,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(vehicleUuid: UUID, location: Location): BluetoothAssignViewModel
    }

    data class State(
        /** The vehicle assigned a sensor, null until read */
        val vehicle: Vehicle? = null,
        val step: AssignStep = AssignStep.Ask,
        val dialog: Dialog? = null,
        /** Nothing was found on a wheel for a while: maybe the tyre is nearly flat */
        val offersLowPressure: Boolean = false,
        /** The sensor found was assigned, the flow is done */
        val isAssigned: Boolean = false,
        /** The found sensor's, to show its id the way it's printed */
        val foundBrand: SensorBrand? = null,
    )

    /** Asked before going on: on the page itself, but [BoundElsewhere] in a dialog */
    sealed interface Dialog {
        /** A single sensor was found on a wheel: assign it, or make sure by taking it off and back */
        data object OneFound : Dialog

        /** Several were, taking it off and back tells which one */
        data class ManyFound(val count: Int) : Dialog

        /** The sensor found belongs to [vehicle], assigning it here takes it from there */
        data class BoundElsewhere(val vehicle: Vehicle) : Dialog
    }

    sealed interface Event {
        data object Leave : Event
    }

    private val mutableStateFlow = MutableStateFlow(State())
    val stateFlow: StateFlow<State> = mutableStateFlow.asStateFlow()

    private val channel = Channel<Event>(BUFFERED)
    val eventChannel: ReceiveChannel<Event> = channel

    /** The last reading of each sensor heard, to bind the one found with it */
    private val heard = mutableMapOf<Int, Tyre.SensorInput>()
    private var readings = Readings()

    /** The sensors already on this vehicle, left out */
    private val boundIds = sensorDatabase
        .selectListByVehicleId(vehicleUuid)
        .asFlow()
        .map { sensors -> sensors.map { it.id }.toSet() }
        .flowOn(IO)
        .stateIn(viewModelScope, Eagerly, emptySet())

    init {
        viewModelScope.launch {
            val vehicle = withContext(IO) { vehicleDatabase.selectByUuid(vehicleUuid).execute() }
            mutableStateFlow.update { it.copy(vehicle = vehicle) }
        }
        // Listens while a step waits for sensors
        mutableStateFlow
            .map { it.step.isListening() }
            .distinctUntilChanged()
            .flatMapLatest { isListening -> if (isListening) scanner.highDutyScan() else emptyFlow() }
            .filter { tyre -> tyre.sensorId !in boundIds.value }
            .onEach(::onReading)
            .launchIn(viewModelScope)
        // Nothing on a wheel 10 s after it should be: offers the sensors heard below it
        mutableStateFlow
            .map { it.step is AssignStep.PutOn }
            .distinctUntilChanged()
            .onEach { isPuttingOn ->
                mutableStateFlow.update { it.copy(offersLowPressure = false) }
                if (isPuttingOn) {
                    delay(LOW_PRESSURE_AFTER)
                    mutableStateFlow.update { it.copy(offersLowPressure = true) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun onReading(tyre: Tyre.SensorInput) {
        val kpa = tyre.pressure.kpa
        val step = mutableStateFlow.value.step.after(tyre.sensorId, kpa, readings)
        readings += tyre.sensorId to kpa
        heard[tyre.sensorId] = tyre
        mutableStateFlow.update { it.copy(step = step) }
        (step as? AssignStep.Found)?.also { found(it.sensorId) }
    }

    fun answer(isOnWheel: Boolean) = mutableStateFlow.update {
        it.copy(step = if (isOnWheel) AssignStep.TakeOff(null) else AssignStep.PutOn())
    }

    /** Done putting the sensor on the wheel */
    fun next() = mutableStateFlow.update { state ->
        when (val step = state.step) {
            is AssignStep.PutOn -> state.copy(
                dialog = if (step.found.size == 1) Dialog.OneFound else Dialog.ManyFound(step.found.size)
            )

            is AssignStep.TakeOff -> state.copy(step = step.next())
            else -> state
        }
    }

    fun lowPressure() = mutableStateFlow.update { state ->
        (state.step as? AssignStep.PutOn)?.let { state.copy(step = it.lowPressure()) } ?: state
    }

    /** The single sensor found on a wheel is assigned without taking it off and back */
    fun assignFound() {
        val step = mutableStateFlow.value.step as? AssignStep.PutOn ?: return
        mutableStateFlow.update { it.copy(dialog = null) }
        found(step.found.single())
    }

    /** Takes the sensors found on a wheel off it, to tell which one is the user's */
    fun checkAgain() = mutableStateFlow.update { state ->
        (state.step as? AssignStep.PutOn)?.let { state.copy(step = it.next(), dialog = null) } ?: state
    }

    /** Takes the sensor from the vehicle it was bound to */
    fun confirmBoundElsewhere() {
        val sensorId = (mutableStateFlow.value.step as? AssignStep.Found)?.sensorId ?: return
        viewModelScope.launch { bind(sensorId) }
    }

    fun dismissDialog() = mutableStateFlow.update { state ->
        when (state.dialog) {
            // Starts listening again rather than assigning the sensor of another vehicle
            is Dialog.BoundElsewhere -> state.copy(step = AssignStep.Ask, dialog = null)
            else -> state.copy(dialog = null)
        }
    }

    private fun found(sensorId: Int) = viewModelScope.launch {
        mutableStateFlow.update { it.copy(step = AssignStep.Found(sensorId), foundBrand = heard[sensorId]?.brand) }
        withContext(IO) { vehicleDatabase.selectBySensorId(sensorId).execute() }
            ?.takeIf { it.uuid != vehicleUuid }
            ?.also { vehicle -> mutableStateFlow.update { it.copy(dialog = Dialog.BoundElsewhere(vehicle)) } }
            ?: bind(sensorId)
    }

    private suspend fun bind(sensorId: Int) {
        val tyre = heard.getValue(sensorId)
        bindSensorToVehicleUseCase.bind(vehicleUuid, Sensor(sensorId, location, tyre.brand), tyre)
        mutableStateFlow.update { it.copy(dialog = null, isAssigned = true) }
    }

    fun done() = viewModelScope.launch { channel.send(Event.Leave) }

    private fun AssignStep.isListening() =
        this is AssignStep.PutOn || this is AssignStep.TakeOff || this is AssignStep.PutBack

    private companion object {
        val LOW_PRESSURE_AFTER = 10.seconds
    }
}
