package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Picked folders, scanned directories and the scan's writes to `game`. */
@Dao
abstract class ScanDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFolder(folder: RomFolderEntity): Long

    @Query("SELECT * FROM rom_folder WHERE tree_uri = :treeUri")
    abstract suspend fun folderByTreeUri(treeUri: String): RomFolderEntity?

    /** The picked folders; never the reserved ones of pinned shortcuts ([PinnedShortcutDao]) or installed apps ([AppGameDao]). */
    @Query("SELECT * FROM rom_folder WHERE " + PICKED + " ORDER BY id")
    abstract suspend fun folders(): List<RomFolderEntity>

    @Query("SELECT * FROM rom_folder WHERE " + PICKED + " ORDER BY id")
    abstract fun observeFolders(): Flow<List<RomFolderEntity>>

    /** Removes a folder with its games, directories, play history and per-game choices. */
    @Query("DELETE FROM rom_folder WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: Long)

    @Query("UPDATE rom_folder SET last_scan_finished_at = :finishedAt WHERE id = :folderId")
    abstract suspend fun markScanFinished(folderId: Long, finishedAt: Long)

    @Query("SELECT * FROM scanned_directory WHERE folder_id = :folderId")
    abstract suspend fun directories(folderId: Long): List<ScannedDirectoryEntity>

    /** Forgets every directory record, so the next scan lists everything again; games stay. */
    @Query("DELETE FROM scanned_directory")
    abstract suspend fun forgetDirectories()

    /**
     * Settings › Advanced › Reset library: forgets every game with what the person attached to it
     * (favourites, hidden marks, play history, per-game emulator choices and media go with the rows
     * by CASCADE) and every directory record, so the next scan reads all folders from scratch. In
     * one transaction: a crash midway leaves the library as it was. The picked folders stay (their
     * rows, so the lasting grants Android holds for them keep being used), and so do the systems'
     * emulator choices, which belong to Settings › Emulators rather than to the games.
     */
    @Transaction
    open suspend fun forgetLibrary() {
        deleteAllGames()
        // Name fixes are the person's data on games too; content keys would outlive the rows.
        deleteAllNameFixes()
        forgetDirectories()
        clearScanTimes()
    }

    @Query("DELETE FROM name_fix")
    protected abstract suspend fun deleteAllNameFixes()

    @Query("DELETE FROM game")
    protected abstract suspend fun deleteAllGames()

    @Query("UPDATE rom_folder SET last_scan_finished_at = NULL")
    protected abstract suspend fun clearScanTimes()

    @Query(
        "SELECT id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, " +
            "file_title, file_sort_name, file_regions, disc_group, disc_count, system_id, is_primary_copy FROM game " +
            "WHERE folder_id = :folderId AND parent_document_id = :parentDocumentId",
    )
    abstract suspend fun gamesInDirectory(folderId: Long, parentDocumentId: String): List<StoredGame>

    /** All games of a folder; only for tests and diagnostics, never for a list screen. */
    @Query("SELECT * FROM game WHERE folder_id = :folderId ORDER BY relative_path")
    abstract suspend fun gamesInFolder(folderId: Long): List<GameEntity>

    /** Games under a directory path (the directory itself and everything below it). */
    @Query(
        "SELECT id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, " +
            "file_title, file_sort_name, file_regions, disc_group, disc_count, system_id, is_primary_copy FROM game " +
            "WHERE folder_id = :folderId AND (substr(relative_path, 1, length(:pathWithSlash)) = :pathWithSlash)",
    )
    protected abstract suspend fun gamesUnder(folderId: Long, pathWithSlash: String): List<StoredGame>

    suspend fun gamesInSubtree(folderId: Long, path: String): List<StoredGame> = gamesUnder(folderId, "$path/")

    /** Rows inserted by a scan of the folder that did not finish (see [GameEntity]). */
    @Query("SELECT id FROM game WHERE folder_id = :folderId AND is_pending = 1")
    abstract suspend fun pendingGameIds(folderId: Long): List<Long>

    /** Every directory that holds a game of the folder, to find games of directories that are gone. */
    @Query("SELECT DISTINCT parent_document_id FROM game WHERE folder_id = :folderId")
    abstract suspend fun parentDocumentIds(folderId: Long): List<String>

    /**
     * Inserts the batch's new games and updates its changed ones. Returns the rows actually
     * inserted. A new game whose document id is already stored (a provider with ids that survive a
     * move) is not inserted again; its stored row takes the new place instead.
     *
     * Inserted rows are pending until [finishScan]: if the scan stops before then, the next scan
     * still treats them as possibly moved files.
     */
    @Transaction
    open suspend fun applyBatch(batch: ScanBatch): List<StoredGame> {
        if (batch.systems.isNotEmpty()) insertSystems(batch.systems)
        val ids = if (batch.newGames.isEmpty()) emptyList() else insertGames(batch.newGames.map { it.copy(isPending = true) })
        val inserted = ArrayList<StoredGame>(batch.newGames.size)
        batch.newGames.forEachIndexed { index, game ->
            val fields = game.scanFields()
            if (ids[index] != -1L) {
                inserted += StoredGame(ids[index], fields)
            } else {
                gameIdByDocument(batch.folderId, game.documentId)?.let { updateScanFields(StoredGame(it, fields)) }
            }
        }
        for (game in batch.changedGames) updateScanFields(game)
        return inserted
    }

    @Transaction
    open suspend fun finishScan(finish: ScanFinish) {
        for (move in finish.moves) {
            if (move.ontoNewLaunchRow) {
                moveOntoNewLaunchRow(keptId = move.keptGameId, newId = move.newRow.id)
            } else {
                mergePersonData(keptId = move.keptGameId, newId = move.newRow.id)
            }
            deleteGames(listOf(move.newRow.id))
            updateScanFields(StoredGame(move.keptGameId, move.newRow.fields))
        }
        for (fold in finish.folds) {
            foldPersonData(keptId = fold.keptGameId, foldedId = fold.foldedGameId)
            deleteGames(listOf(fold.foldedGameId))
        }
        finish.removedGameIds.chunked(MAX_IDS_PER_STATEMENT).forEach { deleteGames(it) }
        for (path in finish.removedDirectoryPaths) deleteDirectoriesInSubtree(finish.folderId, path, "$path/")
        if (finish.directories.isNotEmpty()) upsertDirectories(finish.directories)
        clearPending(finish.folderId)
    }

    /**
     * The new row of a move was on the list screens since its batch landed, so the person may have
     * marked or played it. Before it is deleted (CASCADE takes its `play` and `emulator_choice`),
     * its data joins the kept row: favourite and hidden if either row has them, play time summed
     * with the later last-played time (its `play_session` rows move to the kept row), and its
     * emulator choice and row-key name fix when the kept row has none.
     */
    private suspend fun mergePersonData(keptId: Long, newId: Long) {
        mergeMarks(keptId, newId)
        addPlay(keptId, newId)
        movePlayIfKeptHasNone(keptId, newId)
        moveSessions(keptId, newId)
        moveEmulatorChoiceIfKeptHasNone(keptId, newId)
        moveRowKeyFixIfKeptHasNone(keptId, newId, NameFixEntity.rowKey(keptId))
        markForNamingIfFixed(keptId, NameFixEntity.rowKey(keptId))
    }

    /**
     * A row about to go because its file is now part of the kept row's game (grouping: a
     * `Game - Disc 2` entry of an older scan, a disc set an `.m3u` now stands for). Several rows
     * may fold into one; each fold:
     * - favourite: either row's;
     * - out of the lists (hidden, or "Not a game") only if every row was: a person who hid the
     *   extra `Disc 2` entry, marked it "Not a game", or hid the `.m3u` as a duplicate of the
     *   visible discs kept the game itself visible. So the kept row's hidden mark stays only when
     *   the folded row was hidden or not a game, and when the folded row was a listed game the
     *   kept row's "Not a game" fixes go, under its `game:<id>` key and its content keys alike
     *   (a content-key fix also covered other copies of that same file: they come back too);
     * - a folded row's "Not a game" fix never moves;
     * - play time summed and its sessions moved, emulator choice and another row-key fix moved when
     *   the kept row has none;
     *   the kept row is named again when a fix touches it, so `is_not_game` follows.
     */
    private suspend fun foldPersonData(keptId: Long, foldedId: Long) {
        val keptKey = NameFixEntity.rowKey(keptId)
        mergeFavorite(keptId, foldedId)
        keepHiddenOnlyIfFoldedWasUnlisted(keptId, foldedId)
        if (isListedGame(foldedId) == true) {
            deleteNotAGameFixes(FixKeys.split(storedFixKeys(keptId).orEmpty()) + keptKey)
        }
        addPlay(keptId, foldedId)
        movePlayIfKeptHasNone(keptId, foldedId)
        moveSessions(keptId, foldedId)
        moveEmulatorChoiceIfKeptHasNone(keptId, foldedId)
        moveNamingRowKeyFixIfKeptHasNone(keptId, foldedId, keptKey)
        markForNamingIfFixedOrNotAGame(keptId, keptKey)
    }

    /**
     * An old row moved onto the game's new launch row (an `.m3u` new in this scan, or pending from
     * an interrupted one, now stands for the old row's discs). As a rename, except: the hidden mark
     * stays the old row's (a fresh row is visible by default, which says nothing; a pending `.m3u`
     * the person hid as a duplicate of the visible discs must not hide the game), and the new
     * row's "Not a game" fix never moves (it marked the extra entry, not the game). Play time and
     * sessions join the old row as in a rename.
     */
    private suspend fun moveOntoNewLaunchRow(keptId: Long, newId: Long) {
        val keptKey = NameFixEntity.rowKey(keptId)
        mergeFavorite(keptId, newId)
        addPlay(keptId, newId)
        movePlayIfKeptHasNone(keptId, newId)
        moveSessions(keptId, newId)
        moveEmulatorChoiceIfKeptHasNone(keptId, newId)
        moveNamingRowKeyFixIfKeptHasNone(keptId, newId, keptKey)
        markForNamingIfFixed(keptId, keptKey)
    }

    @Query("SELECT fix_keys FROM game WHERE id = :gameId")
    protected abstract suspend fun storedFixKeys(gameId: Long): String?

    @Query("SELECT is_hidden = 0 AND is_not_game = 0 FROM game WHERE id = :gameId")
    protected abstract suspend fun isListedGame(gameId: Long): Boolean?

    @Query("DELETE FROM name_fix WHERE kind = '" + NOT_A_GAME + "' AND fix_key IN (:keys)")
    protected abstract suspend fun deleteNotAGameFixes(keys: List<String>)

    @Query(
        "UPDATE game SET is_favorite = is_favorite OR (SELECT is_favorite FROM game WHERE id = :foldedId) " +
            "WHERE id = :keptId AND EXISTS (SELECT 1 FROM game WHERE id = :foldedId)",
    )
    protected abstract suspend fun mergeFavorite(keptId: Long, foldedId: Long)

    @Query(
        "UPDATE game SET is_hidden = is_hidden AND " +
            "(SELECT is_hidden OR is_not_game FROM game WHERE id = :foldedId) " +
            "WHERE id = :keptId AND EXISTS (SELECT 1 FROM game WHERE id = :foldedId)",
    )
    protected abstract suspend fun keepHiddenOnlyIfFoldedWasUnlisted(keptId: Long, foldedId: Long)

    @Query(
        "UPDATE name_fix SET game_id = :keptId, fix_key = :keptKey WHERE game_id = :foldedId " +
            "AND kind != '" + NOT_A_GAME + "' AND NOT EXISTS (SELECT 1 FROM name_fix WHERE fix_key = :keptKey)",
    )
    protected abstract suspend fun moveNamingRowKeyFixIfKeptHasNone(keptId: Long, foldedId: Long, keptKey: String)

    @Query(
        "UPDATE game SET needs_identify = 1 WHERE id = :keptId " +
            "AND (is_not_game = 1 OR EXISTS (SELECT 1 FROM name_fix WHERE fix_key = :keptKey))",
    )
    protected abstract suspend fun markForNamingIfFixedOrNotAGame(keptId: Long, keptKey: String)

    // A file that only moved is not named again by itself (same size, time and name): with a
    // row-key fix now on the kept row, the next pass must run so the list shows the fix.
    @Query(
        "UPDATE game SET needs_identify = 1 WHERE id = :keptId " +
            "AND EXISTS (SELECT 1 FROM name_fix WHERE fix_key = :keptKey)",
    )
    protected abstract suspend fun markForNamingIfFixed(keptId: Long, keptKey: String)

    // Only the `game:<id>` fallback fix is tied to a row; fixes under content keys follow the file.
    @Query(
        "UPDATE name_fix SET game_id = :keptId, fix_key = :keptKey WHERE game_id = :newId " +
            "AND NOT EXISTS (SELECT 1 FROM name_fix WHERE fix_key = :keptKey)",
    )
    protected abstract suspend fun moveRowKeyFixIfKeptHasNone(keptId: Long, newId: Long, keptKey: String)

    @Query(
        "UPDATE game SET " +
            "is_favorite = is_favorite OR (SELECT is_favorite FROM game WHERE id = :newId), " +
            "is_hidden = is_hidden OR (SELECT is_hidden FROM game WHERE id = :newId) " +
            "WHERE id = :keptId AND EXISTS (SELECT 1 FROM game WHERE id = :newId)",
    )
    protected abstract suspend fun mergeMarks(keptId: Long, newId: Long)

    @Query(
        "UPDATE play SET " +
            "total_seconds = total_seconds + (SELECT total_seconds FROM play WHERE game_id = :newId), " +
            "last_played_at = max(last_played_at, (SELECT last_played_at FROM play WHERE game_id = :newId)), " +
            // Out of Recent only when both were: a merge never hides a game the person kept.
            "recent_removed = recent_removed AND (SELECT recent_removed FROM play WHERE game_id = :newId) " +
            "WHERE game_id = :keptId AND EXISTS (SELECT 1 FROM play WHERE game_id = :newId)",
    )
    protected abstract suspend fun addPlay(keptId: Long, newId: Long)

    @Query(
        "UPDATE play SET game_id = :keptId WHERE game_id = :newId " +
            "AND NOT EXISTS (SELECT 1 FROM play WHERE game_id = :keptId)",
    )
    protected abstract suspend fun movePlayIfKeptHasNone(keptId: Long, newId: Long)

    // Every session goes to the kept row, whose total now holds their seconds (`addPlay`); the
    // CASCADE of the delete that follows would drop them. `system_id` stays what was played.
    @Query("UPDATE play_session SET game_id = :keptId WHERE game_id = :fromId")
    protected abstract suspend fun moveSessions(keptId: Long, fromId: Long)

    @Query(
        "UPDATE emulator_choice SET game_id = :keptId WHERE game_id = :newId " +
            "AND NOT EXISTS (SELECT 1 FROM emulator_choice WHERE game_id = :keptId)",
    )
    protected abstract suspend fun moveEmulatorChoiceIfKeptHasNone(keptId: Long, newId: Long)

    @Query("UPDATE game SET is_pending = 0 WHERE folder_id = :folderId AND is_pending = 1")
    protected abstract suspend fun clearPending(folderId: Long)

    @Query("SELECT id FROM game WHERE folder_id = :folderId AND document_id = :documentId")
    protected abstract suspend fun gameIdByDocument(folderId: Long, documentId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSystems(systems: List<SystemEntity>)

    // IGNORE: a file already stored (same folder and document id) keeps its row and its
    // person-owned columns; the scan never inserts the same file twice. -1 marks an ignored row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertGames(games: List<GameEntity>): List<Long>

    @Upsert
    protected abstract suspend fun upsertDirectories(directories: List<ScannedDirectoryEntity>)

    @Query("DELETE FROM game WHERE id IN (:ids)")
    protected abstract suspend fun deleteGames(ids: List<Long>)

    // substr instead of LIKE: folder names may contain % or _.
    @Query(
        "DELETE FROM scanned_directory WHERE folder_id = :folderId AND (relative_path = :path " +
            "OR substr(relative_path, 1, length(:pathWithSlash)) = :pathWithSlash)",
    )
    protected abstract suspend fun deleteDirectoriesInSubtree(folderId: Long, path: String, pathWithSlash: String)

    /**
     * Every row of the directories [parentDocumentIds] of [folderId], for the grouping of copies
     * across sibling game folders (library `GameFolderCopies`). Indexed (`folder_id,
     * parent_document_id`); no file is read.
     */
    suspend fun gamesInDirectories(folderId: Long, parentDocumentIds: Collection<String>): List<GameFolderRow> =
        parentDocumentIds.distinct().chunked(MAX_IDS_PER_STATEMENT).flatMap { gamesInDirectoriesChunk(folderId, it) }

    @Query(
        "SELECT id, parent_document_id, relative_path, file_name, file_sort_name, file_regions, system_id, " +
            "is_not_game, is_primary_copy, disc_count FROM game WHERE folder_id = :folderId AND parent_document_id IN (:parentDocumentIds)",
    )
    protected abstract suspend fun gamesInDirectoriesChunk(folderId: Long, parentDocumentIds: List<String>): List<GameFolderRow>

    /**
     * Makes [shown] the copies their groups show and [leftOut] copies the lists leave out, in one
     * transaction. Touches nothing else: marks stay on the row they were made on.
     *
     * @return the number of rows that changed.
     */
    @Transaction
    open suspend fun setPrimaryCopies(shown: List<Long>, leftOut: List<Long>): Int =
        shown.chunked(MAX_IDS_PER_STATEMENT).sumOf { setPrimaryCopyChunk(it, true) } +
            leftOut.chunked(MAX_IDS_PER_STATEMENT).sumOf { setPrimaryCopyChunk(it, false) }

    @Query("UPDATE game SET is_primary_copy = :isPrimaryCopy WHERE id IN (:ids) AND is_primary_copy != :isPrimaryCopy")
    protected abstract suspend fun setPrimaryCopyChunk(ids: List<Long>, isPrimaryCopy: Boolean): Int

    /**
     * Rows of one directory the identifier named as arcade sets (`ARCADE_NAME`) that are listed
     * games: library's naming pass decides which of them shows (`ArcadeCopies`), so the walk keeps
     * their stored `is_primary_copy`. Every other row takes the catalogue's.
     */
    @Query(
        "SELECT id FROM game WHERE folder_id = :folderId AND parent_document_id = :parentDocumentId " +
            "AND id_method = '" + IdentifyDao.ARCADE_NAME + "' AND is_not_game = 0",
    )
    abstract suspend fun arcadeNamedIn(folderId: Long, parentDocumentId: String): List<Long>

    /**
     * Makes the next scan list [documentIds] of [folderId] again (stored time 0, as for a time too
     * close to the scan): library's naming pass asks it for a directory whose arcade row left the
     * arcade grouping, so the catalogue gives that row its answer again.
     */
    @Query("UPDATE scanned_directory SET last_modified = 0 WHERE folder_id = :folderId AND document_id IN (:documentIds)")
    abstract suspend fun listAgain(folderId: Long, documentIds: List<String>)

    protected suspend fun updateScanFields(game: StoredGame) {
        val f = game.fields
        updateScanFields(
            game.id, f.documentId, f.parentDocumentId, f.relativePath, f.fileName, f.sizeBytes, f.modifiedAt,
            f.title, f.sortName, f.regions, f.discGroup, f.discCount, f.systemId, f.isPrimaryCopy,
        )
    }

    // SQLite evaluates every right-hand side on the row as it was, so the CASEs compare the old
    // file with the new one. The shown name follows the file name only while nothing better is
    // known; a changed file (size, time, name or system) is identified again by the next pass.
    @Query(
        "UPDATE game SET " +
            "needs_identify = CASE WHEN size_bytes != :sizeBytes OR modified_at != :modifiedAt " +
            "OR file_name != :fileName OR system_id != :systemId OR file_title != :title THEN 1 ELSE needs_identify END, " +
            "title = CASE WHEN name_trust = '" + NameTrustNames.FILE_NAME + "' THEN :title ELSE title END, " +
            "sort_name = CASE WHEN name_trust = '" + NameTrustNames.FILE_NAME + "' THEN :sortName ELSE sort_name END, " +
            "regions = CASE WHEN name_trust = '" + NameTrustNames.FILE_NAME + "' THEN :regions ELSE regions END, " +
            "document_id = :documentId, parent_document_id = :parentDocumentId, " +
            "relative_path = :relativePath, file_name = :fileName, size_bytes = :sizeBytes, " +
            "modified_at = :modifiedAt, file_title = :title, file_sort_name = :sortName, file_regions = :regions, " +
            "disc_group = :discGroup, disc_count = :discCount, system_id = :systemId, " +
            "is_primary_copy = :isPrimaryCopy WHERE id = :id",
    )
    protected abstract suspend fun updateScanFields(
        id: Long,
        documentId: String,
        parentDocumentId: String,
        relativePath: String,
        fileName: String,
        sizeBytes: Long,
        modifiedAt: Long,
        title: String,
        sortName: String,
        regions: String,
        discGroup: String?,
        discCount: Int,
        systemId: String,
        isPrimaryCopy: Boolean,
    )

    private fun GameEntity.scanFields() = GameScanFields(
        documentId, parentDocumentId, relativePath, fileName, sizeBytes, modifiedAt, fileTitle, fileSortName,
        fileRegions, discGroup, discCount, systemId, isPrimaryCopy,
    )

    private companion object {
        // SQLite on Android 6 allows 999 bound parameters per statement.
        const val MAX_IDS_PER_STATEMENT = 500

        // NameFixKind.NOT_A_GAME as Room stores the enum (its name); the fold tests fail if it drifts.
        const val NOT_A_GAME = "NOT_A_GAME"

        /** A folder the person picked: every `rom_folder` row but the reserved ones of pinned shortcuts and installed apps. */
        const val PICKED = "tree_uri != '" + PinnedShortcutDao.FOLDER_TREE_URI + "' AND tree_uri != '" + AppGameDao.FOLDER_TREE_URI + "'"
    }
}
