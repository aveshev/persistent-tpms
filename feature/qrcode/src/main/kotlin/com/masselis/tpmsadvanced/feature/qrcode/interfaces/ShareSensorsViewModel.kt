package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensors
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** The QR code another phone scans to get the sensors of the vehicle [vehicleUuid], see [SharedSensors] */
@AssistedInject
internal class ShareSensorsViewModel(
    vehicleDatabase: VehicleDatabase,
    sensorDatabase: SensorDatabase,
    @Assisted vehicleUuid: UUID,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(vehicleUuid: UUID): ShareSensorsViewModel
    }

    sealed interface State {
        data object Loading : State

        /** [vehicle] has no sensor to share */
        data class NoSensor(val vehicle: Vehicle) : State

        /** [code] holds [shared], one bit per module, its quiet zone left out */
        data class Ready(val shared: SharedSensors, val code: BitMatrix) : State
    }

    // Follows the sensors, the page can be opened while one is being assigned
    val stateFlow: StateFlow<State> = combine(
        vehicleDatabase.selectByUuid(vehicleUuid).asFlow(),
        sensorDatabase.selectListByVehicleId(vehicleUuid).asFlow(),
        sensorDatabase.selectCalibrations(vehicleUuid).asFlow(),
    ) { vehicle, sensors, calibrations ->
        sensors
            .takeIf { it.isNotEmpty() }
            // In the order of the vehicle's wheels, like the page lists them
            ?.sortedBy { sensor -> vehicle.kind.locations.indexOf(sensor.location) }
            ?.map { sensor ->
                SharedSensor(
                    sensor,
                    calibrations.firstOrNull { it.first == sensor.id }?.second ?: SensorCalibration.None,
                )
            }
            ?.let { SharedSensors(vehicle.kind, vehicle.name, it) }
            ?.let { shared ->
                State.Ready(
                    shared,
                    QRCodeWriter().encode(
                        shared.uri,
                        BarcodeFormat.QR_CODE,
                        0,
                        0,
                        // Medium, read off a screen it isn't damaged, a smaller code is easier to scan
                        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
                    ),
                )
            }
            ?: State.NoSensor(vehicle)
    }
        .flowOn(Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), State.Loading)
}
