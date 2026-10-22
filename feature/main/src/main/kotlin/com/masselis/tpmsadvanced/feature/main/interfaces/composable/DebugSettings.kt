package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel
import kotlinx.coroutines.launch

/**
 * The page opened from the "Debug" item of [MiscSettings]: what the sensors and the
 * phone report, shown on the home screen, and how the tyres are scanned. [additionalItems] are
 * appended to the first group, for debug options owned by other features.
 */
@Composable
public fun DebugSettings(
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
): Unit = DebugSettings(modifier, additionalItems, viewModel { DebugSettingsViewModel() })

@Composable
internal fun DebugSettings(
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: DebugSettingsViewModel = viewModel { DebugSettingsViewModel() },
) {
    // With nothing selected there is nothing to show: same as turning the switch off
    OnLeaveEffect(viewModel::disableIfNoneSelected)
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showSensorFlags by viewModel.showSensorFlags.collectAsState()
    val balancedAfterFirstReading by viewModel.balancedAfterFirstReading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DebugSettings(
        showSensorId = showSensorId,
        onShowSensorId = { viewModel.showSensorId.value = it },
        showSensorFlags = showSensorFlags,
        onShowSensorFlags = { viewModel.showSensorFlags.value = it },
        balancedAfterFirstReading = balancedAfterFirstReading,
        onBalancedAfterFirstReading = { viewModel.balancedAfterFirstReading.value = it },
        onExportDatabase = {
            scope.launch {
                viewModel
                    .exportDatabase()
                    .let { FileProvider.getUriForFile(context, "${context.packageName}.database_export", it) }
                    .let { uri ->
                        Intent(Intent.ACTION_SEND)
                            .setType("application/vnd.sqlite3")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    .let { Intent.createChooser(it, "Export database") }
                    .also { context.startActivity(it) }
            }
        },
        modifier = modifier,
        additionalItems = additionalItems,
    )
}

@Composable
private fun DebugSettings(
    showSensorId: Boolean,
    onShowSensorId: (Boolean) -> Unit,
    showSensorFlags: Boolean,
    onShowSensorFlags: (Boolean) -> Unit,
    balancedAfterFirstReading: Boolean,
    onBalancedAfterFirstReading: (Boolean) -> Unit,
    onExportDatabase: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "What the sensors and the phone report, to help investigate them. Turning Debug off in the app settings hides, turns off or puts back to its default all of it, and keeps what is selected here."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Sensor ID",
            checked = showSensorId,
            onCheckedChange = onShowSensorId,
            modifier = Modifier.testTag(DebugSettingsTags.showSensorId),
        )
        SwitchSettingsItem(
            headline = "Sensor flags",
            supporting = "Bits 7 to 0 of the last packet's status bytes, the unset ones greyed out",
            checked = showSensorFlags,
            onCheckedChange = onShowSensorFlags,
            modifier = Modifier.testTag(DebugSettingsTags.showSensorFlags),
        )
        additionalItems()
    }
    SettingsSectionHeader("Scanning")
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Balanced after the first reading",
            supporting = "Low latency only until a reading comes in, as before: less battery, more missed updates",
            checked = balancedAfterFirstReading,
            onCheckedChange = onBalancedAfterFirstReading,
            modifier = Modifier.testTag(DebugSettingsTags.balancedAfterFirstReading),
        )
    }
    SettingsSectionHeader("Data")
    SettingsGroup {
        TextSettingsItem(
            headline = "Export database",
            supporting = "Every stored reading, with the raw packet it came from",
            onClick = onExportDatabase,
            modifier = Modifier.testTag(DebugSettingsTags.exportDatabase),
        )
    }
}

@Preview
@Composable
internal fun DebugSettingsPreview() {
    DebugSettings(
        showSensorId = true,
        onShowSensorId = {},
        showSensorFlags = false,
        onShowSensorFlags = {},
        balancedAfterFirstReading = false,
        onBalancedAfterFirstReading = {},
        onExportDatabase = {},
    )
}

@Suppress("ConstPropertyName")
internal object DebugSettingsTags {
    const val showSensorId = "DebugSettingsTags_showSensorId"
    const val showSensorFlags = "DebugSettingsTags_showSensorFlags"
    const val balancedAfterFirstReading = "DebugSettingsTags_balancedAfterFirstReading"
    const val exportDatabase = "DebugSettingsTags_exportDatabase"
}
