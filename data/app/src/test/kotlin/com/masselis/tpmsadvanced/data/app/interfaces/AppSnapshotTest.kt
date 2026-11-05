package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AppSnapshotTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** An app, with its own folders and settings */
    private inner class App(name: String) {
        val root: File = folder.newFolder(name)
        val database = File(root, "databases/car.db")
        val staging = File(root, "no_backup/import")
        val settings = mutableMapOf<String, MutableMap<String, Any?>>()

        val context = mockk<Context> {
            every { noBackupFilesDir } returns File(root, "no_backup")
            every { getSharedPreferences(any(), MODE_PRIVATE) } answers {
                preferences(settings.getOrPut(firstArg()) { mutableMapOf() })
            }
        }

        fun holds(content: String) = database.also { it.parentFile?.mkdirs() }.writeText(content)

        fun stored(name: String): Map<String, Any?>? = settings[name]

        private fun preferences(values: MutableMap<String, Any?>): SharedPreferences {
            val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
            fun put(key: String, value: Any?) = editor.also { values[key] = value }
            editor.apply {
                every { putBoolean(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { putInt(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { putLong(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { putFloat(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { putString(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { putStringSet(any(), any()) } answers { put(firstArg(), secondArg()) }
                every { clear() } answers {
                    values.clear()
                    editor
                }
                every { commit() } returns true
            }
            return mockk {
                every { edit() } returns editor
                every { all } answers { values.toMap() }
            }
        }
    }

    private lateinit var source: App
    private lateinit var target: App

    @Before
    fun setup() {
        source = App("source")
        target = App("target")
    }

    private fun snapshot(): InputStream = ByteArrayOutputStream()
        .also { AppSnapshot.write(source.context, source.database, it) }
        .toByteArray()
        .inputStream()

    private fun zip(vararg entries: Pair<String, String>): InputStream = ByteArrayOutputStream()
        .also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }
        .toByteArray()
        .inputStream()

    private fun stageAndCommit(input: InputStream) {
        AppSnapshot.stage(target.context, input)
        AppSnapshot.commitStaged(target.context, "The other app")
    }

    private fun import(input: InputStream, tier: AppTier = AppTier.PRO) {
        stageAndCommit(input)
        AppSnapshot.applyStaged(target.context, target.database, tier)
    }

    @Test
    fun `an app's database comes back on another one`() {
        source.holds("The source's database")
        import(snapshot())
        assertEquals("The source's database", target.database.readText())
    }

    @Test
    fun `every type of setting comes back as it was`() {
        source.holds("")
        val app = mapOf(
            "BOOLEAN" to true,
            "INT" to -85,
            "LONG" to Long.MAX_VALUE,
            "FLOAT" to 0.1f,
            "STRING" to "Text with \"quotes\" and é",
            "SET" to setOf("AA:BB", "CC:DD"),
            "EMPTY_SET" to emptySet<String>(),
        )
        source.settings["APP"] = app.toMutableMap()
        import(snapshot())
        assertEquals(app, target.stored("APP"))
    }

    @Test
    fun `the units and the scanner's settings come too`() {
        source.holds("")
        source.settings["UNITS"] = mutableMapOf("PRESSURE" to "BAR")
        source.settings[SCANNER] = mutableMapOf("IS_DEMO" to true)
        import(snapshot())
        assertEquals(mapOf<String, Any?>("PRESSURE" to "BAR"), target.stored("UNITS"))
        assertEquals(mapOf<String, Any?>("IS_DEMO" to true), target.stored(SCANNER))
    }

    @Test
    fun `settings holding the running app's state stay on each side`() {
        source.holds("")
        source.settings["SILENCE"] = mutableMapOf("UNTIL" to 1L)
        target.settings["SILENCE"] = mutableMapOf("UNTIL" to 2L)
        import(snapshot())
        assertEquals(mapOf<String, Any?>("UNTIL" to 2L), target.stored("SILENCE"))
    }

    @Test
    fun `imported settings replace those of the app, they aren't merged`() {
        source.holds("")
        source.settings["APP"] = mutableMapOf("THEIRS" to true)
        target.settings["APP"] = mutableMapOf("OURS" to true)
        target.settings["UNITS"] = mutableMapOf("PRESSURE" to "PSI")
        import(snapshot())
        assertEquals(mapOf<String, Any?>("THEIRS" to true), target.stored("APP"))
        assertEquals(emptyMap(), target.stored("UNITS"))
    }

    @Test
    fun `the former database goes with its logs`() {
        source.holds("Theirs")
        target.holds("Ours")
        val logs = listOf("-wal", "-shm", "-journal").map { File(target.database.path + it) }
        logs.forEach { it.writeText("Ours") }
        import(snapshot())
        assertEquals("Theirs", target.database.readText())
        logs.forEach { assertFalse(it.exists(), it.name) }
    }

    @Test
    fun `Lite leaves out what it has no use for`() {
        source.holds("")
        source.settings["APP"] = mutableMapOf(
            "SUSPEND_SCANNING_ON_BLUETOOTH" to true,
            "SUSPEND_BLUETOOTH_DEVICES" to setOf("AA:BB"),
            "BEACON_OVERRIDES_SUSPEND" to true,
            "SHOW_DETECTED_ACTIVITIES" to true,
            "KEPT" to true,
        )
        import(snapshot(), AppTier.LITE)
        assertEquals(mapOf<String, Any?>("KEPT" to true), target.stored("APP"))
    }

    @Test
    fun `Pro keeps everything`() {
        source.holds("")
        val app = mapOf("SUSPEND_SCANNING_ON_BLUETOOTH" to true, "KEPT" to true)
        source.settings["APP"] = app.toMutableMap()
        import(snapshot(), AppTier.PRO)
        assertEquals(app, target.stored("APP"))
    }

    @Test
    fun `staging tells the database for the caller to check`() {
        source.holds("Theirs")
        assertEquals("Theirs", AppSnapshot.stage(target.context, snapshot()).readText())
    }

    @Test
    fun `a snapshot without a database is refused`() {
        assertFailsWith<IllegalStateException> {
            AppSnapshot.stage(target.context, zip("preferences.json" to "{}"))
        }
    }

    @Test
    fun `nothing is applied until it's committed`() {
        source.holds("Theirs")
        source.settings["APP"] = mutableMapOf("THEIRS" to true)
        target.holds("Ours")
        target.settings["APP"] = mutableMapOf("OURS" to true)
        AppSnapshot.stage(target.context, snapshot())
        AppSnapshot.applyStaged(target.context, target.database, AppTier.PRO)
        assertEquals("Ours", target.database.readText())
        assertEquals(mapOf<String, Any?>("OURS" to true), target.stored("APP"))
        assertNull(AppSnapshot.consumeImported(target.context))
    }

    @Test
    fun `an import left half staged is dropped`() {
        source.holds("Theirs")
        AppSnapshot.stage(target.context, snapshot())
        AppSnapshot.applyStaged(target.context, target.database, AppTier.PRO)
        assertFalse(target.staging.exists())
    }

    @Test
    fun `what's discarded isn't applied`() {
        source.holds("Theirs")
        target.holds("Ours")
        stageAndCommit(snapshot())
        AppSnapshot.discardStaged(target.context)
        AppSnapshot.applyStaged(target.context, target.database, AppTier.PRO)
        assertEquals("Ours", target.database.readText())
    }

    @Test
    fun `staging again replaces what was staged`() {
        source.holds("Theirs")
        source.settings["APP"] = mutableMapOf("THEIRS" to true)
        target.settings["APP"] = mutableMapOf("OURS" to true)
        AppSnapshot.stage(target.context, snapshot())
        import(zip("database" to "Another"))
        assertEquals("Another", target.database.readText())
        assertEquals(mapOf<String, Any?>("OURS" to true), target.stored("APP"))
    }

    @Test
    fun `an entry which isn't the snapshot's never leaves the staging folder`() {
        AppSnapshot.stage(
            target.context,
            zip("../out" to "Evil", "../../out" to "Evil", "other" to "Evil", "database" to "Theirs"),
        )
        assertEquals(listOf("database"), target.staging.list()?.toList())
        assertFalse(File(target.root, "no_backup/out").exists())
        assertFalse(File(target.root, "out").exists())
    }

    @Test
    fun `an import interrupted once its database moved applies its settings again`() {
        source.holds("Theirs")
        source.settings["APP"] = mutableMapOf("THEIRS" to true)
        stageAndCommit(snapshot())
        // The database is already there, the process died right after moving it
        File(target.staging, "database").delete()
        target.holds("Theirs")
        AppSnapshot.applyStaged(target.context, target.database, AppTier.PRO)
        assertEquals("Theirs", target.database.readText())
        assertEquals(mapOf<String, Any?>("THEIRS" to true), target.stored("APP"))
        assertEquals("The other app", AppSnapshot.consumeImported(target.context))
    }

    @Test
    fun `a snapshot without settings leaves them`() {
        target.settings["APP"] = mutableMapOf("OURS" to true)
        import(zip("database" to "Theirs"))
        assertEquals(mapOf<String, Any?>("OURS" to true), target.stored("APP"))
    }

    @Suppress("MaxLineLength")
    @Test
    fun `a type of setting from a newer app is left out`() {
        import(
            zip(
                "database" to "Theirs",
                "preferences.json" to """{"APP":{"NEW":{"type":"fromTheFuture","value":1},"KEPT":{"type":"int","value":2}}}""",
            )
        )
        assertEquals(mapOf<String, Any?>("KEPT" to 2), target.stored("APP"))
    }

    @Test
    fun `the app tells once which app the data came from`() {
        source.holds("")
        import(snapshot())
        assertEquals("The other app", AppSnapshot.consumeImported(target.context))
        assertNull(AppSnapshot.consumeImported(target.context))
    }

    @Test
    fun `no import tells nothing`() {
        AppSnapshot.applyStaged(target.context, target.database, AppTier.PRO)
        assertNull(AppSnapshot.consumeImported(target.context))
        assertFalse(target.database.exists())
    }

    @Test
    fun `nothing is left staged once applied`() {
        source.holds("")
        import(snapshot())
        assertFalse(target.staging.exists())
        assertTrue(target.database.exists())
    }

    private companion object {
        const val SCANNER = "com.masselis.tpmsadvanced.data.vehicle.interfaces.ScannerDatabase"
    }
}
