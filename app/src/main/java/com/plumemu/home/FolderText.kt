package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.collections.entryIdOf
import com.plumemu.collections.ownCollectionIdOf

/**
 * The words of a folder: the built-in ones come from strings.xml, an own collection's name is the person's ([name]),
 * New collection… is the folder list's last row. Content rows carry ids and names only, so a language change needs no new content.
 */
@Composable
internal fun folderTitle(
    id: String,
    name: String?,
): String =
    when (id) {
        entryIdOf(Folder.BuiltIn(BuiltInFolder.Recent, 0)) -> stringResource(R.string.home_entry_recent)
        entryIdOf(Folder.BuiltIn(BuiltInFolder.MostPlayed, 0)) -> stringResource(R.string.collections_most_played)
        entryIdOf(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0)) -> stringResource(R.string.collections_recently_added)
        HomeContent.NEW_COLLECTION_ID -> stringResource(R.string.collections_new)
        else -> name.orEmpty()
    }

/** Which folder a folders-page row is, for the words that explain it ([folderAbout], [folderEmptyText]). */
internal enum class FolderKind { Recent, MostPlayed, RecentlyAdded, Own, New }

/** The kind of the folders-page row or folder entry [id]; null for any other entry. */
internal fun folderKindOf(id: String): FolderKind? =
    when {
        id == entryIdOf(Folder.BuiltIn(BuiltInFolder.Recent, 0)) -> FolderKind.Recent
        id == entryIdOf(Folder.BuiltIn(BuiltInFolder.MostPlayed, 0)) -> FolderKind.MostPlayed
        id == entryIdOf(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0)) -> FolderKind.RecentlyAdded
        id == HomeContent.NEW_COLLECTION_ID -> FolderKind.New
        ownCollectionIdOf(id) != null -> FolderKind.Own
        else -> null
    }

/**
 * What a folder is, in one line: the built-ins say what fills them (the rules of `SmartListDao` and Recent), New collection… what a
 * collection is and how to fill one. Null for an own collection: its name says it.
 */
@Composable
internal fun folderAbout(kind: FolderKind): String? =
    when (kind) {
        FolderKind.Recent -> stringResource(R.string.collections_about_recent)
        FolderKind.MostPlayed -> stringResource(R.string.collections_about_most_played)
        FolderKind.RecentlyAdded -> stringResource(R.string.collections_about_recently_added)
        FolderKind.New -> stringResource(R.string.collections_new_about, stringResource(R.string.home_key_start))
        FolderKind.Own -> null
    }

/** Why a folder with no game is empty and how it fills, instead of a bare 0 (the same line its opened list shows). */
@Composable
internal fun folderEmptyText(kind: FolderKind): String =
    when (kind) {
        FolderKind.Recent -> stringResource(R.string.home_recent_empty)
        FolderKind.MostPlayed -> stringResource(R.string.collections_empty_most_played)
        FolderKind.RecentlyAdded -> stringResource(R.string.collections_empty_recently_added)
        FolderKind.Own, FolderKind.New -> stringResource(R.string.collections_empty_own, stringResource(R.string.home_key_start))
    }
