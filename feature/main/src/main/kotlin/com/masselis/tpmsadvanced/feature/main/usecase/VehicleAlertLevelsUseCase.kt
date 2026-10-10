package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.Companion.obsoleteTimeout
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

/**
 * The worst alert level of each vehicle's tyres, as their readings show it on the vehicle's own
 * screen (see [TyreStatsStateFlow]), for the vehicles alerting. A reading counts until the tyre
 * stops showing it, see [TyreIconStateFlow]: an alert read days ago says nothing of the vehicle
 * now. Listening to a vehicle's tyres is what scans them, as its screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class VehicleAlertLevelsUseCase internal constructor(
    vehicleListUseCase: VehicleListUseCase,
    tyreStates: (Vehicle) -> List<Flow<State>> = { vehicle ->
        VehicleComponent(vehicle).let { component ->
            vehicle.kind.locations.map { component.TyreComponent(it).tyreStatsStateFlow }
        }
    },
) {
    /** Starts with no vehicle alerting, the tyres' readings follow */
    public val levels: Flow<Map<UUID, AlertLevel>> = vehicleListUseCase
        .vehicleListFlow
        // Editing a vehicle (ranges, name...) re-emits the list; only a change in the set of
        // vehicles resubscribes, otherwise the tyres' scans restart
        .distinctUntilChanged { old, new -> old.map(Vehicle::uuid) == new.map(Vehicle::uuid) }
        .flatMapLatest { vehicles ->
            vehicles
                .map { vehicle ->
                    tyreStates(vehicle)
                        .map { tyre ->
                            tyre.transformLatest { state ->
                                (state as? State.Detected)
                                    ?.let { (it.timestamp + obsoleteTimeout.toDouble(SECONDS) - now()).seconds to it }
                                    ?.takeIf { (left) -> left.isPositive() }
                                    ?.also { (left, detected) ->
                                        emit(detected.levels.values.maxOrNull())
                                        delay(left)
                                    }
                                emit(null)
                            }
                        }
                        .let { tyres -> combine(tyres) { levels -> levels.filterNotNull().maxOrNull() } }
                        .map { vehicle.uuid to it }
                }
                .let { perVehicle ->
                    combine(perVehicle) { levels ->
                        levels
                            .mapNotNull { (uuid, level) -> level?.let { uuid to it } }
                            .toMap()
                    }
                }
        }
        .onStart { emit(emptyMap()) }
        .distinctUntilChanged()
}
