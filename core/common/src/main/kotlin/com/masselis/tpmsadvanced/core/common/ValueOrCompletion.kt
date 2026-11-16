package com.masselis.tpmsadvanced.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile

// Based on https://github.com/Kotlin/kotlinx.coroutines/issues/2092#issuecomment-1433092654

public sealed class ValueOrCompletion<out T> {
    public data class Value<out T>(val value: T) : ValueOrCompletion<T>()
    public data class Completion(val exception: Throwable?) : ValueOrCompletion<Nothing>()
}

public fun <T> Flow<T>.materializeCompletion(): Flow<ValueOrCompletion<T>> = this
    .map<T, ValueOrCompletion<T>> { ValueOrCompletion.Value(it) }
    // Neither takes the collector stopping or failing for this flow completing
    .onCompletion { if (it == null) emit(ValueOrCompletion.Completion(null)) }
    .catch { emit(ValueOrCompletion.Completion(it)) }

public fun <T> Flow<ValueOrCompletion<T>>.dematerializeCompletion(): Flow<T> =
    transformWhile { vc ->
        when (vc) {
            is ValueOrCompletion.Value -> {
                emit(vc.value)
                true
            }

            is ValueOrCompletion.Completion -> {
                vc.exception?.let { throw it }
                false
            }
        }
    }
