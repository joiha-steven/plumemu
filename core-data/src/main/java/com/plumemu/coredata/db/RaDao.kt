package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * RetroAchievements in the library database (schema 14): each library game's match and hash
 * cache (`ra_match`, [RaMatchEntity]) and the signed-in person's progress (`ra_progress`,
 * [RaProgressEntity]). Nothing here is read while signed out: callers only build what reads it
 * after the account check.
 */
@Dao
abstract class RaDao {
    /** The stored match of [gameId], whatever file it was read from; null when never checked. */
    @Query("SELECT * FROM ra_match WHERE game_id = :gameId")
    abstract suspend fun match(gameId: Long): RaMatchEntity?

    /**
     * The stored match of [gameId] when it was read from the same file as now: same [fileKey],
     * [sizeBytes] and [modifiedAt]. Null means the file must be hashed (again).
     *
     * Only the hash (or the cannot-hash answer) is cached by file. A row with an [RaMatchEntity.md5]
     * is looked up again in the system's game list (the matcher's `matchHash`), so a set
     * RetroAchievements adds later turns a stored [RaMatchEntity.NO_SET] into a match.
     */
    @Query(
        "SELECT * FROM ra_match WHERE game_id = :gameId AND file_key = :fileKey " +
            "AND size_bytes = :sizeBytes AND modified_at = :modifiedAt",
    )
    abstract suspend fun freshMatch(gameId: Long, fileKey: String, sizeBytes: Long, modifiedAt: Long): RaMatchEntity?

    /**
     * Stores [match], replacing the game's earlier one. False, and nothing written, when the game
     * row is gone (a scan removed it while the file was being hashed): the check and the write are
     * one transaction, so a row is never written for a deleted game.
     */
    @Transaction
    open suspend fun saveMatch(match: RaMatchEntity): Boolean {
        if (!gameExists(match.gameId)) return false
        upsertMatch(match)
        return true
    }

    /**
     * RetroAchievements' game id of each of [gameIds] that is matched; games not matched (or never
     * checked) are absent. Any number of ids: read in chunks Android 6's SQLite accepts.
     */
    @Transaction
    open suspend fun matchedRaGameIds(gameIds: Collection<Long>): Map<Long, Long> = matchedRaGameIdsInChunks(gameIds, MAX_IDS)

    /** [matchedRaGameIds] read [chunkSize] ids per statement; tests pass a small size to see the chunks joined. */
    internal suspend fun matchedRaGameIdsInChunks(gameIds: Collection<Long>, chunkSize: Int): Map<Long, Long> =
        gameIds.distinct().chunked(chunkSize).flatMap { matchedChunk(it) }.associate { it.gameId to it.raGameId }

    /** Writes [rows] (one per RetroAchievements game) in one transaction, replacing rows of the same game. */
    @Upsert
    abstract suspend fun upsertProgress(rows: List<RaProgressEntity>)

    /**
     * Makes [rows] the whole progress table: every other row is deleted (another account's, a game
     * whose progress was reset on the site). One transaction, so readers never see it empty.
     */
    @Transaction
    open suspend fun replaceProgress(rows: List<RaProgressEntity>) {
        clearProgress()
        upsertProgress(rows)
    }

    /**
     * The library games whose RetroAchievements game is mastered, for the mark on game rows.
     *
     * The rule: the progress row's highest award is `mastered`. That is RetroAchievements' own
     * judgement and what its site shows, so the mark agrees with what the person sees there. The
     * counts are not compared: a set that gains achievements after the award keeps the award on
     * the site, and the counts alone cannot tell a hardcore mastery from a softcore completion.
     * Every library copy matched to that game is in the set.
     */
    fun masteredGameIds(): Flow<Set<Long>> = masteredGameIdList().map { it.toSet() }.distinctUntilChanged()

    /** The person's progress in the RetroAchievements game [gameId] (a library game id) is matched to; null when none. */
    @Query(
        "SELECT ra_progress.* FROM ra_match JOIN ra_progress ON ra_progress.ra_game_id = ra_match.ra_game_id " +
            "WHERE ra_match.game_id = :gameId AND ra_match.status IN " + RaMatchEntity.MATCHED_STATUSES,
    )
    abstract fun observeProgress(gameId: Long): Flow<RaProgressEntity?>

    /** RetroAchievements' game ids that at least one library game is matched to. */
    @Query("SELECT DISTINCT ra_game_id FROM ra_match WHERE status IN " + RaMatchEntity.MATCHED_STATUSES + " AND ra_game_id IS NOT NULL")
    abstract suspend fun matchedRaGameIdList(): List<Long>

    /**
     * Library games of [systemIds] that are not matched to a RetroAchievements game (never checked,
     * no set or cannot be hashed), with their titles and stored answer: the completion refresh
     * compares these titles with mastered games it cannot place, so it hashes only the few whose
     * title agrees and that were never checked.
     */
    @Query(
        "SELECT game.id AS game_id, game.system_id, game.title, game.file_title, ra_match.status AS match_status, " +
            "ra_match.md5 AS md5 FROM game " +
            "LEFT JOIN ra_match ON ra_match.game_id = game.id WHERE game.system_id IN (:systemIds) " +
            "AND game.is_not_game = 0 AND (ra_match.status IS NULL OR ra_match.status NOT IN " + RaMatchEntity.MATCHED_STATUSES + ")",
    )
    abstract suspend fun unmatchedGames(systemIds: List<String>): List<RaUnmatchedGame>

    /**
     * Deletes the cached answers a newer matcher may answer differently: "cannot hash" for a format
     * that needed a decoder, and name matches. Cache rows only (the next check recomputes them).
     */
    @Query(
        "DELETE FROM ra_match WHERE status = '" + RaMatchEntity.MATCHED_BY_NAME + "' OR " +
            "(status = '" + RaMatchEntity.CANNOT_HASH + "' AND cannot_hash_reason = 'NeedsDecoder')",
    )
    abstract suspend fun deleteRowsToCheckAgain()

    /** Deletes every progress row (sign-out). Matches are not personal and stay. */
    @Query("DELETE FROM ra_progress")
    abstract suspend fun clearProgress()

    @Upsert
    protected abstract suspend fun upsertMatch(match: RaMatchEntity)

    @Query("SELECT EXISTS (SELECT 1 FROM game WHERE id = :gameId)")
    protected abstract suspend fun gameExists(gameId: Long): Boolean

    @Query(
        "SELECT game_id, ra_game_id FROM ra_match WHERE status IN " + RaMatchEntity.MATCHED_STATUSES + " " +
            "AND ra_game_id IS NOT NULL AND game_id IN (:gameIds)",
    )
    protected abstract suspend fun matchedChunk(gameIds: List<Long>): List<RaMatchedGame>

    @Query(
        "SELECT ra_match.game_id FROM ra_match JOIN ra_progress ON ra_progress.ra_game_id = ra_match.ra_game_id " +
            "WHERE ra_match.status IN " + RaMatchEntity.MATCHED_STATUSES + " AND ra_progress.highest_award = '" +
            RaProgressEntity.MASTERED + "'",
    )
    protected abstract fun masteredGameIdList(): Flow<List<Long>>

    private companion object {
        // SQLite on Android 6 allows 999 bound parameters per statement.
        const val MAX_IDS = 900
    }
}

/** A matched library game and its RetroAchievements game id ([RaDao.matchedRaGameIds]). */
data class RaMatchedGame(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "ra_game_id") val raGameId: Long,
)

/** A library game not matched yet, with its titles ([RaDao.unmatchedGames]). */
data class RaUnmatchedGame(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "file_title") val fileTitle: String,
    /** The stored match's status ([RaMatchEntity.NO_SET], [RaMatchEntity.CANNOT_HASH]); null when never checked. */
    @ColumnInfo(name = "match_status") val matchStatus: String? = null,
    /** The stored hash, when the file was hashed. */
    @ColumnInfo(name = "md5") val md5: String? = null,
)
