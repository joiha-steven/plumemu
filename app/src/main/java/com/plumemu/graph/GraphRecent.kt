package com.plumemu.graph

import android.util.Log
import com.plumemu.coredata.db.PlumDatabase

/**
 * Recent's two writes: the game menu's Remove from Recent and Settings' Clear Recent.
 * Both only mark `play.recent_removed`; play time, sessions and statistics stay, and the next play
 * of a game brings it back (core-data `PlayDao`). The home screen's Recent follows by itself (Room
 * observes `play`). Callers run these where leaving the screen does not cancel them (the game
 * menu's `keptWrite`, the process scope) and tell the person when one throws. Lines carry ids and
 * counts only.
 */
internal class GraphRecent(
    private val database: PlumDatabase,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    /** [gameId] leaves Recent until it is played again. False: it was never played, so it was not in Recent. */
    suspend fun removeFromRecent(gameId: Long): Boolean {
        val changed = database.playDao().removeFromRecent(gameId)
        log("Recent: game $gameId removed ($changed row)")
        return changed > 0
    }

    /** Every game leaves Recent until it is played again; returns how many left it. */
    suspend fun clearRecent(): Int {
        val cleared = database.playDao().clearRecent()
        log("Recent: cleared ($cleared games)")
        return cleared
    }

    private companion object {
        const val TAG = "PlumRecent"
    }
}
