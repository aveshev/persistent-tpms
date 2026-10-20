package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase.State.Connected
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase.State.Disconnected
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanSuspensionUseCaseTest {

    private lateinit var appPreferences: AppPreferences
    private lateinit var phoneIdleUseCase: PhoneIdleUseCase
    private lateinit var wifiConnectionUseCase: WifiConnectionUseCase
    private lateinit var bluetoothDevicesUseCase: BluetoothDevicesUseCase

    private lateinit var suspendConditions: MutableStateFlow<Boolean>
    private lateinit var suspendScanningInDoze: MutableStateFlow<Boolean>
    private lateinit var suspendScanningOnWifi: MutableStateFlow<Boolean>
    private lateinit var wifiExceptionEnabled: MutableStateFlow<Boolean>
    private lateinit var exceptedWifiSsids: MutableStateFlow<Set<String>>
    private lateinit var phoneIdle: MutableStateFlow<Boolean>
    private lateinit var wifiState: MutableStateFlow<WifiConnectionUseCase.State>
    private lateinit var suspendScanningOnBluetooth: MutableStateFlow<Boolean>
    private lateinit var suspendBluetoothDevices: MutableStateFlow<Set<String>>
    private lateinit var bluetoothConnected: MutableStateFlow<Set<String>>
    private lateinit var tier: AppTier

    private fun test() = ScanSuspensionUseCase(
        appPreferences,
        phoneIdleUseCase,
        wifiConnectionUseCase,
        bluetoothDevicesUseCase,
        tier,
    )

    @Before
    fun setup() {
        suspendConditions = MutableStateFlow(true)
        suspendScanningInDoze = MutableStateFlow(false)
        suspendScanningOnWifi = MutableStateFlow(false)
        wifiExceptionEnabled = MutableStateFlow(false)
        exceptedWifiSsids = MutableStateFlow(emptySet())
        phoneIdle = MutableStateFlow(false)
        wifiState = MutableStateFlow(Disconnected)
        suspendScanningOnBluetooth = MutableStateFlow(false)
        suspendBluetoothDevices = MutableStateFlow(emptySet())
        bluetoothConnected = MutableStateFlow(emptySet())
        tier = PRO

        appPreferences = mockk {
            every { suspendConditions } returns this@ScanSuspensionUseCaseTest.suspendConditions
            every { suspendScanningInDoze } returns this@ScanSuspensionUseCaseTest.suspendScanningInDoze
            every { suspendScanningOnWifi } returns this@ScanSuspensionUseCaseTest.suspendScanningOnWifi
            every { wifiExceptionEnabled } returns this@ScanSuspensionUseCaseTest.wifiExceptionEnabled
            every { exceptedWifiSsids } returns this@ScanSuspensionUseCaseTest.exceptedWifiSsids
            every { suspendScanningOnBluetooth } returns this@ScanSuspensionUseCaseTest.suspendScanningOnBluetooth
            every { suspendBluetoothDevices } returns this@ScanSuspensionUseCaseTest.suspendBluetoothDevices
        }
        phoneIdleUseCase = mockk { every { isIdle } returns this@ScanSuspensionUseCaseTest.phoneIdle }
        wifiConnectionUseCase = mockk { every { state } returns this@ScanSuspensionUseCaseTest.wifiState }
        bluetoothDevicesUseCase = mockk {
            every { connected } returns bluetoothConnected.map { addresses ->
                addresses.map { PairedDevice(it, it, isAudio = true) }.toSet()
            }
        }
    }

    @Test
    fun `no suspension when both toggles are off`() = runTest {
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for doze when the toggle is off even if the device is idle`() = runTest {
        phoneIdle.value = true
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when doze is enabled and the device is idle`() = runTest {
        suspendScanningInDoze.value = true
        phoneIdle.value = true
        test().suspensionReasons.test {
            assertEquals(setOf(IDLE), awaitItem())
        }
    }

    @Test
    fun `turning the suspend conditions off lifts every suspension`() = runTest {
        suspendScanningInDoze.value = true
        phoneIdle.value = true
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("Home")
        test().suspensionReasons.test {
            assertEquals(setOf(IDLE, WIFI), awaitItem())
            suspendConditions.value = false
            assertEquals(emptySet(), awaitItem())
            suspendConditions.value = true
            assertEquals(setOf(IDLE, WIFI), awaitItem())
        }
    }

    @Test
    fun `does not suspend for wifi when disconnected`() = runTest {
        suspendScanningOnWifi.value = true
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when connected to wifi and no exception is configured`() = runTest {
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(WIFI), awaitItem())
        }
    }

    @Test
    fun `does not suspend when connected to the excepted wifi`() = runTest {
        suspendScanningOnWifi.value = true
        wifiExceptionEnabled.value = true
        exceptedWifiSsids.value = setOf("CarWifi")
        wifiState.value = Connected("CarWifi")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `suspends when connected to a non-excepted wifi even with exceptions enabled`() = runTest {
        suspendScanningOnWifi.value = true
        wifiExceptionEnabled.value = true
        exceptedWifiSsids.value = setOf("CarWifi")
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(WIFI), awaitItem())
        }
    }

    @Test
    fun `combines both reasons when both conditions are met`() = runTest {
        suspendScanningInDoze.value = true
        phoneIdle.value = true
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("HomeNetwork")
        test().suspensionReasons.test {
            assertEquals(setOf(IDLE, WIFI), awaitItem())
        }
    }

    @Test
    fun `suspends while a selected Bluetooth device is connected`() = runTest {
        suspendScanningOnBluetooth.value = true
        suspendBluetoothDevices.value = setOf("SPEAKER")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
            bluetoothConnected.value = setOf("SPEAKER")
            assertEquals(setOf(BLUETOOTH), awaitItem())
            bluetoothConnected.value = emptySet()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for a Bluetooth device that is not selected`() = runTest {
        suspendScanningOnBluetooth.value = true
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("CAR")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `does not suspend for Bluetooth when the toggle is off`() = runTest {
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("SPEAKER")
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun `Lite only suspends while the phone is idle`() = runTest {
        tier = LITE
        suspendScanningOnWifi.value = true
        wifiState.value = Connected("Home")
        suspendScanningOnBluetooth.value = true
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("SPEAKER")
        suspendScanningInDoze.value = true
        test().suspensionReasons.test {
            assertEquals(emptySet(), awaitItem())
            phoneIdle.value = true
            assertEquals(setOf(IDLE), awaitItem())
        }
    }

    @Test
    fun `Lite ignores the suspend conditions being turned off`() = runTest {
        tier = LITE
        suspendConditions.value = false
        suspendScanningInDoze.value = true
        phoneIdle.value = true
        test().suspensionReasons.test {
            assertEquals(setOf(IDLE), awaitItem())
        }
    }
}
