@file:Suppress("TooManyFunctions")

package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.BuildConfig
import com.masselis.tpmsadvanced.R
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ActivateBluetoothDevices
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.AlertSoundSettings
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.AlertsSettings
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.BeaconMinRssiSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.BeaconScan
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.BeaconScanModeSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.Beacons
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ActivateScanConditions
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.DetectedActivitiesSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.PhoneIdleMechanismSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ScanStatusAnnouncementsSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ExceptedWifis
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.PersistentScanningDetails
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.LitePersistentScanningSettings
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.PersistentScanningSettings
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.StayActiveDuration
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.SuspendBluetoothDevices
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.SuspendScanConditions
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DebugSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DeveloperOptionsSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DisplaySettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.MagnifierDuration
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DimDisplayDelay
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.PressureLossSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.TextSize
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.SensorReadingsUpdateDetails
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.TimeSinceUpdateDetails
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.ReadingsSettings
import com.masselis.tpmsadvanced.feature.unit.interfaces.UnitsSettingsItems
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.alerts
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.persistentMode
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.developerOptions
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.persistentScanning
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.display
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.readings
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries

@Composable
internal fun AppSettings(
    openTimeSinceUpdate: () -> Unit,
    openMagnifier: () -> Unit,
    openDimDisplay: () -> Unit,
    openTextSize: () -> Unit,
    openAlertSound: () -> Unit,
    openPersistentMode: () -> Unit,
    openDebug: () -> Unit,
    openPressureLoss: () -> Unit,
    openLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsPage(modifier) {
    SettingsSectionHeader("Display")
    DisplaySettings(
        openTextSize = openTextSize,
        openMagnifier = openMagnifier,
        openDimDisplay = openDimDisplay,
        modifier = Modifier.testTag(display),
    )
    SettingsSectionHeader("Readings")
    ReadingsSettings(
        openTimeSinceUpdate = openTimeSinceUpdate,
        modifier = Modifier.testTag(readings),
        leadingItems = { UnitsSettingsItems() },
    )
    SettingsSectionHeader("Alerts")
    AlertsSettings(openAlertSound, Modifier.testTag(alerts))
    SettingsSectionHeader("Background monitoring")
    // A page of its own, also opened from the main screen's menu
    SettingsGroup {
        TextSettingsItem(
            headline = "Persistent mode",
            supporting = "Persistent scanning and its scan conditions",
            onClick = openPersistentMode,
            modifier = Modifier.testTag(persistentMode),
        )
    }
    SettingsSectionHeader("Developer options")
    DeveloperOptionsSettings(
        openDebug = openDebug,
        openPressureLoss = openPressureLoss,
        modifier = Modifier.testTag(developerOptions),
    )
    // Its own section, only while the other version of the app (Lite or Pro) is installed
    ImportSettings()
    SettingsSectionHeader("About")
    LocalUriHandler.current.also { uriHandler ->
        SettingsGroup {
            TextSettingsItem(headline = "Version", supporting = BuildConfig.VERSION_NAME)
            TextSettingsItem(
                headline = "Based on TPMS Advanced",
                supporting = "By Vincent Masselis, under the Apache License 2.0",
                onClick = { uriHandler.openUri("https://github.com/VincentMasselis/TPMS-advanced") },
            )
            TextSettingsItem(
                headline = "Source code",
                supporting = "github.com/aveshev/persistent-tpms",
                onClick = { uriHandler.openUri("https://github.com/aveshev/persistent-tpms") },
            )
            TextSettingsItem(
                headline = "Privacy policy",
                onClick = { uriHandler.openUri("https://aveshev.com/persistent-tpms/privacy/") },
            )
            TextSettingsItem(
                headline = "Open-source licenses",
                onClick = openLicenses,
            )
        }
    }
}

/** Persistent scanning and its scan conditions, opened from the app settings or the main screen's menu */
@Composable
@Suppress("LongParameterList")
internal fun PersistentModeSettings(
    openPersistentScanning: () -> Unit,
    openActivateScanConditions: () -> Unit,
    openSuspendScanConditions: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsPage(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "When on, keeps monitoring your tyres in the background, even after the phone restarts, starting and suspending scanning based on the options below. That way you don't have to open the app manually each ride."
    )
    when (AppTier.current) {
        // Its few conditions are right on this page, the condition pages aren't reachable
        LITE -> LitePersistentScanningSettings(
            openPersistentScanning = openPersistentScanning,
            openBluetoothDevices = openBluetoothDevices,
            openBeacons = openBeacons,
            openStayActiveDuration = openStayActiveDuration,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(persistentScanning),
        )

        PRO -> PersistentScanningSettings(
            openPersistentScanning = openPersistentScanning,
            openActivateConditions = openActivateScanConditions,
            openSuspendConditions = openSuspendScanConditions,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(persistentScanning),
        )
    }
}

@Composable
internal fun Licenses(
    modifier: Modifier = Modifier
) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    LibrariesContainer(libraries, modifier.fillMaxSize())
}

@Composable
internal fun DebugSettingsPage(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    DebugSettings(
        additionalItems = {
            DetectedActivitiesSettingsItem()
            ScanStatusAnnouncementsSettingsItem()
            BeaconScanModeSettingsItem()
            BeaconMinRssiSettingsItem()
            PhoneIdleMechanismSettingsItem()
        }
    )
}

@Composable
internal fun AlertSoundSettingsPage(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    AlertSoundSettings()
}

@Composable
internal fun PressureLossSettingsPage(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    PressureLossSettings()
}

@Composable
internal fun TimeSinceUpdateSettings(
    openSensorReadingsUpdateDetails: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    TimeSinceUpdateDetails(openSensorReadingsUpdateDetails)
}

@Composable
internal fun SensorReadingsUpdateDetailsSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    SensorReadingsUpdateDetails()
}

@Composable
internal fun MagnifierDurationSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    MagnifierDuration()
}

@Composable
internal fun DimDisplayDelaySettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    DimDisplayDelay()
}

@Composable
internal fun TextSizeSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    TextSize()
}

@Composable
internal fun PersistentScanningDetailsSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    PersistentScanningDetails()
}

@Composable
internal fun ActivateScanConditionsSettings(
    openStayActiveDuration: () -> Unit,
    openBluetoothDevices: () -> Unit,
    openBeacons: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    ActivateScanConditions(openStayActiveDuration, openBluetoothDevices, openBeacons)
}

@Composable
internal fun StayActiveDurationSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    StayActiveDuration()
}

@Composable
internal fun SuspendScanConditionsSettings(
    openExceptedWifis: () -> Unit,
    openBluetoothDevices: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    SuspendScanConditions(openExceptedWifis, openBluetoothDevices)
}

@Composable
internal fun ExceptedWifisSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    ExceptedWifis()
}

@Composable
internal fun ActivateBluetoothDevicesSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    ActivateBluetoothDevices()
}

@Composable
internal fun SuspendBluetoothDevicesSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    SuspendBluetoothDevices()
}

@Composable
internal fun BeaconsSettings(
    openBeaconScan: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    Beacons(openBeaconScan)
}

@Composable
internal fun BeaconScanSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    BeaconScan()
}

@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    content = content,
)

internal object AppSettingsTag {
    const val display = "AppSettingsTag_display"
    const val readings = "AppSettingsTag_readings"
    const val alerts = "AppSettingsTag_alerts"
    const val persistentScanning = "AppSettingsTag_persistentScanning"
    const val persistentMode = "AppSettingsTag_persistentMode"
    const val developerOptions = "AppSettingsTag_developerOptions"
}
