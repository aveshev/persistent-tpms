package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.BluetoothLeScannerImpl.BluetoothOff
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class RecoveringTest {

    private lateinit var bluetoothOn: MutableStateFlow<Boolean>

    /** Ends the scan running at the time, like the system does */
    private lateinit var failures: MutableSharedFlow<Throwable>

    /** Every status told, the last one last */
    private lateinit var statuses: MutableList<ScanStatus>

    /** How many times the scan was started */
    private var scans = 0

    context(scope: TestScope)
    private fun test() = flow {
        scans++
        emit(scans)
        emitAll(failures.map { throw it })
    }.recovering(bluetoothOn, scope.testScheduler.timeSource) { statuses += it }

    context(scope: TestScope)
    private fun elapsed() = scope.testScheduler.currentTime.milliseconds

    @Before
    fun setup() {
        // Logcat isn't there on the JVM, the failures are logged
        Logger.setLogWriters(CommonWriter())
        bluetoothOn = MutableStateFlow(true)
        failures = MutableSharedFlow()
        statuses = mutableListOf()
        scans = 0
    }

    @Test
    fun `a scan tells it's scanning`() = runTest {
        test().test {
            assertEquals(1, awaitItem())
            assertEquals(listOf<ScanStatus>(Scanning), statuses)
        }
    }

    @Test
    fun `a scan ended by Bluetooth waits for it, then starts over`() = runTest {
        test().test {
            skipItems(1)
            bluetoothOn.value = false
            failures.emit(BluetoothOff(STATE_OFF))
            delay(10.minutes)
            expectNoEvents()
            assertEquals(WaitingForBluetooth, statuses.last())
            bluetoothOn.value = true
            assertEquals(2, awaitItem())
            assertEquals(Scanning, statuses.last())
        }
    }

    @Test
    fun `a scan ended by Bluetooth starts over right away when it's already back`() = runTest {
        test().test {
            skipItems(1)
            failures.emit(BluetoothOff(STATE_OFF))
            assertEquals(2, awaitItem())
            assertEquals(listOf(Scanning, WaitingForBluetooth, Scanning), statuses)
        }
    }

    @Test
    fun `a refused scan is retried, waiting longer each time it fails in a row`() = runTest {
        test().test {
            skipItems(1)
            listOf(5, 10, 20, 40, 60, 60).forEach { seconds ->
                failures.emit(Failure.Scan(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED))
                assertEquals(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED, assertIs<Retrying>(statuses.last()).reason)
                val failedAt = elapsed()
                awaitItem()
                assertEquals(seconds.seconds, elapsed() - failedAt)
            }
            assertEquals(7, scans)
        }
    }

    @Test
    fun `after a refused start, scanning is only told once a start held`() = runTest {
        test().test {
            skipItems(1)
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            awaitItem()
            assertIs<Retrying>(statuses.last())
            delay(9.seconds)
            assertIs<Retrying>(statuses.last())
            delay(1.seconds)
            assertEquals(Scanning, statuses.last())
        }
    }

    @Test
    fun `the starts refused in a row keep when they began failing`() = runTest {
        test().test {
            skipItems(1)
            delay(1.minutes)
            val firstStart = elapsed()
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            awaitItem()
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            awaitItem()
            val retries = statuses.filterIsInstance<Retrying>()
            assertEquals(2, retries.size)
            assertEquals(retries[0], retries[1])
            assertEquals(elapsed() - firstStart, retries[1].since.elapsedNow())
        }
    }

    @Test
    fun `a start that held begins a new run of refused ones`() = runTest {
        test().test {
            skipItems(1)
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            awaitItem()
            delay(10.seconds)
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            val (first, second) = statuses.filterIsInstance<Retrying>()
            // Restarted after 5 s, held for 10 s
            assertEquals(15.seconds, first.since.elapsedNow() - second.since.elapsedNow())
        }
    }

    @Test
    fun `a scan that ran for a while is retried after the shortest delay again`() = runTest {
        test().test {
            skipItems(1)
            repeat(3) {
                failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
                skipItems(1)
            }
            delay(2.minutes)
            failures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            val failedAt = elapsed()
            awaitItem()
            assertEquals(5.seconds, elapsed() - failedAt)
        }
    }

    @Test
    fun `a phone that can't scan ends it`() = runTest {
        test().test {
            skipItems(1)
            failures.emit(Failure.Scan(SCAN_FAILED_FEATURE_UNSUPPORTED))
            assertIs<Failure.Scan>(awaitError())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `a failure besides the scan's ends it`() = runTest {
        test().test {
            skipItems(1)
            failures.emit(SecurityException("Need android.permission.BLUETOOTH_SCAN"))
            assertIs<SecurityException>(awaitError())
            assertEquals(1, scans)
        }
    }

    private companion object {
        // BluetoothAdapter and ScanCallback constants
        const val STATE_OFF = 10
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
