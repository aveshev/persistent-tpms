@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.ui.BluetoothState
import com.masselis.tpmsadvanced.core.ui.LocationState
import com.masselis.tpmsadvanced.core.ui.MissingPermission
import com.masselis.tpmsadvanced.core.ui.rememberBluetoothState
import com.masselis.tpmsadvanced.core.ui.rememberLocationState
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.PreconditionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.PreconditionsViewModel
import dev.zacsweers.metro.Assisted
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

@Composable
public fun Preconditions(
    @Assisted modifier: Modifier = Modifier,
    @Assisted ready: @Composable () -> Unit,
) {
    InternalPreconditions(
        ready = ready,
        modifier = modifier
    )
}

@Suppress("ModifierNotUsedAtRoot")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun InternalPreconditions(
    @Assisted ready: @Composable () -> Unit,
    @Assisted modifier: Modifier = Modifier,
    viewModel: PreconditionsViewModel = viewModel { PreconditionsViewModel() }
) {
    val permissionState = rememberMultiplePermissionsState(viewModel.requiredPermission())
    val bluetoothState = rememberBluetoothState()
    val locationState = rememberLocationState()
    when {
        permissionState.allPermissionsGranted.not() -> Scaffold { padding ->
            // Bluetooth scan permission is granted under "Nearby devices" on API 31+, but under
            // "Location" on older versions (see BluetoothLeScanner.missingPermission()) — match
            // whichever label the system's own Settings screen actually uses.
            val permissionName = if (SDK_INT >= S) stringResource(CommonR.string.nearby_devices) else stringResource(CommonR.string.location)
            @Suppress("MaxLineLength")
            MissingPermission(
                text = stringResource(CommonR.string.persistent_tpms_needs_some_permission_to_continue),
                refusedText = stringResource(CommonR.string.this_app_requires_permission_in_order_to, permissionName),
                permissionState = permissionState,
                autoRequest = true,
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }

        viewModel.isBluetoothRequired() && bluetoothState.isEnabled.not() -> Scaffold { padding ->
            ChipIsOff(
                bluetoothState = bluetoothState,
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }

        viewModel.isLocationRequired() && locationState.isEnabled.not() -> Scaffold { padding ->
            LocationIsOff(
                locationState = locationState,
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }

        else -> ready()
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ChipIsOff(
    bluetoothState: BluetoothState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            @Suppress("MaxLineLength")
            stringResource(CommonR.string.persistent_tpms_needs_the_phone_s_bluetooth),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(bottom = 8.dp)
        )
        FilledTonalButton(onClick = {
            bluetoothState.askEnable()
        }) {
            Text(stringResource(CommonR.string.enable_bluetooth))
        }
    }
}

@Composable
private fun LocationIsOff(
    locationState: LocationState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            @Suppress("MaxLineLength")
            stringResource(CommonR.string.persistent_tpms_needs_the_phone_s_location),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(bottom = 8.dp)
        )
        FilledTonalButton(onClick = locationState::openSettings) {
            Text(stringResource(CommonR.string.open_location_settings))
        }
    }
}
