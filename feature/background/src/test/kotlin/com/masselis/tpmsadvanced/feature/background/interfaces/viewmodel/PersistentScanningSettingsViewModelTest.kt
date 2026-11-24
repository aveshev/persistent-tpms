package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
internal class PersistentScanningSettingsViewModelTest {

    private lateinit var activateConditions: MutableStateFlow<Boolean>
    private lateinit var activateOnCableCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnWirelessCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnAndroidAuto: MutableStateFlow<Boolean>
    private lateinit var activateOnBluetooth: MutableStateFlow<Boolean>
    private lateinit var activateBluetoothDevices: MutableStateFlow<Set<String>>
    private lateinit var activateOnBeacon: MutableStateFlow<Boolean>
    private lateinit var beacons: MutableStateFlow<List<Beacon>>
    private lateinit var activateOnKnownSensor: MutableStateFlow<Boolean>

    private fun test() = PersistentScanningSettingsViewModel(
        mockk<AppPreferences>(relaxed = true) {
            every { activateConditions } returns this@PersistentScanningSettingsViewModelTest.activateConditions
            every { activateOnCableCharging } returns this@PersistentScanningSettingsViewModelTest.activateOnCableCharging
            every { activateOnWirelessCharging } returns this@PersistentScanningSettingsViewModelTest.activateOnWirelessCharging
            every { activateOnAndroidAuto } returns this@PersistentScanningSettingsViewModelTest.activateOnAndroidAuto
            every { activateOnBluetooth } returns this@PersistentScanningSettingsViewModelTest.activateOnBluetooth
            every { activateBluetoothDevices } returns this@PersistentScanningSettingsViewModelTest.activateBluetoothDevices
            every { activateOnBeacon } returns this@PersistentScanningSettingsViewModelTest.activateOnBeacon
            every { beacons } returns this@PersistentScanningSettingsViewModelTest.beacons
            every { activateOnKnownSensor } returns this@PersistentScanningSettingsViewModelTest.activateOnKnownSensor
        },
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<BluetoothDevicesUseCase>(relaxed = true),
        mockk(relaxed = true),
    )

    @Before
    fun setup() {
        activateConditions = MutableStateFlow(true)
        activateOnCableCharging = MutableStateFlow(false)
        activateOnWirelessCharging = MutableStateFlow(false)
        activateOnAndroidAuto = MutableStateFlow(false)
        activateOnBluetooth = MutableStateFlow(true)
        activateBluetoothDevices = MutableStateFlow(setOf("CAR"))
        activateOnBeacon = MutableStateFlow(false)
        beacons = MutableStateFlow(emptyList())
        activateOnKnownSensor = MutableStateFlow(false)
    }

    @Test
    fun `toggling a Bluetooth device adds then removes it`() {
        val viewModel = test()
        viewModel.toggleBluetoothDevice(viewModel.activateBluetooth, "HEADSET")
        assertEquals(setOf("CAR", "HEADSET"), activateBluetoothDevices.value)
        viewModel.toggleBluetoothDevice(viewModel.activateBluetooth, "CAR")
        assertEquals(setOf("HEADSET"), activateBluetoothDevices.value)
    }

    @Test
    fun `the Bluetooth condition stays on with a selected device paired`() {
        val viewModel = test()
        viewModel.disableBluetoothIfNoneSelected(viewModel.activateBluetooth, listOf(device("CAR")))
        assertTrue(activateOnBluetooth.value)
    }

    @Test
    fun `the Bluetooth condition turns off when none of its devices is paired any more`() {
        val viewModel = test()
        viewModel.disableBluetoothIfNoneSelected(viewModel.activateBluetooth, listOf(device("HEADSET")))
        assertFalse(activateOnBluetooth.value)
        // Kept, so that the device comes back selected once paired again
        assertEquals(setOf("CAR"), activateBluetoothDevices.value)
    }

    @Test
    fun `the Bluetooth condition is left as it is while Bluetooth is off`() {
        val viewModel = test()
        viewModel.disableBluetoothIfNoneSelected(viewModel.activateBluetooth, null)
        assertTrue(activateOnBluetooth.value)
    }

    @Test
    fun `the Bluetooth condition alone keeps the activate conditions on`() {
        test().disableActivateConditionsIfNoneSelected()
        assertTrue(activateConditions.value)
    }

    @Test
    fun `the Bluetooth condition without any device doesn't keep the activate conditions on`() {
        activateBluetoothDevices.value = emptySet()
        test().disableActivateConditionsIfNoneSelected()
        assertFalse(activateConditions.value)
    }

    @Test
    fun `the beacon condition alone keeps the activate conditions on`() {
        activateOnBluetooth.value = false
        activateOnBeacon.value = true
        beacons.value = listOf(Beacon("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", label = null))
        test().disableActivateConditionsIfNoneSelected()
        assertTrue(activateConditions.value)
    }

    @Test
    fun `the beacon condition without any beacon doesn't keep the activate conditions on`() {
        activateOnBluetooth.value = false
        activateOnBeacon.value = true
        test().disableActivateConditionsIfNoneSelected()
        assertFalse(activateConditions.value)
    }

    @Test
    fun `the known sensor condition alone keeps the activate conditions on`() {
        activateOnBluetooth.value = false
        activateOnKnownSensor.value = true
        test().disableActivateConditionsIfNoneSelected()
        assertTrue(activateConditions.value)
    }

    private fun device(address: String) = PairedDevice(address, address, isAudio = true)
}
