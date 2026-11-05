package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * RetroAchievements in the library database (schema 14): each library game's match and hash
 * cache (`ra_match`, [RaMatchEntity]), the signed-in person's progress (`ra_progress`,
 * [RaProgressEntity]) and, from schema 17, each game's achievements as its list last said
 * (`ra_achievement`, [RaAchievementEntity]). Nothing here is read while signed out: callers only build what reads it
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
     * whose progress was reset on the site), with its stored achievements. One transaction, so
     * readers never see it empty.
     *
     * The completion list has no points: a row given without them keeps the stored total while the
     * game's achievement count is unchanged, and the stored earned points while the unlocked count
     * is unchanged too; otherwise they become null (not known) until the game's list is read again.
     */
    @Transaction
    open suspend fun replaceProgress(rows: List<RaProgressEntity>) {
        val stored = allProgress().associateBy { it.raGameId }
        deleteProgressRows()
        upsertProgress(rows.map { keepKnownPoints(it, stored[it.raGameId]) })
        deleteAchievementsWithoutProgress()
    }

    /**
     * Stores one game's [progress] and, when [achievements] is not null, makes it that game's whole
     * achievement list (rows of another game in it are ignored). Null keeps the stored list (the
     * answer's list was incomplete). One transaction.
     */
    @Transaction
    open suspend fun saveGameProgress(progress: RaProgressEntity, achievements: List<RaAchievementEntity>?) {
        upsertProgress(listOf(progress))
        if (achievements == null) return
        deleteAchievementsOf(progress.raGameId)
        insertAchievements(achievements.filter { it.raGameId == progress.raGameId })
    }

    /**
     * The [limit] achievements of the RetroAchievements game [raGameId] unlocked last (in either
     * mode), latest first: by the later of the two unlock times, then by id. Locked ones never.
     */
    @Query(
        "SELECT * FROM ra_achievement WHERE ra_game_id = :raGameId AND (earned_at IS NOT NULL OR earned_hardcore_at IS NOT NULL) " +
            "ORDER BY MAX(COALESCE(earned_at, 0), COALESCE(earned_hardcore_at, 0)) DESC, achievement_id LIMIT :limit",
    )
    abstract fun observeRecentAchievements(raGameId: Long, limit: Int): Flow<List<RaAchievementEntity>>

    /**
     * The person's RetroAchievements totals on [systemId]: each RetroAchievements game counted once
     * however many library copies are matched to it, through matched, listed (not hidden, not
     * "Not a game") library games of the system. Every regional copy counts, so a matched copy that
     * is not the shown one still places its game. Zeros when there is nothing.
     */
    fun observeSystemTotals(systemId: String): Flow<RaSystemTotals> = systemTotals(systemId).distinctUntilChanged()

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

    /** Deletes every progress row and every stored achievement (sign-out). Matches are not personal and stay. */
    @Transaction
    open suspend fun clearProgress() {
        deleteProgressRows()
        deleteAllAchievements()
    }

    @Query("DELETE FROM ra_progress")
    protected abstract suspend fun deleteProgressRows()

    @Query("DELETE FROM ra_achievement")
    protected abstract suspend fun deleteAllAchievements()

    @Query("DELETE FROM ra_achievement WHERE ra_game_id NOT IN (SELECT ra_game_id FROM ra_progress)")
    protected abstract suspend fun deleteAchievementsWithoutProgress()

    @Query("DELETE FROM ra_achievement WHERE ra_game_id = :raGameId")
    protected abstract suspend fun deleteAchievementsOf(raGameId: Long)

    // REPLACE is safe here: no table refers to ra_achievement. It only settles an id given twice.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAchievements(rows: List<RaAchievementEntity>)

    @Query("SELECT * FROM ra_progress")
    protected abstract suspend fun allProgress(): List<RaProgressEntity>

    @Query(
        "SELECT COUNT(CASE WHEN highest_award = '" + RaProgressEntity.MASTERED + "' THEN 1 END) AS mastered_games, " +
            "COUNT(*) AS games_with_progress, COUNT(points_earned) AS games_with_points, " +
            "COALESCE(SUM(points_earned), 0) AS points_earned FROM ra_progress WHERE ra_game_id IN (" +
            "SELECT ra_match.ra_game_id FROM ra_match JOIN game ON game.id = ra_match.game_id " +
            "WHERE game.system_id = :systemId AND game.is_hidden = 0 AND game.is_not_game = 0 " +
            "AND ra_match.status IN " + RaMatchEntity.MATCHED_STATUSES + " AND ra_match.ra_game_id IS NOT NULL)",
    )
    protected abstract fun systemTotals(systemId: String): Flow<RaSystemTotals>

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

        /** [row] with the points [stored] knew, while the counts they were read with still hold ([replaceProgress]). */
        fun keepKnownPoints(row: RaProgressEntity, stored: RaProgressEntity?): RaProgressEntity {
            if (stored == null || stored.achievementsTotal != row.achievementsTotal) return row
            val total = row.pointsTotal ?: stored.pointsTotal
            val earned = row.pointsEarned ?: stored.pointsEarned.takeIf { stored.awardedSoftcore == row.awardedSoftcore }
            return row.copy(pointsTotal = total, pointsEarned = earned)
        }
    }
}

/** The person's RetroAchievements totals on one system ([RaDao.observeSystemTotals]). */
data class RaSystemTotals(
    /** RetroAchievements games whose highest award is mastered. */
    @ColumnInfo(name = "mastered_games") val masteredGames: Int,
    /** RetroAchievements games with a progress row. */
    @ColumnInfo(name = "games_with_progress") val gamesWithProgress: Int,
    /** Of those, the games whose points are known: [pointsEarned] sums only these. */
    @ColumnInfo(name = "games_with_points") val gamesWithPoints: Int,
    /** Points earned (either mode) over the games whose points are known; 0 when none are. */
    @ColumnInfo(name = "points_earned") val pointsEarned: Long,
)

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
