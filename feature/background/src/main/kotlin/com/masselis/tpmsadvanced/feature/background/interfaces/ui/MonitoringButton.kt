package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * The top bar button for background monitoring: the manual Start/Stop button, or the bell that
 * reports what persistent scanning or the ride notification is doing once either setting is on.
 */
@Composable
public fun MonitoringButton(modifier: Modifier = Modifier): Unit =
    MonitoringButton(
        modifier,
        viewModel { Bindings.featureBackgroundInternal.persistentScanningViewModel() },
    )

@Composable
internal fun MonitoringButton(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningViewModel = viewModel {
        Bindings.featureBackgroundInternal.persistentScanningViewModel()
    },
) {
    // The ride notification alone runs the service too, the bell tells what it's watching
    val inBackground by viewModel.inBackground.collectAsState()
    if (inBackground) PersistentScanningBell(viewModel, modifier)
    else BackgroundIconButton(modifier)
}
