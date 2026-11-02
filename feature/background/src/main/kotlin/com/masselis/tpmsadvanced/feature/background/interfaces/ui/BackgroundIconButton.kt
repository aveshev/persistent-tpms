package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel.Event
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel.State
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

@Composable
internal fun BackgroundIconButton(
    modifier: Modifier = Modifier,
    viewModel: BackgroundViewModel = viewModel { Bindings.featureBackgroundInternal.backgroundViewModel() },
) {
    val state by viewModel.stateFlow.collectAsState()
    val activity = LocalActivity.current
    val permissions = rememberMonitoringPermissions(
        confirmBeforeStart = true,
        onGranted = { viewModel.monitor() },
    )

    AnimatedContent(state) { state ->
        when (state) {
            State.Idle -> IconButton(
                onClick = permissions::request,
                modifier.testTag("put_in_background_button")
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.format_vertical_align_center),
                    contentDescription = stringResource(CommonR.string.enable_background_monitoring),
                )
            }

            State.Monitoring -> IconButton(
                onClick = viewModel::disableMonitoring,
                modifier.testTag("cancel_background_button")
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.cancel),
                    contentDescription = stringResource(CommonR.string.cancel_background_monitoring),
                )
            }
        }
    }
    LaunchedEffect(viewModel.eventChannel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                Event.FinishActivity -> activity!!.finish()
            }
        }
    }
}
