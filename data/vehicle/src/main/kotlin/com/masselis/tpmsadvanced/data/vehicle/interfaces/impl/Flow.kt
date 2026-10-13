package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.BluetoothLeScannerImpl.BluetoothOff
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Starts a scan over after a failure that can go away, telling what it does to [onStatus]: once
 * [isBluetoothOn] after Bluetooth was turned off, and after a delay for a start the system refused,
 * longer after each one in a row up to [MAX_RETRY_DELAY]. A scan that ran for [RETRY_DELAY_RESET]
 * before failing waits the shortest delay again. Only a phone that can't scan, and failures besides
 * the scan's, end it.
 *
 * After refused starts, the scan is only told [Scanning] again once a start held for [SETTLE]:
 * the system refuses a start right away, the restarts in between would make the status flap.
 */
internal fun <T> Flow<T>.recovering(
    isBluetoothOn: Flow<Boolean>,
    timeSource: TimeSource = TimeSource.Monotonic,
    onStatus: (ScanStatus) -> Unit,
): Flow<T> = channelFlow {
    var startedAt = timeSource.markNow()
    var retryDelay = FIRST_RETRY_DELAY
    // When the starts began failing, null while the last one held
    var failingSince: TimeMark? = null
    var settling: Job? = null
    this@recovering
        .onStart {
            startedAt = timeSource.markNow()
            if (failingSince == null) onStatus(Scanning)
            else settling = launch {
                delay(SETTLE)
                onStatus(Scanning)
            }
        }
        .retryWhen { cause, _ ->
            settling?.cancel()
            if (startedAt.elapsedNow() >= SETTLE) failingSince = null
            when {
                cause is BluetoothOff -> {
                    failingSince = null
                    onStatus(WaitingForBluetooth)
                    logger.i { "Scan stopped by Bluetooth being off, waiting for it" }
                    isBluetoothOn.first { it }
                    retryDelay = FIRST_RETRY_DELAY
                }

                cause is Failure.Scan && cause.isRecoverable -> {
                    if (startedAt.elapsedNow() >= RETRY_DELAY_RESET) retryDelay = FIRST_RETRY_DELAY
                    onStatus(Retrying(cause.reason, failingSince ?: timeSource.markNow().also { failingSince = it }))
                    logger.w(cause) { "Scan failed, retrying in $retryDelay" }
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }

                else -> return@retryWhen false
            }
            true
        }
        .collect { send(it) }
}

/**
 * Drops an element whose [key] was let through less than [window] earlier, by [time]: a
 * `distinctUntilChangedBy` that forgets a key after [window], and that other keys in between don't
 * reset.
 */
internal fun <T> Flow<T>.distinctWithin(window: Duration, key: (T) -> Any, time: (T) -> Duration): Flow<T> = flow {
    // When each key was last let through, only those still within the window
    val passed = mutableMapOf<Any, Duration>()
    collect { element ->
        passed.values.removeAll { time(element) - it >= window }
        element
            .takeIf { key(it) !in passed }
            ?.also { passed[key(it)] = time(it) }
            ?.also { emit(it) }
    }
}

private val logger = Logger.withTag("BluetoothLeScannerImpl")

private val FIRST_RETRY_DELAY = 5.seconds
private val MAX_RETRY_DELAY = 1.minutes
private val RETRY_DELAY_RESET = 2.minutes
private val SETTLE = 10.seconds
