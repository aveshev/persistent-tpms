package com.masselis.tpmsadvanced.interfaces.composable

import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.BELL
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.MODES
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.MONITOR_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.PERMISSIONS
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.PERSISTENT_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.SCREEN_ON
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.THRESHOLDS
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.VEHICLE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.WELCOME
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class OnboardingTest {

    private var step: Step? = null
    private var finished = false

    @Before
    fun setup() {
        step = null
        finished = false
    }

    private fun test(at: Step) = Onboarding(at, onStep = { step = it }, onFinish = { finished = true })

    @Test
    fun `the steps follow the introduction's order`() {
        assertEquals(
            listOf(WELCOME, MODES, PERMISSIONS, SCREEN_ON, MONITOR_MODE, PERSISTENT_MODE, BELL, THRESHOLDS, VEHICLE),
            Step.entries.toList(),
        )
    }

    @Test
    fun `next goes to the following step`() {
        test(PERMISSIONS).next()
        assertEquals(SCREEN_ON, step)
        assertFalse(finished)
    }

    @Test
    fun `the thresholds are told about after the tour`() {
        test(BELL).next()
        assertEquals(THRESHOLDS, step)
        test(THRESHOLDS).next()
        assertEquals(VEHICLE, step)
    }

    @Test
    fun `next after the vehicle finishes`() {
        test(VEHICLE).next()
        assertNull(step)
        assertTrue(finished)
    }

    @Test
    fun `back goes to the previous step, nowhere from the welcome`() {
        test(SCREEN_ON).back()
        assertEquals(PERMISSIONS, step)
        step = null
        test(WELCOME).back()
        assertNull(step)
    }

    @Test
    fun `skipping goes to the vehicle, without finishing`() {
        test(MODES).skip()
        assertEquals(VEHICLE, step)
        assertFalse(finished)
    }

    @Test
    fun `a dot for each step but the vehicle`() {
        assertEquals(8, Step.dotCount)
        assertEquals(listOf(WELCOME, MODES, PERMISSIONS), Step.entries.filter { it.isPage })
        assertEquals(
            listOf(SCREEN_ON, MONITOR_MODE, PERSISTENT_MODE, BELL, THRESHOLDS),
            Step.entries.filter { it.isTour },
        )
    }
}
