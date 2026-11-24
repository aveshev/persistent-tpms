package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class KnownSensorPresenceUseCaseTest {

    private lateinit var stayActive: MutableStateFlow<Boolean>
    private lateinit var stayActiveMinutes: MutableStateFlow<Int>

    @Before
    fun setup() {
        stayActive = MutableStateFlow(false)
        stayActiveMinutes = MutableStateFlow(10)
    }

    context(scope: TestScope)
    private fun test() = KnownSensorPresenceUseCase(
        mockk<AppPreferences> {
            every { stayActive } returns this@KnownSensorPresenceUseCaseTest.stayActive
            every { stayActiveMinutes } returns this@KnownSensorPresenceUseCaseTest.stayActiveMinutes
        },
        scope.testScheduler.timeSource,
        // The virtual time, as the readings' wall clock
        { scope.currentTime / 1000.0 + START },
    )

    /** A reading received right now, on the use case's wall clock */
    context(scope: TestScope)
    private fun reading(rssi: Int? = LOUD, ageSeconds: Double = 0.0) =
        TyreAtmosphere(scope.currentTime / 1000.0 + START - ageSeconds, 1, 230f.kpa, 20f.celsius, rssi = rssi)

    @Test
    fun `a reading makes the sensors present, for the hold after it`() = runTest {
        val useCase = test()
        useCase.isPresent.test {
            assertEquals(false, awaitItem())
            useCase.heard(reading())
            assertEquals(true, awaitItem())
            delay(KnownSensorPresenceUseCase.DEFAULT_HOLD - 1.seconds)
            expectNoEvents()
            delay(2.seconds)
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `each reading while present holds it longer`() = runTest {
        val useCase = test()
        useCase.isPresent.test {
            assertEquals(false, awaitItem())
            useCase.heard(reading())
            assertEquals(true, awaitItem())
            delay(4.minutes)
            useCase.heard(reading())
            delay(4.minutes)
            useCase.heard(reading())
            delay(KnownSensorPresenceUseCase.DEFAULT_HOLD - 1.seconds)
            expectNoEvents()
            delay(2.seconds)
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `a reading after the hold ended makes them present again`() = runTest {
        val useCase = test()
        useCase.isPresent.test {
            assertEquals(false, awaitItem())
            useCase.heard(reading())
            assertEquals(true, awaitItem())
            delay(KnownSensorPresenceUseCase.DEFAULT_HOLD + 1.seconds)
            assertEquals(false, awaitItem())
            useCase.heard(reading())
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `with stay active on, its duration is the hold`() = runTest {
        stayActive.value = true
        val useCase = test()
        useCase.isPresent.test {
            assertEquals(false, awaitItem())
            useCase.heard(reading())
            assertEquals(true, awaitItem())
            delay(10.minutes - 1.seconds)
            expectNoEvents()
            delay(2.seconds)
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `faint, unmeasured and stale readings are ignored`() = runTest {
        val useCase = test()
        useCase.isPresent.test {
            assertEquals(false, awaitItem())
            useCase.heard(reading(rssi = FAINT))
            useCase.heard(reading(rssi = null))
            // The latest stored reading, which listening starts with
            useCase.heard(reading(ageSeconds = 2.minutes.inWholeSeconds.toDouble()))
            expectNoEvents()
        }
    }

    private companion object {
        /** Any wall clock time, so that the readings' times look real */
        const val START = 1_800_000_000.0
        const val LOUD = -60
        const val FAINT = -120
    }
}
