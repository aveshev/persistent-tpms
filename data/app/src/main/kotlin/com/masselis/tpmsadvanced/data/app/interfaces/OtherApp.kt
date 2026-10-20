package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The other version of this app, Pro beside Lite or Lite beside Pro, of the same brand: its ID comes
 * from the manifest, which app/phone's build fills. Each one exposes a provider (`AppLinkProvider`,
 * declared by app/phone) which only apps signed with the same key can use, through [permission]:
 * both declare it, so it's granted whichever is installed first.
 * Through it, this app reads whether persistent scanning is on there, turns it off there, and
 * downloads its data, see [AppSnapshot].
 */
public class OtherApp internal constructor(
    private val context: Context,
) {
    private val logger = Logger.withTag("OtherApp")

    public val packageName: String = context.metaData(OTHER_APPLICATION_ID)

    /** Its name, null while it isn't installed */
    public fun label(): String? = context.packageManager.let { pm ->
        try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: NameNotFoundException) {
            null
        }
    }

    /**
     * Null while it isn't installed, or can't tell: a build from before the provider, or one signed
     * with another key
     */
    public suspend fun status(): Status? = call(STATUS)?.let { bundle ->
        Status(
            persistentScanning = bundle.getBoolean(KEY_PERSISTENT_SCANNING),
            databaseVersion = bundle.getLong(KEY_DATABASE_VERSION),
            latestReading = bundle
                .getDouble(KEY_LATEST_READING, Double.NaN)
                .takeIf { it.isNaN().not() },
            hasData = bundle.getBoolean(KEY_HAS_DATA),
        )
    }

    /** Turns persistent scanning off there and stops its service. @return false when it couldn't */
    public suspend fun turnOffPersistentScanning(): Boolean = call(TURN_OFF_PERSISTENT_SCANNING) != null

    /** Its database and settings, as an [AppSnapshot]. Null when it can't be read. */
    public suspend fun openSnapshot(): InputStream? = withContext(IO) {
        runCatching {
            uri(packageName)
                .buildUpon()
                .appendPath(SNAPSHOT)
                .build()
                .let(context.contentResolver::openInputStream)
        }
            .onFailure { logger.w(it) { "Cannot read the snapshot of $packageName" } }
            .getOrNull()
    }

    private suspend fun call(method: String): Bundle? = withContext(IO) {
        // A missing provider or permission throws, both mean that the other app can't be reached
        runCatching { context.contentResolver.call(uri(packageName), method, null, null) }
            .onFailure { logger.w(it) { "Cannot call $method on $packageName" } }
            .getOrNull()
    }

    public data class Status(
        val persistentScanning: Boolean,
        /** Its database's schema version, a higher one than this build's can't be imported */
        val databaseVersion: Long,
        /** In seconds since the epoch, as the readings store it. Null without any reading. */
        val latestReading: Double?,
        /** Any reading or sensor */
        val hasData: Boolean,
    )

    /** What the provider (`AppLinkProvider`) answers to */
    public companion object {
        public const val STATUS: String = "status"
        public const val TURN_OFF_PERSISTENT_SCANNING: String = "turnOffPersistentScanning"
        public const val SNAPSHOT: String = "snapshot"

        private const val KEY_PERSISTENT_SCANNING = "persistentScanning"
        private const val KEY_DATABASE_VERSION = "databaseVersion"
        private const val KEY_LATEST_READING = "latestReading"
        private const val KEY_HAS_DATA = "hasData"

        // Both set by app/phone's build, which names each brand's Lite and Pro
        private const val OTHER_APPLICATION_ID = "com.masselis.tpmsadvanced.OTHER_APPLICATION_ID"
        private const val APP_LINK_PERMISSION = "com.masselis.tpmsadvanced.APP_LINK_PERMISSION"

        /** The provider's address in the app [packageName] */
        public fun uri(packageName: String): Uri = Uri.parse("content://$packageName.app-link")

        /** The signature permission guarding the provider, the same in both apps of a brand */
        public fun permission(context: Context): String = context.metaData(APP_LINK_PERMISSION)

        private fun Context.metaData(name: String): String = packageManager
            .getApplicationInfo(packageName, GET_META_DATA)
            .metaData
            ?.getString(name)
            .let { checkNotNull(it) { "The manifest has no $name" } }

        public fun Status.asBundle(): Bundle = bundleOf(
            KEY_PERSISTENT_SCANNING to persistentScanning,
            KEY_DATABASE_VERSION to databaseVersion,
            KEY_HAS_DATA to hasData,
        ).apply { latestReading?.also { putDouble(KEY_LATEST_READING, it) } }
    }
}
