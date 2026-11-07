package com.masselis.tpmsadvanced.core.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.masselis.tpmsadvanced.core.common.isLocationOn
import com.masselis.tpmsadvanced.core.common.isLocationOnFlow

@Composable
public fun rememberLocationState(): LocationState {
    val context = LocalContext.current
    val state = remember(context) { MutableLocationState(context) }
    LaunchedEffect(state) {
        context.isLocationOnFlow.collect { state.isEnabled = it }
    }
    return state
}

@Stable
private class MutableLocationState(private val context: Context) : LocationState {

    override var isEnabled: Boolean by mutableStateOf(context.isLocationOn)

    // Unlike Bluetooth, the public API has no request turning it on
    override fun openSettings() {
        context.startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
    }
}

@Stable
public interface LocationState {
    public val isEnabled: Boolean
    public fun openSettings()
}
