package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * All of an app's data in one stream, a zip, which [OtherApp] downloads to import it: the database,
 * copied consistently by the app which sends it, and the settings. The settings are read from
 * memory rather than copied as files, which can lag behind the latest `apply()`.
 *
 * Imported data replaces this app's, it's never merged. It's staged first, then swapped in by
 * [applyStaged] when the app starts again, before anything opens the database or the settings.
 */
public object AppSnapshot {

    /**
     * The settings carried over, those the user set up. The others hold the state of the running
     * app (silenced alerts, the service's marker...) and stay as they are.
     */
    private val preferences = listOf(
        "APP",
        "UNITS",
        "com.masselis.tpmsadvanced.data.vehicle.interfaces.ScannerDatabase",
    )

    /**
     * [AppPreferences] keys Lite has no use for, not imported there: the devices Pro suspends on
     * would stay out of reach in Lite's device picker, and the beacon override has no option there
     */
    private val proOnlyPreferences = setOf(
        "SUSPEND_SCANNING_ON_BLUETOOTH",
        "SUSPEND_BLUETOOTH_DEVICES",
        "BEACON_OVERRIDES_SUSPEND",
    )

    private const val DATABASE = "database"
    private const val PREFERENCES = "preferences.json"

    /** Set once everything is staged, so that a half-written import is never applied */
    private const val READY = "ready"

    public fun write(context: Context, database: File, output: OutputStream): Unit =
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(DATABASE))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PREFERENCES))
            preferences
                .fold(JSONObject()) { json, name ->
                    json.put(name, context.getSharedPreferences(name, MODE_PRIVATE).asJson())
                }
                .toString()
                .toByteArray()
                .also(zip::write)
            zip.closeEntry()
        }

    /**
     * Unzips [input] into the staging folder, replacing anything staged before.
     * @return the staged database, for the caller to check that it can open it
     */
    public fun stage(context: Context, input: InputStream): File = context
        .staging()
        .also { it.deleteRecursively() }
        .also { it.mkdirs() }
        .also { staging ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    // Only what write() wrote, never a path out of the staging folder
                    .filter { it.name == DATABASE || it.name == PREFERENCES }
                    .forEach { entry -> File(staging, entry.name).outputStream().use { zip.copyTo(it) } }
            }
        }
        .let { File(it, DATABASE) }
        .also { check(it.exists()) { "The snapshot has no database" } }

    /** The staged data replaces this app's on its next start, see [consumeImported] for [from] */
    public fun commitStaged(context: Context, from: String) {
        File(context.staging(), READY).writeText(from)
    }

    /**
     * The name of the app whose data was swapped in by [applyStaged], once, to tell the user: the
     * app restarts looking like that app, which can't be told apart from nothing happening
     */
    public fun consumeImported(context: Context): String? = context
        .imported()
        .takeIf { it.exists() }
        ?.let { file -> file.readText().also { file.delete() } }

    public fun discardStaged(context: Context) {
        context.staging().deleteRecursively()
    }

    /**
     * Swaps the committed data in, if any. Must run before anything opens [database] or the
     * settings, which are only read from disk the first time they're opened.
     */
    public fun applyStaged(context: Context, database: File, tier: AppTier = AppTier.current) {
        context
            .staging()
            .takeIf { File(it, READY).exists() }
            ?.also { staging ->
                // Gone once moved: an import interrupted after this point only applies the settings again
                File(staging, DATABASE)
                    .takeIf { it.exists() }
                    ?.also { staged ->
                        listOf("", "-wal", "-shm", "-journal").forEach { File(database.path + it).delete() }
                        database.parentFile?.mkdirs()
                        check(staged.renameTo(database)) { "Cannot move the imported database in place" }
                    }
                File(staging, PREFERENCES)
                    .takeIf { it.exists() }
                    ?.let { JSONObject(it.readText()) }
                    ?.also { json ->
                        preferences.forEach { name ->
                            context.getSharedPreferences(name, MODE_PRIVATE).edit(commit = true) {
                                clear()
                                json.optJSONObject(name)?.also { values ->
                                    values
                                        .keys()
                                        .asSequence()
                                        .filter { key -> tier != AppTier.LITE || key !in proOnlyPreferences }
                                        .forEach { key -> put(key, values.getJSONObject(key)) }
                                }
                            }
                        }
                    }
                File(staging, READY).copyTo(context.imported(), overwrite = true)
            }
        // Also drops an import left half-staged by a process which died
        context.staging().deleteRecursively()
    }

    private fun Context.staging() = File(noBackupFilesDir, "import")

    private fun Context.imported() = File(noBackupFilesDir, "imported")

    private fun SharedPreferences.asJson(): JSONObject = all.entries.fold(JSONObject()) { json, (key, value) ->
        when (value) {
            is Boolean -> "boolean" to value
            is Int -> "int" to value
            is Long -> "long" to value
            is Float -> "float" to value.toDouble()
            is String -> "string" to value
            is Set<*> -> "stringSet" to JSONArray(value)
            else -> null
        }?.let { (type, typed) -> json.put(key, JSONObject().put("type", type).put("value", typed)) } ?: json
    }

    private fun SharedPreferences.Editor.put(key: String, typed: JSONObject) {
        when (typed.getString("type")) {
            "boolean" -> putBoolean(key, typed.getBoolean("value"))
            "int" -> putInt(key, typed.getInt("value"))
            "long" -> putLong(key, typed.getLong("value"))
            "float" -> putFloat(key, typed.getDouble("value").toFloat())
            "string" -> putString(key, typed.getString("value"))
            "stringSet" -> typed
                .getJSONArray("value")
                .let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                .also { putStringSet(key, it) }
        }
    }
}
