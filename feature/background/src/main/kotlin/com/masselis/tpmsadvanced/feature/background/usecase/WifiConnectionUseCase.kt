package com.masselis.tpmsadvanced.feature.background.usecase

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.UNKNOWN_SSID
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach

@SuppressLint("MissingPermission")
internal class WifiConnectionUseCase {

    private val logger = Logger.withTag("WifiConnectionUseCase")

    sealed interface State {
        data object Disconnected : State

        data class Connected(val ssid: String?) : State
    }

    val state: Flow<State> = callbackFlow {
        val connectivityManager = appContext.getSystemService<ConnectivityManager>()!!
        val callback = networkCallback(
            // Called again whenever anything about the network changes, its signal strength
            // every few seconds: only a change of state is told below
            onCapabilitiesChanged = { trySend(State.Connected(it.wifiSsid())) },
            onLost = { trySend(State.Disconnected) },
        )
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI).build(),
            callback,
        )
        // The callback reports a network that already exists right after being registered, and
        // reports nothing at all when there is none. Only that second case must be announced:
        // saying "disconnected" first would be wrong for a moment on a connected phone.
        if (connectivityManager.hasWifiNetwork().not()) send(State.Disconnected)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .onEach { state ->
            when (state) {
                is State.Connected -> logger.d { "Connected to a WiFi, its name is readable: ${state.ssid != null}" }
                State.Disconnected -> logger.d { "Not connected to a WiFi" }
            }
        }

    // Since API 31, NetworkCapabilities redacts location-sensitive transport info (including the
    // WiFi SSID) by default, even when the caller holds the permission and location is enabled —
    // FLAG_INCLUDE_LOCATION_INFO on the callback itself is required to opt back in. The flags
    // constructor doesn't exist before API 31, and the transport info of the older callbacks
    // never carries the SSID (confirmed on-device on API 29, see wifiSsid()). The two
    // constructors are called from two distinct anonymous classes on purpose: NetworkCallback(Int)
    // doesn't exist pre-31, so a single call site guarded only by a runtime SDK_INT check would
    // still fail to resolve on older devices.
    private fun networkCallback(
        onCapabilitiesChanged: (NetworkCapabilities) -> Unit,
        onLost: () -> Unit,
    ): NetworkCallback = if (SDK_INT >= S) {
        object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = onCapabilitiesChanged(networkCapabilities)

            override fun onLost(network: Network) = onLost()
        }
    } else {
        object : NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = onCapabilitiesChanged(networkCapabilities)

            override fun onLost(network: Network) = onLost()
        }
    }

    @Suppress("DEPRECATION")
    private fun ConnectivityManager.hasWifiNetwork() = allNetworks
        .any { getNetworkCapabilities(it)?.hasTransport(TRANSPORT_WIFI) == true }

    // Before API 31 the transport info of a network callback is stripped of the SSID whatever the
    // permissions are (confirmed on-device on API 29), and it doesn't even exist before API 29.
    // WifiManager does return it when the location permission is held.
    @Suppress("DEPRECATION")
    private fun NetworkCapabilities.wifiSsid(): String? = when {
        SDK_INT >= S -> (transportInfo as? WifiInfo)?.ssid
        else -> appContext.getSystemService<WifiManager>()?.connectionInfo?.ssid
    }
        ?.removeSurrounding("\"")
        ?.takeUnless { it == UNKNOWN_SSID }

    // ACCESS_FINE_LOCATION is what unredacts the SSID on every API level (confirmed on-device:
    // NEARBY_WIFI_DEVICES alone was NOT sufficient on API 33+). It only does so while the app is
    // visible though: reading the name from the service, in the background or right after a boot,
    // needs ACCESS_BACKGROUND_LOCATION too (confirmed on-device, "while using the app" is not
    // enough). Below API 29 there is no separate background permission.
    fun requiredPermissions(): List<String> = when {
        SDK_INT >= Q -> listOf(ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION)
        else -> listOf(ACCESS_FINE_LOCATION)
    }
}
