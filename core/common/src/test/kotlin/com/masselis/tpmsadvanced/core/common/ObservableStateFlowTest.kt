package com.masselis.tpmsadvanced.core.common

import app.cash.turbine.test
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ObservableStateFlowTest {

    private lateinit var changes: MutableList<Pair<Int, Int>>

    @Before
    fun setup() {
        changes = mutableListOf()
    }

    private fun test(initialValue: Int = 1) =
        observableStateFlow(initialValue) { oldValue, newValue -> changes += oldValue to newValue }

    @Test
    fun `nothing is told until the value changes`() {
        assertEquals(1, test().value)
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `setting the value tells the old and the new one`() {
        test().apply {
            value = 2
            value = 3
        }
        assertEquals(listOf(1 to 2, 2 to 3), changes)
    }

    @Test
    fun `update tells the old and the new value`() {
        test().update { it + 1 }
        assertEquals(listOf(1 to 2), changes)
    }

    @Test
    fun `a compareAndSet which fails tells nothing and keeps the value`() {
        test().apply {
            assertFalse(compareAndSet(5, 6))
            assertEquals(1, value)
        }
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `a compareAndSet which succeeds tells the change`() {
        test().apply {
            assertTrue(compareAndSet(1, 2))
            assertEquals(2, value)
        }
        assertEquals(listOf(1 to 2), changes)
    }

    @Test
    fun `collectors follow the values set`() = runTest {
        test().apply {
            test {
                assertEquals(1, awaitItem())
                value = 2
                assertEquals(2, awaitItem())
                update { it + 1 }
                assertEquals(3, awaitItem())
            }
        }
    }

    @Test
    fun `emit and tryEmit change the value`() = runTest {
        test().apply {
            emit(2)
            assertEquals(2, value)
            assertTrue(tryEmit(3))
            assertEquals(3, value)
        }
    }

    @Test
    fun `emit and tryEmit tell the old and the new value`() = runTest {
        test().apply {
            emit(2)
            assertTrue(tryEmit(3))
        }
        assertEquals(listOf(1 to 2, 2 to 3), changes)
    }
}
