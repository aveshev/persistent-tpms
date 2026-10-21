package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Only one of Lite and Pro may scan persistently. Shown when persistent scanning is turned on here,
 * or the app is opened with it on, while it's on in [otherApp] too.
 */
@Composable
internal fun OtherAppPersistentScanningAlert(
    otherApp: String,
    onSwitch: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        text = { Text(text = "Persistent mode is on in $otherApp. Turn it off there and on here?") },
        // Back or a tap outside leaves the other app scanning, as Cancel does
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onSwitch) {
                Text(text = "Switch to this app")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        },
    )
}

@Preview
@Composable
private fun OtherAppPersistentScanningAlertPreview() = OtherAppPersistentScanningAlert(
    otherApp = "Persistent TPMS Lite",
    onSwitch = {},
    onCancel = {},
)
