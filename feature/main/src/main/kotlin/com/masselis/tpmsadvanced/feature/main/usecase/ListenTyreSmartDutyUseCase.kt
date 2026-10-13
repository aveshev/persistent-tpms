package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * The high duty scan, the whole time: a sensor sends rarely (every minute while riding for some),
 * and from a phone mounted in the cockpit only some of its packets get through, a lower duty misses
 * many of them. [balancedAfterFirstReading] switches to the normal scan after the first reading.
 */
internal class ListenTyreSmartDutyUseCase(
    private val scanner: LocatedTyreScannerUseCase,
    private val balancedAfterFirstReading: Flow<Boolean>,
) : ListenTyreUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun listen(): Flow<Tyre.Located> = balancedAfterFirstReading
        .distinctUntilChanged()
        .flatMapLatest { balancedAfterFirstReading ->
            if (balancedAfterFirstReading) flow {
                emit(scanner.highDutyScan().first())
                emitAll(scanner.normalScan())
            }
            else scanner.highDutyScan()
        }
}
