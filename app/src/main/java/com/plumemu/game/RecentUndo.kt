package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Undo of the game menu's Remove from Recent: the game just removed is offered back for
 * [SHOWN_MS] (the top bar's line, X's hint). [restore] writes the undo; it is told by game, not by
 * list position, so a list that changed meanwhile still gets the right game back. One offer at a time.
 */
internal class RecentUndo(
    private val scope: CoroutineScope,
    private val restore: (GameRef) -> Unit,
) {
    /** The game whose Undo is offered, null otherwise. */
    var game: GameRef? by mutableStateOf(null)
        private set

    private var timer: Job? = null

    fun offer(removed: GameRef) {
        game = removed
        timer?.cancel()
        timer =
            scope.launch {
                delay(SHOWN_MS)
                if (game == removed) game = null
            }
    }

    /** The offer ends: a menu opened over the list, where X belongs to the panel. */
    fun end() {
        game = null
        timer?.cancel()
    }

    /** Undo pressed: false (not taken) when nothing is offered. */
    fun take(): Boolean {
        val offered = game ?: return false
        game = null
        timer?.cancel()
        restore(offered)
        return true
    }

    companion object {
        /** As long as the top bar keeps a library notice (`NOTICE_SHOWN_MS`). */
        const val SHOWN_MS: Long = 4_000L
    }
}
