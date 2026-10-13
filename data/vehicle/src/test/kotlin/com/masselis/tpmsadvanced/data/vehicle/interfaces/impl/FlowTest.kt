package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class FlowTest {

    private data class Packet(val key: String, val at: Duration)

    private fun List<Packet>.distinct() = asFlow()
        .distinctWithin(50.seconds, Packet::key, Packet::at)

    @Test
    fun `drops the copies of a burst`() = runTest {
        listOf(Packet("a", 0.seconds), Packet("a", 10.milliseconds), Packet("a", 2.seconds))
            .distinct()
            .toList()
            .also { assertEquals(listOf(Packet("a", 0.seconds)), it) }
    }

    @Test
    fun `lets an unchanged packet through once the window passed`() = runTest {
        listOf(Packet("a", 0.seconds), Packet("a", 50.seconds), Packet("a", 99.seconds), Packet("a", 100.seconds))
            .distinct()
            .map { it.at }
            .toList()
            .also { assertEquals(listOf(0.seconds, 50.seconds, 100.seconds), it) }
    }

    @Test
    fun `another key in between doesn't let a copy through`() = runTest {
        listOf(Packet("a", 0.seconds), Packet("b", 1.seconds), Packet("a", 2.seconds), Packet("b", 3.seconds))
            .distinct()
            .toList()
            .also { assertEquals(listOf(Packet("a", 0.seconds), Packet("b", 1.seconds)), it) }
    }

    @Test
    fun `a changed packet goes through right away`() = runTest {
        listOf(Packet("a", 0.seconds), Packet("b", 10.milliseconds))
            .distinct()
            .map { it.key }
            .toList()
            .also { assertEquals(listOf("a", "b"), it) }
    }
}
