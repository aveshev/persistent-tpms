@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.text.format.DateUtils
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Crash
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.ForceStop
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Killed
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.LowMemory
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.PermissionRevoked
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Unknown
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.UnexpectedStop
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * Tells the user that persistent scanning stopped behind their back since the app was last opened,
 * and offers the settings that prevent it on the phone's brand, see [KeepAliveInstructions].
 */
@Composable
internal fun UnexpectedStopAlert(
    unexpectedStop: UnexpectedStop,
    onDismissRequest: () -> Unit,
    onLearnMore: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CommonR.string.tyre_monitoring_was_stopped)) },
        text = {
            Text(
                text = unexpectedStop
                    .stoppedAt
                    ?.let {
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), MINUTE_IN_MILLIS)
                    }
                    .let { stoppedAt ->
                        stringResource(CommonR.string.persistent_monitoring_stopped) + "${stoppedAt?.let { " $it" }.orEmpty()} " +
                                when (unexpectedStop.cause) {
                                    ForceStop -> stringResource(CommonR.string.stop_force)

                                    LowMemory -> stringResource(CommonR.string.stop_memory)
                                    Crash -> stringResource(CommonR.string.stop_crash)
                                    PermissionRevoked -> stringResource(CommonR.string.stop_permission)
                                    Killed, Unknown -> stringResource(CommonR.string.stop_unknown)
                                } +
                                stringResource(CommonR.string.stop_end)
                    }
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onLearnMore) {
                Text(text = stringResource(CommonR.string.learn_more))
            }
        },
    )
}

@Preview
@Composable
@Suppress("MagicNumber")
private fun UnexpectedStopAlertPreview() = UnexpectedStopAlert(
    UnexpectedStop(System.currentTimeMillis() - 90 * MINUTE_IN_MILLIS, ForceStop),
    onDismissRequest = {},
    onLearnMore = {},
)

@Preview
@Composable
private fun UnexpectedStopAlertUnknownPreview() =
    UnexpectedStopAlert(UnexpectedStop(null, Unknown), onDismissRequest = {}, onLearnMore = {})
