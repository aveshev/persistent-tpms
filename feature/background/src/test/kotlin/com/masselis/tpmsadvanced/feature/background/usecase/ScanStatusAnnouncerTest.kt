package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanStatusAnnouncerTest {

    private lateinit var decision: MutableSharedFlow<ScanDecision>

    // The speaking side stays off: no text-to-speech in unit tests
    private fun TestScope.test() = ScanStatusAnnouncer(
        mockk<AppPreferences> {
            every { debugOptionsInUse } returns MutableStateFlow(false)
            every { announceScanStatus } returns MutableStateFlow(false)
        },
        mockk<ScanPolicyUseCase> {
            every { decision } returns this@ScanStatusAnnouncerTest.decision
        },
        backgroundScope,
    )

    @Before
    fun setup() {
        decision = MutableSharedFlow()
    }

    @Test
    fun `each change is told with its reasons, not the one when collecting`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            assertEquals("TPMS active, cable", awaitItem())
            decision.emit(ScanDecision.Suspended(setOf(IDLE)))
            assertEquals("TPMS suspended, sleep", awaitItem())
            decision.emit(ScanDecision.Idle)
            assertEquals("TPMS idle", awaitItem())
        }
    }

    @Test
    fun `a change of reasons keeping the same status is told`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            assertEquals("TPMS active, cable", awaitItem())
            decision.emit(ScanDecision.Active(setOf(STAY_ACTIVE)))
            assertEquals("TPMS active, stay", awaitItem())
            decision.emit(ScanDecision.Suspended(setOf(WIFI)))
            assertEquals("TPMS suspended, WiFi", awaitItem())
            decision.emit(ScanDecision.Suspended(setOf(WIFI, IDLE)))
            assertEquals("TPMS suspended, sleep and WiFi", awaitItem())
        }
    }

    @Test
    fun `several reasons are told in a fixed order`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(BLUETOOTH, CABLE)))
            assertEquals("TPMS active, cable and Bluetooth", awaitItem())
        }
    }

    @Test
    fun `a change of the devices behind a reason is not told`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("A")))
            assertEquals("TPMS active, Bluetooth", awaitItem())
            decision.emit(ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("A", "B")))
            decision.emit(ScanDecision.Idle)
            assertEquals("TPMS idle", awaitItem())
        }
    }

    @Test
    fun `turning persistent scanning off is told as off`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            decision.emit(ScanDecision.Active(setOf(MANUAL)))
            assertEquals("TPMS off", awaitItem())
        }
    }
}
