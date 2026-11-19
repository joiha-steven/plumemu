package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.R
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

    /** The top bar's words for the offer ("Removed from Recent", "Removed from collection"); kept after the offer ends so the line fades out with its own words. */
    var notice: Int by mutableIntStateOf(R.string.home_notice_removed_from_recent)
        private set

    private var restoreOne: ((GameRef) -> Unit)? = null

    /** [restoreWith]: what Undo does for this offer instead of the Recent restore (a game taken out of an own collection). */
    fun offer(removed: GameRef, @androidx.annotation.StringRes notice: Int = R.string.home_notice_removed_from_recent, restoreWith: ((GameRef) -> Unit)? = null) {
        game = removed
        this.notice = notice
        restoreOne = restoreWith
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
        (restoreOne ?: restore)(offered)
        return true
    }

    companion object {
        /** As long as the top bar keeps a library notice (`NOTICE_SHOWN_MS`). */
        const val SHOWN_MS: Long = 4_000L
    }
}
