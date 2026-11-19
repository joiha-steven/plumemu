package com.plumemu.home

import androidx.annotation.StringRes
import com.plumemu.R
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.collections.FolderHint
import com.plumemu.collections.hintsFor as folderHintsFor
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.ShoulderKeys

/**
 * The hints for where the focus is (the board's RP5-1, RP5-2, RP5-3 and Nova-3). SELECT shows for an
 * entry that can be a grid ([HomeContent.hasGridFor]): a system, Recent, Favorites and Apps, with or without box art. "A Select" only where there is something to open
 * (an empty Recent has nothing), "L1 R1" only when there is another list to switch to (with no
 * games only Apps), "X Search" only when there are games to find, "Y Favorite" (or "Y Unfavorite"
 * for a favorite, [focusedIsFavorite]) only on a game. A grid shows A, B and "SELECT List view"
 * (RP5-3) and "L1 R1 Size" (they change the grid size); Apps' grid has none (its
 * size is fixed and L1 / R1 do nothing there); every list and grid with another
 * entry to go to also shows "L2 R2 Switch list" (L2 / R2 switch the entry everywhere, L1 / R1 jump a letter in a list); the narrow details stop A, B, Y and "START More" (Nova-3).
 * The pair names here are the default's: with Switch list with = L1 / R1 ([shoulders]) the two pairs trade places in these hints.
 */
internal fun hintsFor(
    content: HomeContent,
    column: Column,
    entry: HomeEntry?,
    showsGrid: Boolean = false,
    letters: ButtonLetters = ButtonLetters.Default,
    focusedIsFavorite: Boolean = false,
    pushRightPlays: Boolean = false,
    pushRightArmed: Boolean = false,
    updateWaiting: Boolean = false,
    shoulders: ShoulderKeys = ShoulderKeys.Default,
    hasDetailsStop: Boolean = false,
    /** The empty state's focused button; null while it shows none (reading the folders): A then offers nothing. */
    emptyButton: EmptyButton? = EmptyButton.AddFolder,
    /** On the folders page: the focused row is New collection… (A reads "New collection", not "Open"). */
    focusedFolderIsNew: Boolean = false,
): List<HintSpec> {
    // The folders page acts on a folder row only: no Favorite, Sort, Menu or view switch (CollectionHints.kt).
    if (column == Column.Games && entry?.isFolderList == true) return folderListHints(content, focusedFolderIsNew, letters, shoulders)
    // While an update waits START opens Settings on it, so the hint says what it is for.
    val settings = HintSpec(R.string.home_key_start, if (updateWaiting) R.string.home_hint_update else R.string.home_hint_settings, Action.OpenSettings)
    val hasItems = (entry?.count ?: 0) > 0
    val isApps = entry?.kind == EntryKind.Apps
    val play = HintSpec(letters.confirm, if (isApps) R.string.home_hint_open else R.string.home_hint_play, Action.Play)
    // Push right to play: "→ Twice to play" beside "A Play" (a list; not a grid); after the first Right "→ Again to play".
    val pushRight = if (pushRightPlays && hasItems) HintSpec(R.string.home_key_right, pushRightLabel(isApps, pushRightArmed), Action.Play) else null
    val back = HintSpec(letters.back, R.string.home_hint_back, Action.Back)
    val search = if (content.gameCount > 0) HintSpec(letters.search, R.string.home_hint_search, Action.Search) else null
    val favorite = if (hasItems && !isApps) favoriteHint(letters, focusedIsFavorite) else null
    // An empty folder has nothing to play and no game to open a menu on (START emits nothing there).
    val emptyFolder = entry?.isFolder == true && !hasItems
    val specs =
        when {
            column == Column.Systems ->
                listOfNotNull(
                    when {
                        hasItems -> HintSpec(letters.confirm, R.string.home_hint_select, Action.Enter)
                        // The only button says it itself; with two, the hint says which one A presses.
                        content.asksForFolderAt(entry) -> emptyButton?.takeIf { it != EmptyButton.AddFolder }?.let { HintSpec(letters.confirm, it.label(), Action.Enter) }
                        else -> null
                    },
                    search,
                    settings,
                    // Where the focused system's games can be a grid: SELECT says which view comes next (as the games list's hint does).
                    if (hasItems && entry != null && content.hasGridFor(entry)) {
                        HintSpec(R.string.home_key_select, if (showsGrid) R.string.home_hint_list_view else R.string.home_hint_grid_view, Action.ToggleView)
                    } else {
                        null
                    },
                )
            // Narrow with box art and Push right to play: Right at the stop arms, so the hint is here too.
            column == Column.Details -> listOfNotNull(play, pushRight.takeIf { !showsGrid }, back, favorite, HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu))
            // Apps' grid has one fixed size and L1 R1 do nothing there: no L1 R1 hint.
            showsGrid && (isApps || entry?.isAppGamesGrid == true) ->
                appsGridHints(play, back, search, switchSystem(content, shoulders))
            // L1 R1 change the grid size here, not the system (the system column is a Back away).
            showsGrid ->
                listOfNotNull(
                    play,
                    back,
                    favorite,
                    HintSpec(shoulders.sizeCap, R.string.home_hint_grid_size, Action.NextSystem, cyclesGridSize = true),
                    switchSystem(content, shoulders),
                    HintSpec(R.string.home_key_select, R.string.home_hint_list_view, Action.ToggleView),
                )
            else ->
                listOfNotNull(
                    play,
                    // With a details stop Right goes there first, so the hint waits for the stop.
                    pushRight.takeIf { !hasDetailsStop },
                    // B leaves a folder for the folders page: its list says so, as its grid does.
                    back.takeIf { entry?.isFolder == true },
                    // No "B Back" here: the key still goes back, the games list's bar leaves it out.
                    favorite,
                    search,
                    // "R3 Sort" on a list the sort orders (a system's, Favorites); it gives way after Switch list.
                    if (hasItems && entry?.kind?.followsSort == true) HintSpec(R.string.home_key_r3, R.string.home_hint_sort, Action.Sort) else null,
                    HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu),
                    switchSystem(content, shoulders),
                    if (entry != null && content.hasGridFor(entry)) HintSpec(R.string.home_key_select, R.string.home_hint_grid_view, Action.ToggleView) else null,
                )
        }
    return if (emptyFolder) specs.filterNot { it.action == Action.Play || it.action == Action.Menu || it.action == Action.ToggleView } else specs
}

/** The folders page's hints: A Open (New collection on its row), X Search, the L2/R2 switch, B Back. */
private fun folderListHints(
    content: HomeContent,
    onNewCollection: Boolean,
    letters: ButtonLetters,
    shoulders: ShoulderKeys,
): List<HintSpec> {
    val switch = switchSystem(content, shoulders)
    val focused = if (onNewCollection) Folder.NewCollection else Folder.BuiltIn(BuiltInFolder.Recent, 0)
    return folderHintsFor(focused, hasOtherEntries = switch != null).mapNotNull {
        when (it) {
            FolderHint.Open -> HintSpec(letters.confirm, R.string.collections_hint_open, Action.Enter)
            FolderHint.NewCollection -> HintSpec(letters.confirm, R.string.collections_hint_new, Action.Enter)
            // X does nothing without games to find, so it has no hint (as everywhere).
            FolderHint.Search -> if (content.gameCount > 0) HintSpec(letters.search, R.string.home_hint_search, Action.Search) else null
            FolderHint.SwitchEntry -> switch
            FolderHint.Back -> HintSpec(letters.back, R.string.home_hint_back, Action.Back)
        }
    }
}

/**
 * True where the games area says "No game folder yet": no system has games and [entry] shows nothing.
 * A there (or a tap on the message) opens the folder picker instead of a column.
 */
internal fun HomeContent.asksForFolderAt(entry: HomeEntry?): Boolean = isLibraryEmpty && entry != null && entry.rows.isEmpty()

/**
 * "L2 R2 Switch list" when there is another list to switch to (with no games only Apps has rows). Its
 * action is the switching pair's own name, so a tap goes through the same reading as the key ([ShoulderKeys.read], [homeAction]).
 */
private fun switchSystem(
    content: HomeContent,
    shoulders: ShoulderKeys,
): HintSpec? =
    if (content.entries.count { it.count > 0 } > 1) {
        // Under L1 / R1 the tap sends L1 / R1's name, the one the screen reads as "switch system" there.
        val tap = if (shoulders.bumpersSwitch) Action.NextSystem else Action.JumpLetterNext
        HintSpec(shoulders.switchSystemCap, R.string.home_hint_switch_system, Action.JumpLetterNext, tapAction = tap)
    } else {
        null
    }

@StringRes
internal fun pushRightLabel(
    isApps: Boolean,
    armed: Boolean,
): Int =
    when {
        armed -> if (isApps) R.string.home_hint_again_open else R.string.home_hint_again_play
        else -> if (isApps) R.string.home_hint_twice_open else R.string.home_hint_twice_play
    }
