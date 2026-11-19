package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.collections.entryIdOf

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
