package com.plumemu.coredata.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An installed app the app layer decided to list as a game. */
data class NewAppGame(
    val packageName: String,
    /** The app's label: the game's title. */
    val label: String,
    /** identify's `TitleCleaner.sortName(label)`, as a scan sorts a title. */
    val sortName: String,
    /** `android` (Android games) or `ports` (a native port app). */
    val systemId: String,
    /** The `system` row's stored name, used only when the row is missing. */
    val systemName: String,
)

/** What [AppGameDao.sync] did: rows listed for the first time, rows whose title or system changed, rows removed. */
data class AppGameSync(val added: Int, val updated: Int, val removed: Int)

/** The person's answer for an installed app ("Show as game", "Show as port", "Not a game"). */
enum class AppKind(
    /** The value stored in `app_kind_choice.kind`. Never change one: stored choices are read by it. */
    val stored: String,
) {
    GAME("game"),
    PORT("port"),
    APP("app"),
    ;

    companion object {
        /** The kind of a stored value; null for a value this build does not know. */
        fun fromStored(value: String): AppKind? = entries.firstOrNull { it.stored == value }
    }
}

/**
 * Installed apps as games: Android games in the system `android`, native port apps in
 * `ports`. Each is a `game` row in one reserved `rom_folder` ([FOLDER_TREE_URI], separate from the
 * pinned shortcuts' one) plus its [AppGameEntity]; the lists, Favorites, Recent, Search, hiding and
 * Statistics treat it as any game. The reserved folder is left out of [ScanDao.folders] and
 * [ScanDao.observeFolders], and identification and box-art runs skip its games, as for
 * [PinnedShortcutDao]. The person's own answer per package ([AppKindChoiceEntity]) is kept here too.
 */
@Dao
abstract class AppGameDao {
    /**
     * Makes the app games exactly [apps], in one transaction: a package not yet listed gets a game;
     * a package already listed keeps its game id, favourite, play time and hidden mark (a hidden app
     * game stays hidden) and takes the new label, sort name and system; a listed package missing
     * from [apps] loses its game (with its play time, as an uninstalled app's). An empty [apps]
     * removes every app game and nothing else. A package given twice is listed once (the last wins).
     * A package in [keep] that is not in [apps] keeps its game untouched: Android still has it, only
     * disabled or on storage that is not mounted, and its play time must not be lost.
     */
    @Transaction
    open suspend fun sync(apps: List<NewAppGame>, now: Long, keep: Set<String> = emptySet()): AppGameSync {
        val wanted = apps.associateBy { it.packageName }
        val stored = records().associateBy { it.packageName }
        var added = 0
        var updated = 0
        if (wanted.isNotEmpty()) {
            insertFolderIfMissing(RomFolderEntity(treeUri = FOLDER_TREE_URI, displayName = "", addedAt = now))
        }
        val folderId = reservedFolderId()
        for (app in wanted.values) {
            insertSystemIfMissing(SystemEntity(app.systemId, app.systemName))
            val record = stored[app.packageName]
            if (record != null) {
                updated += refreshGame(record.gameId, app.label, app.sortName, app.systemId)
                continue
            }
            val reserved = checkNotNull(folderId) { "reserved installed-apps folder missing after insert" }
            val documentId = documentIdOf(app.packageName)
            insertGameIfMissing(
                GameEntity(
                    folderId = reserved,
                    documentId = documentId,
                    parentDocumentId = "",
                    relativePath = "",
                    fileName = app.label,
                    sizeBytes = 0,
                    modifiedAt = now,
                    title = app.label,
                    sortName = app.sortName,
                    regions = "",
                    discGroup = null,
                    discCount = 1,
                    systemId = app.systemId,
                    isPrimaryCopy = true,
                    // Named by its app on purpose: never offered to the identification pass.
                    needsIdentify = false,
                ),
            )
            val gameId = checkNotNull(gameIdOf(reserved, documentId)) { "app game missing after insert" }
            // A game row left without its record (none should be) is taken over rather than duplicated.
            refreshGame(gameId, app.label, app.sortName, app.systemId)
            insertRecord(AppGameEntity(gameId = gameId, packageName = app.packageName, addedAt = now))
            added++
        }
        val gone = stored.values.filter { it.packageName !in wanted && it.packageName !in keep }.map { it.gameId }
        gone.chunked(MAX_IDS_PER_STATEMENT).forEach { deleteGames(it) }
        deleteGamesWithoutRecord()
        return AppGameSync(added = added, updated = updated, removed = gone.size)
    }

    /** The package of [gameId]; null for any game that is not an installed app. */
    @Query("SELECT package_name FROM app_game WHERE game_id = :gameId")
    abstract suspend fun byGame(gameId: Long): String?

    /**
     * Whether [gameId] sits in the installed apps' reserved folder, record or not: Play must start
     * such a row as an app, never hand it to the emulator engine (it has no file).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM game WHERE id = :gameId AND " + IS_APP_GAME_ROW + ")")
    abstract suspend fun isAppGameRow(gameId: Long): Boolean

    /** Every app game record, by game id. */
    @Query("SELECT * FROM app_game ORDER BY game_id")
    abstract suspend fun records(): List<AppGameEntity>

    /** The person's answers by package; a stored kind this build does not know is left out (logged). */
    fun observeChoices(): Flow<Map<String, AppKind>> = observeChoiceRows().map(::toChoices)

    /** As [observeChoices], read once. */
    suspend fun choices(): Map<String, AppKind> = toChoices(choiceRows())

    /** Stores the person's answer for [packageName], replacing an earlier one. */
    suspend fun setChoice(packageName: String, kind: AppKind, now: Long) =
        upsertChoice(AppKindChoiceEntity(packageName = packageName, kind = kind.stored, chosenAt = now))

    /** Forgets the person's answer: Plumemu decides for [packageName] again. */
    @Query("DELETE FROM app_kind_choice WHERE package_name = :packageName")
    abstract suspend fun clearChoice(packageName: String)

    @Query("SELECT * FROM app_kind_choice ORDER BY package_name")
    protected abstract fun observeChoiceRows(): Flow<List<AppKindChoiceEntity>>

    @Query("SELECT * FROM app_kind_choice ORDER BY package_name")
    protected abstract suspend fun choiceRows(): List<AppKindChoiceEntity>

    // REPLACE is safe here: no table refers to app_kind_choice.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertChoice(choice: AppKindChoiceEntity)

    /**
     * Sets the file names (the app's label) and the system; the shown name only while it is still the
     * label's ([NameTrustNames.FILE_NAME]), so a name the person gave survives every sync. Never the
     * person's marks. 1 when something changed.
     */
    @Query(
        "UPDATE game SET " +
            "title = CASE WHEN " + SHOWS_LABEL + " THEN :label ELSE title END, " +
            "sort_name = CASE WHEN " + SHOWS_LABEL + " THEN :sortName ELSE sort_name END, " +
            "file_name = :label, file_title = :label, file_sort_name = :sortName, system_id = :systemId " +
            "WHERE id = :gameId AND (file_name != :label OR file_title != :label OR file_sort_name != :sortName " +
            "OR system_id != :systemId OR (" + SHOWS_LABEL + " AND (title != :label OR sort_name != :sortName)))",
    )
    protected abstract suspend fun refreshGame(gameId: Long, label: String, sortName: String, systemId: String): Int

    @Query("DELETE FROM game WHERE id IN (:gameIds)")
    protected abstract suspend fun deleteGames(gameIds: List<Long>)

    @Query("DELETE FROM game WHERE " + IS_APP_GAME_ROW + " AND id NOT IN (SELECT game_id FROM app_game)")
    protected abstract suspend fun deleteGamesWithoutRecord()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSystemIfMissing(system: SystemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFolderIfMissing(folder: RomFolderEntity)

    @Query("SELECT id FROM rom_folder WHERE tree_uri = '" + FOLDER_TREE_URI + "'")
    protected abstract suspend fun reservedFolderId(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertGameIfMissing(game: GameEntity)

    @Query("SELECT id FROM game WHERE folder_id = :folderId AND document_id = :documentId")
    protected abstract suspend fun gameIdOf(folderId: Long, documentId: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecord(record: AppGameEntity)

    private fun toChoices(rows: List<AppKindChoiceEntity>): Map<String, AppKind> =
        buildMap {
            for (row in rows) {
                val kind = AppKind.fromStored(row.kind)
                if (kind == null) {
                    Log.w(TAG, "Unknown app kind '${row.kind}' stored for ${row.packageName}; read as no choice")
                } else {
                    put(row.packageName, kind)
                }
            }
        }

    companion object {
        /**
         * The reserved folder's `tree_uri`: not a content URI, so no picked folder can have it.
         * Never change it: stored games are found by it.
         */
        const val FOLDER_TREE_URI = "plumemu:installed-apps"

        /** SQL: the `game` row being read or written is an installed app (it sits in the reserved folder). */
        const val IS_APP_GAME_ROW = "folder_id IN (SELECT id FROM rom_folder WHERE tree_uri = '" + FOLDER_TREE_URI + "')"

        /** SQL, in a query that names `game`: the row is not an installed app (by its folder, record or not). */
        const val NOT_APP_GAME = "game.folder_id NOT IN (SELECT id FROM rom_folder WHERE tree_uri = '" + FOLDER_TREE_URI + "')"

        /** The game row's `document_id`: unique per package inside the reserved folder. */
        fun documentIdOf(packageName: String): String = "app:$packageName"

        private const val TAG = "AppGameDao"

        /** SQL: the row's shown name is still its file name (the app's label), not one the person gave. */
        private const val SHOWS_LABEL = "name_trust = '" + NameTrustNames.FILE_NAME + "'"

        // SQLite on Android 6 allows 999 bound parameters per statement.
        private const val MAX_IDS_PER_STATEMENT = 500
    }
}
