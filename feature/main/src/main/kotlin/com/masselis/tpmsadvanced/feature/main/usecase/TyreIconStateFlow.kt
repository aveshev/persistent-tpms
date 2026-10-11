package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.Parcelable
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("OPT_IN_TO_INHERITANCE")
@OptIn(ExperimentalCoroutinesApi::class)
public class TyreIconStateFlow internal constructor(
    alertsUseCase: TyreAlertsUseCase,
    rangeUseCase: VehicleRangesUseCase,
    location: Location,
    sensorBindingUseCase: SensorBindingUseCase,
    scope: CoroutineScope,
    // Starts over with the sensor bound, moved or swapped here, see TyreStatsStateFlow
    stateFlow: StateFlow<State> = sensorBindingUseCase.boundSensor().boundIds().flatMapLatest {
        combine(
        alertsUseCase.listen(),
        rangeUseCase.highTemp,
        rangeUseCase.normalTemp,
        rangeUseCase.lowTemp,
    ) { alerts, highTemp, normalTemp, lowTemp ->
        requireNotNull(alerts.latest).let { latest ->
            Data(
                latest.timestamp,
                // A battery says nothing about the tyre itself, its reading blinks instead
                alerts.levels.minus(BATTERY).values.maxOrNull(),
                latest.temperature,
                highTemp,
                normalTemp,
                lowTemp,
            )
        }
    }
        .transformLatest { (timestamp, level, temperature, highTemp, normalTemp, lowTemp) ->
            emit(
                // Amber doesn't change the tyre, its readings tell it
                if (level != null && level >= RED)
                    State.Alerting(isCritical = level == CRIMSON)
                else
                    when (temperature) {
                        in Temperature(Float.NEGATIVE_INFINITY)..lowTemp ->
                            State.Normal.BlueToGreen(Fraction(0f))

                        in lowTemp..normalTemp ->
                            State.Normal.BlueToGreen(
                                Fraction(
                                    temperature.celsius
                                        .minus(lowTemp.celsius)
                                        .div(normalTemp.celsius - lowTemp.celsius)
                                )
                            )

                        // Up to red, from the hot temperature on the tyre alerts
                        else ->
                            State.Normal.GreenToRed(
                                Fraction(
                                    temperature.celsius
                                        .minus(normalTemp.celsius)
                                        .div(highTemp.celsius - normalTemp.celsius)
                                        .coerceIn(0f, 1f)
                                )
                            )
                    }
            )
            timestamp
                .plus(obsoleteTimeout.toDouble(DurationUnit.SECONDS))
                .let { it - now() }
                .seconds
                .also { delay(it) }
            emit(State.NotDetected)
        }
            .onStart { emit(State.NotDetected) }
    }
        .catch {
            Logger.withTag("TyreIconStateFlow").e("Failed to listen for atmosphere", it)
            emit(State.DetectionIssue)
        }
        // Traces #35, a tyre not drawn at all: what the icon was told to show, compared to what
        // Tyre logs it drew
        .onEach { Logger.withTag("TyreIconStateFlow").d { "$location: $it" } }
        .stateIn(scope, WhileSubscribed(), State.NotDetected),
) : StateFlow<State> by stateFlow {

    private data class Data(
        val timestamp: Double,
        /** The worst of the tyre's alert levels but the battery's, null while it doesn't alert */
        val level: AlertLevel?,
        val temperature: Temperature,
        val highTemp: Temperature,
        val normalTemp: Temperature,
        val lowTemp: Temperature,
    )

    public sealed interface State : Parcelable {
        // Shows an outlined tyre icon
        @Parcelize
        public data object NotDetected : State

        // Shows a blue/green/red tyre
        public sealed interface Normal : State {
            public val fraction: Fraction

            @Parcelize
            public data class BlueToGreen(override val fraction: Fraction) : Normal

            @Parcelize
            public data class GreenToRed(override val fraction: Fraction) : Normal
        }

        /**
         * Shows a blinking red tyre, for a red or crimson alert of the tyre (pressure, temperature),
         * blinking faster when [isCritical]
         */
        @Parcelize
        public data class Alerting(val isCritical: Boolean = false) : State

        @Parcelize
        public data object DetectionIssue : State
    }

    private companion object {
        private val obsoleteTimeout = 5.minutes
    }
}
