package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.PRO
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.ON_FOOT
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.STILL
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.DEEP_DOZE
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.NO_SIGNIFICANT_MOTION
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.STANDING_STILL
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
internal class PhoneIdleUseCaseTest {

    private lateinit var mechanism: MutableStateFlow<String?>
    private lateinit var deviceIdle: MutableStateFlow<Boolean>
    private lateinit var inUse: MutableStateFlow<Boolean>
    private lateinit var charging: MutableStateFlow<ChargingStateUseCase.State>
    private var motions: Flow<Unit>? = null
    private lateinit var activities: MutableSharedFlow<List<Activity>>
    private var activityPermitted = true

    context(scope: TestScope)
    private fun test(tier: AppTier = PRO) = PhoneIdleUseCase(
        mockk<AppPreferences> { every { phoneIdleMechanismInUse } returns mechanism },
        mockk<DeviceIdleModeUseCase> { every { isDeviceIdle } returns deviceIdle },
        mockk<ScreenStateUseCase> { every { isInUse } returns inUse },
        mockk<ChargingStateUseCase> { every { state } returns charging },
        mockk<SignificantMotionUseCase> { every { this@mockk.motions } returns this@PhoneIdleUseCaseTest.motions },
        mockk<ActivityRecognitionUseCase> {
            every { isPermitted() } answers { activityPermitted }
            every { probableActivities } returns activities
        },
        scope.backgroundScope,
        { scope.testScheduler.currentTime.milliseconds },
        tier,
    )

    @Before
    fun setup() {
        mechanism = MutableStateFlow(null)
        deviceIdle = MutableStateFlow(false)
        inUse = MutableStateFlow(false)
        charging = MutableStateFlow(ChargingStateUseCase.State(cable = false, wireless = false))
        motions = MutableSharedFlow()
        // Replayed: a sample sent before the detector subscribed is not lost
        activities = MutableSharedFlow(replay = 1)
        activityPermitted = true
    }

    private fun still(confidence: Int) = listOf(Activity(STILL, confidence), Activity(ON_FOOT, 100 - confidence))

    @Test
    fun `no significant motion decides by default`() = runTest {
        test().isIdle.test {
            assertEquals(false, awaitItem())
            assertEquals(true, awaitItem())
            assertEquals(5.minutes, currentTime.milliseconds)
        }
    }

    @Test
    fun `deep doze decides once selected`() = runTest {
        mechanism.value = DEEP_DOZE.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            deviceIdle.value = true
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `a mechanism not selected is only tracked`() = runTest {
        mechanism.value = STANDING_STILL.name
        deviceIdle.value = true
        test().isIdle.test {
            assertEquals(false, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `idle once no significant motion was reported for the quiet period`() = runTest {
        mechanism.value = NO_SIGNIFICANT_MOTION.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            assertEquals(true, awaitItem())
            assertEquals(5.minutes, currentTime.milliseconds)
        }
    }

    @Test
    fun `a significant motion starts the quiet period over`() = runTest {
        val motions = MutableSharedFlow<Unit>().also { motions = it }
        mechanism.value = NO_SIGNIFICANT_MOTION.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(4.minutes)
            motions.emit(Unit)
            assertEquals(true, awaitItem())
            assertEquals(9.minutes, currentTime.milliseconds)
            motions.emit(Unit)
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `falls back on deep doze without a significant motion sensor`() = runTest {
        motions = null
        mechanism.value = NO_SIGNIFICANT_MOTION.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(10.minutes)
            expectNoEvents()
            deviceIdle.value = true
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `idle once still enough for the quiet period, without samples meanwhile`() = runTest {
        mechanism.value = STANDING_STILL.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            activities.emit(still(95))
            assertEquals(true, awaitItem())
            assertEquals(5.minutes, currentTime.milliseconds)
        }
    }

    @Test
    fun `further still samples do not start the quiet period over`() = runTest {
        mechanism.value = STANDING_STILL.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            activities.emit(still(95))
            advanceTimeBy(3.minutes)
            activities.emit(still(100))
            assertEquals(true, awaitItem())
            assertEquals(5.minutes, currentTime.milliseconds)
        }
    }

    @Test
    fun `a sample below the threshold starts the quiet period over`() = runTest {
        mechanism.value = STANDING_STILL.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            activities.emit(still(95))
            advanceTimeBy(4.minutes)
            activities.emit(still(89))
            advanceTimeBy(1.minutes)
            activities.emit(still(90))
            assertEquals(true, awaitItem())
            assertEquals(10.minutes, currentTime.milliseconds)
            activities.emit(still(50))
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `never idle without the physical activity permission`() = runTest {
        activityPermitted = false
        mechanism.value = STANDING_STILL.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(10.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `Lite falls back on the default mechanism for standing still, imported from Pro`() = runTest {
        mechanism.value = STANDING_STILL.name
        test(LITE).isIdle.test {
            assertEquals(false, awaitItem())
            assertEquals(true, awaitItem())
            assertEquals(5.minutes, currentTime.milliseconds)
        }
    }

    @Test
    fun `Lite does not offer standing still`() {
        assertEquals(listOf(DEEP_DOZE, NO_SIGNIFICANT_MOTION), PhoneIdleUseCase.mechanisms(LITE))
        assertEquals(listOf(DEEP_DOZE, NO_SIGNIFICANT_MOTION, STANDING_STILL), PhoneIdleUseCase.mechanisms(PRO))
    }

    @Test
    fun `selecting another mechanism switches to what it tells`() = runTest {
        mechanism.value = DEEP_DOZE.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(6.minutes)
            mechanism.value = NO_SIGNIFICANT_MOTION.name
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `never idle while in use`() = runTest {
        inUse.value = true
        deviceIdle.value = true
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(10.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `never idle while charging`() = runTest {
        charging.value = ChargingStateUseCase.State(cable = false, wireless = true)
        mechanism.value = NO_SIGNIFICANT_MOTION.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(10.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `the quiet period starts over once no longer in use`() = runTest {
        mechanism.value = NO_SIGNIFICANT_MOTION.name
        test().isIdle.test {
            assertEquals(false, awaitItem())
            advanceTimeBy(4.minutes)
            inUse.value = true
            advanceTimeBy(1.minutes)
            inUse.value = false
            assertEquals(true, awaitItem())
            assertEquals(10.minutes, currentTime.milliseconds)
            inUse.value = true
            assertEquals(false, awaitItem())
        }
    }
}
