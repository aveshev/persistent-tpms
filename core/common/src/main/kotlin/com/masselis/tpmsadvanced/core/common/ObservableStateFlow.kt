package com.masselis.tpmsadvanced.core.common

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow

public fun <T> observableStateFlow(
    initialValue: T,
    onValue: (oldValue: T, newValue: T) -> Unit
): MutableStateFlow<T> = ObservableStateFlow(initialValue, onValue)

@Suppress("UnnecessaryOptInAnnotation")
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ObservableStateFlow<T> private constructor(
    private val stateFlow: MutableStateFlow<T>,
    private val onValue: (oldValue: T, newValue: T) -> Unit
) : MutableStateFlow<T> by stateFlow {

    constructor(
        initialValue: T,
        onValue: (oldValue: T, newValue: T) -> Unit
    ) : this(
        MutableStateFlow(initialValue),
        onValue
    )

    override var value: T
        get() = stateFlow.value
        set(value) {
            val previous = stateFlow.value
            stateFlow.value = value
            onValue(previous, value)
        }

    override fun compareAndSet(expect: T, update: T): Boolean {
        val previous = stateFlow.value
        return stateFlow.compareAndSet(expect, update).also { wasUpdated ->
            if (wasUpdated)
                onValue(previous, update)
        }
    }

    override suspend fun emit(value: T) {
        val previous = stateFlow.value
        stateFlow.emit(value)
        onValue(previous, value)
    }

    override fun tryEmit(value: T): Boolean {
        val previous = stateFlow.value
        return stateFlow.tryEmit(value).also { onValue(previous, value) }
    }
}
