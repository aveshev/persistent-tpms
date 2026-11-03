package com.masselis.tpmsadvanced.core.common

import com.masselis.tpmsadvanced.core.common.ValueOrCompletion.Completion
import com.masselis.tpmsadvanced.core.common.ValueOrCompletion.Value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class ValueOrCompletionTest {

    private val failure = IOException("Scan failed")

    private val failing = flow {
        emit(1)
        throw failure
    }

    @Test
    fun `a flow ending normally completes without an exception`() = runTest {
        assertEquals(
            listOf(Value(1), Value(2), Completion(null)),
            flowOf(1, 2).materializeCompletion().toList(),
        )
    }

    @Test
    fun `a flow failing completes with its exception`() = runTest {
        assertEquals(
            listOf(Value(1), Completion(failure)),
            failing.materializeCompletion().toList(),
        )
    }

    @Test
    fun `dematerializing stops at the completion`() = runTest {
        assertEquals(
            listOf(1),
            flowOf(Value(1), Completion(null), Value(2)).dematerializeCompletion().toList(),
        )
    }

    @Test
    fun `dematerializing throws the completion's exception, after the values before it`() = runTest {
        val values = mutableListOf<Int>()
        assertEquals(
            failure,
            assertFailsWith<IOException> {
                flowOf(Value(1), Completion(failure), Value(2))
                    .dematerializeCompletion()
                    .collect { values += it }
            },
        )
        assertEquals(listOf(1), values)
    }

    @Test
    fun `there and back is the same flow`() = runTest {
        assertEquals(
            listOf(1, 2),
            flowOf(1, 2).materializeCompletion().dematerializeCompletion().toList(),
        )
    }

    @Test
    fun `there and back fails the same way`() = runTest {
        assertEquals(
            failure,
            assertFailsWith<IOException> {
                failing.materializeCompletion().dematerializeCompletion().toList()
            },
        )
    }

    @Test
    fun `a collector taking the first value only stops the flow`() = runTest {
        assertEquals(Value(1), flowOf(1, 2).materializeCompletion().first())
    }

    @Test
    fun `a collector taking some values only stops the flow`() = runTest {
        assertEquals(
            listOf(1),
            flowOf(1, 2).materializeCompletion().dematerializeCompletion().take(1).toList(),
        )
    }

    @Test
    fun `a collector failing isn't the flow completing`() = runTest {
        val seen = mutableListOf<ValueOrCompletion<Int>>()
        assertEquals(
            failure,
            assertFailsWith<IOException> {
                flowOf(1, 2).materializeCompletion().collect {
                    seen += it
                    throw failure
                }
            },
        )
        assertEquals(listOf<ValueOrCompletion<Int>>(Value(1)), seen)
    }
}
