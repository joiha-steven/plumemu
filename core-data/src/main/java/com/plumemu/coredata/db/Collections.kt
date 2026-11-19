package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A collection the person made (schema 20): a name and its place in the system column. Not tied to
 * any game row or folder, so Reset library, a rescan and a removed folder keep it. Written only
 * through [CollectionDao], which keeps [position] 0..n-1 and names unique (case-insensitively).
 */
@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Trimmed, 1 to [CollectionDao.MAX_NAME_LENGTH] characters. */
    val name: String,
    /** Order in the column, 0 first. */
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * One game in one collection (schema 20). [gameId] is the row the membership shows now; it is a
 * cache that can be lost, while [contentKey] and [pathKey] are what the membership is:
 *
 * - `game_id` is `ON DELETE SET NULL`, never CASCADE: Reset library deletes every `game` row
 *   ([ScanDao.forgetLibrary]), a removed file or SD card deletes its rows, an uninstalled app game
 *   goes, and the membership must outlive all of them. A row with no game is "unbound": never listed,
 *   never counted, never deleted by a scan.
 * - [contentKey] is the game's first fingerprint fix key ([CollectionKeys.contentKey]: `crc:`,
 *   `chd:`, `arcade:`, never `code:`, which revisions and hacks share, nor the `game:<id>` row key,
 *   which dies with the row). It finds the same file after a move to another folder or a rename.
 *   Null while the game has none (a file not read yet); kept when a later read gives none.
 * - [pathKey] is the picked folder's tree URI and the file's path below it ([CollectionKeys.pathKey]).
 *   `rom_folder` rows survive Reset library, but the tree URI also survives removing and picking the
 *   same folder again, which a folder id does not. Pinned shortcuts and app games have no path (their
 *   `relative_path` is empty); their reserved folder's URI and `document_id` (`shortcut:<user>/<package>/<id>`,
 *   `app:<package>`) name them across Reset library and a reinstall.
 *
 * [ScanDao.finishScan] (and [AppGameDao.sync], [PinnedShortcutDao.add] for their reserved folders)
 * binds unbound rows again, content key first, then path key, and refreshes the keys of bound rows
 * ([CollectionDao.bindFolder]). Row moves and folds move memberships to the kept row before the
 * delete (`ScanDao.moveMemberships`).
 *
 * A game is in a collection at most once: unique (`collection_id`, `game_id`), which SQLite allows
 * several NULLs in, so unbound rows never collide; every bind checks the index first, so a rebind
 * that would duplicate a membership leaves that row unbound instead of failing the scan.
 */
@Entity(
    tableName = "collection_game",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["collection_id", "game_id"], unique = true), Index(value = ["game_id"])],
)
data class CollectionGameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "game_id") val gameId: Long?,
    @ColumnInfo(name = "content_key") val contentKey: String?,
    @ColumnInfo(name = "path_key") val pathKey: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** The durable keys of a membership, computed from the game row ([CollectionGameEntity]). */
object CollectionKeys {
    /** The first fingerprint fix key of `game.fix_keys` ([FixKeys.fixTargets]); null when it has none. */
    fun contentKey(fixKeys: String): String? = FixKeys.fixTargets(FixKeys.split(fixKeys)).firstOrNull()

    /** Every fingerprint fix key of `game.fix_keys`: an unbound row's content key may be any of them now. */
    fun contentKeys(fixKeys: String): List<String> = FixKeys.fixTargets(FixKeys.split(fixKeys))

    /**
     * The folder's tree URI and the file's path below it (`relative_path` holds the file name too);
     * for the reserved folders of shortcuts and app games, which have no path, the row's document id.
     * A tree URI holds no line break (it is percent-encoded), so the first one ends it.
     */
    fun pathKey(treeUri: String, relativePath: String, documentId: String): String {
        val reserved = treeUri == PinnedShortcutDao.FOLDER_TREE_URI || treeUri == AppGameDao.FOLDER_TREE_URI
        return treeUri + "\n" + if (reserved) documentId else relativePath
    }
}

/** A collection in the system column: [gameCount] counts the games its list shows. */
data class CollectionRow(
    val id: Long,
    val name: String,
    val position: Int,
    @ColumnInfo(name = "game_count") val gameCount: Int,
)

/** What [CollectionDao] reads of a game to fill or match a membership's keys. */
data class CollectionGameKeys(
    val id: Long,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
) {
    val contentKey: String? get() = CollectionKeys.contentKey(fixKeys)
    val pathKey: String get() = CollectionKeys.pathKey(treeUri, relativePath, documentId)
}

/** A bound membership with its game's current keys, for the refresh in [CollectionDao.bindFolder]. */
data class BoundMembership(
    val id: Long,
    @ColumnInfo(name = "content_key") val contentKey: String?,
    @ColumnInfo(name = "path_key") val pathKey: String,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
)

/** An unbound membership: what [CollectionDao.bindFolder] matches against a folder's games. */
data class UnboundMembership(
    val id: Long,
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "content_key") val contentKey: String?,
    @ColumnInfo(name = "path_key") val pathKey: String,
)
