package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.combineState
import com.masselis.tpmsadvanced.core.common.observableStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

public class AppPreferences internal constructor(
    context: Context
) {
    private val sharedPreferences = context.getSharedPreferences(
        "APP",
        Context.MODE_PRIVATE
    )

    public val showSensorId: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_SENSOR_ID", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_SENSOR_ID", newValue) }
    }

    /**
     * Whether the switch of the debug options is shown in the app settings, revealed and hidden
     * again by tapping the version. While hidden, [debugOptions] keeps its value but doesn't apply.
     */
    public val developer: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("DEVELOPER", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("DEVELOPER", newValue) }
    }

    /**
     * The switch of the debug options: while off, none of them is shown, whatever is selected.
     * On by default when the sensor ID, the only one which predates it, was shown. What applies is
     * [debugOptionsInUse].
     */
    public val debugOptions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("DEBUG_OPTIONS", sharedPreferences.getBoolean("SHOW_SENSOR_ID", false))
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("DEBUG_OPTIONS", newValue) }
    }

    /** [debugOptions], unless [developer] is off: what the debug options check before applying */
    public val debugOptionsInUse: StateFlow<Boolean> = developer.combineState(debugOptions, Boolean::and)

    /** Shows the status byte of the last packet under each tyre, bit by bit */
    public val showSensorFlags: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_SENSOR_FLAGS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_SENSOR_FLAGS", newValue) }
    }

    /** Shows the activities the phone detects, with their confidence, on the home screen */
    public val showDetectedActivities: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_DETECTED_ACTIVITIES", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_DETECTED_ACTIVITIES", newValue) }
    }

    /**
     * Speaks the status of persistent scanning (idle, active, suspended, off) each time it changes,
     * unless the main screen is shown
     */
    public val announceScanStatus: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ANNOUNCE_SCAN_STATUS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ANNOUNCE_SCAN_STATUS", newValue) }
    }

    /**
     * Switches the tyre scan to balanced after its first reading, as it used to, instead of keeping
     * it at low latency the whole time it runs (main screen shown, or persistent scanning active)
     */
    public val balancedAfterFirstReading: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("BALANCED_AFTER_FIRST_READING", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("BALANCED_AFTER_FIRST_READING", newValue) }
    }

    public val showTimeSinceUpdate:MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_TIME_SINCE_UPDATE", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_TIME_SINCE_UPDATE", newValue) }
    }

    /** A voltage getting low or alarming is shown whatever this is set to */
    public val showBatteryVoltage: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_BATTERY_VOLTAGE", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_BATTERY_VOLTAGE", newValue) }
    }

    /** Tapping a tyre's part of the main screen shows its reading magnified, to read it on a shaking phone */
    public val magnifier: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("MAGNIFIER", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("MAGNIFIER", newValue) }
    }

    /** How long, in seconds, a reading stays magnified, see [magnifier] */
    public val magnifierSeconds: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("MAGNIFIER_DURATION_SECONDS", DEFAULT_MAGNIFIER_SECONDS)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("MAGNIFIER_DURATION_SECONDS", newValue) }
    }

    /**
     * Dims the main screen once nothing new was alerted or warned about, nor touched, for
     * [dimDisplaySeconds], to keep a phone mounted in a car from glaring at night
     */
    public val dimDisplay: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("DIM_DISPLAY", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("DIM_DISPLAY", newValue) }
    }

    /** How long, in seconds, the main screen waits before dimming, see [dimDisplay] */
    public val dimDisplaySeconds: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("DIM_DISPLAY_SECONDS", DEFAULT_DIM_DISPLAY_SECONDS)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("DIM_DISPLAY_SECONDS", newValue) }
    }

    /**
     * What the brightness the system would give the screen is divided by when dimmed, rounded
     * down, see [dimDisplay]
     */
    public val dimDisplayDivisor: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("DIM_DISPLAY_DIVIDED_BY", DEFAULT_DIM_DISPLAY_DIVISOR)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("DIM_DISPLAY_DIVIDED_BY", newValue) }
    }

    /**
     * While dimmed, and shortly after, the proximity sensor covered (a pocket) blocks the main
     * screen's touches, see [dimDisplay], and after a while lets the screen turn off
     */
    public val pocketGuard: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("POCKET_GUARD", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("POCKET_GUARD", newValue) }
    }

    /** Whether the app is light or dark, [Theme.SYSTEM] following the phone's own setting */
    public val theme: MutableStateFlow<Theme> = observableStateFlow(
        sharedPreferences
            .getString("THEME", null)
            ?.let { name -> Theme.entries.firstOrNull { it.name == name } }
            ?: Theme.SYSTEM
    ) { _, newValue ->
        sharedPreferences.edit { putString("THEME", newValue.name) }
    }

    public enum class Theme {
        SYSTEM,
        LIGHT,
        DARK,
    }

    /**
     * The tyre readouts' text size, multiplying the system's font size: 1 follows it. The readouts
     * still shrink from there when they don't fit the screen.
     */
    public val readoutTextScale: MutableStateFlow<Float> = observableStateFlow(
        sharedPreferences.getFloat("READOUT_TEXT_SCALE", 1f)
    ) { _, newValue ->
        sharedPreferences.edit { putFloat("READOUT_TEXT_SCALE", newValue) }
    }

    /**
     * Warns about a tyre losing at least [pressureLossMinDrop] kPa while riding. Experimental, so
     * off by default.
     */
    public val pressureLoss: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("PRESSURE_LOSS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("PRESSURE_LOSS", newValue) }
    }

    /** In kPa */
    public val pressureLossMinDrop: MutableStateFlow<Float> = observableStateFlow(
        sharedPreferences.getFloat("PRESSURE_LOSS_MIN_DROP_KPA", DEFAULT_PRESSURE_LOSS_MIN_DROP)
    ) { _, newValue ->
        sharedPreferences.edit { putFloat("PRESSURE_LOSS_MIN_DROP_KPA", newValue) }
    }

    /**
     * How the red and crimson tyre alerts sound, on top of their notifications. Was a switch for
     * the speech, whose off is [AlertSound.NONE].
     */
    public val alertSound: MutableStateFlow<AlertSound> = observableStateFlow(
        sharedPreferences
            .getString("ALERT_SOUND", null)
            ?.let { name -> AlertSound.entries.firstOrNull { it.name == name } }
            ?: if (sharedPreferences.getBoolean("SPOKEN_ALERTS", true)) AlertSound.SPEECH else AlertSound.NONE
    ) { oldValue, newValue ->
        sharedPreferences.edit {
            putString("ALERT_SOUND", newValue.name).remove("SPOKEN_ALERTS")
            // The sound being chosen, or the one being turned off
            (newValue.takeIf { it != AlertSound.NONE } ?: oldValue.takeIf { it != AlertSound.NONE })
                ?.also { putString("LAST_ALERT_SOUND", it.name) }
        }
    }

    /** The last [alertSound] other than [AlertSound.NONE], the one its switch turns back on */
    public val lastAlertSound: AlertSound
        get() = sharedPreferences
            .getString("LAST_ALERT_SOUND", null)
            ?.let { name -> AlertSound.entries.firstOrNull { it.name == name } }
            ?: alertSound.value.takeIf { it != AlertSound.NONE }
            ?: AlertSound.SPEECH

    public enum class AlertSound {
        /** Says what the alert is about */
        SPEECH,

        /** Plays a tone pattern, more urgent for a crimson alert */
        TONES,

        /** Only the notifications' sound */
        NONE,
    }

    /**
     * Vibrates along with the red and crimson tyre alerts' sound, in the same loops, harder for a
     * crimson one. Works with [AlertSound.NONE] too, vibrating on its own.
     */
    public val vibrateAlerts: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("VIBRATE_ALERTS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("VIBRATE_ALERTS", newValue) }
    }

    public val persistentScanning: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("PERSISTENT_SCANNING", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("PERSISTENT_SCANNING", newValue) }
    }

    /**
     * Whether persistent scanning was turned on once from its settings page, which explains when it
     * scans. The main screen's menu only offers its switch after that. Those who already use it,
     * from before this was stored, have done so.
     */
    public val persistentScanningSetUp: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean(
            "PERSISTENT_SCANNING_SET_UP",
            sharedPreferences.getBoolean("PERSISTENT_SCANNING", false),
        )
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("PERSISTENT_SCANNING_SET_UP", newValue) }
    }

    public val activateOnCableCharging: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_CABLE_CHARGING", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_CABLE_CHARGING", newValue) }
    }

    public val activateOnWirelessCharging: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_WIRELESS_CHARGING", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_WIRELESS_CHARGING", newValue) }
    }

    public val activateOnAndroidAuto: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_ANDROID_AUTO", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_ANDROID_AUTO", newValue) }
    }

    /** Scans while one of [activateBluetoothDevices] is connected */
    public val activateOnBluetooth: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_BLUETOOTH", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_BLUETOOTH", newValue) }
    }

    /**
     * Addresses of the Bluetooth devices picked for [activateOnBluetooth]. A device unpaired since
     * then stays in the set, so that it comes back selected once paired again.
     */
    public val activateBluetoothDevices: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("ACTIVATE_BLUETOOTH_DEVICES", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("ACTIVATE_BLUETOOTH_DEVICES", newValue) }
    }

    /** Scans while one of [beacons] is nearby */
    public val activateOnBeacon: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_BEACON", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_BEACON", newValue) }
    }

    /** The beacons of [activateOnBeacon], in the order they were added */
    public val beacons: MutableStateFlow<List<Beacon>> = observableStateFlow(
        sharedPreferences
            .getString("BEACONS", null)
            ?.let(::JSONArray)
            ?.let { array -> List(array.length()) { array.getJSONObject(it).asBeacon() } }
            .orEmpty()
    ) { _, newValue ->
        sharedPreferences.edit {
            putString("BEACONS", JSONArray(newValue.map { it.asJson() }).toString())
        }
    }

    /**
     * A nearby beacon overrides every suspend condition but the phone being idle: it tells
     * the vehicle is around, while a phone left still for a long time was likely forgotten in it
     */
    public val beaconOverridesSuspend: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("BEACON_OVERRIDES_SUSPEND", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("BEACON_OVERRIDES_SUSPEND", newValue) }
    }

    /**
     * The name of the BLE scan mode looking for [beacons] in the background, a debug option. Left
     * as a name: the modes belong to the scanner, the reader falls back on its default for an
     * unknown one.
     */
    public val beaconScanMode: MutableStateFlow<String?> = observableStateFlow(
        sharedPreferences.getString("BEACON_SCAN_MODE", null)
    ) { _, newValue ->
        sharedPreferences.edit { putString("BEACON_SCAN_MODE", newValue) }
    }

    /** [beaconScanMode] while [debugOptionsInUse], else null for the reader's default */
    public val beaconScanModeInUse: StateFlow<String?> =
        debugOptionsInUse.combineState(beaconScanMode) { inUse, mode -> mode.takeIf { inUse } }

    /**
     * The signal (dBm) from which one of the [beacons] counts as nearby, a debug option. Weaker
     * than that, it is likely someone else's or across the street.
     */
    public val beaconMinRssi: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("BEACON_MIN_RSSI", DEFAULT_BEACON_MIN_RSSI)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("BEACON_MIN_RSSI", newValue) }
    }

    /** [beaconMinRssi] while [debugOptionsInUse], else [DEFAULT_BEACON_MIN_RSSI] */
    public val beaconMinRssiInUse: StateFlow<Int> =
        debugOptionsInUse.combineState(beaconMinRssi) { inUse, rssi ->
            rssi.takeIf { inUse } ?: DEFAULT_BEACON_MIN_RSSI
        }

    /** Keeps scanning for [stayActiveMinutes] once every activate condition has ended */
    public val stayActive: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("STAY_ACTIVE", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("STAY_ACTIVE", newValue) }
    }

    public val stayActiveMinutes: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("STAY_ACTIVE_MINUTES", DEFAULT_STAY_ACTIVE_MINUTES)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("STAY_ACTIVE_MINUTES", newValue) }
    }

    /** When off, persistent scanning ignores the activate conditions and always scans */
    public val activateConditions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_CONDITIONS", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_CONDITIONS", newValue) }
    }

    /** When off, none of the suspend conditions applies, whatever each one is set to */
    public val suspendConditions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_CONDITIONS", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_CONDITIONS", newValue) }
    }

    /**
     * On by default: a phone idle for that long is not on a moving vehicle, it was left somewhere.
     * What "idle" means is [phoneIdleMechanism].
     */
    public val suspendScanningInDoze: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_IN_DOZE", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_IN_DOZE", newValue) }
    }

    /**
     * The name of the mechanism telling the phone is idle for [suspendScanningInDoze], a debug
     * option. Left as a name like [beaconScanMode]: the reader falls back on its default for an
     * unknown one.
     */
    public val phoneIdleMechanism: MutableStateFlow<String?> = observableStateFlow(
        sharedPreferences.getString("PHONE_IDLE_MECHANISM", null)
    ) { _, newValue ->
        sharedPreferences.edit { putString("PHONE_IDLE_MECHANISM", newValue) }
    }

    /** [phoneIdleMechanism] while [debugOptionsInUse], else null for the reader's default */
    public val phoneIdleMechanismInUse: StateFlow<String?> =
        debugOptionsInUse.combineState(phoneIdleMechanism) { inUse, mechanism -> mechanism.takeIf { inUse } }

    public val suspendScanningOnWifi: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_ON_WIFI", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_ON_WIFI", newValue) }
    }

    public val wifiExceptionEnabled: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("WIFI_EXCEPTION_ENABLED", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("WIFI_EXCEPTION_ENABLED", newValue) }
    }

    public val exceptedWifiSsids: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("EXCEPTED_WIFI_SSIDS", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("EXCEPTED_WIFI_SSIDS", newValue) }
    }

    /** Suspends scanning while one of [suspendBluetoothDevices] is connected */
    public val suspendScanningOnBluetooth: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_ON_BLUETOOTH", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_ON_BLUETOOTH", newValue) }
    }

    /** Same as [activateBluetoothDevices], for [suspendScanningOnBluetooth] */
    public val suspendBluetoothDevices: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("SUSPEND_BLUETOOTH_DEVICES", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("SUSPEND_BLUETOOTH_DEVICES", newValue) }
    }

    /**
     * A device recognized by its advertisements, by [address] or by [advertisedName] since some
     * phones fail to match a random address. [label] is the name the user gave it, never matched.
     */
    public data class Beacon(
        val address: String,
        val advertisedName: String?,
        val label: String?,
    ) {
        /** What the user reads: their own name for it first, the advertised one, else its address */
        val displayName: String get() = label ?: advertisedName ?: address
    }

    private fun JSONObject.asBeacon() = Beacon(
        address = getString("address"),
        advertisedName = optString("advertisedName").takeIf { has("advertisedName") },
        label = optString("label").takeIf { has("label") },
    )

    private fun Beacon.asJson() = JSONObject()
        .put("address", address)
        // put() with null removes the key, which is how a missing name is told apart
        .put("advertisedName", advertisedName)
        .put("label", label)

    private val packageInfo
        get() = appContext
            .packageManager
            .run {
                if (SDK_INT >= TIRAMISU) {
                    getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    getPackageInfo(appContext.packageName, 0)
                }
            }!!

    public val previousVersionCode: Long? = sharedPreferences
        .getLong("VC", Long.MIN_VALUE)
        .takeIf { it != Long.MIN_VALUE }

    public val currentVersionCode: Long = packageInfo.longVersionCode

    public val isFreshInstallation: Boolean =
        packageInfo.let { it.firstInstallTime == it.lastUpdateTime }

    init {
        if (currentVersionCode != previousVersionCode)
            sharedPreferences.edit { putLong("VC", currentVersionCode) }
    }

    public companion object {
        /** See [beaconMinRssi] */
        public const val DEFAULT_BEACON_MIN_RSSI: Int = -85
        private const val DEFAULT_STAY_ACTIVE_MINUTES = 10
        private const val DEFAULT_PRESSURE_LOSS_MIN_DROP = 7f
        private const val DEFAULT_MAGNIFIER_SECONDS = 5
        private const val DEFAULT_DIM_DISPLAY_SECONDS = 60
        private const val DEFAULT_DIM_DISPLAY_DIVISOR = 50
    }
}
