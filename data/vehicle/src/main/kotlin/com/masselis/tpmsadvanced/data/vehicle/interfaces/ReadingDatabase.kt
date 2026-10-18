package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.Reading
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.AdvertisingPacket
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The readings of the bound sensors, stored as their advertisement and decoded when read back, see
 * `Reading.sq`. A reading the decoders don't read anymore is skipped.
 */
public class ReadingDatabase internal constructor(
    database: Database
) {
    private val queries = database.readingQueries

    /** A reading without its advertisement, only made up by previews and tests, isn't stored */
    public suspend fun insert(tyre: Tyre.Located, vehicleId: UUID): Unit = withContext(IO) {
        tyre.raw?.also { raw ->
            queries.insert(vehicleId, tyre.location, tyre.timestamp, tyre.sensorId, tyre.rssi, raw)
        }
    }

    public fun latestByLocation(location: Location, vehicleId: UUID): QueryOneOrNull<Tyre.Located> = queries
        .latestByLocation(vehicleId, location)
        .asOneOrNull { it.decoded() }

    /**
     * The latest reading of the sensor [sensorId], if it was read at [location]: null once it was
     * read elsewhere, after it was reassigned, rather than a stale reading of this tyre
     */
    public fun latestBySensorByLocation(
        sensorId: Int,
        location: Location,
        vehicleId: UUID,
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestBySensorByLocation(sensorId = sensorId, vehicleId = vehicleId, location = location)
        .asOneOrNull { it.decoded() }

    /** All the stored readings of [location], oldest first, at most [CAP] of them */
    public fun allByLocation(location: Location, vehicleId: UUID): QueryList<Tyre.Located> = queries
        .allByLocation(vehicleId, location)
        .asList { it.decoded() }

    /** The readings of [location] stored after [timestamp], oldest first */
    public fun afterByLocation(
        location: Location,
        vehicleId: UUID,
        timestamp: Double,
    ): QueryList<Tyre.Located> = queries
        .afterByLocation(vehicleId, location, timestamp)
        .asList { it.decoded() }

    /**
     * Keeps the readings of [location] below [CAP], about 600 KB: once over it, the oldest are
     * deleted down to [KEPT] so this doesn't have to run at every insert.
     */
    public suspend fun prune(location: Location, vehicleId: UUID): Unit = withContext(IO) {
        queries.transaction {
            queries
                .countByLocation(vehicleId, location)
                .executeAsOne()
                .takeIf { it > CAP }
                ?.also { queries.keepLatestByLocation(vehicleId, location, KEPT) }
        }
    }

    public companion object {
        /** A reading takes about 70 bytes */
        public const val CAP: Long = 8_000
        public const val KEPT: Long = 6_000
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun Reading.decoded(): Tyre.Located? = AdvertisingPacket(raw.hexToByteArray())
    .decode()
    ?.asTyre(timestamp, rssi, sensorId, raw)
    ?.let { Tyre.Located(it, location) }
