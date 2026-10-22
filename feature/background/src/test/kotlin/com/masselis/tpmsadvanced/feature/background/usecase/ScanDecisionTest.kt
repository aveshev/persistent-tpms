package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanDecisionTest {

    @Test
    fun `nothing enabled is idle`() {
        assertEquals(ScanDecision.Idle, decide(emptySet(), setOf(CABLE), emptySet()))
    }

    @Test
    fun `enabled conditions that are not fulfilled are idle`() {
        assertEquals(ScanDecision.Idle, decide(setOf(CABLE, WIRELESS), emptySet(), emptySet()))
    }

    @Test
    fun `a fulfilled condition that is not enabled is ignored`() {
        assertEquals(ScanDecision.Idle, decide(setOf(WIRELESS), setOf(CABLE), emptySet()))
    }

    @Test
    fun `any enabled and fulfilled condition activates scanning`() {
        assertEquals(
            ScanDecision.Active(setOf(CABLE)),
            decide(setOf(CABLE, WIRELESS), setOf(CABLE), emptySet())
        )
    }

    @Test
    fun `every fulfilled condition is reported as a cause`() {
        assertEquals(
            ScanDecision.Active(setOf(CABLE, ANDROID_AUTO)),
            decide(
                setOf(CABLE, WIRELESS, ANDROID_AUTO),
                setOf(CABLE, ANDROID_AUTO),
                emptySet(),
            )
        )
    }

    @Test
    fun `a suspend condition wins over a fulfilled activate condition`() {
        assertEquals(
            ScanDecision.Suspended(setOf(WIFI)),
            decide(setOf(CABLE), setOf(CABLE), setOf(WIFI))
        )
    }

    @Test
    fun `every holding suspend condition is reported`() {
        assertEquals(
            ScanDecision.Suspended(setOf(IDLE, WIFI)),
            decide(setOf(CABLE), setOf(CABLE), setOf(IDLE, WIFI))
        )
    }

    @Test
    fun `a suspend condition without any activate condition is idle, not suspended`() {
        assertEquals(ScanDecision.Idle, decide(setOf(CABLE), emptySet(), setOf(WIFI)))
    }

    @Test
    fun `turning the activate conditions off activates scanning without any of them`() {
        assertEquals(
            ScanDecision.Active(setOf(ALWAYS)),
            decide(setOf(ALWAYS, CABLE), emptySet(), emptySet())
        )
    }

    @Test
    fun `turning the activate conditions off makes each of them irrelevant`() {
        assertEquals(
            ScanDecision.Active(setOf(ALWAYS)),
            decide(setOf(ALWAYS, CABLE), setOf(CABLE), emptySet())
        )
    }

    @Test
    fun `always scanning is still subject to suspend conditions`() {
        assertEquals(
            ScanDecision.Suspended(setOf(IDLE)),
            decide(setOf(ALWAYS), emptySet(), setOf(IDLE))
        )
    }

    @Test
    fun `the rationale reminds that the app scans by itself while opened unless already active`() {
        val note = "Note: Scanning is still active while the app is opened!"
        assertEquals(
            "Background scanning is active due to charging with a cable",
            ScanDecision.Active(setOf(CABLE)).rationale()
        )
        assertEquals(
            "Background scanning is suspended due to WiFi being connected.\n$note",
            ScanDecision.Suspended(setOf(WIFI)).rationale()
        )
        assertEquals(
            "Background scanning is idle: no activate condition is currently fulfilled.\n$note",
            ScanDecision.Idle.rationale()
        )
    }

    @Test
    fun `always scanning is explained as the user's choice`() {
        assertEquals(
            "Background scanning is active because you chose it to be always active",
            ScanDecision.Active(setOf(ALWAYS)).explanation()
        )
    }

    @Suppress("MaxLineLength")
    @Test
    fun `idle is explained by Lite's own conditions in Lite`() {
        assertEquals(
            "Background scanning is idle: none of the selected Bluetooth devices is connected and none of the added beacons is nearby",
            ScanDecision.Idle.explanation(AppTier.LITE)
        )
    }

    @Test
    fun `explanations name what caused the decision`() {
        assertEquals(
            "Background scanning is active due to charging with a cable, Android Auto being connected",
            ScanDecision.Active(setOf(CABLE, ANDROID_AUTO)).explanation()
        )
        assertEquals(
            "Background scanning is active due to monitoring being started manually",
            ScanDecision.Active(setOf(MANUAL)).explanation()
        )
        assertEquals(
            "Background scanning is suspended due to the phone being idle, WiFi being connected",
            ScanDecision.Suspended(setOf(IDLE, WIFI)).explanation()
        )
        assertEquals(
            "Background scanning is active due to soundcore Liberty 5 being connected",
            ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("soundcore Liberty 5")).explanation()
        )
        assertEquals(
            "Background scanning is active due to Car, Intercom and Watch being connected",
            ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("Car", "Intercom", "Watch")).explanation()
        )
        assertEquals(
            "Background scanning is suspended due to Speaker and TV being connected",
            ScanDecision.Suspended(setOf(Reason.BLUETOOTH), bluetoothDevices = listOf("Speaker", "TV")).explanation()
        )
        assertEquals(
            "Background scanning is suspended due to a Bluetooth device being connected",
            ScanDecision.Suspended(setOf(Reason.BLUETOOTH)).explanation()
        )
        assertEquals(
            "Background scanning is active due to Bike and Car being nearby",
            ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike", "Car")).explanation()
        )
        assertEquals(
            "Background scanning is active due to a Bluetooth beacon being nearby",
            ScanDecision.Active(setOf(BEACON)).explanation()
        )
        assertEquals(
            "Background scanning is idle: no activate condition is currently fulfilled",
            ScanDecision.Idle.explanation()
        )
    }

    @Test
    fun `the beacon override activates a nearby beacon despite the suspend conditions`() {
        assertEquals(
            ScanDecision.Active(setOf(BEACON, CABLE)),
            decide(
                setOf(BEACON, CABLE),
                setOf(BEACON, CABLE),
                setOf(WIFI, Reason.BLUETOOTH),
                beaconOverridesSuspend = true,
            )
        )
    }

    @Test
    fun `the beacon override does not apply while the phone is idle`() {
        assertEquals(
            ScanDecision.Suspended(setOf(WIFI, IDLE)),
            decide(setOf(BEACON), setOf(BEACON), setOf(WIFI, IDLE), beaconOverridesSuspend = true)
        )
    }

    @Test
    fun `the beacon override needs a beacon nearby`() {
        assertEquals(
            ScanDecision.Suspended(setOf(WIFI)),
            decide(setOf(BEACON, CABLE), setOf(CABLE), setOf(WIFI), beaconOverridesSuspend = true)
        )
    }

    @Test
    fun `a nearby beacon is suspended like any other condition without the override`() {
        assertEquals(
            ScanDecision.Suspended(setOf(WIFI)),
            decide(setOf(BEACON), setOf(BEACON), setOf(WIFI))
        )
    }
}
