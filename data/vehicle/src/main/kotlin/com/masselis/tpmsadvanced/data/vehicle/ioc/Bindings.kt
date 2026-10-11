package com.masselis.tpmsadvanced.data.vehicle.ioc

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.database.SQLiteOpenHelperUseCase
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.Sensor
import com.masselis.tpmsadvanced.data.vehicle.Reading
import com.masselis.tpmsadvanced.data.vehicle.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.afterVersion10
import com.masselis.tpmsadvanced.data.vehicle.interfaces.afterVersion3
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.DelicateCoroutinesApi
import java.nio.ByteBuffer
import java.util.UUID

@OptIn(DelicateCoroutinesApi::class)
@Suppress("unused")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    @SingleIn(AppScope::class)
    private fun vehicleDatabase(database: Database): VehicleDatabase = VehicleDatabase(database)

    @Provides
    private fun sensorDatabase(database: Database): SensorDatabase = SensorDatabase(database)

    @Provides
    private fun readingDatabase(database: Database): ReadingDatabase = ReadingDatabase(database)

    /** Its 16 bytes, most significant first, as `unhex()` turned the text ones into */
    @Provides
    private fun uuidAdapter(): ColumnAdapter<UUID, ByteArray> = object : ColumnAdapter<UUID, ByteArray> {
        override fun decode(databaseValue: ByteArray): UUID = ByteBuffer
            .wrap(databaseValue)
            .let { UUID(it.long, it.long) }

        override fun encode(value: UUID): ByteArray = ByteBuffer
            .allocate(UUID_BYTES)
            .putLong(value.mostSignificantBits)
            .putLong(value.leastSignificantBits)
            .array()
    }

    @Provides
    private fun brandAdapter(): ColumnAdapter<SensorBrand, Long> = object : ColumnAdapter<SensorBrand, Long> {
        override fun decode(databaseValue: Long): SensorBrand = SensorBrand.of(databaseValue)
        override fun encode(value: SensorBrand): Long = value.code
    }

    /** The advertisements are stored as bytes, and handled in hexadecimal */
    @OptIn(ExperimentalStdlibApi::class)
    @Provides
    private fun hexAdapter(): ColumnAdapter<String, ByteArray> = object : ColumnAdapter<String, ByteArray> {
        override fun decode(databaseValue: ByteArray): String = databaseValue.toHexString()
        override fun encode(value: String): ByteArray = value.hexToByteArray()
    }

    @Provides
    private fun tyreLocationAdapter(): ColumnAdapter<SensorLocation, Long> =
        object : ColumnAdapter<SensorLocation, Long> {
            override fun decode(databaseValue: Long): SensorLocation = SensorLocation.entries
                .first { it.ordinal.toLong() == databaseValue }

            override fun encode(value: SensorLocation): Long = value.ordinal.toLong()
        }

    @Provides
    private fun pressureAdapter(): ColumnAdapter<Pressure, Double> =
        object : ColumnAdapter<Pressure, Double> {
            override fun decode(databaseValue: Double): Pressure = Pressure(databaseValue.toFloat())
            override fun encode(value: Pressure): Double = value.kpa.toDouble()
        }

    @Provides
    private fun temperatureAdapter(): ColumnAdapter<Temperature, Double> =
        object : ColumnAdapter<Temperature, Double> {
            override fun decode(databaseValue: Double): Temperature =
                Temperature(databaseValue.toFloat())

            override fun encode(value: Temperature): Double = value.celsius.toDouble()
        }

    @Provides
    private fun voltageAdapter(): ColumnAdapter<Voltage, Double> =
        object : ColumnAdapter<Voltage, Double> {
            override fun decode(databaseValue: Double): Voltage = Voltage(databaseValue.toFloat())
            override fun encode(value: Voltage): Double = value.volts.toDouble()
        }

    @Provides
    private fun databaseExport(driver: SqlDriver): DatabaseExport = DatabaseExport(driver)

    @Suppress("MagicNumber")
    @Provides
    private fun locationAdapter(): ColumnAdapter<Location, Long> =
        object : ColumnAdapter<Location, Long> {

            override fun encode(value: Location): Long = when (value) {
                is Location.Axle -> 0L + value.axle.ordinal.toLong()
                is Location.Side -> 10L + value.side.ordinal
                is Location.Wheel -> 20L + value.location.ordinal
                Location.Spare -> SPARE
                Location.Single -> SINGLE
            }

            override fun decode(databaseValue: Long): Location {
                val ordinal = databaseValue.toInt() % 10
                return when (databaseValue) {
                    in 0..9 -> Location.Axle(
                        SensorLocation.Axle.entries.first { it.ordinal == ordinal }
                    )

                    in 10..19 -> Location.Side(
                        SensorLocation.Side.entries.first { it.ordinal == ordinal }
                    )

                    in 20..29 -> Location.Wheel(
                        SensorLocation.entries.first { it.ordinal == ordinal }
                    )

                    SPARE -> Location.Spare
                    SINGLE -> Location.Single

                    else -> error("Unable to parse this input $databaseValue")
                }
            }
        }

    @Provides
    @SingleIn(AppScope::class)
    private fun driver(
        useCase: SQLiteOpenHelperUseCase,
        locationAdapter: ColumnAdapter<Location, Long>
    ): SqlDriver = AndroidSqliteDriver(
        schema = Database.Schema,
        context = appContext,
        name = "car.db",
        factory = useCase.factory,
        callback = object : AndroidSqliteDriver.Callback(
            Database.Schema,
            Database.afterVersion3(locationAdapter),
            Database.afterVersion10(),
        ) {
            val delegate = AndroidSqliteDriver.Callback(Database.Schema)

            override fun onConfigure(db: SupportSQLiteDatabase) {
                delegate.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
                // Increase SQLite performance, see https://developer.android.com/topic/performance/sqlite-performance-best-practices
                db.enableWriteAheadLogging()
                db.execSQL("PRAGMA synchronous = NORMAL")
            }
        },
    )

    @Provides
    @SingleIn(AppScope::class)
    private fun database(
        driver: SqlDriver,
        uuidAdapter: ColumnAdapter<UUID, ByteArray>,
        sensorLocationAdapter: ColumnAdapter<Location, Long>,
        pressureAdapter: ColumnAdapter<Pressure, Double>,
        temperatureAdapter: ColumnAdapter<Temperature, Double>,
        voltageAdapter: ColumnAdapter<Voltage, Double>,
        brandAdapter: ColumnAdapter<SensorBrand, Long>,
        hexAdapter: ColumnAdapter<String, ByteArray>,
    ): Database = Database(
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
        SensorAdapter = Sensor.Adapter(
            IntColumnAdapter,
            sensorLocationAdapter,
            uuidAdapter,
            brandAdapter,
            pressureAdapter,
        ),
        ReadingAdapter = Reading.Adapter(
            uuidAdapter,
            sensorLocationAdapter,
            IntColumnAdapter,
            IntColumnAdapter,
            hexAdapter,
        ),
    )
}

private const val UUID_BYTES = 16

// Locations without an axle, side or wheel, see the location adapter
private const val SPARE = 30L
private const val SINGLE = 40L
