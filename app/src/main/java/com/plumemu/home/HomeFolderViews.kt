package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.GameMenuController

/**
 * SELECT's write: a folder's choice goes to [HomeCallbacks.onFolderView] (the set holds the lists, so "joins it" is a list and
 * the grid flag is the reverse), every other entry's to [HomeCallbacks.onGridView].
 */
@Composable
internal fun rememberToggleView(
    gridViews: GridViews,
    listViews: GridViews,
    state: StripState,
    menu: GameMenuController,
    content: () -> HomeContent,
    callbacks: () -> HomeCallbacks,
): (String) -> Unit =
    remember(gridViews, listViews, state, menu) {
        { id: String ->
            if (content().folderEntry(id) != null) {
                listViews.toggle(id, { folder, isList, onFailed -> callbacks().onFolderView(folder, !isList, onFailed) }) { showSaveFailed(content(), state, menu) }
            } else {
                gridViews.toggle(id, callbacks().onGridView) { showSaveFailed(content(), state, menu) }
            }
        }
    }

/** A cold start with no saved focus: the games column of Favorites, where [StripFocus.reconciled] keeps it only if Favorites has games. */
internal val ColdStartFocus = StripFocus(Column.Games, 0, HomeContent.FAVORITES_ID)

/**
 * The grid ids with the folders' views in: a folder is a grid unless it is in [lists] (`appearance.list_collections`), whatever
 * [grids] holds under its id (Recent was once stored there).
 */
internal fun gridIdsWithFolders(
    grids: Set<String>,
    lists: Set<String>,
    folders: List<HomeEntry>,
): Set<String> {
    if (folders.isEmpty()) return grids
    val ids = folders.mapTo(HashSet()) { it.id }
    return grids.filterTo(HashSet()) { it !in ids } + ids.filter { it !in lists }
}
