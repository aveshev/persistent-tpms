package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The tyre's atmosphere, its pressure being corrected by its sensor's calibration so the display,
 * the alerts and the background monitoring all agree on it.
 */
public class TyreAtmosphereUseCase internal constructor(
    private val listenTyreUseCase: ListenTyreUseCase,
    private val calibrationUseCase: VehicleCalibrationUseCase,
) {
    public fun listen(): Flow<TyreAtmosphere> = listenTyreUseCase
        .listen()
        .combine(calibrationUseCase.calibrations) { record, calibrations ->
            record.toAtmosphere(calibrations)
        }
}
