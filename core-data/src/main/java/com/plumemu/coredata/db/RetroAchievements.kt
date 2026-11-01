package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema 14: which RetroAchievements game a library game is, and the hash read to find out.
 * Not personal (the same file gives the same answer for anyone), so signing out keeps it.
 *
 * The row is also the hash cache: while the file's [fileKey], [sizeBytes] and [modifiedAt] are
 * the ones stored, the file is not hashed again ([RaDao.freshMatch]). Only the hash is cached that
 * way: a stored [md5] is looked up in the game list again, so [NO_SET] is not final; a
 * [CANNOT_HASH] answer and a [MATCHED_BY_NAME] one stay until the file changes (or the app's matcher version rises and clears them, [RaDao.deleteRowsToCheckAgain]). Deleting the game deletes
 * the row (a rescan that gives the file a new game row hashes it once more).
 *
 * Plain values only: this module does not depend on the achievements module, whose matcher
 * produces these answers.
 */
@Entity(
    tableName = "ra_match",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ra_game_id"])],
)
data class RaMatchEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    /** [MATCHED], [MATCHED_BY_NAME], [NO_SET] or [CANNOT_HASH]. */
    @ColumnInfo(name = "status") val status: String,
    /** RetroAchievements' game id; set only when [status] is [MATCHED] or [MATCHED_BY_NAME]. */
    @ColumnInfo(name = "ra_game_id") val raGameId: Long?,
    /** The file's RetroAchievements hash (32 lowercase hex digits); null when it could not be hashed. */
    @ColumnInfo(name = "md5") val md5: String?,
    /** Why the file could not be hashed (the hasher's reason name); set only for [CANNOT_HASH]. */
    @ColumnInfo(name = "cannot_hash_reason") val cannotHashReason: String?,
    /** The path or document URI that was hashed. */
    @ColumnInfo(name = "file_key") val fileKey: String,
    /** The file's size in bytes when it was hashed. */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    /** The file's modified time (epoch milliseconds) when it was hashed. */
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    /** Epoch milliseconds of the check. */
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
) {
    companion object {
        /** RetroAchievements lists a game with this hash. */
        const val MATCHED = "matched"

        /** Hashed, but RetroAchievements lists no game with this hash for the system. */
        const val NO_SET = "no_set"

        /** The file cannot be given a hash (format, archive, empty file). */
        const val CANNOT_HASH = "cannot_hash"

        /**
         * The file needs a decoder Plumemu lacks, so it has no hash, but exactly one game of the
         * console has its name; [RaMatchEntity.raGameId] is set, [RaMatchEntity.md5] is null. Less
         * certain than [MATCHED]: Info says "Matched by name". Counts as matched everywhere
         * ([MATCHED_STATUSES]).
         */
        const val MATCHED_BY_NAME = "matched_by_name"

        /** SQL list of the statuses that count as matched (progress, mastered, Info, after a game). */
        val MATCHED_STATUS_LIST = listOf(MATCHED, MATCHED_BY_NAME)

        /** [MATCHED_STATUS_LIST] as the SQL list. */
        const val MATCHED_STATUSES = "('" + MATCHED + "', '" + MATCHED_BY_NAME + "')"
    }
}

/**
 * Schema 14: the signed-in person's progress in one RetroAchievements game, from the completion
 * list. Personal: [RaDao.clearProgress] deletes every row on sign-out. Keyed by RetroAchievements'
 * game id, so several library copies of one game share a row and no library row is needed.
 */
@Entity(tableName = "ra_progress")
data class RaProgressEntity(
    @PrimaryKey @ColumnInfo(name = "ra_game_id") val raGameId: Long,
    /** Achievements in the game's set. */
    @ColumnInfo(name = "achievements_total") val achievementsTotal: Int,
    /** Achievements unlocked in either mode (RetroAchievements counts a hardcore unlock here too). */
    @ColumnInfo(name = "awarded_softcore") val awardedSoftcore: Int,
    /** Achievements unlocked in hardcore. */
    @ColumnInfo(name = "awarded_hardcore") val awardedHardcore: Int,
    /**
     * RetroAchievements' highest award kind as it sends it (`beaten-softcore`, `beaten-hardcore`,
     * `completed`, `mastered`); null when there is none.
     */
    @ColumnInfo(name = "highest_award") val highestAward: String?,
    /** Epoch milliseconds of the refresh that wrote the row. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        /** The award kind of a mastered game (every achievement unlocked in hardcore). */
        const val MASTERED = "mastered"
    }
}
