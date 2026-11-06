package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.SharedPreferences
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.observableStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun SharedPreferences.booleanFlow(key: String, default: Boolean): MutableStateFlow<Boolean> =
    observableStateFlow(getBoolean(key, default)) { _, newValue -> edit { putBoolean(key, newValue) } }

internal fun SharedPreferences.intFlow(key: String, default: Int): MutableStateFlow<Int> =
    observableStateFlow(getInt(key, default)) { _, newValue -> edit { putInt(key, newValue) } }

internal fun SharedPreferences.floatFlow(key: String, default: Float): MutableStateFlow<Float> =
    observableStateFlow(getFloat(key, default)) { _, newValue -> edit { putFloat(key, newValue) } }

internal fun SharedPreferences.stringFlow(key: String): MutableStateFlow<String?> =
    observableStateFlow(getString(key, null)) { _, newValue -> edit { putString(key, newValue) } }

internal fun SharedPreferences.stringSetFlow(key: String): MutableStateFlow<Set<String>> =
    observableStateFlow(getStringSet(key, emptySet())!!.toSet()) { _, newValue ->
        edit { putStringSet(key, newValue) }
    }
