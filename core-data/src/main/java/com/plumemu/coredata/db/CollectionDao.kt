package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.plumemu.coredata.settings.SortGamesSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The person's collections ([CollectionEntity]) and their games ([CollectionGameEntity]). Every
 * write is one transaction; Room runs write transactions one at a time, so two creates at once
 * still get different names and positions. A collection's list leaves out what Favorites leaves
 * out (hidden games, "Not a game") and every unbound membership.
 */
@Dao
abstract class CollectionDao {
    /**
     * Makes a collection at the end of the column and returns its id. [name] is trimmed and must
     * hold 1 to [MAX_NAME_LENGTH] characters; a name already used (ignoring case) gets " 2", " 3"…
     * (cut so the whole fits [MAX_NAME_LENGTH]).
     *
     * @throws IllegalArgumentException for a blank or too long name.
     */
    @Transaction
    open suspend fun create(name: String, now: Long = System.currentTimeMillis()): Long {
        val wanted = CollectionNames.checked(name)
        val unique = CollectionNames.unique(wanted, names(excludingId = 0))
        return insertCollection(CollectionEntity(name = unique, position = count(), createdAt = now))
    }

    /**
     * [create] and [add] in one transaction (the game menu's New collection…): a game that is gone
     * makes no collection. Returns the new id, or null when the game is gone.
     *
     * @throws IllegalArgumentException for a blank or too long name.
     */
    @Transaction
    open suspend fun createWith(name: String, gameId: Long, now: Long = System.currentTimeMillis()): Long? {
        CollectionNames.checked(name)
        if (gameKeys(gameId) == null) return null
        val id = create(name, now)
        return if (add(id, gameId, now)) id else null
    }

    /**
     * Renames [collectionId] by the rules of [create] (its own name does not count as used).
     * Returns the stored name, or null when the collection is gone.
     */
    @Transaction
    open suspend fun rename(collectionId: Long, name: String): String? {
        val wanted = CollectionNames.checked(name)
        if (!exists(collectionId)) return null
        val unique = CollectionNames.unique(wanted, names(excludingId = collectionId))
        setName(collectionId, unique)
        return unique
    }

    /** Deletes [collectionId] with its memberships (CASCADE); the others close the gap. Games stay. */
    @Transaction
    open suspend fun delete(collectionId: Long) {
        deleteCollection(collectionId)
        writePositions(orderedIds())
    }

    /** Moves [collectionId] to [toPosition] (clamped to the column); positions stay 0..n-1. */
    @Transaction
    open suspend fun move(collectionId: Long, toPosition: Int) {
        val ids = orderedIds().toMutableList()
        if (!ids.remove(collectionId)) return
        ids.add(toPosition.coerceIn(0, ids.size), collectionId)
        writePositions(ids)
    }

    /**
     * Puts the collections in the order of [orderedIds]; ids that are not collections are skipped,
     * collections missing from it follow in their current order. Positions stay 0..n-1.
     */
    @Transaction
    open suspend fun reorder(orderedIds: List<Long>) {
        val current = orderedIds()
        val known = current.toSet()
        val first = orderedIds.distinct().filter { it in known }
        writePositions(first + current.filter { it !in first.toSet() })
    }

    /**
     * Puts [gameId] in [collectionId], its keys read from the game row. An unbound membership of
     * the collection with the game's path key, or its content key while no game sits at that row's
     * path (another copy's membership is not taken), is bound again instead of a second row.
     * Returns whether the game is in the collection now (false when the game or collection is gone).
     */
    @Transaction
    open suspend fun add(collectionId: Long, gameId: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!exists(collectionId)) return false
        val game = gameKeys(gameId) ?: return false
        if (holds(collectionId, gameId)) return true
        val contentKey = game.contentKey
        val pathKey = game.pathKey
        val rows = unboundIn(collectionId)
        val unbound = rows.firstOrNull { it.pathKey == pathKey }
            ?: rows.firstOrNull { contentKey != null && it.contentKey == contentKey && !hasGameAt(it.pathKey) }
        if (unbound != null) {
            rebind(unbound.id, gameId, contentKey ?: unbound.contentKey, pathKey, now)
        } else {
            insertMembership(CollectionGameEntity(collectionId = collectionId, gameId = gameId, contentKey = contentKey, pathKey = pathKey, addedAt = now))
        }
        return true
    }

    /** Takes [gameId] out of [collectionId]. */
    @Query("DELETE FROM collection_game WHERE collection_id = :collectionId AND game_id = :gameId")
    abstract suspend fun remove(collectionId: Long, gameId: Long)

    /** The collections in column order, each with the number of games its list shows. */
    fun observeCollections(): Flow<List<CollectionRow>> = collections().distinctUntilChanged()

    /** The ids of the collections holding [gameId] (the game menu's ticks). */
    fun observeCollectionsOf(gameId: Long): Flow<List<Long>> = collectionsOf(gameId).distinctUntilChanged()

    /**
     * The games of [collectionId] in [sort] order, built exactly as [GameDao.observeFavorites]
     * (same row, same filters: hidden and "Not a game" left out, every copy the person put in kept,
     * same orders). Unbound memberships are never listed.
     */
    fun observeCollectionGames(collectionId: Long, sort: SortGamesSetting = SortGamesSetting.Name): Flow<List<GameListRow>> =
        when (sort) {
            SortGamesSetting.Name -> games(collectionId)
            SortGamesSetting.LastPlayed -> gamesByLastPlayed(collectionId)
            SortGamesSetting.MostPlayed -> gamesByMostPlayed(collectionId)
            SortGamesSetting.RecentlyAdded -> gamesByRecentlyAdded(collectionId)
            SortGamesSetting.Year -> gamesByYear(collectionId)
            SortGamesSetting.Rating -> gamesByRating(collectionId)
        }.distinctUntilChanged()

    /**
     * After a scan (or an app sync, a pin) of [folderId], inside its transaction, unbound
     * memberships are bound to the folder's games in two passes, then the keys of every membership
     * bound to the folder's games are refreshed (a `crc:` key read since, a moved file's new path;
     * a content key is never cleared by a game that has none yet).
     *
     * 1. Path: the game at the row's own path key, unless it is another file now (it has
     *    fingerprint keys and none is the row's content key). Two copies of one file, in one
     *    collection or in two, each come back to their own file this way.
     * 2. Content, for the rows pass 1 left: a game of this folder with the row's content key that
     *    the collection does not hold yet (primary copy first, then the lowest id). A row whose path
     *    is in another picked folder is taken only once that folder has finished a scan and has no
     *    game at that path: after Reset library a duplicate in a folder scanned first must not take
     *    a membership its own folder will find by path; a file moved from one folder to another is
     *    found once the old folder's scan saw it gone.
     *
     * A bind that would put a game twice in one collection is skipped and the row stays unbound.
     * Reads the folder's games only while some membership is unbound.
     */
    @Transaction
    open suspend fun bindFolder(folderId: Long) {
        if (!hasMemberships()) return
        val unbound = unbound()
        if (unbound.isNotEmpty()) {
            val games = folderGames(folderId).sortedWith(compareByDescending<CollectionGameKeys> { it.isPrimaryCopy }.thenBy { it.id })
            val byPath = HashMap<String, CollectionGameKeys>()
            val byContent = HashMap<String, MutableList<Long>>()
            for (game in games) {
                byPath.getOrPut(game.pathKey) { game }
                for (key in CollectionKeys.contentKeys(game.fixKeys)) byContent.getOrPut(key) { ArrayList() }.add(game.id)
            }
            val left = ArrayList<UnboundMembership>()
            for (row in unbound) {
                val atPath = byPath[row.pathKey]
                val keys = atPath?.let { CollectionKeys.contentKeys(it.fixKeys) }.orEmpty()
                val sameFile = atPath != null && (row.contentKey == null || keys.isEmpty() || row.contentKey in keys)
                // Its own file already in the collection through another row: no other copy stands in for it.
                if (sameFile) bindIfFree(row.id, row.collectionId, atPath!!.id) else left += row
            }
            val treeUri = folderTreeUri(folderId)
            for (row in left) {
                val candidates = row.contentKey?.let(byContent::get) ?: continue
                if (treeUri == null || !mayTakeByContent(row.pathKey, treeUri)) continue
                for (gameId in candidates) if (bindIfFree(row.id, row.collectionId, gameId) == 1) break
            }
        }
        for (row in boundInFolder(folderId)) {
            val contentKey = CollectionKeys.contentKey(row.fixKeys) ?: row.contentKey
            val pathKey = CollectionKeys.pathKey(row.treeUri, row.relativePath, row.documentId)
            if (contentKey != row.contentKey || pathKey != row.pathKey) setKeys(row.id, contentKey, pathKey)
        }
    }

    /** Pass 2's rule for a row whose path is outside the folder [treeUri] being bound ([bindFolder]). */
    private suspend fun mayTakeByContent(pathKey: String, treeUri: String): Boolean {
        val rowTree = pathKey.substringBefore('\n')
        if (rowTree == treeUri) return true
        val folder = folderFinished(rowTree) ?: return true
        if (folder.lastScanFinishedAt == null) return false
        return !hasGameAt(pathKey)
    }

    /** Whether a game now sits at [pathKey] (any folder). */
    private suspend fun hasGameAt(pathKey: String): Boolean {
        val rest = pathKey.substringAfter('\n')
        return gamesNamed(pathKey.substringBefore('\n'), rest).any { it.pathKey == pathKey }
    }

    private suspend fun writePositions(ids: List<Long>) = ids.forEachIndexed { index, id -> setPosition(id, index) }

    @Insert
    protected abstract suspend fun insertCollection(collection: CollectionEntity): Long

    // IGNORE: the unique (collection_id, game_id) index already holds it.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMembership(row: CollectionGameEntity): Long

    @Query("SELECT name FROM collection WHERE id != :excludingId")
    protected abstract suspend fun names(excludingId: Long): List<String>

    @Query("SELECT COUNT(*) FROM collection")
    protected abstract suspend fun count(): Int

    @Query("SELECT EXISTS (SELECT 1 FROM collection WHERE id = :collectionId)")
    protected abstract suspend fun exists(collectionId: Long): Boolean

    @Query("SELECT id FROM collection ORDER BY position, id")
    protected abstract suspend fun orderedIds(): List<Long>

    @Query("UPDATE collection SET name = :name WHERE id = :collectionId")
    protected abstract suspend fun setName(collectionId: Long, name: String)

    @Query("UPDATE collection SET position = :position WHERE id = :collectionId AND position != :position")
    protected abstract suspend fun setPosition(collectionId: Long, position: Int)

    @Query("DELETE FROM collection WHERE id = :collectionId")
    protected abstract suspend fun deleteCollection(collectionId: Long)

    @Query("SELECT EXISTS (SELECT 1 FROM collection_game WHERE collection_id = :collectionId AND game_id = :gameId)")
    protected abstract suspend fun holds(collectionId: Long, gameId: Long): Boolean

    @Query(
        "SELECT game.id, game.fix_keys, game.relative_path, game.document_id, rom_folder.tree_uri, game.is_primary_copy " +
            "FROM game JOIN rom_folder ON rom_folder.id = game.folder_id WHERE game.id = :gameId",
    )
    protected abstract suspend fun gameKeys(gameId: Long): CollectionGameKeys?

    @Query(
        "SELECT game.id, game.fix_keys, game.relative_path, game.document_id, rom_folder.tree_uri, game.is_primary_copy " +
            "FROM game JOIN rom_folder ON rom_folder.id = game.folder_id WHERE game.folder_id = :folderId",
    )
    protected abstract suspend fun folderGames(folderId: Long): List<CollectionGameKeys>

    @Query("SELECT tree_uri FROM rom_folder WHERE id = :folderId")
    protected abstract suspend fun folderTreeUri(folderId: Long): String?

    @Query("SELECT * FROM rom_folder WHERE tree_uri = :treeUri")
    protected abstract suspend fun folderFinished(treeUri: String): RomFolderEntity?

    @Query(
        "SELECT game.id, game.fix_keys, game.relative_path, game.document_id, rom_folder.tree_uri, game.is_primary_copy " +
            "FROM game JOIN rom_folder ON rom_folder.id = game.folder_id " +
            "WHERE rom_folder.tree_uri = :treeUri AND (game.relative_path = :rest OR game.document_id = :rest)",
    )
    protected abstract suspend fun gamesNamed(treeUri: String, rest: String): List<CollectionGameKeys>

    @Query("SELECT id, collection_id, content_key, path_key FROM collection_game WHERE game_id IS NULL AND collection_id = :collectionId")
    protected abstract suspend fun unboundIn(collectionId: Long): List<UnboundMembership>

    @Query("SELECT id, collection_id, content_key, path_key FROM collection_game WHERE game_id IS NULL ORDER BY id")
    protected abstract suspend fun unbound(): List<UnboundMembership>

    @Query("SELECT EXISTS (SELECT 1 FROM collection_game)")
    protected abstract suspend fun hasMemberships(): Boolean

    @Query(
        "SELECT collection_game.id, collection_game.content_key, collection_game.path_key, game.fix_keys, game.relative_path, " +
            "game.document_id, rom_folder.tree_uri FROM collection_game JOIN game ON game.id = collection_game.game_id " +
            "JOIN rom_folder ON rom_folder.id = game.folder_id WHERE game.folder_id = :folderId",
    )
    protected abstract suspend fun boundInFolder(folderId: Long): List<BoundMembership>

    @Query(
        "UPDATE collection_game SET game_id = :gameId WHERE id = :rowId AND game_id IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM collection_game WHERE collection_id = :collectionId AND game_id = :gameId)",
    )
    protected abstract suspend fun bindIfFree(rowId: Long, collectionId: Long, gameId: Long): Int

    @Query("UPDATE collection_game SET game_id = :gameId, content_key = :contentKey, path_key = :pathKey, added_at = :addedAt WHERE id = :rowId")
    protected abstract suspend fun rebind(rowId: Long, gameId: Long, contentKey: String?, pathKey: String, addedAt: Long)

    @Query("UPDATE collection_game SET content_key = :contentKey, path_key = :pathKey WHERE id = :rowId")
    protected abstract suspend fun setKeys(rowId: Long, contentKey: String?, pathKey: String)

    @Query(
        "SELECT collection.id, collection.name, collection.position, " +
            "(SELECT COUNT(*) FROM collection_game JOIN game ON game.id = collection_game.game_id " +
            "WHERE collection_game.collection_id = collection.id AND game.is_hidden = 0 AND game.is_not_game = 0) AS game_count " +
            "FROM collection ORDER BY collection.position, collection.id",
    )
    protected abstract fun collections(): Flow<List<CollectionRow>>

    @Query("SELECT collection_id FROM collection_game WHERE game_id = :gameId ORDER BY collection_id")
    protected abstract fun collectionsOf(gameId: Long): Flow<List<Long>>

    @Query("SELECT " + ROW + MEMBERS + "WHERE " + LISTED + "ORDER BY game.sort_name, game.id")
    protected abstract fun games(collectionId: Long): Flow<List<GameListRow>>

    @Query(
        "SELECT " + ROW + MEMBERS + "LEFT JOIN play ON play.game_id = game.id WHERE " + LISTED +
            "ORDER BY play.last_played_at IS NULL, play.last_played_at DESC, game.sort_name, game.id",
    )
    protected abstract fun gamesByLastPlayed(collectionId: Long): Flow<List<GameListRow>>

    @Query(
        "SELECT " + ROW + MEMBERS + "LEFT JOIN play ON play.game_id = game.id WHERE " + LISTED +
            "ORDER BY " + GameDao.PLAYED_ORDER + ", game.sort_name, game.id",
    )
    protected abstract fun gamesByMostPlayed(collectionId: Long): Flow<List<GameListRow>>

    @Query("SELECT " + ROW + MEMBERS + "WHERE " + LISTED + "ORDER BY " + GameDao.ADDED_ORDER + ", game.sort_name, game.id")
    protected abstract fun gamesByRecentlyAdded(collectionId: Long): Flow<List<GameListRow>>

    @Query("SELECT " + ROW + MEMBERS + GameDao.YEAR_JOIN + "WHERE " + LISTED + "ORDER BY " + GameDao.YEAR_ORDER + ", game.sort_name, game.id")
    protected abstract fun gamesByYear(collectionId: Long): Flow<List<GameListRow>>

    @Query("SELECT " + ROW + MEMBERS + GameDao.YEAR_JOIN + "WHERE " + LISTED + "ORDER BY " + GameDao.RATING_ORDER + ", game.sort_name, game.id")
    protected abstract fun gamesByRating(collectionId: Long): Flow<List<GameListRow>>

    companion object {
        /** The longest name, in characters (code points). */
        const val MAX_NAME_LENGTH = CollectionNames.MAX_LENGTH

        private const val ROW =
            "game.id, game.title, game.regions, game.is_favorite, game.system_id, game.sort_name, game.file_name, game.art_aspect, " +
                GameDao.LIST_SCRAPE_KEY + " "

        private const val MEMBERS = "FROM collection_game JOIN game ON game.id = collection_game.game_id "

        /** Favorites' filter, on one collection. */
        private const val LISTED = "collection_game.collection_id = :collectionId AND game.is_hidden = 0 AND game.is_not_game = 0 "
    }
}
