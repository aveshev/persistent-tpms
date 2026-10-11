package com.masselis.tpmsadvanced.feature.main.usecase

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * The tyre's leak warning, null while it isn't losing pressure or the [VehiclePressureLossUseCase]
 * is off. The [PressureLoss.Tracker] starts from the stored
 * readings, so a restarted app or service doesn't forget a loss in progress, then follows the live
 * ones. Changing the calibration or the warning's settings starts it over from the stored readings,
 * which stay as read: changing the calibration can't look like a loss.
 */
@Suppress("OPT_IN_TO_INHERITANCE", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
public class TyrePressureLossStateFlow internal constructor(
    vehicle: Vehicle,
    location: Location,
    readingDatabase: ReadingDatabase,
    listenTyreUseCase: ListenTyreUseCase,
    calibrationUseCase: VehicleCalibrationUseCase,
    pressureLossUseCase: VehiclePressureLossUseCase,
    scope: CoroutineScope,
    stateFlow: StateFlow<PressureLoss?> = combine(
        calibrationUseCase.calibrations,
        pressureLossUseCase.rule,
    ) { calibrations, rule -> calibrations to rule }
        .flatMapLatest { (calibrations, rule) ->
            rule
                ?.let {
                    flow { emit(readingDatabase.allByLocation(location, vehicle.uuid).execute()) }
                        .flowOn(Dispatchers.IO)
                        .map { stored ->
                            stored.fold(PressureLoss.Tracker()) { tracker, record ->
                                tracker.next(record.toAtmosphere(calibrations), rule)
                            }
                        }
                        .flatMapLatest { tracker ->
                            listenTyreUseCase
                                .listen()
                                .runningFold(tracker) { tracker, record ->
                                    tracker.next(record.toAtmosphere(calibrations), rule)
                                }
                        }
                        .map { it.loss }
                }
                ?: flowOf(null)
        }
        .flowOn(Dispatchers.Default)
        .catch {
            Logger.withTag("TyrePressureLossStateFlow").e("Failed to check the pressure loss", it)
            emit(null)
        }
        .stateIn(scope, WhileSubscribed(), null),
) : StateFlow<PressureLoss?> by stateFlow
