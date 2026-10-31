package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** A shortcut to add as a game: what the app's accept screen read from Android's pin request. */
data class NewPinnedShortcut(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    /** The shortcut's short label: the game's title until the person names it otherwise. */
    val label: String,
    /** identify's `TitleCleaner.sortName(label)`, as a scan sorts a title. */
    val sortName: String,
    /** Where the game is listed: `steam`, `windows` or the shortcuts list (the app files it). */
    val systemId: String,
    /** The `system` row's stored name, used only when the platform data does not know [systemId]. */
    val systemName: String,
    val addedAt: Long,
)

/** What [PinnedShortcutDao.add] did. */
data class PinnedShortcutAdded(
    val gameId: Long,
    /** False when the shortcut was already a game (pinned again): nothing new was listed. */
    val isNew: Boolean,
)

/** One stored record as the availability check needs it. */
data class PinnedShortcutKey(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "is_available") val isAvailable: Boolean,
)

/**
 * Pinned shortcuts as games. Each is a `game`
 * row in one reserved `rom_folder` ([FOLDER_TREE_URI]) plus its [PinnedShortcutEntity]; the lists,
 * Favorites, Recent, Search, hiding and Statistics treat it as any game. The reserved folder is
 * left out of [ScanDao.folders] and [ScanDao.observeFolders], so no scan, identification pass or
 * Settings folder list sees it, and box-art runs skip these games (`ScrapedGameDao`).
 */
@Dao
abstract class PinnedShortcutDao {
    /**
     * Lists the shortcut as a game, once: a shortcut already stored (same app, id and user) keeps
     * its game, favourite and play time, is shown again if the person had hidden it, is marked
     * available and is filed again under [NewPinnedShortcut.systemId] (the platform data may have
     * learnt its app since). One transaction, so two accepts of the same pin at once make one game.
     */
    @Transaction
    open suspend fun add(shortcut: NewPinnedShortcut): PinnedShortcutAdded {
        val existing = record(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
        if (existing != null) {
            insertSystemIfMissing(SystemEntity(shortcut.systemId, shortcut.systemName))
            pinnedAgain(existing.gameId, shortcut.label, shortcut.systemId)
            return PinnedShortcutAdded(existing.gameId, isNew = false)
        }
        insertSystemIfMissing(SystemEntity(shortcut.systemId, shortcut.systemName))
        insertFolderIfMissing(RomFolderEntity(treeUri = FOLDER_TREE_URI, displayName = "", addedAt = shortcut.addedAt))
        val folderId = checkNotNull(reservedFolderId()) { "reserved shortcuts folder missing after insert" }
        val documentId = documentIdOf(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
        insertGameIfMissing(
            GameEntity(
                folderId = folderId,
                documentId = documentId,
                parentDocumentId = "",
                relativePath = "",
                fileName = shortcut.label,
                sizeBytes = 0,
                modifiedAt = shortcut.addedAt,
                title = shortcut.label,
                sortName = shortcut.sortName,
                regions = "",
                discGroup = null,
                discCount = 1,
                systemId = shortcut.systemId,
                isPrimaryCopy = true,
                // Named by its app on purpose: never offered to the identification pass.
                needsIdentify = false,
            ),
        )
        val gameId = checkNotNull(gameIdOf(folderId, documentId)) { "shortcut game missing after insert" }
        insertRecord(
            PinnedShortcutEntity(
                gameId = gameId,
                packageName = shortcut.packageName,
                shortcutId = shortcut.shortcutId,
                userSerial = shortcut.userSerial,
                label = shortcut.label,
                addedAt = shortcut.addedAt,
            ),
        )
        return PinnedShortcutAdded(gameId, isNew = true)
    }

    /** The record of [gameId]; null for a game from a ROM folder. Play on a shortcut game starts it from this record. */
    @Query("SELECT * FROM pinned_shortcut WHERE game_id = :gameId")
    abstract suspend fun byGame(gameId: Long): PinnedShortcutEntity?

    /**
     * Whether [gameId] sits in the reserved folder, record or not: Play stops a shortcut row whose
     * record is missing instead of handing it to the emulator engine (no file, no system's app to ask).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM game WHERE id = :gameId AND " + IS_SHORTCUT_ROW + ")")
    abstract suspend fun isShortcutRow(gameId: Long): Boolean

    /** Every record, for the availability check after a start or a package change. */
    @Query("SELECT game_id, package_name, shortcut_id, user_serial, is_available FROM pinned_shortcut ORDER BY game_id")
    abstract suspend fun keys(): List<PinnedShortcutKey>

    /** The records of one app for one user, for `LauncherApps.Callback.onShortcutsChanged`. */
    @Query(
        "SELECT game_id, package_name, shortcut_id, user_serial, is_available FROM pinned_shortcut " +
            "WHERE package_name = :packageName AND user_serial = :userSerial ORDER BY game_id",
    )
    abstract suspend fun keysOf(packageName: String, userSerial: Long): List<PinnedShortcutKey>

    /** Marks a shortcut its app disabled or removed (false) or offers again (true); the game stays listed. */
    @Query("UPDATE pinned_shortcut SET is_available = :isAvailable WHERE game_id = :gameId")
    abstract suspend fun setAvailable(gameId: Long, isAvailable: Boolean)

    @Query(
        "SELECT * FROM pinned_shortcut WHERE package_name = :packageName AND shortcut_id = :shortcutId " +
            "AND user_serial = :userSerial",
    )
    protected abstract suspend fun record(packageName: String, shortcutId: String, userSerial: Long): PinnedShortcutEntity?

    @Transaction
    protected open suspend fun pinnedAgain(gameId: Long, label: String, systemId: String) {
        refreshRecord(gameId, label)
        showAgainIn(gameId, systemId)
    }

    @Query("UPDATE pinned_shortcut SET label = :label, is_available = 1 WHERE game_id = :gameId")
    protected abstract suspend fun refreshRecord(gameId: Long, label: String)

    @Query("UPDATE game SET is_hidden = 0, system_id = :systemId WHERE id = :gameId")
    protected abstract suspend fun showAgainIn(gameId: Long, systemId: String)

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
    protected abstract suspend fun insertRecord(record: PinnedShortcutEntity)

    companion object {
        /**
         * The reserved folder's `tree_uri`: not a content URI, so no picked folder can have it.
         * Never change it: stored games are found by it.
         */
        const val FOLDER_TREE_URI = "plumemu:pinned-shortcuts"

        /** SQL: the `game` row being read or written is a pinned shortcut (it sits in the reserved folder). */
        const val IS_SHORTCUT_ROW = "folder_id IN (SELECT id FROM rom_folder WHERE tree_uri = '" + FOLDER_TREE_URI + "')"

        /** SQL, in a query that names `game`: the row is not a pinned shortcut. */
        const val NOT_SHORTCUT_GAME = "NOT EXISTS (SELECT 1 FROM pinned_shortcut WHERE pinned_shortcut.game_id = game.id)"

        /**
         * SQL, in a query that names `game`: the row is not a pinned shortcut the box-art run skips.
         * A shortcut filed under a system ScreenScraper finds by name only (`steam`: DroidDeck's
         * games) is asked like a `.steam` file; shortcuts of other systems are not.
         * The list mirrors `SystemIds.NAME_ONLY` in the scraper module, which core-data cannot see;
         * `ShortcutScrapeTest` in library fails when the two differ.
         */
        const val NOT_SKIPPED_BY_BOX_ART = "(" + NOT_SHORTCUT_GAME + " OR game.system_id IN ('steam'))"

        /** The game row's `document_id`: unique per shortcut and user inside the reserved folder. */
        fun documentIdOf(packageName: String, shortcutId: String, userSerial: Long): String =
            "shortcut:$userSerial/$packageName/$shortcutId"
    }
}
