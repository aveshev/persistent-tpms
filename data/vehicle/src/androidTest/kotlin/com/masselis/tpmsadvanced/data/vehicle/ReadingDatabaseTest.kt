package com.masselis.tpmsadvanced.data.vehicle

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.flags
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("MaxLineLength")
@RunWith(AndroidJUnit4::class)
internal class ReadingDatabaseTest {

    @ContributesTo(AppScope::class)
    internal interface Extractor {
        val database: Database
        val databaseExport: DatabaseExport
    }

    private lateinit var database: Database
    private lateinit var databaseExport: DatabaseExport
    private lateinit var readingDatabase: ReadingDatabase
    private lateinit var currentVehicleUuid: UUID

    @Before
    fun setup() {
        appContext.getDatabasePath("car.db").delete()
        database = (appGraph as Extractor).database
        databaseExport = (appGraph as Extractor).databaseExport
        readingDatabase = ReadingDatabase(database)
        currentVehicleUuid = database.vehicleQueries.currentFavourite().executeAsOne().uuid
    }

    // The decoded values don't matter, the stored ones are decoded from raw when read back
    private fun tyre(raw: String?) = Tyre.Located(
        1.0, -60, 42, 2f.bar, 20f.celsius, PECHAM, false, Location.Wheel(FRONT_LEFT), null, raw,
    )

    private fun latest() = readingDatabase
        .latestByLocation(Location.Wheel(FRONT_LEFT), currentVehicleUuid)
        .execute()

    // A real Pecham advertisement, whose status byte is 0x80
    private val pecham = "0303a5270308425208ff801b1b02a584ea"

    @Test
    fun rawIsStoredAndTheValuesDecodedFromIt() = runTest {
        readingDatabase.insert(tyre(pecham), currentVehicleUuid)
        val latest = assertNotNull(latest())
        assertEquals(pecham, latest.raw)
        assertEquals(listOf<UByte>(0x80u), latest.flags)
        assertEquals(PECHAM, latest.brand)
        assertEquals(42, latest.sensorId)
        assertEquals(-60, latest.rssi)
        assertEquals(27f, latest.temperature.celsius)
    }

    // Reassigned from the front left wheel to the front right one, then back
    @Test
    fun aSensorReadElsewhereSinceHasNoLatestReadingHere() = runTest {
        val frontRight = Location.Wheel(FRONT_RIGHT)
        readingDatabase.insert(tyre(pecham), currentVehicleUuid)
        readingDatabase.insert(tyre(pecham).copy(timestamp = 2.0, location = frontRight), currentVehicleUuid)
        assertNull(readingDatabase.latestBySensorByLocation(42, Location.Wheel(FRONT_LEFT), currentVehicleUuid).execute())
        assertEquals(
            2.0,
            readingDatabase.latestBySensorByLocation(42, frontRight, currentVehicleUuid).execute()?.timestamp,
        )
    }

    @Test
    fun aReadingWithoutRawIsNotStored() = runTest {
        readingDatabase.insert(tyre(null), currentVehicleUuid)
        assertNull(latest())
    }

    @Test
    fun aReadingTheDecodersDontReadIsSkipped() = runTest {
        readingDatabase.insert(tyre("0201060303b0fb"), currentVehicleUuid)
        assertNull(latest())
    }

    @Test
    fun exportHoldsTheReadings() = runTest {
        readingDatabase.insert(tyre(pecham), currentVehicleUuid)
        val file = File(appContext.cacheDir, "database_export/test.db")
        // Twice: an existing copy must be replaced, VACUUM INTO alone refuses to overwrite
        databaseExport.exportTo(file)
        databaseExport.exportTo(file)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT hex(raw) FROM Reading", null).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(pecham, cursor.getString(0).lowercase())
            }
        }
    }
}
