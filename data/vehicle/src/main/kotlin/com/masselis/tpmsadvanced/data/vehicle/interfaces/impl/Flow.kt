package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Retrying
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.Scanning
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus.WaitingForBluetooth
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.BluetoothLeScannerImpl.BluetoothOff
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Starts a scan over after a failure that can go away, telling what it does to [onStatus]: once
 * [isBluetoothOn] after Bluetooth was turned off, and after a delay for a start the system refused,
 * longer after each one in a row up to [MAX_RETRY_DELAY]. A scan that ran for [RETRY_DELAY_RESET]
 * before failing waits the shortest delay again. Only a phone that can't scan, and failures besides
 * the scan's, end it.
 */
internal fun <T> Flow<T>.recovering(
    isBluetoothOn: Flow<Boolean>,
    timeSource: TimeSource = TimeSource.Monotonic,
    onStatus: (ScanStatus) -> Unit,
): Flow<T> = flow {
    var startedAt = timeSource.markNow()
    var retryDelay = FIRST_RETRY_DELAY
    emitAll(
        onStart {
            startedAt = timeSource.markNow()
            onStatus(Scanning)
        }.retryWhen { cause, _ ->
            when {
                cause is BluetoothOff -> {
                    onStatus(WaitingForBluetooth)
                    logger.i { "Scan stopped by Bluetooth being off, waiting for it" }
                    isBluetoothOn.first { it }
                    retryDelay = FIRST_RETRY_DELAY
                }

                cause is Failure.Scan && cause.isRecoverable -> {
                    if (startedAt.elapsedNow() >= RETRY_DELAY_RESET) retryDelay = FIRST_RETRY_DELAY
                    onStatus(Retrying(cause.reason))
                    logger.w(cause) { "Scan failed, retrying in $retryDelay" }
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }

                else -> return@retryWhen false
            }
            true
        }
    )
}

private val logger = Logger.withTag("BluetoothLeScannerImpl")

private val FIRST_RETRY_DELAY = 5.seconds
private val MAX_RETRY_DELAY = 1.minutes
private val RETRY_DELAY_RESET = 2.minutes
