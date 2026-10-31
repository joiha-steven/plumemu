package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * One row that may be a copy of the game another row stands for (the game menu's Other versions):
 * what library's `copiesOf` needs to tell copies apart and label them, and nothing the screen does
 * not need. Titles and file names are for the screen only.
 */
data class GameCopyRow(
    val id: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_sort_name") val fileSortName: String,
    /** The shown region codes (`USA,EUR`); a name pack may know them better than the file name. */
    val regions: String,
    @ColumnInfo(name = "file_regions") val fileRegions: String,
    /** identify's `Method` name; `ARCADE_NAME` rows are grouped by the arcade pack, not by file name. */
    @ColumnInfo(name = "id_method") val idMethod: String?,
    @ColumnInfo(name = "is_not_game") val isNotGame: Boolean,
    /** Path below the picked folder; its directory's name tells copies in sibling game folders apart. */
    @ColumnInfo(name = "relative_path") val relativePath: String,
    /** Discs the row stands for: a disc tag in the name of a one-disc row tells discs apart, not copies. */
    @ColumnInfo(name = "disc_count") val discCount: Int,
)

/** A stored choice of a copy, as the launch compares them. */
data class CopyChoice(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "chosen_at") val chosenAt: Long,
)

/**
 * The copies of a row's game and the person's choice among them (schema 7, `copy_choice`). Which
 * rows are copies of one game is library's rule (`copiesOf`: file names for consoles, the arcade
 * pack for arcade sets); this DAO only reads the candidates of one place (folder, directory,
 * system) and stores the choice. Nothing here changes which copy the lists show.
 */
@Dao
abstract class CopyChoiceDao {
    @Query("SELECT " + COPY_COLUMNS + " FROM game WHERE id = :gameId")
    abstract suspend fun copyRow(gameId: Long): GameCopyRow?

    /**
     * The rows of [gameId]'s folder, directory and system with its file sort name that are games
     * and not named by the arcade pack, [gameId] included: the console copy rule's candidates
     * (the variant key is compared by the caller, it is not stored).
     */
    @Query(
        "SELECT " + COPY_COLUMNS_OF_GAME + " FROM game JOIN game AS own ON own.id = :gameId " +
            "AND own.folder_id = game.folder_id AND own.parent_document_id = game.parent_document_id " +
            "AND own.system_id = game.system_id AND own.file_sort_name = game.file_sort_name " +
            "WHERE game.is_not_game = 0 AND (game.id_method IS NULL OR game.id_method != '" + IdentifyDao.ARCADE_NAME + "') " +
            "ORDER BY game.file_name, game.id",
    )
    abstract suspend fun fileNameCopies(gameId: Long): List<GameCopyRow>

    /**
     * The rows of [gameId]'s folder, directory and system named by the arcade pack, by id and file
     * name only (an arcade folder may hold 10,000 sets): the caller keeps the sets of the game.
     */
    @Query(
        "SELECT game.id, game.file_name FROM game JOIN game AS own ON own.id = :gameId " +
            "AND own.folder_id = game.folder_id AND own.parent_document_id = game.parent_document_id " +
            "AND own.system_id = game.system_id " +
            "WHERE game.is_not_game = 0 AND game.id_method = '" + IdentifyDao.ARCADE_NAME + "'",
    )
    abstract suspend fun arcadeSetsBeside(gameId: Long): List<PlayedSibling>

    /** [ids] as [copyRow] reads them, by file name. */
    suspend fun copyRows(ids: List<Long>): List<GameCopyRow> =
        ids.chunked(MAX_IDS).flatMap { copyRowsChunk(it) }.sortedWith(compareBy({ it.fileName }, { it.id }))

    @Query("SELECT " + COPY_COLUMNS + " FROM game WHERE id IN (:ids)")
    protected abstract suspend fun copyRowsChunk(ids: List<Long>): List<GameCopyRow>

    /**
     * The number of rows in [gameId]'s directory, [gameId] included: a directory with one row is
     * one game's folder, whose copies may be in its sibling folders ([gameFolderCopies]).
     */
    @Query(
        "SELECT COUNT(*) FROM game JOIN game AS own ON own.id = :gameId " +
            "AND own.folder_id = game.folder_id AND own.parent_document_id = game.parent_document_id",
    )
    abstract suspend fun rowsBeside(gameId: Long): Int

    /**
     * The rows of [gameId]'s folder and system with its file sort name that are alone in a
     * directory directly below the same parent (the path starts with [parentPrefix], `PS2/`, or `""`
     * for the picked folder, and has [slashes] slashes), are games and are not named by the arcade
     * pack, [gameId] included: the candidates of the rule for copies kept one game per folder
     * (library `GameFolderCopies`; the variant key is compared by the caller).
     */
    @Query(
        "SELECT " + COPY_COLUMNS_OF_GAME + " FROM game JOIN game AS own ON own.id = :gameId " +
            "AND own.folder_id = game.folder_id AND own.system_id = game.system_id " +
            "AND own.file_sort_name = game.file_sort_name " +
            "WHERE substr(game.relative_path, 1, length(:parentPrefix)) = :parentPrefix " +
            "AND length(game.relative_path) - length(replace(game.relative_path, '/', '')) = :slashes " +
            "AND game.is_not_game = 0 AND (game.id_method IS NULL OR game.id_method != '" + IdentifyDao.ARCADE_NAME + "') " +
            "AND (SELECT COUNT(*) FROM game AS beside WHERE beside.folder_id = game.folder_id " +
            "AND beside.parent_document_id = game.parent_document_id) = 1 " +
            "ORDER BY game.file_name, game.id",
    )
    abstract suspend fun gameFolderCopies(gameId: Long, parentPrefix: String, slashes: Int): List<GameCopyRow>

    /**
     * Whether any copy of [gameId]'s folder, directory and system is chosen: one indexed read, so
     * a launch looks the game's copies up (which may open the arcade pack) only when one could be.
     */
    @Query(
        "SELECT EXISTS (SELECT 1 FROM copy_choice JOIN game ON game.id = copy_choice.game_id " +
            "JOIN game AS own ON own.id = :gameId AND own.folder_id = game.folder_id " +
            "AND own.parent_document_id = game.parent_document_id AND own.system_id = game.system_id)",
    )
    abstract suspend fun hasChoiceBeside(gameId: Long): Boolean

    /**
     * [hasChoiceBeside], or a chosen row of [gameId]'s folder and system with its file sort name in
     * any directory (copies in sibling game folders, [gameFolderCopies]): what a launch asks first.
     * One read; a choice it finds that is no copy only costs the exact lookup.
     */
    @Query(
        "SELECT EXISTS (SELECT 1 FROM copy_choice JOIN game ON game.id = copy_choice.game_id " +
            "JOIN game AS own ON own.id = :gameId AND own.folder_id = game.folder_id " +
            "AND own.system_id = game.system_id " +
            "AND (own.parent_document_id = game.parent_document_id OR own.file_sort_name = game.file_sort_name))",
    )
    abstract suspend fun hasChoiceNear(gameId: Long): Boolean

    /** The stored choices among [ids], latest first. */
    suspend fun choicesAmong(ids: List<Long>): List<CopyChoice> =
        ids.chunked(MAX_IDS).flatMap { choicesChunk(it) }.sortedWith(compareByDescending<CopyChoice> { it.chosenAt }.thenByDescending { it.gameId })

    @Query("SELECT game_id, chosen_at FROM copy_choice WHERE game_id IN (:ids)")
    protected abstract suspend fun choicesChunk(ids: List<Long>): List<CopyChoice>

    /**
     * The person chose [chosenId] among [copies] (every copy of the game, the row launched from
     * included): the other copies' choices go, and [chosenId] is stored at [at], unless it is
     * [shownId], the row's own file, which is the default and needs no row. One transaction, so a
     * launch never reads two choices of one press.
     */
    @Transaction
    open suspend fun choose(shownId: Long, chosenId: Long, copies: List<Long>, at: Long) {
        copies.chunked(MAX_IDS).forEach { deleteChunk(it) }
        if (chosenId != shownId) insert(CopyChoiceEntity(chosenId, at))
    }

    @Query("DELETE FROM copy_choice WHERE game_id IN (:ids)")
    protected abstract suspend fun deleteChunk(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(choice: CopyChoiceEntity)

    private companion object {
        // SQLite on Android 6 allows 999 bound parameters: a longer list is split.
        const val MAX_IDS = 500

        const val COPY_COLUMNS = "id, system_id, file_name, file_sort_name, regions, file_regions, id_method, is_not_game, relative_path, disc_count"

        const val COPY_COLUMNS_OF_GAME =
            "game.id, game.system_id, game.file_name, game.file_sort_name, game.regions, game.file_regions, game.id_method, game.is_not_game, game.relative_path, game.disc_count"
    }
}
