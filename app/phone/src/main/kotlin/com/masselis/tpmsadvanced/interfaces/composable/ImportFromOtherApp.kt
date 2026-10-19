package com.masselis.tpmsadvanced.interfaces.composable

import android.text.format.DateUtils.FORMAT_ABBREV_MONTH
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.text.format.DateUtils.FORMAT_SHOW_YEAR
import android.text.format.DateUtils.formatDateTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Failed
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Idle
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Imported
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Importing
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.NothingToImport
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Offer
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Unreachable
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.UpdateNeeded
import com.masselis.tpmsadvanced.ioc.Bindings.Companion.ImportViewModel

/** Offers to import the other version of the app's data (Lite or Pro) when the app opens */
@Composable
internal fun ImportOffer(
    viewModel: ImportViewModel = viewModel(key = "ImportOffer") { ImportViewModel() },
) {
    LaunchedEffect(Unit) { viewModel.offerOnce() }
    val state by viewModel.state.collectAsState()
    ImportDialog(state, viewModel::confirm, viewModel::dismiss, viewModel::neverAsk)
}

/** "Import from <other app>", only while the other version of the app is installed */
@Composable
internal fun ImportSettings(
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = viewModel(key = "ImportSettings") { ImportViewModel() },
) {
    val otherApp = remember { viewModel.otherAppLabel() } ?: return
    val state by viewModel.state.collectAsState()
    SettingsSectionHeader("Data")
    SettingsGroup(modifier) {
        TextSettingsItem(
            headline = "Import from $otherApp",
            supporting = "Replaces this app's vehicles, sensors, readings and settings",
            onClick = viewModel::requestImport,
        )
    }
    ImportDialog(state, viewModel::confirm, viewModel::dismiss)
}

@Composable
private fun ImportDialog(
    state: State,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onNeverAsk: () -> Unit = {},
) {
    when (state) {
        Idle -> Unit
        is Offer -> AlertDialog(
            title = { Text(text = "Import from ${state.otherApp}?") },
            text = { Text(text = state.text()) },
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onConfirm) { Text(text = "Import") } },
            dismissButton = {
                if (state.fromSettings) {
                    TextButton(onClick = onDismiss) { Text(text = "Cancel") }
                } else {
                    // Back or a tap outside dismisses it until the next opening, see onDismissRequest
                    TextButton(onClick = onNeverAsk) { Text(text = "Don't ask again") }
                }
            },
        )

        is Importing -> AlertDialog(
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(text = "Importing from ${state.otherApp}…")
                }
            },
            // Can't be stopped halfway, the app restarts once done
            onDismissRequest = {},
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )

        is Imported -> ImportMessage(
            title = "Imported from ${state.otherApp}",
            text = "Its vehicles, sensors, readings and settings are now in this app.",
            onDismiss = onDismiss,
        )

        is UpdateNeeded -> ImportMessage(
            title = "Update this app first",
            text = "${state.otherApp} is a newer version, whose data this app can't read yet. Update " +
                "this app, then import from its settings.",
            onDismiss = onDismiss,
        )

        is Unreachable -> ImportMessage(
            title = "Cannot import",
            text = "${state.otherApp} didn't answer. Update it, then try again.",
            onDismiss = onDismiss,
        )

        is NothingToImport -> ImportMessage(
            title = "Nothing to import",
            text = "${state.otherApp} has no sensors or readings yet.",
            onDismiss = onDismiss,
        )

        is Failed -> ImportMessage(
            title = "Import failed",
            text = "The data of ${state.otherApp} couldn't be read. This app's data is unchanged.",
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ImportMessage(title: String, text: String, onDismiss: () -> Unit) = AlertDialog(
    title = { Text(text = title) },
    text = { Text(text = text) },
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
)

@Composable
private fun Offer.text(): String = LocalContext.current.let { context ->
    @Suppress("MagicNumber")
    fun Double?.asDate() = this
        ?.let { formatDateTime(context, (it * 1000).toLong(), DATE_FLAGS) }
        ?: "none"
    if (replacing) {
        "Latest reading in $otherApp: ${theirLatestReading.asDate()}\n" +
            "Latest reading here: ${ourLatestReading.asDate()}\n\n" +
            "Importing replaces this app's vehicles, sensors, readings and settings with those of $otherApp."
    } else {
        theirLatestReading
            ?.let { "$otherApp is installed on this phone, with its latest reading on ${it.asDate()}.\n\n" }
            .orEmpty() +
            "Importing copies its vehicles, sensors, readings and settings into this app."
    } + if (fromSettings) "" else " You can also import later, from the settings."
}

private const val DATE_FLAGS = FORMAT_SHOW_DATE or FORMAT_SHOW_TIME or FORMAT_SHOW_YEAR or FORMAT_ABBREV_MONTH

@Preview
@Composable
@Suppress("MagicNumber")
private fun EmptyAppOfferPreview() = ImportDialog(
    Offer("Persistent TPMS Lite", 1_792_400_000.0, null, replacing = false, fromSettings = false),
    {},
    {},
)

@Preview
@Composable
@Suppress("MagicNumber")
private fun ReplacingOfferPreview() = ImportDialog(
    Offer("Persistent TPMS", 1_792_400_000.0, 1_791_000_000.0, replacing = true, fromSettings = true),
    {},
    {},
)
