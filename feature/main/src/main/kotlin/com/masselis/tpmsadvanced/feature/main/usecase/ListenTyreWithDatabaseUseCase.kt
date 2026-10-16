package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreWithDatabaseUseCase.StoredLatest.FOUND
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreWithDatabaseUseCase.StoredLatest.NONE
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreWithDatabaseUseCase.StoredLatest.NOT_LOOKED_UP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlin.time.DurationUnit

internal interface ListenTyreWithDatabaseUseCase : ListenTyreUseCase {

    /** What looking up the bound sensor's latest stored reading, which [listen] starts with, found */
    val storedLatest: StateFlow<StoredLatest>

    enum class StoredLatest { NOT_LOOKED_UP, FOUND, NONE }

    class Impl(
        vehicle: Vehicle,
        location: Location,
        readingDatabase: ReadingDatabase,
        listenTyreUseCase: ListenTyreUseCase,
        sensorBindingUseCase: SensorBindingUseCase,
        scope: CoroutineScope,
    ) : ListenTyreWithDatabaseUseCase {

        // Only touched by the shared upstream, a single collector
        private var lastStored: Tyre.Located? = null
        private var storedCount = 0

        override val storedLatest = MutableStateFlow(NOT_LOOKED_UP)

        private val flow = listenTyreUseCase
            .listen()
            .onStart { readingDatabase.prune(location, vehicle.uuid) }
            .onEach { tyre ->
                tyre
                    // Sensors send each advertisement several times in a burst, only the first one
                    // is stored, with its signal strength
                    .takeIf { new ->
                        lastStored
                            ?.let { last ->
                                new.sensorId == last.sensorId &&
                                    new.raw == last.raw &&
                                    new.timestamp - last.timestamp < BURST_SECONDS
                            }
                            ?.not()
                            ?: true
                    }
                    ?.also { readingDatabase.insert(it, vehicle.uuid) }
                    ?.also { lastStored = it }
                    ?.takeIf { ++storedCount % PRUNE_EVERY == 0 }
                    ?.also { readingDatabase.prune(location, vehicle.uuid) }
            }
            .materializeCompletion()
            .shareIn(scope, WhileSubscribed())
            .dematerializeCompletion()
            .onStart {
                // The bound sensor's own latest record: records of other sensors stored before it
                // was bound would be dropped by ListenBoundTyreUseCase. A location without a bound
                // sensor shows nothing until one is, see LocatedTyreScannerUseCase.
                sensorBindingUseCase
                    .boundSensor()
                    .value
                    ?.let { readingDatabase.latestBySensorByLocation(it.id, location, vehicle.uuid) }
                    ?.execute()
                    .also { storedLatest.value = if (it != null) FOUND else NONE }
                    ?.also { emit(it) }
            }
            .flowOn(Dispatchers.IO)

        override fun listen(): Flow<Tyre.Located> = flow

        private companion object {
            val BURST_SECONDS = BluetoothLeScanner.BURST.toDouble(DurationUnit.SECONDS)
            const val PRUNE_EVERY = 500
        }
    }

    class Wrapper(private val source: ListenTyreUseCase) : ListenTyreWithDatabaseUseCase {
        override val storedLatest = MutableStateFlow(NONE)

        override fun listen(): Flow<Tyre.Located> = source.listen()
    }
}
