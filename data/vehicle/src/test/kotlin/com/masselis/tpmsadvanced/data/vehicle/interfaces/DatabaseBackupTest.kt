package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.masselis.tpmsadvanced.data.vehicle.Database
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * car.db as the app creates it, with a second vehicle made the favourite (so the favourite
 * triggers already ran once), a sensor and readings for three tyres of two vehicles.
 */
@Suppress("MaxLineLength")
internal class DatabaseBackupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: File
    private lateinit var copy: File

    @Before
    fun setup() {
        database = folder.root.resolve("car.db")
        copy = folder.root.resolve("car-backup.db")
        driver(database).use { driver ->
            // As Android creates it, opening car.db
            driver.execute(null, "CREATE TABLE android_metadata (locale TEXT)", 0)
            driver.execute(null, "INSERT INTO android_metadata VALUES ('en_US')", 0)
            Database.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = ${Database.Schema.version}", 0)
            driver.execute(null, "INSERT INTO Vehicle(uuid, kind, name, isFavourite) VALUES (X'02', 'CAR', 'Second', 1)", 0)
            driver.execute(null, "INSERT INTO Sensor(id, location, vehicleId, brand) VALUES (7, 20, X'02', 1)", 0)
            listOf(
                Triple("X'02'", 20, 1.0),
                Triple("X'02'", 20, 3.0),
                Triple("X'02'", 20, 2.0),
                Triple("X'02'", 21, 5.0),
                Triple("X'02'", 21, 4.0),
                Triple("(SELECT uuid FROM Vehicle WHERE name = 'My car')", 20, 6.0),
            ).forEach { (vehicle, location, timestamp) ->
                driver.execute(
                    null,
                    "INSERT INTO Reading(vehicleId, location, timestamp, sensorId, rssi, raw) VALUES ($vehicle, $location, $timestamp, 7, -60, X'0A0B')",
                    0
                )
            }
        }
    }

    private fun test() = driver(database).use { source ->
        with(DatabaseBackup) { source.copySlimInto(copy, ::driver) }
    }

    @Test
    fun `keeps only each tyre's latest reading`() {
        test()
        assertEquals(
            listOf("02 20 3.0", "02 21 5.0", "My car 20 6.0"),
            driver(copy).use {
                it.rows(
                    """
                        SELECT coalesce((SELECT name FROM Vehicle WHERE uuid = vehicleId AND name = 'My car'), hex(vehicleId)), location, timestamp
                        FROM Reading
                    """.trimIndent(),
                    columns = 3,
                )
            }.sorted()
        )
    }

    @Test
    fun `copies the other tables in full, the favourite included`() {
        test()
        listOf(
            "SELECT hex(uuid), name, isFavourite, kind FROM Vehicle ORDER BY name",
            "SELECT id, location, hex(vehicleId), brand FROM Sensor",
            "SELECT locale FROM android_metadata",
        ).forEach { sql ->
            assertEquals(driver(database).use { it.rows(sql) }, driver(copy).use { it.rows(sql) })
        }
        assertEquals(listOf("Second"), driver(copy).use { it.rows("SELECT name FROM Vehicle WHERE isFavourite = 1") })
    }

    @Test
    fun `has the same schema and version`() {
        test()
        listOf(
            "SELECT type, name, sql FROM sqlite_master ORDER BY name",
            "PRAGMA user_version",
        ).forEach { sql ->
            assertEquals(driver(database).use { it.rows(sql) }, driver(copy).use { it.rows(sql) })
        }
    }

    @Test
    fun `replaces an earlier copy`() {
        test()
        driver(database).use { it.execute(null, "INSERT INTO Reading VALUES (X'02', 20, 9.0, 7, -60, X'0A0B')", 0) }
        test()
        assertEquals(listOf("9.0"), driver(copy).use { it.rows("SELECT timestamp FROM Reading WHERE vehicleId = X'02' AND location = 20") })
    }

    @Test
    fun `a full copy, for a device transfer, keeps every reading`() {
        driver(database).use { source -> with(DatabaseBackup) { source.copyFullInto(copy) } }
        listOf(
            "SELECT hex(vehicleId), location, timestamp FROM Reading ORDER BY 1, 2, 3",
            "SELECT hex(uuid), name, isFavourite, kind FROM Vehicle ORDER BY name",
            "SELECT type, name, sql FROM sqlite_master ORDER BY name",
            "PRAGMA user_version",
        ).forEach { sql ->
            assertEquals(driver(database).use { it.rows(sql) }, driver(copy).use { it.rows(sql) })
        }
        assertEquals(listOf("6"), driver(copy).use { it.rows("SELECT count(*) FROM Reading") })
    }

    @Test
    fun `a restored copy becomes car_db`() {
        test()
        val bytes = copy.readBytes()
        database.delete()
        File(copy.path + "-journal").createNewFile()
        DatabaseBackup.restore(context())
        assertFalse(copy.exists())
        assertFalse(File(copy.path + "-journal").exists())
        assertContentEquals(bytes, database.readBytes())
    }

    @Test
    fun `a leftover copy doesn't replace car_db`() {
        test()
        val bytes = database.readBytes()
        DatabaseBackup.restore(context())
        assertFalse(copy.exists())
        assertContentEquals(bytes, database.readBytes())
    }

    @Test
    fun `nothing to restore leaves car_db alone`() {
        DatabaseBackup.restore(context())
        assertTrue(database.exists())
    }

    private fun context() = mockk<Context> {
        every { getDatabasePath(any()) } answers { folder.root.resolve(firstArg<String>()) }
    }

    private fun driver(file: File): SqlDriver = SingleConnectionDriver(DriverManager.getConnection("jdbc:sqlite:${file.path}"))

    /** Each row's columns joined by spaces, blobs read in hexadecimal by the query */
    private fun SqlDriver.rows(sql: String, columns: Int = sql.columns()): List<String> = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            buildList {
                while (cursor.next().value) {
                    add((0 until columns).joinToString(" ") { cursor.getString(it).orEmpty() })
                }
            }.let { QueryResult.Value(it) }
        },
        parameters = 0,
    ).value

    /**
     * One connection per database, as on Android. JdbcSqliteDriver opens one per statement for a
     * file, which loses the ATTACH between statements.
     */
    private class SingleConnectionDriver(private val connection: Connection) : JdbcDriver() {
        override fun getConnection(): Connection = connection
        override fun closeConnection(connection: Connection) = Unit
        override fun close() = connection.close()
        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun notifyListeners(vararg queryKeys: String) = Unit
    }

    // The commas between SELECT and FROM, enough for these queries
    private fun String.columns() = substringAfter("SELECT").substringBefore("FROM").count { it == ',' } + 1
}
