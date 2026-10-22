package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Clear
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Conflict
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel.OtherAppCheck.Unchecked
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.LowMemory
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.UnexpectedStop
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PersistentScanningViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var persistentScanning: MutableStateFlow<Boolean>
    private lateinit var persistentScanningSetUp: MutableStateFlow<Boolean>
    private lateinit var suspendConditions: MutableStateFlow<Boolean>
    private lateinit var suspendScanningOnWifi: MutableStateFlow<Boolean>
    private lateinit var wifiExceptionEnabled: MutableStateFlow<Boolean>
    private var unexpectedStop: UnexpectedStop? = null
    private lateinit var controller: MonitoringController
    private var otherAppStatus: OtherApp.Status? = null
    private lateinit var otherApp: OtherApp

    private fun test(tier: AppTier = AppTier.PRO) = PersistentScanningViewModel(
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
        otherApp,
        tier,
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
        otherAppStatus = null
        otherApp = mockk {
            every { packageName } returns "com.aveshev.persistenttpms.lite"
            every { label() } returns "Persistent TPMS Lite"
            coEvery { status() } answers { otherAppStatus }
            coEvery { turnOffPersistentScanning() } returns true
        }
    }

    private fun status(persistentScanning: Boolean) = OtherApp.Status(
        persistentScanning = persistentScanning,
        databaseVersion = 12,
        latestReading = null,
        hasData = true,
    )

    @Test
    fun `the other app scanning persistently too is a conflict, named after it`() {
        otherAppStatus = status(persistentScanning = true)
        val viewModel = test()
        assertEquals(Unchecked, viewModel.otherAppCheck.value)
        viewModel.checkOtherApp()
        assertEquals(Conflict("Persistent TPMS Lite"), viewModel.otherAppCheck.value)
    }

    @Test
    fun `the other app not scanning persistently, or not reachable, clears the check`() {
        otherAppStatus = status(persistentScanning = false)
        val viewModel = test()
        viewModel.checkOtherApp()
        assertEquals(Clear, viewModel.otherAppCheck.value)
        otherAppStatus = null
        viewModel.checkOtherApp()
        assertEquals(Clear, viewModel.otherAppCheck.value)
    }

    @Test
    fun `switching to this app turns persistent scanning off in the other app`() {
        otherAppStatus = status(persistentScanning = true)
        val viewModel = test()
        viewModel.checkOtherApp()
        viewModel.switchFromOtherApp()
        coVerify { otherApp.turnOffPersistentScanning() }
        assertEquals(Clear, viewModel.otherAppCheck.value)
        assertTrue(persistentScanning.value)
    }

    @Test
    fun `cancelling the switch turns persistent scanning off here`() {
        otherAppStatus = status(persistentScanning = true)
        val viewModel = test()
        viewModel.checkOtherApp()
        viewModel.keepOtherApp()
        assertFalse(persistentScanning.value)
        verify { controller.stop() }
        coVerify(exactly = 0) { otherApp.turnOffPersistentScanning() }
        assertEquals(Unchecked, viewModel.otherAppCheck.value)
    }

    @Test
    fun `the wifi exception is in use when every setting leading to it is on`() =
        runTest {
            test().wifiExceptionActive.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `Lite never uses the wifi exception, even with the settings imported from Pro`() =
        runTest {
            test(AppTier.LITE).wifiExceptionActive.test { assertEquals(false, awaitItem()) }
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
