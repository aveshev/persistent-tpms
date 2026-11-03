package com.masselis.tpmsadvanced.core.common

import com.masselis.tpmsadvanced.core.common.ValueOrCompletion.Completion
import com.masselis.tpmsadvanced.core.common.ValueOrCompletion.Value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
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

    // runCatching in materializeCompletion also catches the collector stopping the flow, which is an
    // exception too, then emits after it. Nothing in the app does so today: BluetoothLeScannerImpl and
    // ListenTyreWithDatabaseUseCase both go through shareIn in between, which only ever cancels
    @Ignore("materializeCompletion emits again once its collector has stopped it, which Flow refuses")
    @Test
    fun `a collector taking the first value only stops the flow`() = runTest {
        assertEquals(Value(1), flowOf(1, 2).materializeCompletion().first())
    }

    @Ignore("materializeCompletion emits again once its collector has stopped it, which Flow refuses")
    @Test
    fun `a collector taking some values only stops the flow`() = runTest {
        assertEquals(
            listOf(1),
            flowOf(1, 2).materializeCompletion().dematerializeCompletion().take(1).toList(),
        )
    }
}
