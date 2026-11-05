package com.plumemu.graph

import com.plumemu.coredata.db.CopyPlay
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.LeftOutFileDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.coredata.db.PlayedGameRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.plumemu.identify.arcade.ArcadeSystems
import java.util.Locale
import java.util.TimeZone

/**
 * The figures of the second screen's system and Recent cards, read from what is stored (no UI,
 * no schema of its own). Each database part is opened on the reading thread; the suspend reads
 * run on [io] and throw what the database throws, so the caller decides what a failed read shows.
 * A system's total game count is `GameDao.observeSystemsWithGames` (the same filter as [playedGames]).
 */
internal class GraphSystemFigures(
    private val games: () -> GameDao,
    private val plays: () -> PlayDao,
    private val leftOut: () -> LeftOutFileDao,
    private val io: CoroutineDispatcher,
    /** Wall-clock milliseconds; "this week" runs from the week's start to this. */
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
    private val locale: () -> Locale = Locale::getDefault,
    /** As [GraphStatistics]: the grouping of arcade sets. */
    private val arcadeSystems: List<String> = ArcadeSystems.IDS.toList(),
    private val arcadeGames: ArcadeGames = ArcadeGames.None,
) {
    /**
     * The [limit] games of [systemId] played last, newest first. A game is its group of regional
     * copies ([playedGamesOf], as Statistics groups them): it shows once, as the copy the lists
     * show, with the latest last play of any copy, so a game played only on another copy is there
     * too. Games the lists hide are left out.
     */
    fun lastPlayed(systemId: String, limit: Int): Flow<List<PlayedGameRow>> =
        games().observeSystemPlays(systemId).map { plays -> withContext(io) { lastPlayedOf(systemId, limit, plays) } }.distinctUntilChanged()

    private suspend fun lastPlayedOf(systemId: String, limit: Int, copyPlays: List<CopyPlay>): List<PlayedGameRow> {
        val lastById = copyPlays.associate { it.gameId to it.lastPlayedAt }
        val shown =
            playedGamesOf(plays(), arcadeSystems, arcadeGames)
                .filter { it.isListed && it.shown!!.systemId == systemId }
                .mapNotNull { game -> game.copyIds.mapNotNull { lastById[it] }.maxOrNull()?.let { game.shown!!.id to it } }
                .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { it.first })
                .take(limit)
        val rows = games().rowsOf(shown.map { it.first }).associateBy { it.id }
        return shown.mapNotNull { (id, at) ->
            rows[id]?.let { PlayedGameRow(it.id, it.title, it.regions, it.isFavorite, it.systemId, it.scrapeKey, it.sortName, it.fileName, at) }
        }
    }

    /** Recent's games with each last play. */
    fun recent(limit: Int): Flow<List<PlayedGameRow>> = games().observeRecentPlayed(limit)

    /** Seconds played on [systemId], hidden games included (like Statistics' total). */
    suspend fun playSeconds(systemId: String): Long = withContext(io) { plays().totalSecondsInSystem(systemId) }

    /**
     * Listed games of [systemId] played for a minute, by Statistics' rule ([playedGamesOf]): copies
     * of one game group, and a play on any copy counts for the game under the copy the lists show.
     * Each is one primary copy, so it never exceeds the system's game count.
     */
    suspend fun playedGames(systemId: String): Int = withContext(io) { playedGamesBySystem()[systemId] ?: 0 }

    /** [playedGames] of every system with a played game, from one read. */
    suspend fun playedGamesBySystem(): Map<String, Int> =
        withContext(io) {
            mostPlayedOf(playedGamesOf(plays(), arcadeSystems, arcadeGames)).groupingBy { it.shown!!.systemId }.eachCount()
        }

    /** Files left out of [systemId] (the "N files not added" line), "more" rows included. */
    suspend fun leftOutFiles(systemId: String): Int = withContext(io) { leftOut().countInSystem(systemId) }

    /**
     * Distinct games (a group of regional copies is one, as in [lastPlayed]) with play in
     * [[from], [to]): a session counts by [PlayDao.gameIdsPlayedBetween], so one ending exactly at
     * [from] does not. No minute minimum: it is "games touched". Listed games only, as every game
     * count of Statistics ([PlayedGame.isListed]): hidden and "Not a game" rows add seconds to play
     * time but never count as a game.
     */
    suspend fun gamesPlayedBetween(from: Long, to: Long): Int =
        withContext(io) {
            val touched = plays().gameIdsPlayedBetween(from, to).toSet()
            if (touched.isEmpty()) 0 else playedGamesOf(plays(), arcadeSystems, arcadeGames).count { game -> game.isListed && game.copyIds.any { it in touched } }
        }

    /** Distinct games played since this week's start ([weekStartOf]), up to now. */
    suspend fun gamesPlayedThisWeek(): Int {
        val to = now()
        return gamesPlayedBetween(weekStartOf(to, zone(), locale()), to)
    }
}
