package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

private const val MILLIS_PER_MINUTE = 60_000.0

@Suppress("OPT_IN_USAGE")
@AssistedInject
internal class TabScreenViewModel(
    private val currentVehicleUseCase: CurrentVehicleUseCase,
    vehicleListUseCase: VehicleListUseCase,
    private val silenceAlertsUseCase: SilenceAlertsUseCase,
    @Assisted lifecycleOwner: LifecycleOwner,
) : LifecycleOwner by lifecycleOwner {

    @AssistedFactory
    interface Factory {
        operator fun invoke(lifecycleOwner: LifecycleOwner): TabScreenViewModel
    }

    sealed interface State {
        data object Loading : State
        data class Tabs(
            val list: List<Tab.Available>,
            val displayed: Tab.Displayed,
            /** Null while the alerts aren't spoken */
            val speech: Speech? = null,
        ) : State {
            sealed interface Tab {
                val vehicle: Vehicle

                @JvmInline
                value class Available(override val vehicle: Vehicle) : Tab

                data class Displayed(
                    override val vehicle: Vehicle,
                    val tyres: Map<Vehicle.Kind.Location, Pair<TyreIconStateFlow.State, TyreStatsStateFlow.State>>
                ) : Tab
            }
        }
    }

    /** The alerts' speech, which a tap silences for a while, see docs/alerts.md */
    sealed interface Speech {
        data object On : Speech
        data class Offer(val isCritical: Boolean) : Speech
        data class Silenced(val isCritical: Boolean, val minutesLeft: Int) : Speech
    }

    private val mutableStateFlow = MutableStateFlow<State>(State.Loading)
    val stateFlow = mutableStateFlow.asStateFlow()

    init {
        @Suppress("MaxLineLength")
        combine(
            vehicleListUseCase.vehicleListFlow,
            currentVehicleUseCase
        ) { list, current -> list to current }
            .flatMapLatest { (list, current) ->
                current
                    .vehicle.kind.locations.map { current.TyreComponent(it) }
                    .map { tyreComponent ->
                        combine(
                            tyreComponent.tyreIconStateFlow,
                            tyreComponent.tyreStatsStateFlow,
                        ) { iconState, statsState ->
                            tyreComponent.location to (iconState to statsState)
                        }
                    }
                    // A new map for each reading: the state flow drops a state equal to the
                    // previous one, which a single mutated map always was, so readings didn't show
                    .let { tyres -> combine(tyres) { it.toMap() } }
                    .map {
                        State.Tabs(
                            list.map { vehicle -> State.Tabs.Tab.Available(vehicle) },
                            State.Tabs.Tab.Displayed(current.vehicle, it)
                        )
                    }
            }
            .combine(speech()) { tabs, speech -> tabs.copy(speech = speech) }
            .onEach { mutableStateFlow.value = it }
            .launchIn(lifecycleScope)
    }

    @Suppress("MaxLineLength")
    private fun speech(): Flow<Speech?> = silenceAlertsUseCase
        .state
        .flatMapLatest { state ->
            when (state) {
                SilenceAlertsUseCase.State.Disabled -> flowOf(null)
                SilenceAlertsUseCase.State.Idle -> flowOf(Speech.On)
                is SilenceAlertsUseCase.State.Offer -> flowOf(Speech.Offer(state.isCritical))
                // Counting down
                is SilenceAlertsUseCase.State.Silenced -> flow {
                    while (true) {
                        emit(
                            Speech.Silenced(
                                state.isCritical,
                                ceil((state.until - System.currentTimeMillis()).coerceAtLeast(0) / MILLIS_PER_MINUTE).toInt(),
                            )
                        )
                        delay(1.seconds)
                    }
                }.distinctUntilChanged()
            }
        }

    fun silence(isCritical: Boolean) = silenceAlertsUseCase.silence(isCritical)

    fun unmute() = silenceAlertsUseCase.unmute()

    fun currentVehicle(uuid: UUID) = lifecycleScope.launch {
        currentVehicleUseCase.setAsCurrent(uuid)
    }
}
