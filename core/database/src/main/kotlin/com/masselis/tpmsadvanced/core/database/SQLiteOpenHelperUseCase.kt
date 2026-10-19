package com.masselis.tpmsadvanced.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.getkeepsafe.relinker.ReLinker
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import io.requery.android.database.sqlite.SQLiteDatabase.LIBRARY_NAME
import io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import java.io.File

/** Public for the backup agent, which runs without the dependency graph */
public class SQLiteOpenHelperUseCase(context: Context) {

    private val loadNativeLibrary by lazy {
        // Replace the default System.loadLibrary(LIBRARY_NAME) which is less reliable than
        // ReLinker.loadLibrary()
        ReLinker.loadLibrary(context, LIBRARY_NAME)
    }

    public val factory: SupportSQLiteOpenHelper.Factory
        get() {
            loadNativeLibrary
            return RequerySQLiteOpenHelperFactory()
        }

    /** Opens, or creates, a database file as it is: without a schema, a version or migrations */
    public fun open(file: File): SupportSQLiteDatabase {
        loadNativeLibrary
        return SQLiteDatabase.openDatabase(file.path, null, OPEN_READWRITE or CREATE_IF_NECESSARY)
    }
}
