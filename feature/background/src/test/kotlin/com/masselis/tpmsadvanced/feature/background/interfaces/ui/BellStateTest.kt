package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import org.junit.Test
import kotlin.test.assertEquals

internal class BellStateTest {

    @Test
    fun `missing permissions always win over the decision`() {
        listOf(
            ScanDecision.Active(setOf(CABLE)),
            ScanDecision.Suspended(setOf(WIFI)),
            ScanDecision.Idle,
        ).forEach {
            assertEquals(BellState.NeedsPermission, BellState.of(permissionsOk = false, decision = it))
        }
    }

    @Test
    fun `an active decision is green`() {
        assertEquals(BellState.Active, BellState.of(true, ScanDecision.Active(setOf(CABLE))))
    }

    @Test
    fun `a suspended decision is orange`() {
        assertEquals(BellState.Suspended, BellState.of(true, ScanDecision.Suspended(setOf(WIFI))))
    }

    @Test
    fun `an idle decision is neutral`() {
        assertEquals(BellState.Idle, BellState.of(true, ScanDecision.Idle))
    }

    @Test
    fun `Lite scanning without a trigger is green`() {
        assertEquals(BellState.Always, BellState.of(true, ScanDecision.Active(setOf(ALWAYS)), LITE))
    }

    @Test
    fun `Pro's always condition stays green`() {
        assertEquals(BellState.Active, BellState.of(true, ScanDecision.Active(setOf(ALWAYS)), PRO))
    }

    @Test
    fun `a Bluetooth device or a beacon is blue on both tiers`() {
        listOf(LITE, PRO).forEach { tier ->
            listOf(setOf(BLUETOOTH), setOf(BEACON), setOf(BLUETOOTH, STAY_ACTIVE)).forEach {
                assertEquals(BellState.Bluetooth, BellState.of(true, ScanDecision.Active(it), tier))
            }
        }
    }

    @Test
    fun `Pro's other conditions with a Bluetooth device are blue`() {
        assertEquals(BellState.Bluetooth, BellState.of(true, ScanDecision.Active(setOf(CABLE, BLUETOOTH)), PRO))
    }

    @Test
    fun `Pro's always condition wins over a Bluetooth device`() {
        assertEquals(BellState.Active, BellState.of(true, ScanDecision.Active(setOf(ALWAYS, BLUETOOTH)), PRO))
    }

    @Test
    fun `stay active is blue on Lite only`() {
        assertEquals(BellState.Bluetooth, BellState.of(true, ScanDecision.Active(setOf(STAY_ACTIVE)), LITE))
        assertEquals(BellState.Active, BellState.of(true, ScanDecision.Active(setOf(STAY_ACTIVE)), PRO))
    }

    @Test
    fun `the manual monitoring button is green on both tiers`() {
        listOf(LITE, PRO).forEach {
            assertEquals(BellState.Active, BellState.of(true, ScanDecision.Active(setOf(MANUAL)), it))
        }
    }
}
