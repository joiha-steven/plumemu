package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/**
 * The sub-folders a person skips (schema 13, `skipped_subfolder`): Settings › ROM folders
 * lists and changes them, library's walk reads them at its start. Paths are compared ignoring case
 * and stray slashes ([matchKey]), like the walk's fixed list of skipped folders. Feature page
 *.
 */
@Dao
abstract class SkippedSubfolderDao {
    /** Every skipped sub-folder of every picked folder, by folder then path; Settings' folder panel. */
    @Query("SELECT * FROM skipped_subfolder ORDER BY folder_id, relative_path COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<SkippedSubfolderEntity>>

    /** The skipped paths of [folderId], read by the walk at its start. */
    @Query("SELECT relative_path FROM skipped_subfolder WHERE folder_id = :folderId")
    abstract suspend fun paths(folderId: Long): List<String>

    /**
     * Skips [relativePath] of [folderId] and makes the next scan list its parent again (stored time
     * 0), so the walk drops the folder's games even when nothing on the card changed. False, and
     * nothing written, when the path is empty (the picked folder itself) or already skipped in any
     * case. One transaction: a skip is never stored without its parent being listed again.
     */
    @Transaction
    open suspend fun skip(folderId: Long, relativePath: String, now: Long): Boolean {
        val path = normalized(relativePath)
        if (path.isEmpty()) return false
        val key = matchKey(path)
        if (paths(folderId).any { matchKey(it) == key }) return false
        insert(SkippedSubfolderEntity(folderId, path, now))
        listAgain(folderId, parentOf(path))
        return true
    }

    /**
     * Stops skipping [relativePath] of [folderId] (any case) and makes the next scan list its parent
     * again, so the walk finds the folder and its games come back. False when it was not skipped.
     */
    @Transaction
    open suspend fun unskip(folderId: Long, relativePath: String): Boolean {
        val key = matchKey(relativePath)
        val stored = paths(folderId).filter { matchKey(it) == key }
        if (stored.isEmpty()) return false
        for (path in stored) delete(folderId, path)
        listAgain(folderId, parentOf(normalized(relativePath)))
        return true
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(row: SkippedSubfolderEntity)

    @Query("DELETE FROM skipped_subfolder WHERE folder_id = :folderId AND relative_path = :relativePath")
    protected abstract suspend fun delete(folderId: Long, relativePath: String)

    // NOCASE: the record keeps the case the walk saw, the skip the case it was picked in (ASCII only in SQLite).
    @Query("UPDATE scanned_directory SET last_modified = 0 WHERE folder_id = :folderId AND relative_path = :path COLLATE NOCASE")
    protected abstract suspend fun listAgain(folderId: Long, path: String)

    companion object {
        /** [path] without empty segments: no leading, trailing or doubled `/`, segments trimmed of nothing else. */
        fun normalized(path: String): String = path.split('/').filter { it.isNotEmpty() }.joinToString("/")

        /** How the walk and the panel compare a skipped path: [normalized] and lower case. */
        fun matchKey(path: String): String = normalized(path).lowercase(Locale.ROOT)

        private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")
    }
}
