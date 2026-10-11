package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
public class VehicleRangesUseCase internal constructor(
    vehicle: Vehicle,
    scope: CoroutineScope,
    database: VehicleDatabase,
) {

    public val lowPressure: MutableStateFlow<Pressure> =
        MutableStateFlow(database.selectLowPressure(vehicle.uuid))
    public val highPressure: MutableStateFlow<Pressure> =
        MutableStateFlow(database.selectHighPressure(vehicle.uuid))
    public val lowTemp: MutableStateFlow<Temperature> =
        MutableStateFlow(database.selectLowTemp(vehicle.uuid))
    public val normalTemp: MutableStateFlow<Temperature> =
        MutableStateFlow(database.selectNormalTemp(vehicle.uuid))
    public val highTemp: MutableStateFlow<Temperature> =
        MutableStateFlow(database.selectHighTemp(vehicle.uuid))
    /** Only used while [separateRearPressure] is on, but kept when it's turned off */
    public val rearLowPressure: MutableStateFlow<Pressure?> =
        MutableStateFlow(database.selectRearLowPressure(vehicle.uuid))
    public val rearHighPressure: MutableStateFlow<Pressure?> =
        MutableStateFlow(database.selectRearHighPressure(vehicle.uuid))
    public val separateRearPressure: MutableStateFlow<Boolean> =
        MutableStateFlow(database.selectSeparateRearPressure(vehicle.uuid))
    /** Only used while [separateSparePressure] is on, but kept when it's turned off */
    public val spareLowPressure: MutableStateFlow<Pressure?> =
        MutableStateFlow(database.selectSpareLowPressure(vehicle.uuid))
    public val spareHighPressure: MutableStateFlow<Pressure?> =
        MutableStateFlow(database.selectSpareHighPressure(vehicle.uuid))
    public val separateSparePressure: MutableStateFlow<Boolean> =
        MutableStateFlow(database.selectSeparateSparePressure(vehicle.uuid))
    /** A sensor's battery alarms at this voltage or below, and is shown as getting low 0.1 V above */
    public val lowBatteryVoltage: MutableStateFlow<Voltage> =
        MutableStateFlow(database.selectLowBatteryVoltage(vehicle.uuid))
    /** Same as [lowBatteryVoltage] for the Sysgration sensors, which report a percentage */
    public val lowBatteryPercent: MutableStateFlow<Int> =
        MutableStateFlow(database.selectLowBatteryPercent(vehicle.uuid))

    /**
     * Turning the override off keeps the rear range, so an accidental toggle loses nothing. The
     * front range is only copied the first time, when there is no rear range yet.
     */
    public fun setRearOverrideEnabled(enabled: Boolean) {
        if (enabled && (rearLowPressure.value == null || rearHighPressure.value == null)) {
            rearLowPressure.value = lowPressure.value
            rearHighPressure.value = highPressure.value
        }
        separateRearPressure.value = enabled
    }

    /** Like [setRearOverrideEnabled], the spare's range starting from the front's */
    public fun setSpareOverrideEnabled(enabled: Boolean) {
        if (enabled && (spareLowPressure.value == null || spareHighPressure.value == null)) {
            spareLowPressure.value = lowPressure.value
            spareHighPressure.value = highPressure.value
        }
        separateSparePressure.value = enabled
    }

    /** The spare's and the rear's own range while they have one, the front's otherwise */
    public fun resolvedLowPressure(location: Location): Flow<Pressure> = when {
        location == Location.Spare ->
            combine(separateSparePressure, spareLowPressure, lowPressure) { separate, spare, front ->
                spare?.takeIf { separate } ?: front
            }

        location.toAxleOrNull()?.axle == REAR ->
            combine(separateRearPressure, rearLowPressure, lowPressure) { separate, rear, front ->
                rear?.takeIf { separate } ?: front
            }

        else -> lowPressure
    }

    public fun resolvedHighPressure(location: Location): Flow<Pressure> = when {
        location == Location.Spare ->
            combine(separateSparePressure, spareHighPressure, highPressure) { separate, spare, front ->
                spare?.takeIf { separate } ?: front
            }

        location.toAxleOrNull()?.axle == REAR ->
            combine(separateRearPressure, rearHighPressure, highPressure) { separate, rear, front ->
                rear?.takeIf { separate } ?: front
            }

        else -> highPressure
    }

    public fun alertThresholds(location: Location): Flow<AlertThresholds> = combine(
        resolvedLowPressure(location),
        resolvedHighPressure(location),
        highTemp,
        lowBatteryVoltage,
        lowBatteryPercent,
    ) { lowPressure, highPressure, highTemp, lowBatteryVoltage, lowBatteryPercent ->
        AlertThresholds(
            lowPressure,
            highPressure,
            highTemp,
            lowBatteryVoltage,
            lowBatteryPercent,
            // Nothing critical about a spare that isn't driven on
            allowsCritical = location != Location.Spare,
        )
    }

    init {
        lowPressure
            .debounce(100.milliseconds)
            .onEach { database.updateLowPressure(it, vehicle.uuid) }
            .launchIn(scope)

        highPressure
            .debounce(100.milliseconds)
            .onEach { database.updateHighPressure(it, vehicle.uuid) }
            .launchIn(scope)

        lowTemp
            .debounce(100.milliseconds)
            .onEach { database.updateLowTemp(it, vehicle.uuid) }
            .launchIn(scope)

        normalTemp
            .debounce(100.milliseconds)
            .onEach { database.updateNormalTemp(it, vehicle.uuid) }
            .launchIn(scope)

        highTemp
            .debounce(100.milliseconds)
            .onEach { database.updateHighTemp(it, vehicle.uuid) }
            .launchIn(scope)

        rearLowPressure
            .debounce(100.milliseconds)
            .onEach { database.updateRearLowPressure(it, vehicle.uuid) }
            .launchIn(scope)

        rearHighPressure
            .debounce(100.milliseconds)
            .onEach { database.updateRearHighPressure(it, vehicle.uuid) }
            .launchIn(scope)

        separateRearPressure
            .debounce(100.milliseconds)
            .onEach { database.updateSeparateRearPressure(it, vehicle.uuid) }
            .launchIn(scope)

        spareLowPressure
            .debounce(100.milliseconds)
            .onEach { database.updateSpareLowPressure(it, vehicle.uuid) }
            .launchIn(scope)

        spareHighPressure
            .debounce(100.milliseconds)
            .onEach { database.updateSpareHighPressure(it, vehicle.uuid) }
            .launchIn(scope)

        separateSparePressure
            .debounce(100.milliseconds)
            .onEach { database.updateSeparateSparePressure(it, vehicle.uuid) }
            .launchIn(scope)

        lowBatteryVoltage
            .debounce(100.milliseconds)
            .onEach { database.updateLowBatteryVoltage(it, vehicle.uuid) }
            .launchIn(scope)

        lowBatteryPercent
            .debounce(100.milliseconds)
            .onEach { database.updateLowBatteryPercent(it, vehicle.uuid) }
            .launchIn(scope)
    }
}
