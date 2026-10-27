package com.masselis.tpmsadvanced.feature.qrcode.ioc

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeResultViewModel
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.ShareSensorsViewModel
import com.masselis.tpmsadvanced.feature.qrcode.usecase.BoundSensorMapUseCase
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides

@Suppress("unused")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    private fun cameraAnalyser(): CameraAnalyser = CameraAnalyser()

    @Provides
    private fun boundSensorMapUseCase(
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
        sensorDatabase: SensorDatabase,
    ): BoundSensorMapUseCase =
        if (demoOrBleScannerUseCase.isDemo.value) BoundSensorMapUseCase.NoOp
        else BoundSensorMapUseCase.Impl(sensorDatabase)

    @Provides
    private fun qrCodeSensorUseCase(
        cameraAnalyser: CameraAnalyser,
    ): QrCodeSensorUseCase = QrCodeSensorUseCase(cameraAnalyser)

    public val featureQrCodeInternal: Internal

    @Inject
    public class Internal internal constructor(
        internal val qrCodeViewModel: QRCodeViewModel.Factory,
        internal val qrCodeResultViewModel: QrCodeResultViewModel.Factory,
        internal val shareSensorsViewModel: ShareSensorsViewModel.Factory,
    )

    public companion object {
        internal val QrCodeViewModel
            get() = (appGraph as Bindings).featureQrCodeInternal.qrCodeViewModel
        internal val QrCodeResultViewModel
            get() = (appGraph as Bindings).featureQrCodeInternal.qrCodeResultViewModel
        internal val ShareSensorsViewModel
            get() = (appGraph as Bindings).featureQrCodeInternal.shareSensorsViewModel
    }
}
