package com.masselis.tpmsadvanced.core.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class FractionTest {

    @Test
    fun `from 0 to 1 included`() {
        listOf(0f, 0.5f, 1f).forEach { assertEquals(it, Fraction(it).value) }
    }

    @Test
    fun `anything else is refused`() {
        listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertFailsWith<IllegalArgumentException>("$it") { Fraction(it) }
        }
    }
}
