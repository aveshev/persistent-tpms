package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.DeviceMatch
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.BALANCED
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_POWER
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Companion.asBeaconScanMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class BeaconPresenceUseCaseTest {

    private lateinit var advertisements: MutableSharedFlow<Advertisement>
    private lateinit var bluetoothOn: MutableStateFlow<Boolean>
    private lateinit var minRssi: MutableStateFlow<Int>
    private lateinit var scanner: BluetoothLeScanner

    context(scope: TestScope)
    private fun test() = BeaconPresenceUseCase(scanner, bluetoothOn, minRssi, scope.testScheduler.timeSource)

    @Before
    fun setup() {
        advertisements = MutableSharedFlow()
        bluetoothOn = MutableStateFlow(true)
        minRssi = MutableStateFlow(-85)
        scanner = mockk { every { advertisements(any(), any()) } returns advertisements }
    }

    @Test
    fun `a beacon heard is nearby with its last signal`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            advertisements.emit(advertisement(BIKE.address, rssi = -70))
            expectNoEvents()
            advertisements.emit(advertisement(BIKE.address, rssi = -60))
            assertEquals(mapOf(BIKE.address to -60), awaitItem())
            advertisements.emit(advertisement(BIKE.address, rssi = -50))
            assertEquals(mapOf(BIKE.address to -50), awaitItem())
        }
    }

    @Test
    fun `a single stray packet is not nearby`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            advertisements.emit(advertisement(BIKE.address, rssi = -70))
            delay(31.seconds)
            // Too late to count with the first one
            advertisements.emit(advertisement(BIKE.address, rssi = -70))
            delay(31.seconds)
            expectNoEvents()
        }
    }

    @Test
    fun `a packet without a measured signal is ignored`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            advertisements.emit(advertisement(BIKE.address, rssi = Advertisement.RSSI_UNAVAILABLE))
            advertisements.emit(advertisement(BIKE.address, rssi = Advertisement.RSSI_UNAVAILABLE))
            expectNoEvents()
        }
    }

    @Test
    fun `a beacon is recognized by its advertised name when the address differs`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            repeat(2) {
                advertisements.emit(advertisement("11:22:33:44:55:66", name = BIKE.advertisedName, rssi = -70))
            }
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
        }
    }

    @Test
    fun `the address is matched whatever its case`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            repeat(2) { advertisements.emit(advertisement(BIKE.address.lowercase(), rssi = -70)) }
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
        }
    }

    @Test
    fun `a beacon too far away is not nearby`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            advertisements.emit(advertisement(BIKE.address, rssi = -86))
            expectNoEvents()
        }
    }

    @Test
    fun `the threshold set applies to the next packets`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            minRssi.value = -70
            advertisements.emit(advertisement(BIKE.address, rssi = -75))
            advertisements.emit(advertisement(BIKE.address, rssi = -70))
            expectNoEvents()
            advertisements.emit(advertisement(BIKE.address, rssi = -70))
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
        }
    }

    @Test
    fun `another device is ignored`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            advertisements.emit(advertisement("11:22:33:44:55:66", name = "Someone's watch", rssi = -50))
            expectNoEvents()
        }
    }

    @Test
    fun `a beacon gone quiet is no longer nearby`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            repeat(2) { advertisements.emit(advertisement(BIKE.address, rssi = -70)) }
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
            delay(25.seconds)
            expectNoEvents()
            delay(10.seconds)
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun `each packet keeps the beacon nearby`() = runTest {
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            repeat(10) {
                advertisements.emit(advertisement(BIKE.address, rssi = -70))
                delay(20.seconds)
            }
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `nothing is nearby while Bluetooth is off, and scanning starts again once on`() = runTest {
        bluetoothOn.value = false
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            verify(exactly = 0) { scanner.advertisements(any(), any()) }
            bluetoothOn.value = true
            repeat(2) { advertisements.emit(advertisement(BIKE.address, rssi = -70)) }
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
            bluetoothOn.value = false
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun `scans for the beacons by address and advertised name with the mode asked for`() = runTest {
        val nameless = Beacon("C4:CD:82:63:55:15", advertisedName = null, label = "Tag")
        test().nearby(listOf(BIKE, nameless), BALANCED).test {
            assertEquals(emptyMap(), awaitItem())
            verify {
                scanner.advertisements(
                    BALANCED,
                    listOf(DeviceMatch(BIKE.address, BIKE.advertisedName), DeviceMatch(nameless.address, null)),
                )
            }
        }
    }

    @Test
    fun `a scan that can't go on leaves the beacons going away`() = runTest {
        every { scanner.advertisements(any(), any()) } returns flow {
            repeat(2) { emit(advertisement(BIKE.address, rssi = -70)) }
            error("Feature unsupported")
        }
        test().nearby(listOf(BIKE), LOW_POWER).test {
            assertEquals(emptyMap(), awaitItem())
            assertEquals(mapOf(BIKE.address to -70), awaitItem())
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun `an unknown or missing scan mode falls back on the default one`() {
        assertEquals(BALANCED, "BALANCED".asBeaconScanMode())
        assertEquals(LOW_POWER, BeaconPresenceUseCase.DEFAULT_SCAN_MODE)
        // Not offered for the background scan, or not any more
        assertEquals(BeaconPresenceUseCase.DEFAULT_SCAN_MODE, "LOW_LATENCY".asBeaconScanMode())
        assertEquals(BeaconPresenceUseCase.DEFAULT_SCAN_MODE, "LOW_POWER_BATCHED_10".asBeaconScanMode())
        assertEquals(BeaconPresenceUseCase.DEFAULT_SCAN_MODE, null.asBeaconScanMode())
    }

    private fun advertisement(address: String, name: String? = null, rssi: Int) =
        Advertisement(address, name, rssi, timestamp = Duration.ZERO, isTyreSensor = false)

    private companion object {
        val BIKE = Beacon("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", label = "Bike")
    }
}
