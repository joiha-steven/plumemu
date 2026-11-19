package com.plumemu.graph

import com.plumemu.collections.Folder
import com.plumemu.collections.PickerCollection
import com.plumemu.collections.PickerEffect
import com.plumemu.collections.PickerState
import com.plumemu.collections.foldersOf
import com.plumemu.content.LibraryHomeContent
import com.plumemu.coredata.db.CollectionRow
import com.plumemu.coredata.db.PlumDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Collections entry's reads and writes: the folders page, the game menu's picker, adding, removing and creating with
 * a game. The home screen's lists follow by themselves (Room observes the tables). [clock] stamps new memberships.
 */
internal open class GraphCollections(
    private val database: PlumDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Recent, Most played, Recently added, the own collections, then New collection…, with the counts their lists show.
     * The Recently added window is fixed by [now] (epoch milliseconds) when the flow is made, as `observeRecentlyAdded` does:
     * the caller collects `folders(now)` again when the day changes (a day tick), so the count does not go stale past midnight.
     */
    fun folders(now: Long = clock()): Flow<List<Folder>> =
        combine(
            database.gameDao().observeRecent(LibraryHomeContent.RECENT_GAMES).map { it.size },
            database.smartListDao().observeMostPlayed().map { it.size },
            database.smartListDao().observeRecentlyAdded(now).map { it.size },
            database.collectionDao().observeCollections(),
        ) { recent, mostPlayed, recentlyAdded, own -> foldersOf(recent, mostPlayed, recentlyAdded, own) }
            .distinctUntilChanged()

    /** The picker for [gameId]: own collections in position order, ticked where the game is. */
    fun pickerFor(gameId: Long): Flow<PickerState> =
        combine(database.collectionDao().observeCollections(), database.collectionDao().observeCollectionsOf(gameId)) { own, holding ->
            PickerState(own.map { PickerCollection(it.id, it.name) }, holding.toSet())
        }.distinctUntilChanged()

    /**
     * Runs what the picker asked. Returns whether the game is in the collection afterwards for an Add (false: the game or
     * the collection is gone, so the screen can say why), and true for a Remove. [PickerEffect.AskName] and
     * [PickerEffect.None] write nothing (the keyboard comes next) and return false.
     */
    open suspend fun apply(effect: PickerEffect, gameId: Long): Boolean =
        when (effect) {
            is PickerEffect.Add -> writing { database.collectionDao().add(effect.collectionId, gameId, clock()) }
            is PickerEffect.Remove -> writing {
                database.collectionDao().remove(effect.collectionId, gameId)
                true
            }
            PickerEffect.AskName, PickerEffect.None -> false
        }

    /** Makes an empty collection called [name] (trimmed; a name in use gets " 2"); returns its id. A blank or too long name throws. */
    open suspend fun create(name: String): Long = writing { database.collectionDao().create(name, clock()) }

    /**
     * Makes a collection called [name] (trimmed; a name in use gets " 2") holding [gameId], in one transaction.
     * Returns its id, or null when the game is gone. A blank or too long name throws: the keyboard checks it first.
     */
    open suspend fun createWith(name: String, gameId: Long): Long? = writing { database.collectionDao().createWith(name, gameId, clock()) }

    /** The own collections in position order with their game counts (Settings › Library › Collections). */
    fun own(): Flow<List<CollectionRow>> = database.collectionDao().observeCollections()

    /** Renames [id] (trimmed; a name in use gets " 2"); returns the stored name, null when the collection is gone. A blank or too long name throws. */
    open suspend fun rename(id: Long, name: String): String? = writing { database.collectionDao().rename(id, name) }

    /** Deletes [id] with its memberships; the games stay. */
    open suspend fun delete(id: Long) = writing { database.collectionDao().delete(id) }

    /** Puts the own collections in the order of [ids]. */
    open suspend fun reorder(ids: List<Long>) = writing { database.collectionDao().reorder(ids) }

    companion object {
        private val pending = AtomicInteger()

        /** A collection write is running somewhere in the process: the compiled-code restart holds until it lands. */
        val isWriting: Boolean get() = pending.get() > 0

        /** Runs [write] counted in [isWriting], whether it lands or throws. */
        suspend fun <T> writing(write: suspend () -> T): T {
            pending.incrementAndGet()
            try {
                return write()
            } finally {
                pending.decrementAndGet()
            }
        }
    }
}
