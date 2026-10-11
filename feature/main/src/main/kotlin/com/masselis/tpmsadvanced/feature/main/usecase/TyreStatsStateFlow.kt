package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.parcelize.Parcelize

@Suppress("OPT_IN_TO_INHERITANCE", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
public class TyreStatsStateFlow internal constructor(
    alertsUseCase: TyreAlertsUseCase,
    calibrationUseCase: VehicleCalibrationUseCase,
    unitPreferences: UnitPreferences,
    sensorBindingUseCase: SensorBindingUseCase,
    scope: CoroutineScope,
    // Starts over with the sensor bound, moved or swapped here: its own latest reading, if any
    stateFlow: StateFlow<State> = sensorBindingUseCase.boundSensor().boundIds().flatMapLatest {
        combine(
            alertsUseCase.listen(),
            unitPreferences.pressure,
            unitPreferences.temperature,
            calibrationUseCase.calibrations,
        ) { alerts, pressureUnit, temperatureUnit, calibrations ->
        requireNotNull(alerts.latest).let<TyreAtmosphere, State> { atmosphere ->
            State.Detected(
                atmosphere.timestamp,
                atmosphere.sensorId,
                atmosphere.pressure,
                pressureUnit,
                atmosphere.temperature,
                temperatureUnit,
                calibrations.of(atmosphere.sensorId) != null,
                atmosphere.batteryVoltage,
                atmosphere.flags,
                alerts.levels,
                atmosphere.batteryPercent,
            )
        }
        }.onStart { emit(State.NotDetected) }
    }
        .catch { emit(State.NotDetected) }
        .stateIn(scope, WhileSubscribed(), State.NotDetected),
) : StateFlow<State> by stateFlow {

    public sealed class State : Parcelable {
        // Shows "-:-" texts
        @Parcelize
        public data object NotDetected : State()

        /** Shows the read values from the tyre, each in the colour of its alert level */
        @Parcelize
        public data class Detected(
            public val timestamp: Double,
            public val sensorId: Int,
            public val pressure: Pressure,
            public val pressureUnit: PressureUnit,
            public val temperature: Temperature,
            public val temperatureUnit: TemperatureUnit,
            // The pressure was corrected by the vehicle's calibration, marked by an asterisk
            public val isPressureCalibrated: Boolean = false,
            /** Null when the sensor doesn't report a voltage */
            public val batteryVoltage: Voltage? = null,
            // The status bytes of the last packet, see Tyre.flags
            public val flags: List<UByte>? = null,
            /** The level of each class the tyre alerts for, see AlertThresholds.levels */
            public val levels: Map<AlertClass, AlertLevel> = emptyMap(),
            /** Sysgration's battery, which isn't a voltage */
            public val batteryPercent: Int? = null,
        ) : State()
    }
}
