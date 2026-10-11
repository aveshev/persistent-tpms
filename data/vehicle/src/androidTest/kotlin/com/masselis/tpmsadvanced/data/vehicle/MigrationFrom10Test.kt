package com.masselis.tpmsadvanced.data.vehicle

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.afterVersion10
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 10.db holds a motorcycle with a Pecham sensor (a reading with its advertisement, an older one
 * without), a sensor whose only reading has no advertisement, and a car with a Sysgration sensor.
 */
@RunWith(AndroidJUnit4::class)
internal class MigrationFrom10Test {

    @ContributesTo(AppScope::class)
    internal interface Extractor {
        val uuidAdapter: ColumnAdapter<UUID, ByteArray>
        val locationAdapter: ColumnAdapter<Location, Long>
        val pressureAdapter: ColumnAdapter<Pressure, Double>
        val temperatureAdapter: ColumnAdapter<Temperature, Double>
        val voltageAdapter: ColumnAdapter<Voltage, Double>
        val brandAdapter: ColumnAdapter<SensorBrand, Long>
        val hexAdapter: ColumnAdapter<String, ByteArray>
    }

    private lateinit var vehicleDatabase: VehicleDatabase
    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var readingDatabase: ReadingDatabase

    private val bike = UUID.fromString("f68dbb64-57cc-4766-b920-1a64cfdabb7c")
    private val car = UUID.fromString("466b2e13-e33a-4adf-a101-a920140a29a1")

    @Before
    fun setup() {
        val dbFile = appContext.getDatabasePath("car.db")
        dbFile.delete()
        appContext.assets.open("10.db").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        // Its own connection: the app's may already be open on the database another test left
        val driver = AndroidSqliteDriver(SQLiteDatabase.openDatabase(dbFile.absolutePath, null, OPEN_READWRITE))
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // A calibrated car, which 11.sqm moves to its sensors
        driver.execute(
            null,
            "UPDATE Vehicle SET pressureCalibration = 1, pressureOffset = 10, pressureMultiplier = 1.05 WHERE name = 'Car'",
            0,
        )
        // Through every later migration too, the queries below are the latest schema's
        Database.Schema.migrate(driver, 10, Database.Schema.version, Database.afterVersion10())
        val database = with(appGraph as Extractor) {
            Database(
                driver,
                VehicleAdapter = Vehicle.Adapter(
                    uuidAdapter,
                    pressureAdapter,
                    pressureAdapter,
                    temperatureAdapter,
                    temperatureAdapter,
                    temperatureAdapter,
                    EnumColumnAdapter(),
                    pressureAdapter,
                    pressureAdapter,
                    pressureAdapter,
                    voltageAdapter,
                    IntColumnAdapter,
                    pressureAdapter,
                    pressureAdapter,
                ),
                SensorAdapter = Sensor.Adapter(IntColumnAdapter, locationAdapter, uuidAdapter, brandAdapter, pressureAdapter),
                ReadingAdapter = Reading.Adapter(uuidAdapter, locationAdapter, IntColumnAdapter, IntColumnAdapter, hexAdapter),
            )
        }
        vehicleDatabase = VehicleDatabase(database)
        sensorDatabase = SensorDatabase(database)
        readingDatabase = ReadingDatabase(database)
    }

    @Test
    fun vehiclesKeepTheirIdAndSettings() {
        assertEquals("Bike", vehicleDatabase.selectByUuid(bike).execute().name)
        assertEquals("Car", vehicleDatabase.selectByUuid(car).execute().name)
        assertEquals(2.5f, vehicleDatabase.selectLowBatteryVoltage(bike).volts)
        assertEquals(10, vehicleDatabase.selectLowBatteryPercent(bike))
    }

    @Test
    fun sensorsTakeTheBrandOfTheirLatestReading() {
        assertEquals(PECHAM, assertNotNull(sensorDatabase.selectById(1734484831).execute()).brand)
        assertEquals(SYSGRATION, assertNotNull(sensorDatabase.selectById(1192960).execute()).brand)
        // Nothing tells what it is
        assertNull(sensorDatabase.selectById(-945495008).execute())
    }

    @Test
    fun onlyTheReadingsWithTheirAdvertisementAreKept() {
        readingDatabase.allByLocation(Location.Axle(REAR), bike).execute().let { readings ->
            assertEquals(1, readings.size)
            assertEquals(2000.0, readings.single().timestamp)
            assertEquals(-71, readings.single().rssi)
            assertEquals(3.1f, assertNotNull(readings.single().batteryVoltage).volts, 0.001f)
        }
        assertEquals(emptyList(), readingDatabase.allByLocation(Location.Axle(FRONT), bike).execute())
        assertEquals(
            90,
            assertNotNull(readingDatabase.latestByLocation(Location.Wheel(FRONT_LEFT), car).execute()).batteryPercent,
        )
    }

    // 11.sqm: the calibration is each sensor's, they keep reading as their vehicle corrected them
    @Test
    fun sensorsTakeTheirVehicleCalibrationAndTheSpareUsesTheFrontRange() {
        assertEquals(
            SensorCalibration(true, PressureCalibration(10f.kpa, 1.05f)),
            assertNotNull(sensorDatabase.selectCalibration(1192960).execute()),
        )
        assertEquals(
            SensorCalibration.None,
            assertNotNull(sensorDatabase.selectCalibration(1734484831).execute()),
        )
        assertFalse(vehicleDatabase.selectSeparateSparePressure(car))
        assertNull(vehicleDatabase.selectSpareLowPressure(car))
        assertNull(vehicleDatabase.selectSpareHighPressure(car))
    }
}
