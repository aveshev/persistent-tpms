package com.masselis.tpmsadvanced.data.vehicle.ioc

import android.content.Context
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.demo.DemoLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.BluetoothLeScannerImpl
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds


@OptIn(DelicateCoroutinesApi::class)
@Suppress("unused")
@ContributesTo(AppScope::class)
public interface DemoOrBleScannerBinding {

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun demoOrBleScannerUseCase(context: Context): DemoOrBleScannerUseCase =
        DemoOrBleScannerUseCase.Impl(context)

    @Provides
    @SingleIn(AppScope::class)
    private fun bluetoothLeScannerImpl(
        demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
        context: Context,
        appPreferences: AppPreferences,
    ): BluetoothLeScanner =
        if (demoOrBleScannerUseCase.isDemo.value) DemoLeScanner()
        else BluetoothLeScannerImpl(
            context,
            appPreferences.screenOffScanRestartSecondsInUse.map { seconds -> seconds.takeIf { it > 0 }?.seconds },
        )
}
