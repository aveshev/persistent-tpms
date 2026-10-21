package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import app.cash.sqldelight.db.SqlDriver
import com.masselis.tpmsadvanced.data.vehicle.Database
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies the whole database out of the app, to look into its readings elsewhere or to import it into
 * the other version of the app (Lite or Pro)
 */
public class DatabaseExport internal constructor(
    private val driver: SqlDriver,
    private val database: Database,
) {

    /**
     * A consistent copy, safe to take while the scanner writes: unlike copying the file, VACUUM INTO
     * also takes what still sits in the write-ahead log. The app's bundled SQLite supports it on
     * every Android version.
     */
    public suspend fun exportTo(file: File): Unit = withContext(IO) {
        // VACUUM INTO refuses to overwrite a file
        file.parentFile?.mkdirs()
        file.delete()
        driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, file.path) }
    }

    /** When the latest reading was stored, in seconds since the epoch. Null without any reading. */
    public suspend fun latestReading(): Double? = withContext(IO) {
        database.readingQueries.latestTimestamp().executeAsOne().timestamp
    }

    /** Any reading or sensor: a freshly installed app has neither */
    public suspend fun hasData(): Boolean = withContext(IO) {
        latestReading() != null || database.sensorQueries.countAll().executeAsOne() > 0
    }

    public companion object {
        /** The database's file name, in the app's databases folder */
        public const val FILE_NAME: String = DatabaseBackup.DATABASE

        /** This build's schema version: a database of a higher one can't be opened */
        public val version: Long get() = Database.Schema.version

        /** The schema version of the database [file], which isn't open */
        public fun versionOf(file: File): Long = SQLiteDatabase
            .openDatabase(file.path, null, OPEN_READONLY)
            .use { it.version.toLong() }

        public fun file(context: Context): File = context.getDatabasePath(FILE_NAME)
    }
}
