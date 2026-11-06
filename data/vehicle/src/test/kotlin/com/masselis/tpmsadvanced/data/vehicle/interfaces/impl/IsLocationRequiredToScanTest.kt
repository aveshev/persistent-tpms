package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IsLocationRequiredToScanTest {

    @Test
    fun `scans need Location up to Android 11`() {
        (28..30).forEach { assertTrue(isLocationRequiredToScan(it), "API $it") }
    }

    @Test
    fun `neverForLocation waives it from Android 12`() {
        (31..37).forEach { assertFalse(isLocationRequiredToScan(it), "API $it") }
    }
}
