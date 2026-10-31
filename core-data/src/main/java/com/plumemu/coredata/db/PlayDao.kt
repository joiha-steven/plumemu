package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Play history (totals in `play`, sessions in `play_session`); written when the person comes back from a game. */
@Dao
abstract class PlayDao {
    /**
     * Adds a session of [seconds] that ended at [endedAt] (wall-clock milliseconds): the game's
     * total and last play, and one `play_session` row, in one transaction, so a session is never
     * stored without its seconds in the total, nor the other way round. [startedAt] defaults to
     * [endedAt] minus [seconds]; the launcher passes now minus the measured time. Sessions that
     * ended more than [SESSION_RETENTION_MS] before [endedAt] are deleted in the same transaction;
     * totals are never touched by that. A wall clock set far into the future therefore deletes
     * old sessions early (their seconds stay in the totals). [battery] is what the play measured of
     * the battery, stored in the same row; null: not measured. [recordSession] does the
     * same and says what it added.
     */
    suspend fun addSession(
        gameId: Long,
        endedAt: Long,
        seconds: Long,
        startedAt: Long = endedAt - seconds * MILLIS_PER_SECOND,
        battery: SessionBattery? = null,
    ) {
        recordSession(gameId, endedAt, seconds, startedAt, battery)
    }

    /**
     * [addSession], returning what was added for [takeBack] (the launcher's core check); null when
     * [gameId] is not in `game` (no session row is written then). Kept apart so [addSession] stays a
     * Unit call: tests written as `= runBlocking { …; addSession(…) }` must stay void methods.
     */
    @Transaction
    open suspend fun recordSession(
        gameId: Long,
        endedAt: Long,
        seconds: Long,
        startedAt: Long = endedAt - seconds * MILLIS_PER_SECOND,
        battery: SessionBattery? = null,
    ): AddedSession? {
        val previous = play(gameId)
        insertIfMissing(gameId, endedAt)
        addToTotal(gameId, endedAt, seconds)
        insertSession(
            gameId, startedAt, endedAt, seconds,
            battery?.measuredSeconds, battery?.chargeStartMicroampHours, battery?.chargeEndMicroampHours, battery?.drawMicroamps,
        )
        deleteSessionsEndedBefore(endedAt - SESSION_RETENTION_MS)
        val sessionId = newestSessionId(gameId) ?: return null
        return AddedSession(sessionId, gameId, endedAt, seconds, previous?.lastPlayedAt, previous?.recentRemoved == true)
    }

    /**
     * Undoes one [recordSession] (a RetroArch core the person says never started): its `play_session` row goes, its seconds leave the total, and the game's last play
     * goes back to what it was, so Recent is as before (a game the person had removed from Recent leaves
     * it again); a game first played by it loses its `play` row.
     * Nothing else changes: a newer play of the game (last play no longer [AddedSession.endedAt]) keeps
     * its time. Returns false, changing nothing, when the row is already gone (a second answer, or the
     * retention cleanup), so it can never subtract twice.
     */
    @Transaction
    open suspend fun takeBack(session: AddedSession): Boolean {
        if (deleteSession(session.sessionId, session.gameId) == 0) return false
        subtractFromTotal(session.gameId, session.seconds)
        val previous = session.previousLastPlayedAt
        if (previous == null) {
            deletePlayAddedAt(session.gameId, session.endedAt)
        } else {
            restoreLastPlayed(session.gameId, session.endedAt, previous, session.wasRemovedFromRecent)
        }
        return true
    }

    /**
     * [gameId]'s usual battery draw in microamperes: the
     * mean of the draws of its last [USUAL_PACE_PLAYS] measured plays, each weighted by its measured
     * seconds; plays measured under [USUAL_PACE_MIN_SECONDS] do not count (nor are they among the
     * last five). Null when no play counts. Newest by id, the order the plays were recorded in, not by
     * `ended_at`: a play recorded with the clock set a year ahead would stay among the last five for a year.
     */
    @Query(
        "SELECT SUM(battery_draw_ua * battery_seconds) / SUM(battery_seconds) FROM (" +
            "SELECT battery_draw_ua, battery_seconds FROM play_session WHERE game_id = :gameId " +
            "AND battery_seconds >= " + USUAL_PACE_MIN_SECONDS + " AND battery_draw_ua IS NOT NULL " +
            "ORDER BY id DESC LIMIT " + USUAL_PACE_PLAYS + ")",
    )
    abstract suspend fun usualDraw(gameId: Long): Long?

    /**
     * Seconds played in [[from], [to]) (wall-clock milliseconds), for Settings › Statistics' "this
     * week". A session crossing an edge counts only the share of its seconds inside the range,
     * spread evenly over its start to end; one whose end is not after its start (a 0-second
     * session) counts whole when its end is inside. Sessions of games still in the library only
     * (CASCADE takes the others), hidden ones and "not a game" included, like [totalSeconds].
     * The `OR ended_at < :to` keeps such a session whose stored start lies after the range.
     */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN ended_at <= started_at THEN " +
            "CASE WHEN ended_at >= :from AND ended_at < :to THEN seconds ELSE 0 END " +
            "ELSE seconds * (MIN(ended_at, :to) - MAX(started_at, :from)) / (ended_at - started_at) END), 0) " +
            "FROM play_session WHERE ended_at >= :from AND (started_at < :to OR ended_at < :to)",
    )
    abstract suspend fun secondsPlayedBetween(from: Long, to: Long): Long

    /** True once any session is stored (sessions are kept from schema 5 on, for 400 days). */
    @Query("SELECT EXISTS (SELECT 1 FROM play_session)")
    abstract suspend fun hasAnySession(): Boolean

    /**
     * Settings › Statistics' "times played" of one game: its recorded sessions over all its copies
     * ([gameIds]). Sessions exist from schema 5 on and are kept 400 days, so the count never includes
     * older plays; the caller leaves the figure out at 0.
     */
    @Query("SELECT COUNT(*) FROM play_session WHERE game_id IN (:gameIds)")
    abstract suspend fun sessionCount(gameIds: List<Long>): Int

    /**
     * Game menu's Remove from Recent: [gameId] leaves Recent until it is played again.
     * Only the `recent_removed` mark changes; play time, last play and sessions stay. Returns the
     * rows changed (0: the game was never played, so it was not in Recent). Running it twice is harmless.
     */
    @Query("UPDATE play SET recent_removed = 1 WHERE game_id = :gameId")
    abstract suspend fun removeFromRecent(gameId: Long): Int

    /**
     * Settings' Clear Recent: every played game leaves Recent until it is played again;
     * play time, last plays and sessions stay. Returns the rows changed.
     */
    @Query("UPDATE play SET recent_removed = 1 WHERE recent_removed = 0")
    abstract suspend fun clearRecent(): Int

    /** The newest `last_played_at` among [gameIds] (the copies of one game); null when none was played. */
    @Query("SELECT MAX(last_played_at) FROM play WHERE game_id IN (:gameIds)")
    abstract suspend fun lastPlayedAt(gameIds: List<Long>): Long?

    @Query("SELECT * FROM play_session WHERE game_id = :gameId ORDER BY ended_at, id")
    abstract suspend fun sessions(gameId: Long): List<PlaySessionEntity>

    @Query("SELECT * FROM play WHERE game_id = :gameId")
    abstract suspend fun play(gameId: Long): PlayEntity?

    /** The newest play among the games [systemId]'s list shows (the filter of `GameDao.systemGames`); null when none was played. */
    @Query(
        "SELECT game.title, play.last_played_at FROM play JOIN game ON game.id = play.game_id " +
            "WHERE game.system_id = :systemId AND game.is_hidden = 0 AND game.is_not_game = 0 AND game.is_primary_copy = 1 " +
            "ORDER BY play.last_played_at DESC, game.id LIMIT 1",
    )
    abstract suspend fun newestPlayInSystem(systemId: String): LastPlay?

    /** The newest play among the favourites Favorites shows (the filter of `GameDao.favorites`); null when none was played. */
    @Query(
        "SELECT game.title, play.last_played_at FROM play JOIN game ON game.id = play.game_id " +
            "WHERE game.is_favorite = 1 AND game.is_hidden = 0 AND game.is_not_game = 0 " +
            "ORDER BY play.last_played_at DESC, game.id LIMIT 1",
    )
    abstract suspend fun newestFavoritePlay(): LastPlay?

    /**
     * Settings › Statistics' total: every second played on a game still in the library, hidden
     * ones and "not a game" included (time played stays time played); 0 when nothing was played.
     */
    @Query("SELECT COALESCE(SUM(play.total_seconds), 0) FROM play JOIN game ON game.id = play.game_id")
    abstract suspend fun totalSeconds(): Long

    /**
     * Settings › Statistics' copies: every game in a directory, system and file sort name that holds
     * a played game, and for a played game alone in its directory (one game per folder) every game
     * alone in its directory with that folder, system and file sort name, played or not, with its total (0 when never played),
     * its system's stored name and the number of rows in its directory. The cataloguer groups
     * regional copies by directory, system, file sort name and variant key, and the scan groups
     * copies kept one game per folder across sibling folders (library `GameFolderCopies`); the
     * variant key is not stored, so the caller finishes the grouping from [PlayedCopy.fileName] and
     * [PlayedCopy.relativePath] (identify's `TitleCleaner`). The game table is walked once against
     * the few played groups.
     * Arcade sets of one game have other file names (`dino.zip`, `dinou.zip`): when none of a
     * played arcade game's copies here is shown, the caller adds the shown one with [shownRowsIn]
     * and [playedCopiesOf].
     */
    @Query(
        "WITH played AS (SELECT DISTINCT g.folder_id, g.parent_document_id, g.system_id, g.file_sort_name, " +
            ROWS_BESIDE_G + " = 1 AS alone FROM play JOIN game AS g ON g.id = play.game_id) " +
            "SELECT " + PLAYED_COPY_COLUMNS + " " +
            "FROM game JOIN system ON system.id = game.system_id LEFT JOIN play ON play.game_id = game.id " +
            // EXISTS, not a join: a row matching several played groups (two played discs kept one
            // folder each) must come once, or its time would count twice.
            "WHERE EXISTS (SELECT 1 FROM played WHERE played.folder_id = game.folder_id " +
            "AND played.system_id = game.system_id AND played.file_sort_name = game.file_sort_name " +
            "AND (played.parent_document_id = game.parent_document_id OR (played.alone AND " + ROWS_BESIDE + " = 1)))",
    )
    abstract suspend fun playedCopies(): List<PlayedCopy>

    /**
     * The rows of one directory and system that the lists may show (`is_primary_copy`, not "not a
     * game"), by id and file name only, since an arcade folder may hold 10,000 sets: Statistics
     * picks the sets of played arcade games among them and reads those in full with [playedCopiesOf].
     */
    @Query(
        "SELECT id, file_name FROM game WHERE folder_id = :folderId AND parent_document_id = :parentDocumentId " +
            "AND system_id = :systemId AND is_primary_copy = 1 AND is_not_game = 0",
    )
    abstract suspend fun shownRowsIn(folderId: Long, parentDocumentId: String, systemId: String): List<PlayedSibling>

    /** [ids] as [playedCopies] reads its rows. */
    suspend fun playedCopiesOf(ids: List<Long>): List<PlayedCopy> = ids.chunked(MAX_IDS_PER_STATEMENT).flatMap { playedCopiesChunk(it) }

    @Query(
        "SELECT " + PLAYED_COPY_COLUMNS + " FROM game JOIN system ON system.id = game.system_id " +
            "LEFT JOIN play ON play.game_id = game.id WHERE game.id IN (:ids)",
    )
    protected abstract suspend fun playedCopiesChunk(ids: List<Long>): List<PlayedCopy>

    @Query("INSERT OR IGNORE INTO play (game_id, last_played_at, total_seconds) VALUES (:gameId, :at, 0)")
    protected abstract suspend fun insertIfMissing(gameId: Long, at: Long)

    @Query(
        // A new play brings a game the person removed from Recent back into it.
        "UPDATE play SET last_played_at = :at, total_seconds = total_seconds + :seconds, recent_removed = 0 " +
            "WHERE game_id = :gameId",
    )
    protected abstract suspend fun addToTotal(gameId: Long, at: Long, seconds: Long)

    // The system is copied from the game row; no row is written for a game not in `game`.
    @Query(
        "INSERT INTO play_session (game_id, system_id, started_at, ended_at, seconds, " +
            "battery_seconds, charge_start_uah, charge_end_uah, battery_draw_ua) " +
            "SELECT id, system_id, :startedAt, :endedAt, :seconds, :batterySeconds, :chargeStart, :chargeEnd, :draw " +
            "FROM game WHERE id = :gameId",
    )
    protected abstract suspend fun insertSession(
        gameId: Long,
        startedAt: Long,
        endedAt: Long,
        seconds: Long,
        batterySeconds: Long?,
        chargeStart: Long?,
        chargeEnd: Long?,
        draw: Long?,
    )

    // Inside addSession's transaction: the row its insertSession just wrote.
    @Query("SELECT MAX(id) FROM play_session WHERE game_id = :gameId")
    protected abstract suspend fun newestSessionId(gameId: Long): Long?

    @Query("DELETE FROM play_session WHERE id = :sessionId AND game_id = :gameId")
    protected abstract suspend fun deleteSession(sessionId: Long, gameId: Long): Int

    @Query("UPDATE play SET total_seconds = MAX(0, total_seconds - :seconds) WHERE game_id = :gameId")
    protected abstract suspend fun subtractFromTotal(gameId: Long, seconds: Long)

    @Query(
        // MAX: a Remove or Clear made after the play (while its core question waited) must stay.
        "UPDATE play SET last_played_at = :previous, recent_removed = MAX(recent_removed, :wasRemovedFromRecent) " +
            "WHERE game_id = :gameId AND last_played_at = :endedAt",
    )
    protected abstract suspend fun restoreLastPlayed(gameId: Long, endedAt: Long, previous: Long, wasRemovedFromRecent: Boolean)

    @Query("DELETE FROM play WHERE game_id = :gameId AND last_played_at = :endedAt AND total_seconds = 0")
    protected abstract suspend fun deletePlayAddedAt(gameId: Long, endedAt: Long)

    @Query("DELETE FROM play_session WHERE ended_at < :cutoff")
    protected abstract suspend fun deleteSessionsEndedBefore(cutoff: Long)

    companion object {
        /** How long sessions are kept: a little over a year, so "this week" and a later "this year" have them. */
        const val SESSION_RETENTION_MS: Long = 400L * 24 * 60 * 60 * 1_000

        /** A game's usual pace is the mean of this many of its last measured plays. */
        const val USUAL_PACE_PLAYS = 5

        /** Plays measured for less than this (three minutes) do not count towards the usual pace. */
        const val USUAL_PACE_MIN_SECONDS = 180L

        private const val MILLIS_PER_SECOND = 1_000L

        // SQLite on Android 6 allows 999 bound parameters per statement.
        private const val MAX_IDS_PER_STATEMENT = 500

        /**
         * The rows in `game`'s directory, itself included, counted up to 2 only: an arcade
         * directory holds 10,000 sets, and only whether it is 1 matters.
         */
        private const val ROWS_BESIDE =
            "(SELECT COUNT(*) FROM (SELECT 1 FROM game AS beside WHERE beside.folder_id = game.folder_id " +
                "AND beside.parent_document_id = game.parent_document_id LIMIT 2))"

        /** [ROWS_BESIDE] of the played row `g`. */
        private const val ROWS_BESIDE_G =
            "(SELECT COUNT(*) FROM (SELECT 1 FROM game AS beside WHERE beside.folder_id = g.folder_id " +
                "AND beside.parent_document_id = g.parent_document_id LIMIT 2))"

        private const val PLAYED_COPY_COLUMNS =
            "game.id, game.title, game.sort_name, game.file_name, game.folder_id, game.parent_document_id, game.system_id, " +
                "game.file_sort_name, game.is_primary_copy, game.is_hidden, game.is_not_game, " +
                "COALESCE(play.total_seconds, 0) AS total_seconds, system.name AS system_name, " +
                ScrapedGameDao.SCRAPE_KEY + " AS scrape_key, game.relative_path, game.disc_count, " +
                ROWS_BESIDE + " AS rows_beside"
    }
}

/**
 * One session [PlayDao.addSession] wrote: its `play_session` row, and the game's last play before it
 * (null: the game had no `play` row) and whether it was out of Recent then, so [PlayDao.takeBack] can
 * undo exactly that session.
 */
data class AddedSession(
    val sessionId: Long,
    val gameId: Long,
    val endedAt: Long,
    val seconds: Long,
    val previousLastPlayedAt: Long?,
    val wasRemovedFromRecent: Boolean = false,
)
