package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.TONES
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AppPreferencesTest {

    private lateinit var stored: MutableMap<String, Any?>
    private lateinit var writes: MutableList<String>
    private lateinit var packageInfo: PackageInfo
    private lateinit var context: Context

    @Before
    fun setup() {
        stored = mutableMapOf()
        writes = mutableListOf()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        fun put(key: String, value: Any?) = editor.also {
            stored[key] = value
            writes += key
        }
        editor.apply {
            every { putBoolean(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { putString(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { putInt(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { putLong(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { putFloat(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { putStringSet(any(), any()) } answers { put(firstArg(), secondArg()) }
            every { remove(any()) } answers {
                stored.remove(firstArg())
                editor
            }
        }
        // Reading a key with another type than it was written with fails, like in the real ones
        val preferences = mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getBoolean(any(), any()) } answers { stored[firstArg()] as Boolean? ?: secondArg() }
            every { getString(any(), any()) } answers { stored[firstArg()] as String? ?: secondArg() }
            every { getInt(any(), any()) } answers { stored[firstArg()] as Int? ?: secondArg() }
            every { getLong(any(), any()) } answers { stored[firstArg()] as Long? ?: secondArg() }
            every { getFloat(any(), any()) } answers { stored[firstArg()] as Float? ?: secondArg() }
            every { getStringSet(any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                (stored[firstArg()] as Set<String>? ?: secondArg<Set<String>>()).toMutableSet()
            }
        }
        packageInfo = mockk { every { longVersionCode } returns CURRENT_VERSION }
        context = mockk {
            every { getSharedPreferences("APP", Context.MODE_PRIVATE) } returns preferences
            every { packageName } returns "package"
            every { packageManager } returns mockk {
                every { getPackageInfo("package", 0) } returns packageInfo
            }
        }
        mockkStatic(APP_CONTEXT)
        every { appContext } returns context
    }

    @After
    fun tearDown() {
        unmockkStatic(APP_CONTEXT)
    }

    private fun test() = AppPreferences(context)

    // Every preference at once, since they're written by hand one after the other
    private fun preferences() = AppPreferences::class
        .memberProperties
        .filter { it.visibility == KVisibility.PUBLIC && it.returnType.classifier == MutableStateFlow::class }

    @Suppress("UNCHECKED_CAST")
    private fun AppPreferences.flow(name: String) = preferences()
        .first { it.name == name }
        .get(this) as MutableStateFlow<Any?>

    private fun Any?.changed(): Any = when (this) {
        is Boolean -> not()
        is Int -> plus(1)
        is Float -> plus(1f)
        is Set<*> -> setOf("changed")
        is List<*> -> listOf(AppPreferences.Beacon("changed", null, null))
        is Enum<*> -> declaringJavaClass.enumConstants.first { it != this }
        else -> "changed"
    }

    @Test
    fun `there's a preference to check`() {
        assertTrue(preferences().size > 40)
    }

    @Test
    fun `every preference comes back as it was set`() {
        preferences().forEach { property ->
            val changed = test().flow(property.name).run {
                value.changed().also { value = it }
            }
            assertEquals(changed, test().flow(property.name).value, property.name)
        }
    }

    @Test
    fun `every preference has keys of its own`() {
        preferences()
            .flatMap { property ->
                writes.clear()
                test().flow(property.name).run { value = value.changed() }
                assertTrue(writes.isNotEmpty(), property.name)
                writes.filterNot { it == "VC" }.distinct().map { it to property.name }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (key, properties) -> assertEquals(1, properties.size, "$key: $properties") }
    }

    @Test
    fun `the debug options start off`() {
        assertFalse(test().debugOptions.value)
    }

    @Test
    fun `the debug options start on when the sensor ID was shown`() {
        stored["SHOW_SENSOR_ID"] = true
        assertTrue(test().debugOptions.value)
    }

    @Test
    fun `the debug options' own value wins over the sensor ID's`() {
        stored["SHOW_SENSOR_ID"] = true
        stored["DEBUG_OPTIONS"] = false
        assertFalse(test().debugOptions.value)
    }

    @Test
    fun `the debug options only apply to a developer`() {
        test().apply {
            assertFalse(debugOptionsInUse.value)
            debugOptions.value = true
            assertFalse(debugOptionsInUse.value)
            developer.value = true
            assertTrue(debugOptionsInUse.value)
            debugOptions.value = false
            assertFalse(debugOptionsInUse.value)
        }
    }

    @Test
    fun `hiding the debug options keeps their value`() {
        test().apply {
            developer.value = true
            debugOptions.value = true
            developer.value = false
            assertTrue(debugOptions.value)
            developer.value = true
            assertTrue(debugOptionsInUse.value)
        }
    }

    @Test
    fun `the debug options' values only apply while they're in use`() {
        test().apply {
            beaconScanMode.value = "SCAN_MODE_LOW_LATENCY"
            phoneIdleMechanism.value = "DEEP_DOZE"
            beaconMinRssi.value = -60
            assertNull(beaconScanModeInUse.value)
            assertNull(phoneIdleMechanismInUse.value)
            assertEquals(AppPreferences.DEFAULT_BEACON_MIN_RSSI, beaconMinRssiInUse.value)

            developer.value = true
            debugOptions.value = true
            assertEquals("SCAN_MODE_LOW_LATENCY", beaconScanModeInUse.value)
            assertEquals("DEEP_DOZE", phoneIdleMechanismInUse.value)
            assertEquals(-60, beaconMinRssiInUse.value)
        }
    }

    @Test
    fun `alerts speak by default`() {
        assertEquals(SPEECH, test().alertSound.value)
    }

    @Test
    fun `the former spoken alerts' switch decides the sound`() {
        stored["SPOKEN_ALERTS"] = false
        assertEquals(NONE, test().alertSound.value)
        stored["SPOKEN_ALERTS"] = true
        assertEquals(SPEECH, test().alertSound.value)
    }

    @Test
    fun `the alert sound wins over the former switch`() {
        stored["SPOKEN_ALERTS"] = false
        stored["ALERT_SOUND"] = TONES.name
        assertEquals(TONES, test().alertSound.value)
    }

    @Test
    fun `an alert sound this version doesn't know goes back to the former switch`() {
        stored["ALERT_SOUND"] = "FROM_A_NEWER_VERSION"
        stored["SPOKEN_ALERTS"] = false
        assertEquals(NONE, test().alertSound.value)
    }

    @Test
    fun `setting the alert sound drops the former switch`() {
        stored["SPOKEN_ALERTS"] = true
        test().alertSound.value = TONES
        assertFalse("SPOKEN_ALERTS" in stored)
        assertEquals(TONES.name, stored["ALERT_SOUND"])
    }

    @Test
    fun `the sound chosen is the one its switch turns back on`() {
        test().apply {
            alertSound.value = TONES
            assertEquals(TONES, lastAlertSound)
            alertSound.value = NONE
            assertEquals(TONES, lastAlertSound)
        }
        assertEquals(TONES, test().lastAlertSound)
    }

    @Test
    fun `turning off the sound in use remembers it`() {
        stored["ALERT_SOUND"] = TONES.name
        test().alertSound.value = NONE
        assertEquals(TONES.name, stored["LAST_ALERT_SOUND"])
    }

    @Test
    fun `turning off a sound already off remembers nothing`() {
        stored["ALERT_SOUND"] = NONE.name
        test().alertSound.value = NONE
        assertFalse("LAST_ALERT_SOUND" in stored)
    }

    @Test
    fun `without a last sound its switch turns on the current one, or speech`() {
        stored["ALERT_SOUND"] = TONES.name
        assertEquals(TONES, test().lastAlertSound)
        stored["ALERT_SOUND"] = NONE.name
        assertEquals(SPEECH, test().lastAlertSound)
    }

    @Test
    fun `a last sound this version doesn't know is ignored`() {
        stored["ALERT_SOUND"] = TONES.name
        stored["LAST_ALERT_SOUND"] = "FROM_A_NEWER_VERSION"
        assertEquals(TONES, test().lastAlertSound)
    }

    @Test
    fun `the app follows the phone's theme by default`() {
        assertEquals(Theme.SYSTEM, test().theme.value)
    }

    @Test
    fun `a theme this version doesn't know is the phone's`() {
        stored["THEME"] = "FROM_A_NEWER_VERSION"
        assertEquals(Theme.SYSTEM, test().theme.value)
    }

    @Test
    fun `a first launch has no previous version and stores the current one`() {
        assertNull(test().previousVersionCode)
        assertEquals(CURRENT_VERSION, stored["VC"])
    }

    @Test
    fun `an update tells the previous version and stores the current one`() {
        stored["VC"] = CURRENT_VERSION - 1
        assertEquals(CURRENT_VERSION - 1, test().previousVersionCode)
        assertEquals(CURRENT_VERSION, stored["VC"])
    }

    @Test
    fun `another launch of the version doesn't write it again`() {
        stored["VC"] = CURRENT_VERSION
        test().apply {
            assertEquals(CURRENT_VERSION, previousVersionCode)
            assertEquals(CURRENT_VERSION, currentVersionCode)
        }
        assertEquals(emptyList(), writes)
    }

    @Test
    fun `an installation is fresh until its first update`() {
        packageInfo.firstInstallTime = 1
        packageInfo.lastUpdateTime = 1
        assertTrue(test().isFreshInstallation)
        packageInfo.lastUpdateTime = 2
        assertFalse(test().isFreshInstallation)
    }

    @Test
    fun `a beacon reads as its label, else its name, else its address`() {
        assertEquals("Label", AppPreferences.Beacon("Address", "Name", "Label").displayName)
        assertEquals("Name", AppPreferences.Beacon("Address", "Name", null).displayName)
        assertEquals("Address", AppPreferences.Beacon("Address", null, null).displayName)
    }

    @Test
    fun `no beacon is stored at first`() {
        assertEquals(emptyList(), test().beacons.value)
        assertFalse("BEACONS" in stored)
    }

    @Test
    fun `beacons come back in their order, with their names or without`() {
        val beacons = listOf(
            AppPreferences.Beacon("AA:BB", "Name", "Label"),
            AppPreferences.Beacon("CC:DD", null, "Label"),
            AppPreferences.Beacon("EE:FF", "Name", null),
            AppPreferences.Beacon("00:11", null, null),
        )
        test().beacons.value = beacons
        assertEquals(beacons, test().beacons.value)
    }

    @Test
    fun `removing the last beacon leaves none`() {
        test().beacons.value = listOf(AppPreferences.Beacon("AA:BB", null, null))
        test().beacons.value = emptyList()
        assertEquals(emptyList(), test().beacons.value)
    }

    private companion object {
        const val APP_CONTEXT = "com.masselis.tpmsadvanced.core.common.CoreCommonInitializerKt"
        const val CURRENT_VERSION = 5L
    }
}
