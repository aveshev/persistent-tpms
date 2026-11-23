@file:Suppress("MaxLineLength")

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
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource
import com.masselis.tpmsadvanced.core.common.localized

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
    SettingsSectionHeader(stringResource(CommonR.string.data))
    SettingsGroup(modifier) {
        TextSettingsItem(
            headline = stringResource(CommonR.string.import_from, otherApp),
            supporting = stringResource(CommonR.string.replaces_this_app_s_vehicles_sensors_readings),
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
            title = { Text(text = stringResource(CommonR.string.import_from_2, state.otherApp)) },
            text = { Text(text = state.text()) },
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onConfirm) { Text(text = stringResource(CommonR.string.text_import)) } },
            dismissButton = {
                if (state.fromSettings) {
                    TextButton(onClick = onDismiss) { Text(text = stringResource(CommonR.string.cancel)) }
                } else {
                    // Back or a tap outside dismisses it until the next opening, see onDismissRequest
                    TextButton(onClick = onNeverAsk) { Text(text = stringResource(CommonR.string.don_t_ask_again)) }
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
                    Text(text = stringResource(CommonR.string.importing_from, state.otherApp))
                }
            },
            // Can't be stopped halfway, the app restarts once done
            onDismissRequest = {},
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )

        is Imported -> ImportMessage(
            title = stringResource(CommonR.string.imported_from, state.otherApp),
            text = stringResource(CommonR.string.all_vehicles_sensors_readings_and_settings_where),
            onDismiss = onDismiss,
        )

        is UpdateNeeded -> ImportMessage(
            title = stringResource(CommonR.string.update_this_app_first),
            text = stringResource(CommonR.string.is_a_newer_version_whose_data_this, state.otherApp),
            onDismiss = onDismiss,
        )

        is Unreachable -> ImportMessage(
            title = stringResource(CommonR.string.cannot_import),
            text = stringResource(CommonR.string.didn_t_answer_update_it_then_try, state.otherApp),
            onDismiss = onDismiss,
        )

        is NothingToImport -> ImportMessage(
            title = stringResource(CommonR.string.nothing_to_import),
            text = stringResource(CommonR.string.has_no_sensors_or_readings_yet, state.otherApp),
            onDismiss = onDismiss,
        )

        is Failed -> ImportMessage(
            title = stringResource(CommonR.string.import_failed),
            text = stringResource(CommonR.string.the_data_of_couldn_t_be_read, state.otherApp),
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
        ?: localized(CommonR.string.date_none)
    if (replacing) {
        localized(CommonR.string.latest_reading_in_latest_reading_here_importing, otherApp, theirLatestReading.asDate(), ourLatestReading.asDate())
    } else {
        theirLatestReading
            ?.let { localized(CommonR.string.other_app_installed, otherApp, it.asDate()) }
            .orEmpty() +
            localized(CommonR.string.import_copies)
    } + if (fromSettings) "" else localized(CommonR.string.import_later)
}

private const val DATE_FLAGS = FORMAT_SHOW_DATE or FORMAT_SHOW_TIME or FORMAT_SHOW_YEAR or FORMAT_ABBREV_MONTH

@Preview
@Composable
@Suppress("MagicNumber")
internal fun EmptyAppOfferPreview() = ImportDialog(
    Offer("Persistent TPMS Lite", 1_792_400_000.0, null, replacing = false, fromSettings = false),
    {},
    {},
)

@Preview
@Composable
@Suppress("MagicNumber")
internal fun ReplacingOfferPreview() = ImportDialog(
    Offer("Persistent TPMS", 1_792_400_000.0, 1_791_000_000.0, replacing = true, fromSettings = true),
    {},
    {},
)
