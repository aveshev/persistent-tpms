package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSpeaker.Ongoing
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/** Only what the loops follow whatever the speech: the speech itself needs text-to-speech */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AlertSpeakerTest {

    private lateinit var alertSound: MutableStateFlow<AlertSound>
    private lateinit var isScanningTyres: MutableStateFlow<Boolean>
    private lateinit var isRepeating: MutableStateFlow<Boolean>
    private lateinit var silenced: MutableStateFlow<AlertLevel?>

    @Before
    fun setup() {
        // Nothing is ever said nor vibrated, the speech engine isn't started
        alertSound = MutableStateFlow(NONE)
        isScanningTyres = MutableStateFlow(true)
        isRepeating = MutableStateFlow(true)
        silenced = MutableStateFlow(null)
    }

    private fun TestScope.test() = AlertSpeaker(
        mockk<AppPreferences> {
            every { alertSound } returns this@AlertSpeakerTest.alertSound
            every { vibrateAlerts } returns MutableStateFlow(false)
        },
        isScanningTyres,
        isRepeating,
        silenced,
        backgroundScope,
    ).also { runCurrent() }

    @Test
    fun `an alert goes on until it stops, whether the speech is off or silenced`() = runTest {
        silenced.value = CRIMSON
        val speaker = test()
        speaker.red(TAG, CAR, PRESSURE)
        assertEquals(mapOf(TAG to Ongoing(CAR, RED)), speaker.ongoing.value)
        speaker.crimson(TAG, CAR, PRESSURE)
        speaker.red(MOTORCYCLE_TAG, MOTORCYCLE, PRESSURE)
        assertEquals(
            mapOf(TAG to Ongoing(CAR, CRIMSON), MOTORCYCLE_TAG to Ongoing(MOTORCYCLE, RED)),
            speaker.ongoing.value,
        )
        speaker.stop(TAG, isDismissed = true)
        assertEquals(mapOf(MOTORCYCLE_TAG to Ongoing(MOTORCYCLE, RED)), speaker.ongoing.value)
    }

    @Test
    fun `the alerts end with the loops, and only a new one starts again`() = runTest {
        val speaker = test()
        speaker.red(TAG, CAR, PRESSURE)
        isRepeating.value = false
        runCurrent()
        assertEquals(emptyMap(), speaker.ongoing.value)
        // Said once, not repeated
        speaker.crimson(TAG, CAR, PRESSURE)
        assertEquals(emptyMap(), speaker.ongoing.value)
        isRepeating.value = true
        runCurrent()
        assertEquals(emptyMap(), speaker.ongoing.value)
        speaker.crimson(TAG, CAR, PRESSURE)
        assertEquals(mapOf(TAG to Ongoing(CAR, CRIMSON)), speaker.ongoing.value)
    }

    @Test
    fun `the alerts end with the scans`() = runTest {
        val speaker = test()
        speaker.red(TAG, CAR, PRESSURE)
        isScanningTyres.value = false
        runCurrent()
        assertEquals(emptyMap(), speaker.ongoing.value)
    }

    @Test
    fun `a vehicle alerts with its worst alert going on`() = runTest {
        val speaker = test()
        val levels = AlertingVehiclesUseCase(speaker).levels
        speaker.red(TAG, CAR, PRESSURE)
        speaker.crimson(OTHER_TAG, CAR, BATTERY)
        speaker.red(MOTORCYCLE_TAG, MOTORCYCLE, PRESSURE)
        assertEquals(mapOf(CAR to CRIMSON, MOTORCYCLE to RED), levels.first())
        speaker.stop(OTHER_TAG)
        assertEquals(mapOf(CAR to RED, MOTORCYCLE to RED), levels.first())
    }

    private companion object {
        const val TAG = "car-front-left-pressure"
        const val OTHER_TAG = "car-front-left-battery"
        const val MOTORCYCLE_TAG = "motorcycle-front-pressure"
        val CAR: UUID = UUID.randomUUID()
        val MOTORCYCLE: UUID = UUID.randomUUID()
    }
}
