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

    public val showSensorId: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("SHOW_SENSOR_ID", false)

    /**
     * Whether the switch of the debug options is shown in the app settings, revealed and hidden
     * again by tapping the version. While hidden, [debugOptions] keeps its value but doesn't apply.
     */
    public val developer: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("DEVELOPER", false)

    /**
     * The switch of the debug options: while off, none of them is shown, whatever is selected.
     * On by default when the sensor ID, the only one which predates it, was shown. What applies is
     * [debugOptionsInUse].
     */
    public val debugOptions: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("DEBUG_OPTIONS", sharedPreferences.getBoolean("SHOW_SENSOR_ID", false))

    /** [debugOptions], unless [developer] is off: what the debug options check before applying */
    public val debugOptionsInUse: StateFlow<Boolean> = developer.combineState(debugOptions, Boolean::and)

    /** Shows the status byte of the last packet under each tyre, bit by bit */
    public val showSensorFlags: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("SHOW_SENSOR_FLAGS", false)

    /** Shows the activities the phone detects, with their confidence, on the home screen */
    public val showDetectedActivities: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SHOW_DETECTED_ACTIVITIES", false)

    /**
     * Speaks the status of persistent scanning (idle, active, suspended, off) each time it changes,
     * unless the main screen is shown
     */
    public val announceScanStatus: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ANNOUNCE_SCAN_STATUS", false)

    /**
     * Switches the tyre scan to balanced after its first reading, as it used to, instead of keeping
     * it at low latency the whole time it runs (main screen shown, or persistent scanning active)
     */
    public val balancedAfterFirstReading: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("BALANCED_AFTER_FIRST_READING", false)

    public val showTimeSinceUpdate: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SHOW_TIME_SINCE_UPDATE", true)

    /** A voltage getting low or alarming is shown whatever this is set to */
    public val showBatteryVoltage: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SHOW_BATTERY_VOLTAGE", false)

    /** Tapping a tyre's part of the main screen shows its reading magnified, to read it on a shaking phone */
    public val magnifier: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("MAGNIFIER", true)

    /** How long, in seconds, a reading stays magnified, see [magnifier] */
    public val magnifierSeconds: MutableStateFlow<Int> =
        sharedPreferences.intFlow("MAGNIFIER_DURATION_SECONDS", DEFAULT_MAGNIFIER_SECONDS)

    /**
     * Dims the main screen once nothing new was alerted or warned about, nor touched, for
     * [dimDisplaySeconds], to keep a phone mounted in a car from glaring at night
     */
    public val dimDisplay: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("DIM_DISPLAY", true)

    /** How long, in seconds, the main screen waits before dimming, see [dimDisplay] */
    public val dimDisplaySeconds: MutableStateFlow<Int> =
        sharedPreferences.intFlow("DIM_DISPLAY_SECONDS", DEFAULT_DIM_DISPLAY_SECONDS)

    /**
     * What the brightness the system would give the screen is divided by when dimmed, rounded
     * down, see [dimDisplay]
     */
    public val dimDisplayDivisor: MutableStateFlow<Int> =
        sharedPreferences.intFlow("DIM_DISPLAY_DIVIDED_BY", DEFAULT_DIM_DISPLAY_DIVISOR)

    /**
     * While dimmed, and shortly after, the proximity sensor covered (a pocket) blocks the main
     * screen's touches, see [dimDisplay], and after a while lets the screen turn off
     */
    public val pocketGuard: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("POCKET_GUARD", true)

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
    public val readoutTextScale: MutableStateFlow<Float> = sharedPreferences.floatFlow("READOUT_TEXT_SCALE", 1f)

    /**
     * Warns about a tyre losing at least [pressureLossMinDrop] kPa while riding. Experimental, so
     * off by default.
     */
    public val pressureLoss: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("PRESSURE_LOSS", false)

    /** In kPa */
    public val pressureLossMinDrop: MutableStateFlow<Float> =
        sharedPreferences.floatFlow("PRESSURE_LOSS_MIN_DROP_KPA", DEFAULT_PRESSURE_LOSS_MIN_DROP)

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
    public val vibrateAlerts: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("VIBRATE_ALERTS", false)

    public val persistentScanning: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("PERSISTENT_SCANNING", false)

    /**
     * Whether persistent scanning was turned on once from its settings page, which explains when it
     * scans. The main screen's menu only offers its switch after that. Those who already use it,
     * from before this was stored, have done so.
     */
    public val persistentScanningSetUp: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow(
        "PERSISTENT_SCANNING_SET_UP",
        sharedPreferences.getBoolean("PERSISTENT_SCANNING", false),
    )

    public val activateOnCableCharging: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ACTIVATE_ON_CABLE_CHARGING", true)

    public val activateOnWirelessCharging: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ACTIVATE_ON_WIRELESS_CHARGING", true)

    public val activateOnAndroidAuto: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ACTIVATE_ON_ANDROID_AUTO", true)

    /** Scans while one of [activateBluetoothDevices] is connected */
    public val activateOnBluetooth: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ACTIVATE_ON_BLUETOOTH", false)

    /**
     * Addresses of the Bluetooth devices picked for [activateOnBluetooth]. A device unpaired since
     * then stays in the set, so that it comes back selected once paired again.
     */
    public val activateBluetoothDevices: MutableStateFlow<Set<String>> =
        sharedPreferences.stringSetFlow("ACTIVATE_BLUETOOTH_DEVICES")

    /** Scans while one of [beacons] is nearby */
    public val activateOnBeacon: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("ACTIVATE_ON_BEACON", false)

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
    public val beaconOverridesSuspend: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("BEACON_OVERRIDES_SUSPEND", false)

    /**
     * The name of the BLE scan mode looking for [beacons] in the background, a debug option. Left
     * as a name: the modes belong to the scanner, the reader falls back on its default for an
     * unknown one.
     */
    public val beaconScanMode: MutableStateFlow<String?> = sharedPreferences.stringFlow("BEACON_SCAN_MODE")

    /** [beaconScanMode] while [debugOptionsInUse], else null for the reader's default */
    public val beaconScanModeInUse: StateFlow<String?> =
        debugOptionsInUse.combineState(beaconScanMode) { inUse, mode -> mode.takeIf { inUse } }

    /**
     * The signal (dBm) from which one of the [beacons] counts as nearby, a debug option. Weaker
     * than that, it is likely someone else's or across the street.
     */
    public val beaconMinRssi: MutableStateFlow<Int> =
        sharedPreferences.intFlow("BEACON_MIN_RSSI", DEFAULT_BEACON_MIN_RSSI)

    /** [beaconMinRssi] while [debugOptionsInUse], else [DEFAULT_BEACON_MIN_RSSI] */
    public val beaconMinRssiInUse: StateFlow<Int> =
        debugOptionsInUse.combineState(beaconMinRssi) { inUse, rssi ->
            rssi.takeIf { inUse } ?: DEFAULT_BEACON_MIN_RSSI
        }

    /** Keeps scanning for [stayActiveMinutes] once every activate condition has ended */
    public val stayActive: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("STAY_ACTIVE", false)

    public val stayActiveMinutes: MutableStateFlow<Int> =
        sharedPreferences.intFlow("STAY_ACTIVE_MINUTES", DEFAULT_STAY_ACTIVE_MINUTES)

    /** When off, persistent scanning ignores the activate conditions and always scans */
    public val activateConditions: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("ACTIVATE_CONDITIONS", true)

    /** When off, none of the suspend conditions applies, whatever each one is set to */
    public val suspendConditions: MutableStateFlow<Boolean> = sharedPreferences.booleanFlow("SUSPEND_CONDITIONS", true)

    /**
     * On by default: a phone idle for that long is not on a moving vehicle, it was left somewhere.
     * What "idle" means is [phoneIdleMechanism].
     */
    public val suspendScanningInDoze: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SUSPEND_SCANNING_IN_DOZE", true)

    /**
     * The name of the mechanism telling the phone is idle for [suspendScanningInDoze], a debug
     * option. Left as a name like [beaconScanMode]: the reader falls back on its default for an
     * unknown one.
     */
    public val phoneIdleMechanism: MutableStateFlow<String?> = sharedPreferences.stringFlow("PHONE_IDLE_MECHANISM")

    /** [phoneIdleMechanism] while [debugOptionsInUse], else null for the reader's default */
    public val phoneIdleMechanismInUse: StateFlow<String?> =
        debugOptionsInUse.combineState(phoneIdleMechanism) { inUse, mechanism -> mechanism.takeIf { inUse } }

    public val suspendScanningOnWifi: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SUSPEND_SCANNING_ON_WIFI", false)

    public val wifiExceptionEnabled: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("WIFI_EXCEPTION_ENABLED", false)

    public val exceptedWifiSsids: MutableStateFlow<Set<String>> = sharedPreferences.stringSetFlow("EXCEPTED_WIFI_SSIDS")

    /** Suspends scanning while one of [suspendBluetoothDevices] is connected */
    public val suspendScanningOnBluetooth: MutableStateFlow<Boolean> =
        sharedPreferences.booleanFlow("SUSPEND_SCANNING_ON_BLUETOOTH", false)

    /** Same as [activateBluetoothDevices], for [suspendScanningOnBluetooth] */
    public val suspendBluetoothDevices: MutableStateFlow<Set<String>> =
        sharedPreferences.stringSetFlow("SUSPEND_BLUETOOTH_DEVICES")

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
