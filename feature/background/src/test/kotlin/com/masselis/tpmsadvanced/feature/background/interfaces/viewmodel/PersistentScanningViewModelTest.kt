package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.LowMemory
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.UnexpectedStop
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PersistentScanningViewModelTest {

    private lateinit var persistentScanning: MutableStateFlow<Boolean>
    private lateinit var persistentScanningSetUp: MutableStateFlow<Boolean>
    private lateinit var suspendConditions: MutableStateFlow<Boolean>
    private lateinit var suspendScanningOnWifi: MutableStateFlow<Boolean>
    private lateinit var wifiExceptionEnabled: MutableStateFlow<Boolean>
    private var unexpectedStop: UnexpectedStop? = null
    private lateinit var controller: MonitoringController

    private fun test() = PersistentScanningViewModel(
        mockk<AppPreferences> {
            every { persistentScanning } returns this@PersistentScanningViewModelTest.persistentScanning
            every { persistentScanningSetUp } returns this@PersistentScanningViewModelTest.persistentScanningSetUp
            every { suspendConditions } returns this@PersistentScanningViewModelTest.suspendConditions
            every { suspendScanningOnWifi } returns this@PersistentScanningViewModelTest.suspendScanningOnWifi
            every { wifiExceptionEnabled } returns this@PersistentScanningViewModelTest.wifiExceptionEnabled
        },
        mockk<ScanPolicyUseCase> { every { decision } returns MutableSharedFlow<ScanDecision>() },
        mockk<WifiConnectionUseCase>(),
        mockk<UnexpectedStopUseCase> { every { consume() } returns unexpectedStop },
        controller,
    )

    @Before
    fun setup() {
        persistentScanning = MutableStateFlow(true)
        persistentScanningSetUp = MutableStateFlow(true)
        suspendConditions = MutableStateFlow(true)
        suspendScanningOnWifi = MutableStateFlow(true)
        wifiExceptionEnabled = MutableStateFlow(true)
        unexpectedStop = null
        controller = mockk { every { stop() } returns true }
    }

    @Test
    fun `the wifi exception is in use when every setting leading to it is on`() =
        runTest {
            test().wifiExceptionActive.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `turning off any of the four settings stops the wifi exception from being in use`() = runTest {
        test().wifiExceptionActive.test {
            assertEquals(true, awaitItem())
            persistentScanning.value = false
            assertEquals(false, awaitItem())
            persistentScanning.value = true
            assertEquals(true, awaitItem())
            suspendConditions.value = false
            assertEquals(false, awaitItem())
            suspendConditions.value = true
            assertEquals(true, awaitItem())
            suspendScanningOnWifi.value = false
            assertEquals(false, awaitItem())
            suspendScanningOnWifi.value = true
            assertEquals(true, awaitItem())
            wifiExceptionEnabled.value = false
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `disabling the wifi exception turns the setting off`() {
        test().disableWifiException()
        assertFalse(wifiExceptionEnabled.value)
    }

    @Test
    fun `an unexpected stop is reported until acknowledged`() {
        unexpectedStop = UnexpectedStop(42L, LowMemory)
        val viewModel = test()
        assertEquals(unexpectedStop, viewModel.unexpectedStop.value)
        viewModel.acknowledgeUnexpectedStop()
        assertNull(viewModel.unexpectedStop.value)
    }

    @Test
    fun `an unexpected stop is not reported while persistent scanning is off`() {
        unexpectedStop = UnexpectedStop(42L, LowMemory)
        persistentScanning.value = false
        assertNull(test().unexpectedStop.value)
    }

    @Test
    fun `the menu's switch turns persistent scanning off and stops the service`() {
        test().setPersistentScanning(false)
        assertFalse(persistentScanning.value)
        verify { controller.stop() }
    }

    @Test
    fun `the menu's switch only turns persistent scanning on, the host starts the service`() {
        persistentScanning.value = false
        test().setPersistentScanning(true)
        assertTrue(persistentScanning.value)
        verify(exactly = 0) { controller.stop() }
    }
}
