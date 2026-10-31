package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A folder below a picked folder whose name matches no system and that holds files some system
 * could open (Settings › Library's "Unknown folder: <name> · N files"). [relativePath] is the
 * folder right below the picked folder (`Xbox 360`); [files] counts the files in it and every
 * folder below it that is unknown too. Names are for the screen only.
 */
data class UnknownFolderRow(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val files: Int,
)

/**
 * A folder the person answered ([systemId] null: Ignore this folder), listed in Settings › Library
 * under the unknown ones so the answer can be changed. Keyed by picked folder and [relativePath].
 */
data class AnsweredFolderRow(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "system_id") val systemId: String?,
    /**
     * Files in it (and the folders below it) that the last scan could not give a system: 0 while the
     * answer works. More than 0 means the answer is not used (its system left the platform data),
     * so Settings lists the folder as unanswered again.
     */
    val files: Int,
)

/**
 * The person's system for a folder whose name matches no system (schema 9, `folder_system`), and
 * the unknown folders the scan found (`scanned_directory.unknown_files`, written by library's
 * walk).
 */
@Dao
abstract class FolderSystemDao {
    /** Every choice of [folderId], read by the walk at its start. */
    @Query("SELECT * FROM folder_system WHERE folder_id = :folderId")
    abstract suspend fun choices(folderId: Long): List<FolderSystemEntity>

    /**
     * The unknown folders of the picked folders, grouped under the folder right below the picked
     * one, leaving out those the person answered (a system or Ignore). A directory's count is
     * written only when its whole path is unknown, so its first path segment is unknown too.
     */
    @Query(
        "SELECT u.folder_id, u.relative_path, u.files FROM (" +
            "SELECT d.folder_id AS folder_id, " + TOP_SEGMENT + " AS relative_path, SUM(d.unknown_files) AS files " +
            "FROM scanned_directory AS d JOIN rom_folder AS r ON r.id = d.folder_id AND r.tree_uri != '" + PinnedShortcutDao.FOLDER_TREE_URI + "' " +
                "AND r.tree_uri != '" + AppGameDao.FOLDER_TREE_URI + "' " +
            "WHERE d.unknown_files > 0 AND d.relative_path != '' GROUP BY d.folder_id, " + TOP_SEGMENT +
            ") AS u WHERE NOT EXISTS (SELECT 1 FROM folder_system AS f " +
            "WHERE f.folder_id = u.folder_id AND f.relative_path = u.relative_path) " +
            "ORDER BY u.folder_id, u.relative_path",
    )
    abstract fun observeUnknownFolders(): Flow<List<UnknownFolderRow>>

    /**
     * Every answered folder (not the pinned shortcuts' folder) that still exists in the library, by picked folder
     * then name. Exists means the scan keeps a directory record for it or below it; the answer itself stays stored
     * when the folder is gone, so it applies again if the folder comes back. Read only.
     */
    @Query(
        "SELECT f.folder_id, f.relative_path, f.system_id, COALESCE((SELECT SUM(d.unknown_files) FROM scanned_directory AS d " +
            "WHERE d.folder_id = f.folder_id AND (d.relative_path = f.relative_path " +
            "OR substr(d.relative_path, 1, length(f.relative_path) + 1) = f.relative_path || '/')), 0) AS files " +
            "FROM folder_system AS f " +
            "JOIN rom_folder AS r ON r.id = f.folder_id AND r.tree_uri != '" + PinnedShortcutDao.FOLDER_TREE_URI + "' " +
                "AND r.tree_uri != '" + AppGameDao.FOLDER_TREE_URI + "' " +
            "WHERE EXISTS (SELECT 1 FROM scanned_directory AS e WHERE e.folder_id = f.folder_id AND (e.relative_path = f.relative_path " +
            "OR substr(e.relative_path, 1, length(f.relative_path) + 1) = f.relative_path || '/')) " +
            "ORDER BY f.folder_id, f.relative_path",
    )
    abstract fun observeAnsweredFolders(): Flow<List<AnsweredFolderRow>>

    /** [observeUnknownFolders] of one picked folder, once: the walk compares it before and after. */
    @Query(
        "SELECT u.folder_id, u.relative_path, u.files FROM (" +
            "SELECT d.folder_id AS folder_id, " + TOP_SEGMENT + " AS relative_path, SUM(d.unknown_files) AS files " +
            "FROM scanned_directory AS d WHERE d.folder_id = :folderId AND d.unknown_files > 0 AND d.relative_path != '' " +
            "GROUP BY d.folder_id, " + TOP_SEGMENT +
            ") AS u WHERE NOT EXISTS (SELECT 1 FROM folder_system AS f " +
            "WHERE f.folder_id = u.folder_id AND f.relative_path = u.relative_path) " +
            "ORDER BY u.relative_path",
    )
    abstract suspend fun unknownFolders(folderId: Long): List<UnknownFolderRow>

    /**
     * Stores the person's answer for [relativePath] of [folderId] ([systemId] null: Ignore this
     * folder) and makes the next scan list that folder and every folder below it again (stored
     * time 0), so its files are scanned by the new answer even though nothing on the card changed.
     * One transaction: an answer is never stored without its folders being listed again.
     */
    @Transaction
    open suspend fun choose(folderId: Long, relativePath: String, systemId: String?, now: Long) {
        upsert(FolderSystemEntity(folderId, relativePath, systemId, now))
        listSubtreeAgain(folderId, relativePath, "$relativePath/")
    }

    @Upsert
    protected abstract suspend fun upsert(choice: FolderSystemEntity)

    // substr instead of LIKE: folder names may contain % or _.
    @Query(
        "UPDATE scanned_directory SET last_modified = 0 WHERE folder_id = :folderId AND (relative_path = :path " +
            "OR substr(relative_path, 1, length(:pathWithSlash)) = :pathWithSlash)",
    )
    protected abstract suspend fun listSubtreeAgain(folderId: Long, path: String, pathWithSlash: String)

    private companion object {
        /** The first segment of `d.relative_path` (`Xbox 360` of `Xbox 360/Halo`); `instr` exists on Android 6's SQLite. */
        const val TOP_SEGMENT =
            "(CASE WHEN instr(d.relative_path, '/') > 0 THEN substr(d.relative_path, 1, instr(d.relative_path, '/') - 1) " +
                "ELSE d.relative_path END)"
    }
}
