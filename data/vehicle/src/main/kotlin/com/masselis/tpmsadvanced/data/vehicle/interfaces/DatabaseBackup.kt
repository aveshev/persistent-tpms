package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.masselis.tpmsadvanced.core.database.SQLiteOpenHelperUseCase
import java.io.File

/**
 * car.db for Android's Auto Backup, which stops backing an app up once its data passes 25 MB: a
 * copy with every table, but only each tyre's latest reading. It's built when Android takes a
 * backup, and backed up instead of car.db (see the backup rules). On a fresh install, the restored
 * copy becomes car.db before it opens, and the migrations then run on it as on any older database.
 *
 * Takes a [Context] rather than the dependency graph: the backup agent runs in a process started
 * without the app's initializers.
 */
public object DatabaseBackup {

    internal const val DATABASE: String = "car.db"
    private const val COPY = "car-backup.db"

    /** Builds the copy next to car.db for the backup to take, nothing without car.db */
    public fun build(context: Context): Unit = SQLiteOpenHelperUseCase(context).let { sqlite ->
        context
            .getDatabasePath(DATABASE)
            .takeIf { it.exists() }
            ?.let { AndroidSqliteDriver(sqlite.open(it)) }
            ?.use { it.copySlimInto(context.getDatabasePath(COPY)) { file -> AndroidSqliteDriver(sqlite.open(file)) } }
    }

    /** Once backed up, the copy is only taking room */
    public fun delete(context: Context) {
        context.getDatabasePath(COPY).deleteWithJournals()
    }

    /** Before car.db opens: a restored copy becomes car.db if there's none, a leftover one is dropped */
    internal fun restore(context: Context) {
        context
            .getDatabasePath(COPY)
            .takeIf { it.exists() }
            ?.also { copy ->
                context
                    .getDatabasePath(DATABASE)
                    .takeIf { it.exists().not() }
                    ?.also { database ->
                        // A write-ahead log without its database would be applied to the copy
                        JOURNALS.forEach { File(database.path + it).delete() }
                        check(copy.renameTo(database)) { "Unable to restore $copy as $database" }
                        // Its journal, empty, is backed up along with it
                        copy.deleteWithJournals()
                    }
                    ?: copy.delete()
            }
    }

    /**
     * Builds the copy from scratch rather than copying car.db and deleting from it, which would
     * write and then vacuum the whole readings table. Its schema is car.db's own, from
     * `sqlite_master`, at car.db's `user_version`: car.db may not be migrated yet (an update not
     * launched since) and `Schema.create` would also insert the default vehicle. The triggers come
     * after the rows, so copying the vehicles doesn't fire the favourite ones.
     */
    @Suppress("MaxLineLength")
    internal fun SqlDriver.copySlimInto(copy: File, open: (File) -> SqlDriver) {
        val version = executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) },
            parameters = 0,
        ).value
        copy.deleteWithJournals()
        copy.parentFile?.mkdirs()
        // Without what the driver creates on its own in the copy, such as Android's android_metadata
        val schema = open(copy).use { target ->
            target
                .schema()
                .map { it.name }
                .let { created -> schema().filter { it.name !in created } }
                .also { schema ->
                    schema
                        .filter { it.type == "table" || it.type == "index" }
                        .sortedBy { it.type != "table" }
                        .forEach { target.execute(null, it.sql, 0) }
                }
        }
        execute(null, "ATTACH DATABASE ? AS backup", 1) { bindString(0, copy.path) }
        try {
            schema
                .filter { it.type == "table" }
                .forEach {
                    execute(
                        identifier = null,
                        sql = if (it.name == "Reading") {
                            // The primary key (vehicleId, location, timestamp) makes it a lookup per tyre
                            """
                                INSERT INTO backup.Reading SELECT * FROM main.Reading AS latest
                                WHERE latest.timestamp = (SELECT MAX(timestamp) FROM main.Reading WHERE vehicleId = latest.vehicleId AND location = latest.location)
                            """.trimIndent()
                        } else {
                            "INSERT INTO backup.\"${it.name}\" SELECT * FROM main.\"${it.name}\""
                        },
                        parameters = 0,
                    )
                }
        } finally {
            execute(null, "DETACH DATABASE backup", 0)
        }
        open(copy).use { target ->
            schema
                .filter { it.type == "trigger" || it.type == "view" }
                .forEach { target.execute(null, it.sql, 0) }
            target.execute(null, "PRAGMA user_version = $version", 0)
        }
    }

    @Suppress("MaxLineLength")
    private fun SqlDriver.schema(): List<SchemaEntry> = executeQuery(
        identifier = null,
        sql = "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'",
        mapper = { cursor ->
            buildList {
                while (cursor.next().value) add(SchemaEntry(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2)!!))
            }.let { QueryResult.Value(it) }
        },
        parameters = 0,
    ).value

    private data class SchemaEntry(val type: String, val name: String, val sql: String)

    private val JOURNALS = listOf("-wal", "-shm", "-journal")

    private fun File.deleteWithJournals() = (JOURNALS + "").forEach { File(path + it).delete() }
}
