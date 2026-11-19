package com.plumemu.collections

import com.plumemu.coredata.db.CollectionRow

/** The built-in folders, in the order the folders page lists them. */
enum class BuiltInFolder { Recent, MostPlayed, RecentlyAdded }

/** One row of the Collections entry's folders page. */
sealed interface Folder {
    data class BuiltIn(val kind: BuiltInFolder, val count: Int) : Folder

    data class Own(val id: Long, val name: String, val count: Int) : Folder

    /** The last row: opens the keyboard for a new collection's name. Not a collection, not counted. */
    data object NewCollection : Folder
}

/** The line an empty folder shows under its header; the text itself comes from strings later. */
enum class FolderEmptyLine { Recent, MostPlayed, RecentlyAdded, Own }

/** Built-ins in order, own collections in position order, then New collection. An empty folder is never left out. */
fun foldersOf(recentCount: Int, mostPlayedCount: Int, recentlyAddedCount: Int, own: List<CollectionRow>): List<Folder> =
    listOf(
        Folder.BuiltIn(BuiltInFolder.Recent, recentCount),
        Folder.BuiltIn(BuiltInFolder.MostPlayed, mostPlayedCount),
        Folder.BuiltIn(BuiltInFolder.RecentlyAdded, recentlyAddedCount),
    ) + own.sortedWith(compareBy({ it.position }, { it.id })).map { Folder.Own(it.id, it.name, it.gameCount) } + Folder.NewCollection

/** The number in the header ("5 collections"): every folder but New collection. */
fun collectionCount(folders: List<Folder>): Int = folders.count { it !is Folder.NewCollection }

/** The id a folder's list or grid choice is remembered under (and telemetry never sees a name); null for New collection. */
fun entryIdOf(folder: Folder): String? =
    when (folder) {
        is Folder.BuiltIn -> when (folder.kind) {
            BuiltInFolder.Recent -> "recent"
            BuiltInFolder.MostPlayed -> "most_played"
            BuiltInFolder.RecentlyAdded -> "recently_added"
        }
        is Folder.Own -> "collection:${folder.id}"
        Folder.NewCollection -> null
    }

/** The collection id in an own folder's entry id (`collection:<id>`); null for the built-in folders and any other entry. */
fun ownCollectionIdOf(entryId: String): Long? = entryId.removePrefix("collection:").takeIf { it != entryId }?.toLongOrNull()

/** Every folder opens as a grid unless the person chose a list for it (`appearance.list_collections`). */
fun opensAsGrid(entryId: String, listCollections: Set<String>): Boolean = entryId !in listCollections

/** The set after SELECT on [entryId]: a grid becomes a list (added), a list a grid (removed). */
fun toggledView(entryId: String, listCollections: Set<String>): Set<String> =
    if (entryId in listCollections) listCollections - entryId else listCollections + entryId

/** The empty line of [folder]; null for New collection, which is not a list. */
fun emptyLineOf(folder: Folder): FolderEmptyLine? =
    when (folder) {
        is Folder.BuiltIn -> when (folder.kind) {
            BuiltInFolder.Recent -> FolderEmptyLine.Recent
            BuiltInFolder.MostPlayed -> FolderEmptyLine.MostPlayed
            BuiltInFolder.RecentlyAdded -> FolderEmptyLine.RecentlyAdded
        }
        is Folder.Own -> FolderEmptyLine.Own
        Folder.NewCollection -> null
    }

/** Rename, delete and move are for the person's own collections only. */
fun canRename(folder: Folder): Boolean = folder is Folder.Own

fun canDelete(folder: Folder): Boolean = folder is Folder.Own

fun canMove(folder: Folder): Boolean = folder is Folder.Own
