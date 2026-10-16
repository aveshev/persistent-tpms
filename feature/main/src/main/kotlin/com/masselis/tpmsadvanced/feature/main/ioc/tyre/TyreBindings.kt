package com.masselis.tpmsadvanced.feature.main.ioc.tyre

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl.TyreIconViewModelImpl
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl.TyreStatsViewModelImpl
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleLifecycle
import com.masselis.tpmsadvanced.feature.main.usecase.ListenBoundTyreUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreSmartDutyUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreWithDatabaseUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.LocatedTyreScannerUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.TyreAlertsUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.TyreAtmosphereUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyrePressureLossStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehiclePressureLossUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine

@Suppress("unused", "MaxLineLength", "FunctionNaming")
@ContributesTo(scope = TyreComponent.Scope::class)
public interface TyreBindings {

    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun sensorBindingUseCase(
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
        currentVehicle: Vehicle,
        vehicleDatabase: VehicleDatabase,
        sensorDatabase: SensorDatabase,
        currentLocation: Location,
        @VehicleLifecycle scope: CoroutineScope,
    ): SensorBindingUseCase =
        if (demoOrBleScannerUseCase.isDemo.value)
            SensorBindingUseCase.NoOp
        else
            SensorBindingUseCase.Impl(
                currentVehicle,
                vehicleDatabase,
                sensorDatabase,
                currentLocation,
                scope
            )

    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun listenTyreWithDatabaseUseCase(
        vehicle: Vehicle,
        location: Location,
        readingDatabase: ReadingDatabase,
        listenTyreUseCase: ListenTyreSmartDutyUseCase,
        sensorBindingUseCase: SensorBindingUseCase,
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
        @VehicleLifecycle scope: CoroutineScope,
    ): ListenTyreWithDatabaseUseCase =
        if (demoOrBleScannerUseCase.isDemo.value)
            ListenTyreWithDatabaseUseCase.Wrapper(
                listenTyreUseCase
            )
        else
            ListenTyreWithDatabaseUseCase.Impl(
                vehicle,
                location,
                readingDatabase,
                listenTyreUseCase,
                sensorBindingUseCase,
                scope
            )


    @Provides
    private fun listenBoundTyreUseCase(
        listenTyreUseCase: ListenTyreWithDatabaseUseCase,
        sensorBindingUseCase: SensorBindingUseCase,
    ): ListenBoundTyreUseCase = ListenBoundTyreUseCase(listenTyreUseCase, sensorBindingUseCase)

    @Provides
    private fun listenTyreUseCase(uc: ListenBoundTyreUseCase): ListenTyreUseCase = uc

    @Provides
    private fun tyreAtmosphereUseCase(
        listenTyreUseCase: ListenTyreUseCase,
        calibrationUseCase: VehicleCalibrationUseCase,
    ): TyreAtmosphereUseCase = TyreAtmosphereUseCase(listenTyreUseCase, calibrationUseCase)

    @Provides
    private fun listenTyreSmartDutyUseCase(
        scanner: LocatedTyreScannerUseCase,
        appPreferences: AppPreferences,
    ): ListenTyreSmartDutyUseCase = ListenTyreSmartDutyUseCase(
        scanner,
        // A debug option: turning Debug off turns it off too
        combine(appPreferences.debugOptions, appPreferences.balancedAfterFirstReading, Boolean::and),
    )

    // Single, it keeps the sensor it detects for TyreActionsViewModel
    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun locatedTyreScannerUseCase(
        source: BluetoothLeScanner,
        currentLocation: Location,
        sensorBindingUseCase: SensorBindingUseCase,
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
    ): LocatedTyreScannerUseCase = LocatedTyreScannerUseCase(
        source,
        currentLocation,
        sensorBindingUseCase,
        demoOrBleScannerUseCase.isDemo.value,
    )

    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun tyrePressureLossStateFlow(
        vehicle: Vehicle,
        location: Location,
        readingDatabase: ReadingDatabase,
        listenTyreUseCase: ListenTyreUseCase,
        calibrationUseCase: VehicleCalibrationUseCase,
        pressureLossUseCase: VehiclePressureLossUseCase,
        @VehicleLifecycle scope: CoroutineScope,
    ): TyrePressureLossStateFlow = TyrePressureLossStateFlow(
        vehicle,
        location,
        readingDatabase,
        listenTyreUseCase,
        calibrationUseCase,
        pressureLossUseCase,
        scope
    )

    @Provides
    private fun tyreAlertsUseCase(
        atmosphereUseCase: TyreAtmosphereUseCase,
        rangeUseCase: VehicleRangesUseCase,
        pressureLossStateFlow: TyrePressureLossStateFlow,
        location: Location,
    ): TyreAlertsUseCase = TyreAlertsUseCase(atmosphereUseCase, rangeUseCase, pressureLossStateFlow, location)

    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun tyreStatsUseCase(
        alertsUseCase: TyreAlertsUseCase,
        calibrationUseCase: VehicleCalibrationUseCase,
        unitPreferences: UnitPreferences,
        sensorBindingUseCase: SensorBindingUseCase,
        @VehicleLifecycle scope: CoroutineScope,
        listenTyreWithDatabaseUseCase: ListenTyreWithDatabaseUseCase,
    ): TyreStatsStateFlow = TyreStatsStateFlow(
        alertsUseCase,
        calibrationUseCase,
        unitPreferences,
        sensorBindingUseCase,
        scope,
        listenTyreWithDatabaseUseCase.storedLatest,
    )

    @SingleIn(TyreComponent.Scope::class)
    @Provides
    private fun tyreIconUseCase(
        alertsUseCase: TyreAlertsUseCase,
        rangeUseCase: VehicleRangesUseCase,
        location: Location,
        sensorBindingUseCase: SensorBindingUseCase,
        @VehicleLifecycle scope: CoroutineScope,
    ): TyreIconStateFlow = TyreIconStateFlow(
        alertsUseCase,
        rangeUseCase,
        location,
        sensorBindingUseCase,
        scope
    )

    @Provides
    private fun tyreStatsViewModelImpl(
        uc: TyreStatsStateFlow,
        appPreferences: AppPreferences,
    ): TyreStatsViewModelImpl = TyreStatsViewModelImpl(uc, appPreferences)

    @Provides
    private fun tyreStatsViewModel(vm: TyreStatsViewModelImpl): TyreStatsViewModel = vm

    @Provides
    private fun tyreIconViewModelImpl(uc: TyreIconStateFlow): TyreIconViewModelImpl =
        TyreIconViewModelImpl(uc)

    @Provides
    private fun tyreIconViewModel(vm: TyreIconViewModelImpl): TyreIconViewModel = vm

    @Provides
    private fun tyreActionsViewModel(
        sensorBindingUseCase: SensorBindingUseCase,
        scannerUseCase: LocatedTyreScannerUseCase,
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
    ): TyreActionsViewModel =
        TyreActionsViewModel(sensorBindingUseCase, scannerUseCase, demoOrBleScannerUseCase)

    public val internal: Internal

    @Inject
    public class Internal internal constructor(
        internal val tyreIconViewModel: () -> TyreIconViewModelImpl,
        internal val tyreStatsViewModel: () -> TyreStatsViewModelImpl,
        internal val tyreActionsViewModel: () -> TyreActionsViewModel,
    )

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    public companion object {
        private val TyreComponent.internal
            get() = (this as TyreBindings).internal

        internal fun TyreComponent.TyreIconViewModel() = internal.tyreIconViewModel()
        internal fun TyreComponent.TyreStatsViewModel() = internal.tyreStatsViewModel()
        internal fun TyreComponent.TyreActionsViewModel() = internal.tyreActionsViewModel()
    }
}
