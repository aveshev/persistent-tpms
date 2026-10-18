package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.usecase.ChargingStateUseCase.State
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanPolicyUseCaseTest {

    private lateinit var persistentScanning: MutableStateFlow<Boolean>
    private lateinit var activateConditions: MutableStateFlow<Boolean>
    private lateinit var activateOnCableCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnWirelessCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnAndroidAuto: MutableStateFlow<Boolean>
    private lateinit var activateOnBluetooth: MutableStateFlow<Boolean>
    private lateinit var activateBluetoothDevices: MutableStateFlow<Set<String>>
    private lateinit var stayActive: MutableStateFlow<Boolean>
    private lateinit var stayActiveMinutes: MutableStateFlow<Int>
    private lateinit var suspensionReasons: MutableStateFlow<Set<Reason>>
    private lateinit var charging: MutableStateFlow<State>
    private lateinit var androidAuto: MutableStateFlow<Boolean>
    private lateinit var bluetoothConnected: MutableStateFlow<Set<String>>
    private lateinit var suspendBluetoothDevices: MutableStateFlow<Set<String>>
    private lateinit var activateOnBeacon: MutableStateFlow<Boolean>
    private lateinit var beacons: MutableStateFlow<List<Beacon>>
    private lateinit var beaconOverridesSuspend: MutableStateFlow<Boolean>
    private lateinit var nearbyBeacons: MutableStateFlow<Map<String, Int>>
    private lateinit var tier: AppTier

    context(scope: TestScope)
    private fun test() = ScanPolicyUseCase(
        mockk<AppPreferences> {
            every { persistentScanning } returns this@ScanPolicyUseCaseTest.persistentScanning
            every { activateConditions } returns this@ScanPolicyUseCaseTest.activateConditions
            every { activateOnCableCharging } returns this@ScanPolicyUseCaseTest.activateOnCableCharging
            every { activateOnWirelessCharging } returns this@ScanPolicyUseCaseTest.activateOnWirelessCharging
            every { activateOnAndroidAuto } returns this@ScanPolicyUseCaseTest.activateOnAndroidAuto
            every { activateOnBluetooth } returns this@ScanPolicyUseCaseTest.activateOnBluetooth
            every { activateBluetoothDevices } returns this@ScanPolicyUseCaseTest.activateBluetoothDevices
            every { suspendBluetoothDevices } returns this@ScanPolicyUseCaseTest.suspendBluetoothDevices
            every { stayActive } returns this@ScanPolicyUseCaseTest.stayActive
            every { stayActiveMinutes } returns this@ScanPolicyUseCaseTest.stayActiveMinutes
            every { activateOnBeacon } returns this@ScanPolicyUseCaseTest.activateOnBeacon
            every { beacons } returns this@ScanPolicyUseCaseTest.beacons
            every { beaconOverridesSuspend } returns this@ScanPolicyUseCaseTest.beaconOverridesSuspend
            every { beaconScanMode } returns MutableStateFlow(null)
        },
        mockk<ScanSuspensionUseCase> {
            every { this@mockk.suspensionReasons } returns this@ScanPolicyUseCaseTest.suspensionReasons
        },
        mockk<ChargingStateUseCase> { every { state } returns charging },
        mockk<AndroidAutoUseCase> { every { connected } returns androidAuto },
        mockk<BluetoothDevicesUseCase> {
            // Named after their address
            every { connected } returns bluetoothConnected.map { addresses ->
                addresses.map { PairedDevice(it, it, isAudio = true) }.toSet()
            }
        },
        mockk<BeaconPresenceUseCase> { every { nearby(any(), any()) } returns nearbyBeacons },
        scope.backgroundScope,
        scope.testScheduler.timeSource,
        tier,
    )

    @Before
    fun setup() {
        persistentScanning = MutableStateFlow(true)
        activateConditions = MutableStateFlow(true)
        activateOnCableCharging = MutableStateFlow(true)
        activateOnWirelessCharging = MutableStateFlow(true)
        activateOnAndroidAuto = MutableStateFlow(true)
        activateOnBluetooth = MutableStateFlow(false)
        activateBluetoothDevices = MutableStateFlow(emptySet())
        stayActive = MutableStateFlow(false)
        stayActiveMinutes = MutableStateFlow(10)
        suspensionReasons = MutableStateFlow(emptySet())
        charging = MutableStateFlow(State(cable = false, wireless = false))
        androidAuto = MutableStateFlow(false)
        bluetoothConnected = MutableStateFlow(emptySet())
        suspendBluetoothDevices = MutableStateFlow(emptySet())
        activateOnBeacon = MutableStateFlow(false)
        beacons = MutableStateFlow(emptyList())
        beaconOverridesSuspend = MutableStateFlow(false)
        nearbyBeacons = MutableStateFlow(emptyMap())
        tier = PRO
    }

    @Test
    fun `without persistent scanning the service scans as long as it runs`() = runTest {
        persistentScanning.value = false
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(MANUAL)), awaitItem())
        }
    }

    @Test
    fun `the previous mode's decision is never handed out after switching to persistent scanning`() = runTest {
        persistentScanning.value = false
        val policy = test()
        policy.decision.test {
            assertEquals(ScanDecision.Active(setOf(MANUAL)), awaitItem())
            persistentScanning.value = true
            // Like the service, started at that very moment, while the replay is still the manual one
            policy.decision.test { assertEquals(ScanDecision.Idle, awaitItem()) }
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `activate conditions without any selected always scan, like when turned off`() = runTest {
        activateOnCableCharging.value = false
        activateOnWirelessCharging.value = false
        activateOnAndroidAuto.value = false
        stayActive.value = true
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(ALWAYS)), awaitItem())
        }
    }

    @Test
    fun `nothing to activate scanning is idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `plugging a cable activates scanning and unplugging goes back to idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            charging.value = State(cable = true, wireless = false)
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `wireless charging activates scanning`() = runTest {
        charging.value = State(cable = false, wireless = true)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(WIRELESS)), awaitItem())
        }
    }

    @Test
    fun `a disabled charging condition is ignored`() = runTest {
        activateOnCableCharging.value = false
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a suspend condition suspends an activated scan and releases it afterwards`() = runTest {
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            suspensionReasons.value = setOf(Reason.WIFI)
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
            suspensionReasons.value = emptySet()
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
        }
    }

    @Test
    fun `turning the activate conditions off activates scanning without charging`() = runTest {
        activateConditions.value = false
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(ALWAYS)), awaitItem())
        }
    }

    @Test
    fun `connecting Android Auto activates scanning and disconnecting goes back to idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            androidAuto.value = true
            assertEquals(ScanDecision.Active(setOf(ANDROID_AUTO)), awaitItem())
            androidAuto.value = false
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a disabled Android Auto condition is ignored`() = runTest {
        activateOnAndroidAuto.value = false
        androidAuto.value = true
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `Android Auto and a cable are both reported as causes`() = runTest {
        androidAuto.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE, ANDROID_AUTO)), awaitItem())
        }
    }

    @Test
    fun `Android Auto is suspended by a suspend condition like any other`() = runTest {
        androidAuto.value = true
        suspensionReasons.value = setOf(Reason.IDLE)
        test().decision.test {
            assertEquals(ScanDecision.Suspended(setOf(Reason.IDLE)), awaitItem())
        }
    }

    @Test
    fun `connecting a selected Bluetooth device activates scanning and disconnecting goes back to idle`() = runTest {
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("CAR")
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            bluetoothConnected.value = setOf("HEADPHONES", "CAR")
            assertEquals(ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("CAR")), awaitItem())
            bluetoothConnected.value = setOf("HEADPHONES")
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `every selected Bluetooth device connected is named`() = runTest {
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("INTERCOM", "CAR")
        bluetoothConnected.value = setOf("INTERCOM", "CAR", "HEADPHONES")
        test().decision.test {
            assertEquals(
                ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("CAR", "INTERCOM")),
                awaitItem()
            )
        }
    }

    @Test
    fun `a Bluetooth suspension names the selected devices connected`() = runTest {
        charging.value = State(cable = true, wireless = false)
        suspendBluetoothDevices.value = setOf("SPEAKER")
        bluetoothConnected.value = setOf("SPEAKER", "HEADPHONES")
        suspensionReasons.value = setOf(Reason.BLUETOOTH)
        test().decision.test {
            assertEquals(
                ScanDecision.Suspended(setOf(Reason.BLUETOOTH), bluetoothDevices = listOf("SPEAKER")),
                awaitItem()
            )
        }
    }

    @Test
    fun `a Bluetooth device that is not selected is ignored`() = runTest {
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("CAR")
        bluetoothConnected.value = setOf("HEADPHONES")
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a disabled Bluetooth condition is ignored`() = runTest {
        activateBluetoothDevices.value = setOf("CAR")
        bluetoothConnected.value = setOf("CAR")
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `the Bluetooth condition alone is enough to not always scan`() = runTest {
        activateOnCableCharging.value = false
        activateOnWirelessCharging.value = false
        activateOnAndroidAuto.value = false
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("CAR")
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `the Bluetooth condition without any device always scans, like when nothing is selected`() = runTest {
        activateOnCableCharging.value = false
        activateOnWirelessCharging.value = false
        activateOnAndroidAuto.value = false
        activateOnBluetooth.value = true
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(ALWAYS)), awaitItem())
        }
    }

    @Test
    fun `staying active keeps scanning for the configured time after the last condition ended`() = runTest {
        stayActive.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(10.minutes - 1.seconds)
            expectNoEvents()
            delay(2.seconds)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a condition coming back ends the stay and a new one starts over when it ends again`() = runTest {
        stayActive.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(8.minutes)
            charging.value = State(cable = true, wireless = false)
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            // The 8 minutes of the first stay do not count any more
            delay(9.minutes)
            expectNoEvents()
            delay(2.minutes)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `there is nothing to stay active after when no condition was ever fulfilled`() = runTest {
        stayActive.value = true
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            delay(30.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `without the option the scan goes idle as soon as the last condition ends`() = runTest {
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a suspend condition suspends a stay like any other activation`() = runTest {
        stayActive.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            suspensionReasons.value = setOf(Reason.WIFI)
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
        }
    }

    @Test
    fun `the configured time can be changed`() = runTest {
        stayActive.value = true
        stayActiveMinutes.value = 2
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(3.minutes)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `there is no stay when scanning was suspended the moment the last condition ended`() = runTest {
        stayActive.value = true
        charging.value = State(cable = true, wireless = false)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Idle, awaitItem())
            // Not resumed either when the suspension ends
            suspensionReasons.value = emptySet()
            delay(30.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `a suspension during a stay does not move its end`() = runTest {
        stayActive.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(3.minutes)
            suspensionReasons.value = setOf(Reason.WIFI)
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
            delay(3.minutes)
            suspensionReasons.value = emptySet()
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            // 10 minutes after the condition ended, not after the suspension did
            delay(4.minutes + 1.seconds)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a beacon coming nearby activates scanning and going away goes back to idle`() = runTest {
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE, CAR)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            nearbyBeacons.value = mapOf(BIKE.address to -70)
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            nearbyBeacons.value = emptyMap()
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `every beacon nearby is named, by the name the user gave it first`() = runTest {
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE, CAR)
        nearbyBeacons.value = mapOf(BIKE.address to -70, CAR.address to -80)
        test().decision.test {
            assertEquals(
                ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike", "CFMOTOR_f33467ca7beb")),
                awaitItem()
            )
        }
    }

    @Test
    fun `the signal of a beacon changing does not tell the decision again`() = runTest {
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            nearbyBeacons.value = mapOf(BIKE.address to -60)
            expectNoEvents()
        }
    }

    @Test
    fun `a disabled beacon condition is ignored`() = runTest {
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `the beacon condition alone is enough to not always scan`() = runTest {
        activateOnCableCharging.value = false
        activateOnWirelessCharging.value = false
        activateOnAndroidAuto.value = false
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `the beacon condition without any beacon always scans, like when nothing is selected`() = runTest {
        activateOnCableCharging.value = false
        activateOnWirelessCharging.value = false
        activateOnAndroidAuto.value = false
        activateOnBeacon.value = true
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(ALWAYS)), awaitItem())
        }
    }

    @Test
    fun `a beacon is suspended like any other activation without the override`() = runTest {
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
        }
    }

    @Test
    fun `with the override a nearby beacon scans despite the suspend conditions`() = runTest {
        activateOnBeacon.value = true
        beaconOverridesSuspend.value = true
        beacons.value = listOf(BIKE)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            nearbyBeacons.value = mapOf(BIKE.address to -70)
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            nearbyBeacons.value = emptyMap()
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `the override does not apply to the phone being idle`() = runTest {
        activateOnBeacon.value = true
        beaconOverridesSuspend.value = true
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            suspensionReasons.value = setOf(Reason.WIFI, Reason.IDLE)
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI, Reason.IDLE)), awaitItem())
        }
    }

    @Test
    fun `the override only applies to the beacon, not to the other conditions`() = runTest {
        activateOnBeacon.value = true
        beaconOverridesSuspend.value = true
        beacons.value = listOf(BIKE)
        charging.value = State(cable = true, wireless = false)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
        }
    }

    @Test
    fun `a beacon going away while the override held scanning stays, suspended like any other cause`() = runTest {
        stayActive.value = true
        activateOnBeacon.value = true
        beaconOverridesSuspend.value = true
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        suspensionReasons.value = setOf(Reason.WIFI)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            // Scanning was active when the beacon went away, but the override was the beacon's
            nearbyBeacons.value = emptyMap()
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
            suspensionReasons.value = emptySet()
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(10.minutes + 1.seconds)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `staying active applies to a beacon going away like to any condition`() = runTest {
        stayActive.value = true
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE)
        nearbyBeacons.value = mapOf(BIKE.address to -70)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(BEACON), beacons = listOf("Bike")), awaitItem())
            nearbyBeacons.value = emptyMap()
            assertEquals(ScanDecision.Active(setOf(STAY_ACTIVE)), awaitItem())
            delay(10.minutes + 1.seconds)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `Lite ignores the charging and Android Auto conditions`() = runTest {
        tier = LITE
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("CAR")
        charging.value = State(cable = true, wireless = true)
        androidAuto.value = true
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `Lite activates on a connected Bluetooth device`() = runTest {
        tier = LITE
        activateOnBluetooth.value = true
        activateBluetoothDevices.value = setOf("CAR")
        bluetoothConnected.value = setOf("CAR")
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(BLUETOOTH), bluetoothDevices = listOf("CAR")), awaitItem())
        }
    }

    @Test
    fun `Lite without a Bluetooth device or beacon always scans, whatever the other conditions`() = runTest {
        tier = LITE
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(ALWAYS)), awaitItem())
        }
    }

    @Test
    fun `Lite ignores the activate conditions being turned off`() = runTest {
        tier = LITE
        activateConditions.value = false
        activateOnBeacon.value = true
        beacons.value = listOf(BIKE)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    private companion object {
        val BIKE = Beacon("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", label = "Bike")
        val CAR = Beacon("F3:34:67:CA:7B:EB", "CFMOTOR_f33467ca7beb", label = null)
    }
}
