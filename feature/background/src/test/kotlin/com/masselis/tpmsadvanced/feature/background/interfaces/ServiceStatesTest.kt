package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Active
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.BluetoothOff
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Idle
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.MonitoringFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanKeepsFailing
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanRetrying
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Starting
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Suspended
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class ServiceStatesTest {

    private lateinit var decision: MutableStateFlow<ScanDecision>
    private lateinit var tyreScanStatus: MutableStateFlow<ScanStatus?>
    /** What the scan shows while it runs */
    private lateinit var active: MutableStateFlow<State>

    /** Ends the scan running at the time, like the scanner does when it can't recover */
    private lateinit var scanFailures: MutableSharedFlow<Throwable>

    /** How many times the scan was started */
    private var scans = 0

    private fun test() = decision.serviceStates(tyreScanStatus) {
        flow {
            scans++
            emitAll(merge(active, scanFailures.map { throw it }))
        }
    }

    context(scope: TestScope)
    private fun markNow() = scope.testScheduler.timeSource.markNow()

    @Before
    fun setup() {
        // Logcat isn't there on the JVM, the failures are logged
        Logger.setLogWriters(CommonWriter())
        decision = MutableStateFlow(ScanDecision.Active(setOf(CABLE)))
        tyreScanStatus = MutableStateFlow(null)
        active = MutableStateFlow(ACTIVE)
        scanFailures = MutableSharedFlow()
        scans = 0
    }

    @Test
    fun `the scan's states are shown while active`() = runTest {
        test().test {
            assertEquals(Starting, awaitItem())
            assertEquals(ACTIVE, awaitItem())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `suspended and idle don't scan`() = runTest {
        decision.value = ScanDecision.Suspended(setOf(WIFI))
        test().test {
            assertEquals(Starting, awaitItem())
            assertEquals(Suspended(ScanDecision.Suspended(setOf(WIFI))), awaitItem())
            decision.value = ScanDecision.Idle
            assertEquals(Idle, awaitItem())
            assertEquals(0, scans)
        }
    }

    @Test
    fun `the scan waiting for Bluetooth is told, and the scan's states once it's back`() = runTest {
        test().test {
            skipItems(2)
            tyreScanStatus.value = WaitingForBluetooth
            assertEquals(BluetoothOff, awaitItem())
            tyreScanStatus.value = Scanning
            assertEquals(ACTIVE, awaitItem())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `a refused start is told while the scanner retries it`() = runTest {
        test().test {
            skipItems(2)
            tyreScanStatus.value = Retrying(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED, markNow())
            assertEquals(ScanRetrying(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED), awaitItem())
            tyreScanStatus.value = Scanning
            assertEquals(ACTIVE, awaitItem())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `starts refused for 10 minutes are told as failing, until it scans again`() = runTest {
        test().test {
            skipItems(2)
            tyreScanStatus.value = Retrying(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED, markNow())
            assertEquals(ScanRetrying(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED), awaitItem())
            delay(10.minutes - 1.milliseconds)
            expectNoEvents()
            delay(1.milliseconds)
            assertEquals(ScanKeepsFailing(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED), awaitItem())
            tyreScanStatus.value = Scanning
            assertEquals(ACTIVE, awaitItem())
        }
    }

    @Test
    fun `the time the starts were already refused for counts`() = runTest {
        test().test {
            skipItems(2)
            tyreScanStatus.value = Retrying(SCAN_FAILED_INTERNAL_ERROR, markNow() - 9.minutes)
            assertEquals(ScanRetrying(SCAN_FAILED_INTERNAL_ERROR), awaitItem())
            delay(1.minutes)
            assertEquals(ScanKeepsFailing(SCAN_FAILED_INTERNAL_ERROR), awaitItem())
        }
    }

    @Test
    fun `a phone that can't scan ends with a scan failure`() = runTest {
        test().test {
            skipItems(2)
            scanFailures.emit(Failure.Scan(SCAN_FAILED_FEATURE_UNSUPPORTED))
            assertEquals(ScanFailure(SCAN_FAILED_FEATURE_UNSUPPORTED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a failure besides the scan ends monitoring, without blaming Bluetooth`() = runTest {
        test().test {
            skipItems(2)
            scanFailures.emit(IllegalStateException("Database closed"))
            assertEquals(MonitoringFailure, awaitItem())
            awaitComplete()
        }
    }

    private companion object {
        val ACTIVE = Active(ScanDecision.Active(setOf(CABLE)))

        // ScanCallback constants
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
