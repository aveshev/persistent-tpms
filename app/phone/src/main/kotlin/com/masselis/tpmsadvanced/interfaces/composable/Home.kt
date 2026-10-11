package com.masselis.tpmsadvanced.interfaces.composable

import com.masselis.tpmsadvanced.feature.main.interfaces.composable.SensorCalibrationSettings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.masselis.tpmsadvanced.R
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.isWideWindow
import com.masselis.tpmsadvanced.core.ui.Spotlight
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.DetectedActivitiesIndicator
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.MonitoringButton
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.PersistentScanningHost
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.QuietScanStatusAnnouncementsEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SilenceAlertsButton
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdown
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.SimulateReadingDialog
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.isReadingSimulationAvailable
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.LocalVehicleComponent
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.ManageSensors
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeScan
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeResultDialog
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.ui.BluetoothAssign
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags.backButton
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags.carListDropdownMenu
import com.masselis.tpmsadvanced.interfaces.viewmodel.HomeViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.VehicleHomeViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.VehicleHomeViewModel.Event
import com.masselis.tpmsadvanced.ioc.Bindings.Companion.HomeViewModel
import com.masselis.tpmsadvanced.ioc.Bindings.Companion.VehicleHomeViewModel
import java.util.UUID

@Composable
internal fun Home(
    expectedVehicle: UUID?,
    viewModel: HomeViewModel = viewModel(key = "HomeViewModel_$expectedVehicle") {
        HomeViewModel(expectedVehicle = expectedVehicle)
    }
) {
    val vehicleComponent by viewModel.vehicleComponentStateFlow.collectAsState()
    PersistentScanningHost {
        VehicleHome(
            vehicleComponent = vehicleComponent
        )
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun VehicleHome(
    vehicleComponent: VehicleComponent,
    viewModel: VehicleHomeViewModel = viewModel { VehicleHomeViewModel() }
) {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalVehicleComponent provides vehicleComponent,
        LocalHomeNavController provides navController
    ) {
        var offsetToFocus by remember { mutableStateOf<Offset?>(null) }
        var showManualMonitoringSpotlight by remember { mutableStateOf(false) }
        var showAndroidAutoSupportAlert by remember { mutableStateOf(false) }
        var showWicarlinkSupportAlert by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }
        // A QR code scanned for this location, shown once its camera is closed, see QrCodeResultDialog
        var qrCodeResult by rememberSaveable { mutableStateOf<QrCodeResult?>(null) }
        var qrCodeLocation by rememberSaveable { mutableIntStateOf(0) }
        var qrCodeScan by rememberSaveable { mutableIntStateOf(0) }
        val currentPath = navController.currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route
            ?.let { Path.from(it) }
        Scaffold(
            topBar = {
                // Manage sensors shows its own beside the vehicle, see isWideWindow
                if ((currentPath is Path.ManageSensors && isWideWindow()).not()) TopAppBar(
                    manualBackgroundButtonModifier = Modifier.onGloballyPositioned { coordinates ->
                        if (offsetToFocus != null) return@onGloballyPositioned
                        coordinates.positionInRoot()
                            .takeIf { it != Offset.Unspecified }
                            ?.let { topLeft ->
                                Offset(
                                    topLeft.x + coordinates.size.width.div(2),
                                    topLeft.y + coordinates.size.height.div(2)
                                )
                            }
                            ?.also { offsetToFocus = it }
                    },
                )
            },
            content = { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = "${Path.Home(vehicleComponent.vehicle.uuid)}"
                ) {
                    val modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                    val vehicleUuid = vehicleComponent.vehicle.uuid
                    // A location is told in a route by its index in these
                    val locations = vehicleComponent.vehicle.kind.locations.toList()
                    composable(route = "${Path.Home(vehicleComponent.vehicle.uuid)}") {
                        // The bell tells the status here, nothing to hear
                        QuietScanStatusAnnouncementsEffect()
                        CurrentVehicle(
                            snackbarHostState = snackbarHostState,
                            modifier = modifier,
                            center = { SilenceAlertsButton(it) },
                            scanQrCode = {
                                navController.navigate("${Path.QrCode(vehicleUuid, locations.indexOf(it))}")
                            },
                            scanBluetooth = {
                                navController.navigate("${Path.BluetoothAssign(vehicleUuid, locations.indexOf(it))}")
                            },
                        )
                    }
                    composable("${Path.Settings(vehicleComponent.vehicle.uuid)}") {
                        Settings(
                            openPressure = {
                                navController.navigate("${Path.PressureSettings(vehicleComponent.vehicle.uuid)}")
                            },
                            openTemperature = {
                                navController.navigate("${Path.TemperatureSettings(vehicleComponent.vehicle.uuid)}")
                            },
                            openBattery = {
                                navController.navigate("${Path.BatterySettings(vehicleComponent.vehicle.uuid)}")
                            },
                            openManageSensors = {
                                navController.navigate("${Path.ManageSensors(vehicleComponent.vehicle.uuid)}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.ManageSensors(vehicleComponent.vehicle.uuid)}") {
                        ManageSensors(
                            snackbarHostState = snackbarHostState,
                            scanQrCode = {
                                navController.navigate("${Path.QrCode(vehicleUuid, locations.indexOf(it))}")
                            },
                            scanBluetooth = {
                                navController.navigate("${Path.BluetoothAssign(vehicleUuid, locations.indexOf(it))}")
                            },
                            calibrate = { navController.navigate("${Path.Calibration(vehicleUuid, it)}") },
                            modifier = modifier,
                            navigationIcon = { BackButton() },
                        )
                    }
                    composable("${Path.PressureSettings(vehicleComponent.vehicle.uuid)}") {
                        PressureSettings(
                            modifier = modifier
                        )
                    }
                    composable(
                        route = Path.Calibration.route(vehicleUuid),
                        arguments = listOf(navArgument("sensorId") { type = NavType.IntType }),
                    ) { entry ->
                        // Its own page, not a settings one: its actions stay at the bottom
                        SensorCalibrationSettings(
                            sensorId = requireNotNull(entry.arguments).getInt("sensorId"),
                            onLeave = { navController.popBackStack() },
                            modifier = modifier
                        )
                    }
                    composable("${Path.TemperatureSettings(vehicleComponent.vehicle.uuid)}") {
                        TemperatureSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.BatterySettings(vehicleComponent.vehicle.uuid)}") {
                        BatterySettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.AppSettings}") {
                        AppSettings(
                            openTimeSinceUpdate = {
                                navController.navigate("${Path.TimeSinceUpdate}")
                            },
                            openPersistentScanning = {
                                navController.navigate("${Path.PersistentScanning}")
                            },
                            openActivateScanConditions = {
                                navController.navigate("${Path.ActivateScanConditions}")
                            },
                            openSuspendScanConditions = {
                                navController.navigate("${Path.SuspendScanConditions}")
                            },
                            openDebug = {
                                navController.navigate("${Path.Debug}")
                            },
                            openPressureLoss = {
                                navController.navigate("${Path.PressureLoss}")
                            },
                            openLicenses = {
                                navController.navigate("${Path.Licenses}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.Licenses}") {
                        Licenses(
                            modifier = modifier
                        )
                    }
                    composable("${Path.Debug}") {
                        DebugSettingsPage(
                            modifier = modifier
                        )
                    }
                    composable("${Path.PressureLoss}") {
                        PressureLossSettingsPage(
                            modifier = modifier
                        )
                    }
                    composable("${Path.TimeSinceUpdate}") {
                        TimeSinceUpdateSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.PersistentScanning}") {
                        PersistentScanningDetailsSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.ActivateScanConditions}") {
                        ActivateScanConditionsSettings(
                            openStayActiveDuration = {
                                navController.navigate("${Path.StayActiveDuration}")
                            },
                            openBluetoothDevices = {
                                navController.navigate("${Path.ActivateBluetoothDevices}")
                            },
                            openBeacons = {
                                navController.navigate("${Path.Beacons}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.StayActiveDuration}") {
                        StayActiveDurationSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.SuspendScanConditions}") {
                        SuspendScanConditionsSettings(
                            openExceptedWifis = {
                                navController.navigate("${Path.ExceptedWifis}")
                            },
                            openBluetoothDevices = {
                                navController.navigate("${Path.SuspendBluetoothDevices}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.ExceptedWifis}") {
                        ExceptedWifisSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.ActivateBluetoothDevices}") {
                        ActivateBluetoothDevicesSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.SuspendBluetoothDevices}") {
                        SuspendBluetoothDevicesSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.Beacons}") {
                        BeaconsSettings(
                            openBeaconScan = {
                                navController.navigate("${Path.BeaconScan}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.BeaconScan}") {
                        BeaconScanSettings(
                            modifier = modifier
                        )
                    }
                    composable(
                        route = Path.QrCode.route(vehicleUuid),
                        arguments = listOf(navArgument("location") { type = NavType.IntType }),
                    ) { entry ->
                        val location = entry.arguments!!.getInt("location")
                        QrCodeScan(
                            snackbarHostState = snackbarHostState,
                            onFound = {
                                qrCodeResult = it
                                qrCodeLocation = location
                                qrCodeScan++
                                navController.popBackStack()
                            },
                            modifier = modifier
                        )
                    }
                    composable(
                        route = Path.BluetoothAssign.route(vehicleUuid),
                        arguments = listOf(navArgument("location") { type = NavType.IntType }),
                    ) { entry ->
                        BluetoothAssign(
                            vehicleUuid = vehicleUuid,
                            location = locations[entry.arguments!!.getInt("location")],
                            // Back to the screen the assignment started from, home or the sensors
                            onLeave = {
                                navController.popBackStack(Path.BluetoothAssign.route(vehicleUuid), true)
                                navController.popBackStack(Path.QrCode.route(vehicleUuid), true)
                            },
                            modifier = modifier
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        )
        qrCodeResult?.also { result ->
            QrCodeResultDialog(
                vehicleUuid = vehicleComponent.vehicle.uuid,
                result = result,
                scan = qrCodeScan,
                scanBluetooth = {
                    navController.navigate("${Path.BluetoothAssign(vehicleComponent.vehicle.uuid, qrCodeLocation)}")
                },
                onDismiss = { qrCodeResult = null },
            )
        }
        if (offsetToFocus != null)
            AnimatedVisibility(
                visible = showManualMonitoringSpotlight,
                enter = fadeIn(spring(0f)),
                exit = fadeOut()
            ) {
                with(LocalDensity.current) {
                    @Suppress("MaxLineLength")
                    Spotlight(
                        center = offsetToFocus!!,
                        radius = 50.dp.toPx(),
                        text = AnnotatedString("Something new is waiting for you,\nTap this button to monitor your vehicle while the app is in background"),
                        textPadding = 8.dp.toPx(),
                        textStyle = TextStyle.Default.copy(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        onSpotlight = { showManualMonitoringSpotlight = false }
                    )
                }
            }
        if (showWicarlinkSupportAlert) {
            WicarlinkSupportAlert(
                onDismissRequest = {
                    showWicarlinkSupportAlert = false
                    showAndroidAutoSupportAlert = true
                }
            )
        }
        if (showAndroidAutoSupportAlert) {
            AndroidAutoSupportAlert(onDismissRequest = { showAndroidAutoSupportAlert = false })
        }
        LaunchedEffect(viewModel) {
            for (event in viewModel.eventChannel) {
                when (event) {
                    Event.ManualMonitorDropdown ->
                        showManualMonitoringSpotlight = true

                    Event.WicarlinkSupportAndAndroidAuto ->
                        showWicarlinkSupportAlert = true
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TopAppBar(
    modifier: Modifier = Modifier,
    manualBackgroundButtonModifier: Modifier = Modifier
) {
    val navController = LocalHomeNavController.current
    val currentPath = navController.currentBackStackEntryAsState()
        .value
        ?.destination
        ?.route
        ?.let { Path.from(it) }
    CenterAlignedTopAppBar(
        title = {
            when (currentPath) {
                is Path.Home -> CurrentVehicleDropdown(
                    modifier = Modifier.testTag(carListDropdownMenu),
                )

                is Path.Settings -> Text(
                    text = LocalVehicleComponent.current.vehicleStateFlow.collectAsState().value.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                is Path.ManageSensors -> Text(text = "Manage sensors")
                is Path.PressureSettings -> Text(text = "Pressure")
                is Path.TemperatureSettings -> Text(text = "Temperature")
                is Path.Calibration -> Text(text = "Calibrate sensor")
                is Path.BatterySettings -> Text(text = "Battery")
                is Path.PressureLoss -> Text(text = "Pressure loss")
                is Path.AppSettings -> Text(text = "App settings")
                is Path.TimeSinceUpdate -> Text(text = "Time since last update")
                is Path.Debug -> Text(text = "Debug")
                is Path.PersistentScanning -> Text(text = "Persistent scanning")
                is Path.ActivateScanConditions -> Text(text = "Activate scan conditions")
                is Path.StayActiveDuration -> Text(text = "Stay active")
                is Path.SuspendScanConditions -> Text(text = "Suspend scan conditions")
                is Path.ExceptedWifis -> Text(text = "Excepted WiFis")
                is Path.ActivateBluetoothDevices,
                is Path.SuspendBluetoothDevices -> Text(text = "Bluetooth devices")
                is Path.Beacons -> Text(text = "Bluetooth beacons")
                is Path.BeaconScan -> Text(text = "Add a beacon")
                is Path.Licenses -> Text(text = "Open-source licenses")
                is Path.BluetoothAssign -> Text(text = "Assign via Bluetooth")
                is Path.QrCode, null -> {}
            }
        },
        navigationIcon = {
            when (currentPath) {
                is Path.Settings,
                is Path.ManageSensors,
                is Path.PressureSettings,
                is Path.TemperatureSettings,
                is Path.Calibration,
                is Path.BatterySettings,
                is Path.PressureLoss,
                is Path.AppSettings,
                is Path.TimeSinceUpdate,
                is Path.Debug,
                is Path.PersistentScanning,
                is Path.ActivateScanConditions,
                is Path.StayActiveDuration,
                is Path.SuspendScanConditions,
                is Path.ExceptedWifis,
                is Path.ActivateBluetoothDevices,
                is Path.SuspendBluetoothDevices,
                is Path.Beacons,
                is Path.BeaconScan,
                is Path.Licenses,
                is Path.QrCode,
                is Path.BluetoothAssign -> BackButton()

                // What the phone detects it is doing, when asked for in the debug settings
                is Path.Home -> DetectedActivitiesIndicator()

                null -> {}
            }
        },
        actions = {
            var showMenu by remember { mutableStateOf(false) }
            // Out of the menu, which leaves the composition once closed
            var simulateReading by remember { mutableStateOf(false) }
            if (simulateReading) SimulateReadingDialog(onDismissRequest = { simulateReading = false })
            when (currentPath) {
                is Path.Home -> {
                    MonitoringButton(
                        modifier = manualBackgroundButtonModifier
                            .testTag(HomeTags.Actions.manualBackground)
                    )
                    IconButton(
                        onClick = { showMenu = true },
                        Modifier.testTag(HomeTags.Actions.overflow)
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.more_vert_24px),
                            contentDescription = "Show more options",
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.testTag(HomeTags.Overflow.root)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Vehicle settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("${Path.Settings(currentPath.vehicleUUID)}")
                            },
                            modifier = Modifier.testTag(HomeTags.Overflow.settings)
                        )
                        DropdownMenuItem(
                            text = { Text("App settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("${Path.AppSettings}")
                            },
                            modifier = Modifier.testTag(HomeTags.Overflow.appSettings)
                        )
                        if (isReadingSimulationAvailable()) DropdownMenuItem(
                            text = { Text("Simulate a reading") },
                            onClick = {
                                showMenu = false
                                simulateReading = true
                            },
                        )
                    }
                }

                is Path.Settings,
                is Path.ManageSensors,
                is Path.PressureSettings,
                is Path.TemperatureSettings,
                is Path.Calibration,
                is Path.BatterySettings,
                is Path.PressureLoss,
                is Path.AppSettings,
                is Path.TimeSinceUpdate,
                is Path.Debug,
                is Path.PersistentScanning,
                is Path.ActivateScanConditions,
                is Path.StayActiveDuration,
                is Path.SuspendScanConditions,
                is Path.ExceptedWifis,
                is Path.ActivateBluetoothDevices,
                is Path.SuspendBluetoothDevices,
                is Path.Beacons,
                is Path.BeaconScan,
                is Path.Licenses,
                is Path.QrCode,
                is Path.BluetoothAssign,
                null -> {}
            }
        },
        modifier = modifier
    )
}

@Composable
private fun WicarlinkSupportAlert(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("New sensor support!") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(id = R.drawable.lytpms_icon),
                        contentDescription = "LTPMS Icon app",
                    )
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    "Sensors manufactured by \"Wicarlink\" shown within their app \"LYTPMS\" are now " +
                            "supported by Persistent TPMS",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("I want more !")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun AndroidAutoSupportAlert(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Great news !") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(id = R.drawable.android_auto_icon),
                        contentDescription = "Android Auto",
                    )
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    "Android auto is now fully supported! Connect this device to your car radio and " +
                            "open Persistent TPMS",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        },
        modifier = modifier,
    )
}

@Suppress("ConstPropertyName")
internal object HomeTags {
    const val backButton = "HomeTags_backButton"
    const val carListDropdownMenu = "HomeTags_carListDropdownMenu"

    object Actions {
        const val manualBackground = "HomeTags_Actions_manualBackground"
        const val overflow = "HomeTags_Actions_overflow"

    }

    object Overflow {
        const val root = "HomeTags_Overflow_root"
        const val settings = "HomeTags_Overflow_settings"
        const val appSettings = "HomeTags_Overflow_appSettings"
    }
}

@Composable
private fun BackButton() {
    // As the system's back does, so a page asking before leaving asks here too
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(
        onClick = { dispatcher?.onBackPressed() },
        content = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                contentDescription = "Go back"
            )
        },
        modifier = Modifier.testTag(backButton)
    )
}
