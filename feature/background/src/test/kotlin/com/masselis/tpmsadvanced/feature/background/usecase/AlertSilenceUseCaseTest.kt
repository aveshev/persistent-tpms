package com.masselis.tpmsadvanced.feature.background.usecase

import android.content.Context
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class AlertSilenceUseCaseTest {

    // The wall clock, moved apart from the coroutines' one to tell a sleeping phone
    private var now = 0L

    @Before
    fun setup() {
        now = 1_000_000L
        mockkStatic(CORE_COMMON_INITIALIZER)
        every { appContext } returns mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns mockk(relaxed = true) {
                every { getString(any(), any()) } returns null
            }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(CORE_COMMON_INITIALIZER)
    }

    private fun TestScope.test() = AlertSilenceUseCase(backgroundScope) { now }.also { runCurrent() }

    @Test
    fun `a silence ends at its time on the wall clock`() = runTest {
        val useCase = test()
        useCase.silence(RED)
        runCurrent()
        assertEquals(now + AlertSilenceUseCase.DURATION.inWholeMilliseconds, assertNotNull(useCase.silence.value).until)
        now += AlertSilenceUseCase.DURATION.inWholeMilliseconds - 1
        advanceTimeBy(AlertSilenceUseCase.DURATION.inWholeMilliseconds - 1)
        runCurrent()
        assertNotNull(useCase.silence.value)
        now += 1
        advanceTimeBy(1)
        runCurrent()
        assertNull(useCase.silence.value)
    }

    @Test
    fun `a silence ends once its time passed while the phone slept`() = runTest {
        val useCase = test()
        useCase.silence(RED)
        runCurrent()
        // Asleep: the wall clock passed the end, the coroutines' clock didn't move
        now += AlertSilenceUseCase.DURATION.inWholeMilliseconds
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(useCase.silence.value)
    }

    private companion object {
        const val CORE_COMMON_INITIALIZER = "com.masselis.tpmsadvanced.core.common.CoreCommonInitializerKt"
    }
}
