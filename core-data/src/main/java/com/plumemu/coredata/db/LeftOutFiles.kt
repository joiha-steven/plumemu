package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Schema 15: a file the last scan of its directory saw and left out of the library, with the
 * reason (Settings › Library › Files not added). Written only by [ScanDao.finishScan]: a walked
 * directory's rows are replaced, an unchanged directory keeps its rows, a gone or skipped directory
 * loses them. Goes with the picked folder (CASCADE) and with Reset library.
 *
 * Two kinds of row: a file ([relativePath] set, [more] 0), or "[more] more files" of [reason] in
 * [directoryPath] ([relativePath] null) for the files past [LeftOutFileDao.MAX_ROWS_PER_REASON].
 */
@Entity(
    tableName = "left_out_file",
    foreignKeys = [
        ForeignKey(
            entity = RomFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["folder_id", "directory_path"])],
)
data class LeftOutFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    /** The directory the file is in, below the picked folder (`""` for the folder itself), as `scanned_directory` has it. */
    @ColumnInfo(name = "directory_path") val directoryPath: String,
    /** The file's path below the picked folder (`PS2/notes.txt`); null on a "more" row. */
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    /** library's `LeftOutReason.code`; plain text here (core-data does not know library). */
    val reason: String,
    /** The system the file's folder names, null when it names none. */
    @ColumnInfo(name = "system_id") val systemId: String?,
    /** On a "more" row, how many files it stands for; 0 on a file row. */
    @ColumnInfo(name = "more", defaultValue = "0") val more: Int = 0,
)

/** What one directory's scan left out ([ScanFinish.leftOut]). */
data class LeftOutWrite(
    /** A walk from the picked folder down: every row of the folder is replaced, not only [directoryPaths]'. */
    val replacesAll: Boolean,
    /** The directories this scan listed: their rows are replaced by [rows]. */
    val directoryPaths: List<String>,
    val rows: List<LeftOutFileEntity>,
)

@Dao
abstract class LeftOutFileDao {
    /** Every row, file rows first by path within each folder and reason; at most [MAX_ROWS_PER_REASON] file rows per folder and reason. */
    @Query("SELECT * FROM left_out_file ORDER BY folder_id, reason, relative_path IS NULL, relative_path, directory_path")
    abstract fun observeAll(): Flow<List<LeftOutFileEntity>>

    @Query("SELECT * FROM left_out_file WHERE folder_id = :folderId ORDER BY reason, relative_path IS NULL, relative_path, directory_path")
    abstract suspend fun inFolder(folderId: Long): List<LeftOutFileEntity>

    /**
     * Files left out of [systemId] in every picked folder: file rows plus the files each "more" row
     * stands for. Rows whose folder names no system (null `system_id`) belong to no system.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN relative_path IS NULL THEN more ELSE 1 END), 0) FROM left_out_file WHERE system_id = :systemId")
    abstract suspend fun countInSystem(systemId: String): Int

    /** Called by [ScanDao.finishScan] inside its transaction; never on its own. */
    suspend fun write(
        folderId: Long,
        write: LeftOutWrite,
    ) {
        if (write.replacesAll) {
            deleteFolder(folderId)
        } else {
            write.directoryPaths.distinct().chunked(MAX_IDS_PER_STATEMENT).forEach { deleteDirectories(folderId, it) }
        }
        write.rows.chunked(MAX_IDS_PER_STATEMENT).forEach { insert(it) }
        trim(folderId)
    }

    /** Rows of the directory at [path] and every directory below it (a gone or skipped directory). */
    suspend fun deleteSubtree(
        folderId: Long,
        path: String,
    ) = deleteUnder(folderId, path, "$path/")

    /**
     * Keeps at most [MAX_ROWS_PER_REASON] file rows per reason in the folder, the first by path; the
     * rest become counts on their directory's "more" row, so the totals stay right.
     */
    private suspend fun trim(folderId: Long) {
        for (reason in reasonsOver(folderId, MAX_ROWS_PER_REASON)) {
            val extra = fileRowsAfter(folderId, reason, MAX_ROWS_PER_REASON)
            extra.map { it.id }.chunked(MAX_IDS_PER_STATEMENT).forEach { deleteIds(it) }
            for ((directory, rows) in extra.groupBy { it.directoryPath }) {
                val systemId = rows.first().systemId
                if (addMore(folderId, directory, reason, rows.size) == 0) {
                    insert(listOf(LeftOutFileEntity(folderId = folderId, directoryPath = directory, relativePath = null, reason = reason, systemId = systemId, more = rows.size)))
                }
            }
        }
    }

    @Insert
    protected abstract suspend fun insert(rows: List<LeftOutFileEntity>)

    @Query("DELETE FROM left_out_file WHERE folder_id = :folderId")
    protected abstract suspend fun deleteFolder(folderId: Long)

    @Query("DELETE FROM left_out_file")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM left_out_file WHERE folder_id = :folderId AND directory_path IN (:paths)")
    protected abstract suspend fun deleteDirectories(
        folderId: Long,
        paths: List<String>,
    )

    // substr instead of LIKE: folder names may contain % or _.
    @Query(
        "DELETE FROM left_out_file WHERE folder_id = :folderId AND (directory_path = :path " +
            "OR substr(directory_path, 1, length(:pathWithSlash)) = :pathWithSlash)",
    )
    protected abstract suspend fun deleteUnder(
        folderId: Long,
        path: String,
        pathWithSlash: String,
    )

    @Query(
        "SELECT reason FROM left_out_file WHERE folder_id = :folderId AND relative_path IS NOT NULL " +
            "GROUP BY reason HAVING COUNT(*) > :cap",
    )
    protected abstract suspend fun reasonsOver(
        folderId: Long,
        cap: Int,
    ): List<String>

    @Query(
        "SELECT * FROM left_out_file WHERE folder_id = :folderId AND reason = :reason AND relative_path IS NOT NULL " +
            "ORDER BY relative_path LIMIT -1 OFFSET :cap",
    )
    protected abstract suspend fun fileRowsAfter(
        folderId: Long,
        reason: String,
        cap: Int,
    ): List<LeftOutFileEntity>

    @Query("DELETE FROM left_out_file WHERE id IN (:ids)")
    protected abstract suspend fun deleteIds(ids: List<Long>)

    /** Adds [count] to the directory's "more" row of [reason]; returns how many rows changed (0: there is none yet). */
    @Query(
        "UPDATE left_out_file SET more = more + :count WHERE folder_id = :folderId AND directory_path = :directory " +
            "AND reason = :reason AND relative_path IS NULL",
    )
    protected abstract suspend fun addMore(
        folderId: Long,
        directory: String,
        reason: String,
        count: Int,
    ): Int

    companion object {
        /**
         * File rows kept per picked folder and reason: five reasons, so at most 2,000 file rows
         * (about 200 KB) per folder whatever it holds; the rest are counted.
         */
        const val MAX_ROWS_PER_REASON = 400

        /** Bound parameters per statement stay under Android 6's 999 (a row insert binds 6). */
        private const val MAX_IDS_PER_STATEMENT = 150
    }
}
