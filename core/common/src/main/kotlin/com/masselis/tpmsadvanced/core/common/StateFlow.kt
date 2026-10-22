package com.masselis.tpmsadvanced.core.common

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [transform] of this and [other]'s values, as a [StateFlow] read straight from them rather than
 * kept in a scope, so it can be declared where no scope exists. [transform] runs on each read of
 * [StateFlow.value], so it should be cheap.
 */
public fun <A, B, T> StateFlow<A>.combineState(
    other: StateFlow<B>,
    transform: (A, B) -> T,
): StateFlow<T> = CombinedStateFlow(this, other, transform)

@Suppress("UnnecessaryOptInAnnotation")
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class CombinedStateFlow<A, B, T>(
    private val first: StateFlow<A>,
    private val second: StateFlow<B>,
    private val transform: (A, B) -> T,
) : StateFlow<T> {

    override val value: T
        get() = transform(first.value, second.value)

    override val replayCache: List<T>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        combine(first, second, transform)
            .distinctUntilChanged()
            .collect(collector)
        // Never reached, both are StateFlows which never complete
        awaitCancellation()
    }
}
