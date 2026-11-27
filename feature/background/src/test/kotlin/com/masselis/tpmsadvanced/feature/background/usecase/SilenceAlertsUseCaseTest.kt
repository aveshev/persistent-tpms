package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSilenceUseCase.Silence
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSpeaker.Ongoing
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
internal class SilenceAlertsUseCaseTest {

    private lateinit var alertSound: MutableStateFlow<AlertSound>
    private lateinit var vibrateAlerts: MutableStateFlow<Boolean>
    private lateinit var spoken: MutableStateFlow<AlertLevel?>
    private lateinit var ongoing: MutableStateFlow<Map<String, Ongoing>>
    private lateinit var silence: MutableStateFlow<Silence?>

    private val vehicle = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Before
    fun setup() {
        alertSound = MutableStateFlow(SPEECH)
        vibrateAlerts = MutableStateFlow(false)
        spoken = MutableStateFlow(null)
        ongoing = MutableStateFlow(emptyMap())
        silence = MutableStateFlow(null)
    }

    private val speaker
        get() = mockk<AlertSpeaker> {
            every { level } returns spoken
            every { this@mockk.ongoing } returns this@SilenceAlertsUseCaseTest.ongoing
        }

    private val alertSilence
        get() = mockk<AlertSilenceUseCase> {
            every { this@mockk.silence } returns this@SilenceAlertsUseCaseTest.silence
        }

    private fun test() = SilenceAlertsUseCase(
        mockk<AppPreferences> {
            every { alertSound } returns this@SilenceAlertsUseCaseTest.alertSound
            every { vibrateAlerts } returns this@SilenceAlertsUseCaseTest.vibrateAlerts
        },
        speaker,
        alertSilence,
    )

    private fun alerting(vararg levels: Pair<UUID, AlertLevel>) {
        ongoing.value = levels.withIndex().associate { (index, it) -> "tag$index" to Ongoing(it.first, it.second) }
    }

    @Test
    fun `an alert being said offers to silence it`() = runTest {
        spoken.value = RED
        alerting(vehicle to RED)
        assertEquals(State.Offer(isCritical = false), test().button.first())
    }

    @Test
    fun `an alert going on with the sound off offers to ignore it`() = runTest {
        alertSound.value = NONE
        alerting(vehicle to CRIMSON)
        assertEquals(State.Offer(isCritical = true, isIgnoring = true), test().button.first())
    }

    @Test
    fun `an alert going on after its speech was silenced offers to ignore it`() = runTest {
        alerting(vehicle to RED)
        assertEquals(State.Offer(isCritical = false, isIgnoring = true), test().button.first())
    }

    @Test
    fun `the sound off with nothing going on shows nothing`() = runTest {
        alertSound.value = NONE
        assertEquals(State.Disabled, test().button.first())
        silence.value = Silence(RED, 1L)
        assertEquals(State.Disabled, test().button.first())
    }

    @Test
    fun `vibrating alerts are silenced, not ignored`() = runTest {
        alertSound.value = NONE
        vibrateAlerts.value = true
        spoken.value = RED
        alerting(vehicle to RED)
        assertEquals(State.Offer(isCritical = false), test().button.first())
    }

    @Test
    fun `ignored alerts say so`() = runTest {
        alertSound.value = NONE
        alerting(vehicle to RED)
        silence.value = Silence(RED, 1L)
        assertEquals(State.Silenced(isCritical = false, until = 1L, isIgnoring = true), test().button.first())
    }

    @Test
    fun `a critical alert gets through ignored red ones`() = runTest {
        alertSound.value = NONE
        alerting(vehicle to RED, other to CRIMSON)
        silence.value = Silence(RED, 1L)
        assertEquals(State.Offer(isCritical = true, isIgnoring = true), test().button.first())
    }

    @Test
    fun `Android Auto only follows the speech`() = runTest {
        alertSound.value = NONE
        alerting(vehicle to RED)
        assertEquals(State.Disabled, test().state.first())
        alertSound.value = SPEECH
        assertEquals(State.Idle, test().state.first())
    }

    @Test
    fun `silenced alerts don't keep the screen bright, those above the silence do`() = runTest {
        val useCase = AlertingVehiclesUseCase(speaker, alertSilence)
        alerting(vehicle to RED, other to CRIMSON)
        assertEquals(mapOf(vehicle to RED, other to CRIMSON), useCase.unsilenced.first())
        silence.value = Silence(RED, 1L)
        assertEquals(mapOf(other to CRIMSON), useCase.unsilenced.first())
        silence.value = Silence(CRIMSON, 1L)
        assertEquals(emptyMap(), useCase.unsilenced.first())
        // The picker still blinks for them all
        assertEquals(mapOf(vehicle to RED, other to CRIMSON), useCase.levels.first())
    }
}
