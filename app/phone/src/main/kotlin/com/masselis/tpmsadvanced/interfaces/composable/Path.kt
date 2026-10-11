package com.masselis.tpmsadvanced.interfaces.composable

import java.util.UUID

@Suppress("MemberVisibilityCanBePrivate")
internal sealed interface Path {

    @JvmInline
    value class Home(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/home"
    }

    @JvmInline
    value class Settings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings"
    }

    @JvmInline
    value class ManageSensors(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/manage_sensors"
    }

    @JvmInline
    value class PressureSettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_pressure"
    }

    @JvmInline
    value class TemperatureSettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_temperature"
    }

    @JvmInline
    value class BatterySettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_battery"
    }

    data object AppSettings : Path {
        override fun toString(): String = "app_settings"
    }

    data object TimeSinceUpdate : Path {
        override fun toString(): String = "app_settings/time_since_last_update"
    }

    data object Debug : Path {
        override fun toString(): String = "app_settings/debug"
    }

    data object PressureLoss : Path {
        override fun toString(): String = "app_settings/pressure_loss"
    }

    data object PersistentScanning : Path {
        override fun toString(): String = "app_settings/persistent_scanning"
    }

    data object ActivateScanConditions : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions"
    }

    data object StayActiveDuration : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions/stay_active"
    }

    data object ExceptedWifis : Path {
        override fun toString(): String = "app_settings/suspend_scan_conditions/excepted_wifis"
    }

    data object SuspendScanConditions : Path {
        override fun toString(): String = "app_settings/suspend_scan_conditions"
    }

    data object ActivateBluetoothDevices : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions/bluetooth_devices"
    }

    data object SuspendBluetoothDevices : Path {
        override fun toString(): String = "app_settings/suspend_scan_conditions/bluetooth_devices"
    }

    data object Beacons : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions/beacons"
    }

    data object BeaconScan : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions/beacons/scan"
    }

    data object Licenses : Path {
        override fun toString(): String = "app_settings/licenses"
    }

    /** Scans a QR code for the location at [location] in the vehicle kind's locations */
    data class QrCode(val vehicleUUID: UUID, val location: Int) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/qrcode/$location"

        companion object {
            fun route(vehicleUUID: UUID) = "vehicle/$vehicleUUID/qrcode/{location}"
        }
    }

    /** Assigns the location at [location] in the vehicle kind's locations a sensor by Bluetooth */
    data class BluetoothAssign(val vehicleUUID: UUID, val location: Int) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/bluetooth_assign/$location"

        companion object {
            fun route(vehicleUUID: UUID) = "vehicle/$vehicleUUID/bluetooth_assign/{location}"
        }
    }

    /** Calibrates the sensor [sensorId] of the vehicle */
    data class Calibration(val vehicleUUID: UUID, val sensorId: Int) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/calibration/$sensorId"

        companion object {
            fun route(vehicleUUID: UUID) = "vehicle/$vehicleUUID/calibration/{sensorId}"
        }
    }

    companion object {
        /** Pages that don't depend on a vehicle, their route is fixed */
        private val appPages
            get() = listOf(
                AppSettings,
                TimeSinceUpdate,
                Debug,
                PressureLoss,
                PersistentScanning,
                ActivateScanConditions,
                StayActiveDuration,
                SuspendScanConditions,
                ExceptedWifis,
                ActivateBluetoothDevices,
                SuspendBluetoothDevices,
                Beacons,
                BeaconScan,
                Licenses,
            )

        @Suppress("CyclomaticComplexMethod", "MagicNumber")
        fun from(route: String): Path = when (val page = appPages.firstOrNull { "$it" == route }) {
            null -> route
                .split('/')
                .let { segments ->
                    val (host, uuidString, screen) = segments
                    assert(host == "vehicle")
                    val uuid = UUID.fromString(uuidString)
                    // The location's index, or the sensor's id. A route's pattern, as a destination
                    // has it, has none.
                    val argument = segments.getOrNull(3)?.toIntOrNull() ?: 0
                    when (screen) {
                        "home" -> Home(uuid)
                        "settings" -> Settings(uuid)
                        "manage_sensors" -> ManageSensors(uuid)
                        "settings_pressure" -> PressureSettings(uuid)
                        "settings_temperature" -> TemperatureSettings(uuid)
                        "settings_battery" -> BatterySettings(uuid)
                        "qrcode" -> QrCode(uuid, argument)
                        "bluetooth_assign" -> BluetoothAssign(uuid, argument)
                        "calibration" -> Calibration(uuid, argument)
                        else -> error("Unrecognized route: \"$route\"")
                    }
                }

            else -> page
        }
    }
}
