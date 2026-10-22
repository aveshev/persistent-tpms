package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.SimulatedReadings
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.VOLT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Readings typed in the app, debug builds only, see [SimulatedReadings] */
internal class SimulateReadingViewModel(
    appPreferences: AppPreferences,
    unitPreferences: UnitPreferences,
    private val sensorDatabase: SensorDatabase,
) : ViewModel() {

    /** Shown along with the other debug options */
    val isAvailable: StateFlow<Boolean> = appPreferences
        .debugOptionsInUse
        .map { it && SimulatedReadings.IS_AVAILABLE }
        .stateIn(viewModelScope, WhileSubscribed(), false)

    val pressureUnit = unitPreferences.pressure
    val temperatureUnit = unitPreferences.temperature

    /**
     * From the sensor bound to that tyre, as if it was received, or from a Sysgration sensor
     * advertising its location for a tyre without one, detected there until it's assigned like any
     * unbound sensor, see LocatedTyreScannerUseCase. [battery] is in volts, or a percentage for
     * Sysgration. Values a sensor can't send are dropped, see [SimulatedReadings.send].
     */
    @Suppress("MaxLineLength")
    fun send(
        vehicle: Vehicle,
        location: Location,
        pressure: Pressure,
        temperature: Temperature,
        battery: Float?,
        isAlarm: Boolean,
    ) = viewModelScope.launch {
        withContext(IO) { sensorDatabase.selectByVehicleAndLocation(vehicle.uuid, location).execute() }
            .let { bound -> (bound?.brand ?: SYSGRATION) to (bound?.id ?: UNBOUND_SENSOR_ID) }
            .let { (brand, sensorId) ->
                runCatching {
                    SimulatedReadings.send(
                        brand,
                        sensorId,
                        // A spare has none, nothing would detect it unbound but its bound sensor
                        SensorLocation.entries.firstOrNull { it matches location } ?: SensorLocation.REAR_LEFT,
                        pressure.kpa,
                        temperature.celsius,
                        battery?.let { if (brand.batteryUnit == VOLT) it.times(DECIVOLTS_PER_VOLT).roundToInt() else it.roundToInt() },
                        isAlarm,
                    )
                }.onFailure { logger.w(it) { "Simulated reading not sent" } }
            }
    }

    private infix fun SensorLocation.matches(location: Location) = when (location) {
        is Location.Axle -> axle == location.axle
        is Location.Side -> side == location.side
        is Location.Wheel -> this == location.location
        Location.Spare -> false
        Location.Single -> true
    }

    private companion object {
        val logger = Logger.withTag("SimulateReadingViewModel")
        const val DECIVOLTS_PER_VOLT = 10

        /** Bound to no tyre, or it would only be shown at its binding's. A multiple of 256, as Sysgration's are. */
        const val UNBOUND_SENSOR_ID = 0x7E5700
    }
}
