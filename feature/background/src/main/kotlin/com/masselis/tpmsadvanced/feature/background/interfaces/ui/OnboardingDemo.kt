package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The top bar's [MonitoringButton] as the introduction shows it, doing nothing when tapped: the
 * monitor mode button, or with [persistent] the bell of persistent mode scanning for a detected
 * vehicle, blue
 */
@Composable
public fun DemoMonitoringButton(persistent: Boolean, modifier: Modifier = Modifier) {
    if (persistent) PersistentScanningBell(state = BellState.Bluetooth, onClick = {}, modifier = modifier)
    else IconButton(onClick = {}, modifier = modifier) {
        Icon(
            ImageVector.vectorResource(R.drawable.format_vertical_align_center),
            contentDescription = stringResource(CommonR.string.enable_background_monitoring),
        )
    }
}

/** The menu's [PersistentScanningMenuItem] as the introduction shows it: on, doing nothing when tapped */
@Composable
public fun DemoPersistentScanningMenuItem(modifier: Modifier = Modifier) {
    PersistentScanningMenuItem(
        state = BellState.Bluetooth,
        setUp = true,
        onPersistentScanning = {},
        onClick = {},
        modifier = modifier,
    )
}
