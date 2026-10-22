package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The permission journeys of the app, in the order they asked to prompt the user. Only the first
 * one prompts: its dialogs, the system popups and the trips to Settings come one journey at a
 * time, never on top of each other nor while the app is on its way to Settings.
 */
private val promptQueue = mutableStateListOf<Any>()

/** A journey's place in the queue, see [rememberPromptTurn] */
internal class PromptTurn(
    private val onAwait: () -> Unit,
    private val onDone: () -> Unit,
) {
    /** Queues the journey: it starts prompting once every journey queued before it is done */
    fun await() = onAwait()

    /** Leaves the queue, letting the next journey prompt. Also done when the journey is disposed. */
    fun done() = onDone()
}

/**
 * Runs [onTurn] once the journey [await]ed its turn: it's first in the queue and the app is in
 * front. It keeps its place, until [PromptTurn.done], while the user goes to Settings and back.
 */
@Composable
internal fun rememberPromptTurn(onTurn: () -> Unit): PromptTurn {
    val journey = remember { Any() }
    val currentOnTurn by rememberUpdatedState(onTurn)
    var waiting by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val turn = promptQueue.firstOrNull() == journey && lifecycleState.isAtLeast(RESUMED)
    LaunchedEffect(waiting, turn) {
        if (waiting && turn) {
            waiting = false
            currentOnTurn()
        }
    }
    // A journey gone mid-way, with its screen, must not hold the others back
    DisposableEffect(journey) { onDispose { promptQueue -= journey } }
    return remember {
        PromptTurn(
            onAwait = {
                if (journey !in promptQueue) promptQueue += journey
                waiting = true
            },
            onDone = {
                waiting = false
                promptQueue -= journey
            },
        )
    }
}
