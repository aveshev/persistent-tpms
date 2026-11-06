package com.masselis.tpmsadvanced.core.common

import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import android.location.LocationManager.MODE_CHANGED_ACTION
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Whether the phone's Location is on: the system setting, whatever this app's permissions are */
public val Context.isLocationOn: Boolean
    // A phone without the service has nothing to turn on, nothing should wait for it
    get() = getSystemService<LocationManager>()?.isLocationEnabled ?: true

/** [isLocationOn], from now on */
public val Context.isLocationOnFlow: Flow<Boolean>
    get() = IntentFilter(MODE_CHANGED_ACTION)
        // Protected system broadcast, see asFlow()
        .asFlow(RECEIVER_EXPORTED)
        .map { isLocationOn }
        // Read just before the receiver is registered: nobody turns Location on in the time in
        // between
        .onStart { emit(isLocationOn) }
