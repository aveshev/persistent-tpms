package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.common.AppLanguage
import com.masselis.tpmsadvanced.core.common.AppLanguage.ENGLISH
import com.masselis.tpmsadvanced.core.common.AppLanguage.RUSSIAN
import com.masselis.tpmsadvanced.core.common.AppLanguage.SYSTEM
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Companion.DEFAULT_SCREEN_OFF_SCAN_RESTART_SECONDS
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.BALANCED
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_POWER
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.ListenTyreSmartDutyUseCase
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
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showSensorFlags by viewModel.showSensorFlags.collectAsState()
    val balancedAfterFirstReading by viewModel.balancedAfterFirstReading.collectAsState()
    val backgroundTyreScanMode by viewModel
        .backgroundTyreScanMode
        .collectAsState(initial = ListenTyreSmartDutyUseCase.DEFAULT_BACKGROUND_SCAN_MODE)
    val screenOffScanRestartSeconds by viewModel.screenOffScanRestartSeconds.collectAsState()
    val language by AppLanguage.current.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DebugSettings(
        showSensorId = showSensorId,
        onShowSensorId = { viewModel.showSensorId.value = it },
        showSensorFlags = showSensorFlags,
        onShowSensorFlags = { viewModel.showSensorFlags.value = it },
        balancedAfterFirstReading = balancedAfterFirstReading,
        onBalancedAfterFirstReading = { viewModel.balancedAfterFirstReading.value = it },
        // Below Android 12 the tyre scan always stays on low latency, nothing to choose
        backgroundTyreScanMode = backgroundTyreScanMode
            .takeIf { ListenTyreSmartDutyUseCase.ALWAYS_LOW_LATENCY_IN_BACKGROUND.not() },
        onBackgroundTyreScanMode = viewModel::setBackgroundTyreScanMode,
        screenOffScanRestartSeconds = screenOffScanRestartSeconds,
        onScreenOffScanRestartSeconds = { viewModel.screenOffScanRestartSeconds.value = it },
        language = language,
        onLanguage = { AppLanguage.current.value = it },
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
    backgroundTyreScanMode: ScanMode?,
    onBackgroundTyreScanMode: (ScanMode) -> Unit,
    screenOffScanRestartSeconds: Int,
    onScreenOffScanRestartSeconds: (Int) -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onExportDatabase: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "What the sensors and the phone report, to help investigate them. Turning Debug off in the app settings hides, turns off or puts back to its default all of it, and keeps what is selected here, except the language, which goes back to the phone's."
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
    SettingsSectionHeader("Display")
    SettingsGroup {
        LanguageSettingsItem(language, onLanguage)
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
        backgroundTyreScanMode?.let {
            BackgroundTyreScanModeSettingsItem(it, onBackgroundTyreScanMode)
            // The scans only restart from Android 12, like the mode
            ScreenOffScanRestartSettingsItem(screenOffScanRestartSeconds, onScreenOffScanRestartSeconds)
        }
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
        backgroundTyreScanMode = BALANCED,
        onBackgroundTyreScanMode = {},
        screenOffScanRestartSeconds = DEFAULT_SCREEN_OFF_SCAN_RESTART_SECONDS,
        onScreenOffScanRestartSeconds = {},
        language = SYSTEM,
        onLanguage = {},
        onExportDatabase = {},
    )
}

@Suppress("ConstPropertyName")
internal object DebugSettingsTags {
    const val showSensorId = "DebugSettingsTags_showSensorId"
    const val showSensorFlags = "DebugSettingsTags_showSensorFlags"
    const val balancedAfterFirstReading = "DebugSettingsTags_balancedAfterFirstReading"
    const val backgroundTyreScanMode = "DebugSettingsTags_backgroundTyreScanMode"
    const val backgroundTyreScanModeOption = "DebugSettingsTags_backgroundTyreScanModeOption"
    const val screenOffScanRestart = "DebugSettingsTags_screenOffScanRestart"
    const val screenOffScanRestartOption = "DebugSettingsTags_screenOffScanRestartOption"
    const val language = "DebugSettingsTags_language"
    const val languageOption = "DebugSettingsTags_languageOption"
    const val exportDatabase = "DebugSettingsTags_exportDatabase"
}

/**
 * The mode of the tyre scan while the app isn't visible, from a dropdown, to check whether a phone
 * throttles low latency there
 */
@Composable
private fun BackgroundTyreScanModeSettingsItem(mode: ScanMode, onMode: (ScanMode) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextSettingsItem(
            headline = "Background scan mode",
            supporting = "${mode.label}, for the tyres while the app isn't visible",
            onClick = { expanded = true },
            modifier = Modifier.testTag(DebugSettingsTags.backgroundTyreScanMode),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ListenTyreSmartDutyUseCase.BACKGROUND_SCAN_MODES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onMode(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == mode) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${DebugSettingsTags.backgroundTyreScanModeOption}_${option.name}"),
                )
            }
        }
    }
}

/**
 * How often the tyre scans restart while the screen is off, from a dropdown: some phones suspend a
 * scan shortly after it starts with the screen off
 */
@Composable
private fun ScreenOffScanRestartSettingsItem(seconds: Int, onSeconds: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextSettingsItem(
            headline = "Restart while the screen is off",
            supporting = "${seconds.restartLabel}, for the tyre scans",
            onClick = { expanded = true },
            modifier = Modifier.testTag(DebugSettingsTags.screenOffScanRestart),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SCREEN_OFF_RESTART_SECONDS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.restartLabel) },
                    onClick = {
                        onSeconds(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == seconds) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${DebugSettingsTags.screenOffScanRestartOption}_$option"),
                )
            }
        }
    }
}

// 0 keeps the usual restart, every 4 minutes
@Suppress("MagicNumber")
private val SCREEN_OFF_RESTART_SECONDS = listOf(DEFAULT_SCREEN_OFF_SCAN_RESTART_SECONDS, 30, 10, 0)

private val Int.restartLabel
    get() = when (this) {
        0 -> "Every 4 minutes, as with the screen on"
        DEFAULT_SCREEN_OFF_SCAN_RESTART_SECONDS -> "At screen off, then every $this s (default)"
        else -> "At screen off, then every $this s"
    }

private val ScanMode.label
    get() = when (this) {
        LOW_POWER -> "Low power"
        BALANCED -> "Balanced"
        LOW_LATENCY -> "Low latency"
    }

/** The app's language, from a dropdown */
@Composable
private fun LanguageSettingsItem(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextSettingsItem(
            headline = stringResource(CommonR.string.language),
            supporting = language.label,
            onClick = { languageExpanded = true },
            modifier = Modifier.testTag(DebugSettingsTags.language),
        )
        DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onLanguage(option)
                        languageExpanded = false
                    },
                    trailingIcon = {
                        if (option == language) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${DebugSettingsTags.languageOption}_${option.name}"),
                )
            }
        }
    }
}

/** Each language in its own, so that it can be found from any of them */
private val AppLanguage.label: String
    get() = when (this) {
        SYSTEM -> localized(CommonR.string.language_system)
        ENGLISH -> "English"
        RUSSIAN -> "Русский"
    }
