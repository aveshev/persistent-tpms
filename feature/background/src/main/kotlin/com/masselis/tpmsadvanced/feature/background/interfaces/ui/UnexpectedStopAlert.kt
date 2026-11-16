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
        title = { Text(text = "Tyre monitoring was stopped") },
        text = {
            Text(
                text = unexpectedStop
                    .stoppedAt
                    ?.let {
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), MINUTE_IN_MILLIS)
                    }
                    .let { stoppedAt ->
                        "Persistent monitoring stopped${stoppedAt?.let { " $it" }.orEmpty()} " +
                                when (unexpectedStop.cause) {
                                    ForceStop -> "because the app was force-stopped, by you or " +
                                            "by your phone's power management."

                                    LowMemory -> "because your phone ran low on memory."
                                    Crash -> "because the app crashed."
                                    PermissionRevoked -> "because a permission was revoked."
                                    Killed, Unknown -> "without being turned off, most likely " +
                                            "by your phone's power management."
                                } +
                                " It starts again now that the app is open.\n\n" +
                                "Some phones stop background apps even when their battery " +
                                "usage is unrestricted. Learn more to find the settings your " +
                                "phone needs to keep monitoring running."
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
                Text(text = "Learn more")
            }
        },
    )
}

@Preview
@Composable
@Suppress("MagicNumber")
internal fun UnexpectedStopAlertPreview() = UnexpectedStopAlert(
    UnexpectedStop(System.currentTimeMillis() - 90 * MINUTE_IN_MILLIS, ForceStop),
    onDismissRequest = {},
    onLearnMore = {},
)

@Preview
@Composable
internal fun UnexpectedStopAlertUnknownPreview() =
    UnexpectedStopAlert(UnexpectedStop(null, Unknown), onDismissRequest = {}, onLearnMore = {})
