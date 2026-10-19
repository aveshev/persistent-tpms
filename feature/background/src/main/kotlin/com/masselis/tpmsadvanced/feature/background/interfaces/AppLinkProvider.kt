package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import com.masselis.tpmsadvanced.data.app.interfaces.AppSnapshot
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp.Companion.SNAPSHOT
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp.Companion.STATUS
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp.Companion.TURN_OFF_PERSISTENT_SCANNING
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp.Companion.asBundle
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * What the other version of the app, Lite or Pro, reads and does here through [OtherApp]. Exported,
 * but only apps signed with this app's key hold its permission. Declared by app/phone's manifest,
 * which knows the permission's name.
 */
public class AppLinkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    // Runs on a binder thread, blocking it is fine
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // The manifest's permission guards openFile() and the others, but never call()
        enforcePermission()
        return with(Bindings.featureBackgroundInternal) {
            when (method) {
                STATUS -> runBlocking {
                    OtherApp.Status(
                        persistentScanning = appPreferences.persistentScanning.value,
                        databaseVersion = DatabaseExport.version,
                        latestReading = databaseExport.latestReading(),
                        hasData = databaseExport.hasData(),
                    )
                }.asBundle()

                TURN_OFF_PERSISTENT_SCANNING -> {
                    appPreferences.persistentScanning.value = false
                    controller.stop()
                    Bundle()
                }

                else -> null
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforcePermission()
        require(uri.lastPathSegment == SNAPSHOT && mode == "r") { "Cannot open $uri with mode $mode" }
        val context = checkNotNull(context)
        return File(context.cacheDir, "app-link/${UUID.randomUUID()}")
            .also { it.mkdirs() }
            .let { folder ->
                try {
                    File(folder, "database")
                        .also { runBlocking { Bindings.featureBackgroundInternal.databaseExport.exportTo(it) } }
                        .let { database ->
                            File(folder, "snapshot.zip").also { zip ->
                                zip.outputStream().use { AppSnapshot.write(context, database, it) }
                            }
                        }
                        // Still read through the descriptor once the file is deleted below
                        .let { ParcelFileDescriptor.open(it, MODE_READ_ONLY) }
                } finally {
                    folder.deleteRecursively()
                }
            }
    }

    private fun enforcePermission() = checkNotNull(context).let { context ->
        context.enforceCallingPermission(OtherApp.permission(context), "Only the other version of this app can call it")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
