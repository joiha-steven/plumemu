package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The smart lists: Most played and Recently added, each mixing every system. Both leave
 * out hidden games and "Not a game". Recently added, a list of the whole library, shows one copy per
 * regional group (`is_primary_copy`) as a system's list does; Most played shows the rows the person
 * played, as Recent does, whichever copy that was.
 */
@Dao
abstract class SmartListDao {
    /** The [limit] games with the most play time (more than 0 seconds), most first, then by sort name. */
    fun observeMostPlayed(limit: Int = MOST_PLAYED_LIMIT): Flow<List<GameListRow>> = mostPlayed(limit).distinctUntilChanged()

    /**
     * Games a scan added within [RECENTLY_ADDED_MS] before [now] (epoch milliseconds), newest
     * first. `added_at` is null for the first scan's games (setup, after Reset library) and older
     * rows: they never show here, which is right (they are not new to the person). Pending rows wait
     * for the scan's end, as `GameDao.observeAddedSince`. The window is fixed by [now] when the flow
     * is made: the caller makes a new one when the day changes.
     */
    fun observeRecentlyAdded(now: Long): Flow<List<GameListRow>> = recentlyAdded(now - RECENTLY_ADDED_MS).distinctUntilChanged()

    @Query(
        "SELECT " + ROW + "FROM play JOIN game ON game.id = play.game_id WHERE " + LISTED + "AND play.total_seconds > 0 " +
            "ORDER BY play.total_seconds DESC, game.sort_name, game.id LIMIT :limit",
    )
    protected abstract fun mostPlayed(limit: Int): Flow<List<GameListRow>>

    @Query(
        "SELECT " + ROW + "FROM game WHERE " + LISTED + "AND game.is_primary_copy = 1 AND game.is_pending = 0 " +
            "AND game.added_at >= :since ORDER BY game.added_at DESC, game.id DESC",
    )
    protected abstract fun recentlyAdded(since: Long): Flow<List<GameListRow>>

    companion object {
        /** Most played's length. */
        const val MOST_PLAYED_LIMIT = 50

        /** Recently added's window: 30 days. */
        const val RECENTLY_ADDED_MS = 30L * 24 * 60 * 60 * 1000

        private const val ROW =
            "game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, game.art_aspect, " +
                GameDao.LIST_SCRAPE_KEY + " "

        /** Favorites' and Recent's filter. */
        private const val LISTED = "game.is_hidden = 0 AND game.is_not_game = 0 "
    }
}
