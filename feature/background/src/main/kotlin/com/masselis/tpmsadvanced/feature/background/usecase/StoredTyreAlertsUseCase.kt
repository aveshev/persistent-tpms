package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.time.Duration.Companion.seconds

/**
 * The alerts of every tyre of every vehicle, from the readings stored by whatever scans, the main
 * screen or the monitor service: this never scans by itself. Only the readings stored after it
 * started listening are emitted, the ones stored before set the tyres' history up (see
 * docs/alerts.md, "Only new readings alert"). A change of a tyre's thresholds re-evaluates its
 * latest reading, emitted as a [Update.isReevaluation]. A tyre whose sensor changes (moved, bound
 * or unbound) starts its history over: the readings moved in with a sensor were stored before, even
 * if they're newer than the tyre's.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class StoredTyreAlertsUseCase(
    vehicleListUseCase: VehicleListUseCase,
    private val readingDatabase: ReadingDatabase,
    private val sensorDatabase: SensorDatabase,
) {

    /**
     * A tyre's alerts once [TyreAlerts.latest], a new reading, went through, or went through again
     * under other thresholds if [isReevaluation]
     */
    data class Update(
        val vehicle: Vehicle,
        val location: Location,
        val alerts: TyreAlerts,
        val thresholds: AlertThresholds,
        val loss: PressureLoss?,
        val isReevaluation: Boolean = false,
    )

    val updates: Flow<Update> = vehicleListUseCase
        .vehicleListFlow
        // Editing a vehicle (ranges, name...) re-emits the list, which would start the tyres' history
        // over: only a change in the set of vehicles rebuilds them
        .distinctUntilChanged { old, new -> old.map(Vehicle::uuid) == new.map(Vehicle::uuid) }
        .flatMapLatest { vehicles ->
            vehicles
                .flatMap { vehicle ->
                    vehicle.kind.locations.map { location ->
                        sensorDatabase
                            .selectByVehicleAndLocation(vehicle.uuid, location)
                            .asFlow()
                            .map { it?.id }
                            .distinctUntilChanged()
                            .flatMapLatest { sensorId -> updates(vehicle, location, sensorId) }
                    }
                }
                .merge()
        }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun updates(vehicle: Vehicle, location: Location, sensorId: Int?): Flow<Update> = flow {
        val component = VehicleComponent(vehicle)
        // The leak is followed with the calibration and the rule it started with, both changing
        // starts it over from the stored readings, as TyrePressureLossStateFlow does
        var calibration = component.vehicleCalibrationUseCase.calibrations.first()
        var rule = component.vehiclePressureLossUseCase.rule.first()
        val stored = readingDatabase.allByLocation(location, vehicle.uuid).execute()
        // The thresholds the latest reading went through with
        var thresholds = component.vehicleRangesUseCase.alertThresholds(location).first()
        var (tracker, alerts) = stored.fold(PressureLoss.Tracker() to TyreAlerts()) { (tracker, alerts), record ->
            record
                .toAtmosphere(calibration)
                .let { reading ->
                    (rule?.let { tracker.next(reading, it) } ?: tracker)
                        .let { it to alerts.next(reading, thresholds, it.loss) }
                }
        }
        var since = stored.lastOrNull()?.timestamp ?: Double.NEGATIVE_INFINITY
        merge(
            // Emits on every change of the readings, of any tyre, as null
            readingDatabase
                .latestByLocation(location, vehicle.uuid)
                .asFlow()
                .map { null },
            // A slider being dragged settles first
            component
                .vehicleRangesUseCase
                .alertThresholds(location)
                .debounce(THRESHOLDS_SETTLING),
        ).collect { newThresholds ->
            if (newThresholds != null) {
                // The latest reading again: the notifier only lowers or clears what it alerted for,
                // see docs/alerts.md
                if (newThresholds != thresholds) alerts.latest?.also { latest ->
                    thresholds = newThresholds
                    alerts = alerts.next(latest, thresholds, tracker.loss)
                    emit(Update(vehicle, location, alerts, thresholds, tracker.loss, isReevaluation = true))
                }
            } else readingDatabase
                .afterByLocation(location, vehicle.uuid, since)
                .execute()
                // Moved in with another sensor, in the same transaction: the history starting over
                // with this sensor holds them, see updates
                .takeIf { sensorDatabase.selectByVehicleAndLocation(vehicle.uuid, location).execute()?.id == sensorId }
                .orEmpty()
                .forEach { record ->
                    val newCalibration = component.vehicleCalibrationUseCase.calibrations.first()
                    val newRule = component.vehiclePressureLossUseCase.rule.first()
                    if (newCalibration != calibration || newRule != rule) {
                        calibration = newCalibration
                        rule = newRule
                        tracker = rule
                            ?.let { lossRule ->
                                readingDatabase
                                    .allByLocation(location, vehicle.uuid)
                                    .execute()
                                    .filter { it.timestamp < record.timestamp }
                                    .fold(PressureLoss.Tracker()) { tracker, stored ->
                                        tracker.next(stored.toAtmosphere(calibration), lossRule)
                                    }
                            }
                            ?: PressureLoss.Tracker()
                    }
                    val reading = record.toAtmosphere(calibration)
                    tracker = rule?.let { tracker.next(reading, it) } ?: tracker
                    thresholds = component.vehicleRangesUseCase.alertThresholds(location).first()
                    alerts = alerts.next(reading, thresholds, tracker.loss)
                    since = record.timestamp
                    emit(Update(vehicle, location, alerts, thresholds, tracker.loss))
                }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val THRESHOLDS_SETTLING = 1.seconds
    }
}
