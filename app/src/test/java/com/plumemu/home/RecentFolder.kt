package com.plumemu.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction

/** Recent is a folder inside Collections: from the Favorites entry (the column's first), Down to Collections, A to its list, A on Recent's row. */
internal val OpenRecentFolder: List<Action> = listOf(Action.Move(Direction.Down), Action.Enter, Action.Enter)

/** The same from a harness that jumps by tap: Collections' entry, then A twice. */
internal val OpenRecentFolderByTap: List<Action> = listOf(Action.TapItem(Column.Systems, 1), Action.Enter, Action.Enter)

/** Stands for the Recent folder where a board takes an entry index (Recent is not a column entry since the Collections change). */
internal const val RECENT_FOLDER = -1

/** Open Recent, then put the cursor on its game [index] (a grid by default, a list when the list setting holds it). */
internal fun recentGridActions(index: Int): List<Action> = OpenRecentFolderByTap + Action.TapItem(Column.Games, index)

/** The Recent folder's entry. */
internal val HomeContent.recentEntry: HomeEntry
    get() = folderEntries.first { it.id == HomeContent.RECENT_ID }

/** Recent's games, Favorites' and Apps' (entries 0 and 2 of the column: Favorites, Collections, Apps). */
internal val HomeContent.recentRows: List<GameRowModel> get() = recentEntry.rows
internal val HomeContent.favoriteRows: List<GameRowModel> get() = entries.first { it.kind == EntryKind.Favorites }.rows
internal val HomeContent.appRows: List<GameRowModel> get() = entries.firstOrNull { it.kind == EntryKind.Apps }?.rows.orEmpty()

/** This content with some parts replaced; systems keep their rows (not their shapes). */
internal fun HomeContent.rebuilt(
    recent: List<GameRowModel> = recentRows,
    favorites: List<GameRowModel> = favoriteRows,
    apps: List<GameRowModel> = appRows,
    hasBoxArt: Boolean = this.hasBoxArt,
): HomeContent =
    HomeContent.of(
        recent,
        favorites,
        apps,
        entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows) },
        hasBoxArt = hasBoxArt,
    )

/**
 * The main screen shows its landing: Favorites' first game in the games column (the cold start and Home land there since the Collections
 * change; the systems column is slid out of view, so a system's name is not on screen).
 */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.assertHomeLandedOnFavorites() {
    onNodeWithTag(HomeTags.GAMES).assertIsDisplayed()
}

/**
 * A cold start lands in Favorites' games column since the Collections change; a test about something else starts from the system
 * column (Favorites focused), where the cold start was before.
 */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.fromSystemColumn(router: com.plumemu.coreinput.InputRouter) {
    runOnIdle { router.send(Action.Move(Direction.Left)) }
    waitForIdle()
}
