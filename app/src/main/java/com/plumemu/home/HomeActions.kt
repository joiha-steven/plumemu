package com.plumemu.home

import com.plumemu.telemetry.LaunchFrom
import com.plumemu.usage.launchFromOf
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripIntent
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.GameMenuController
import com.plumemu.game.GameRef
import com.plumemu.settings.ShoulderKeys
import com.plumemu.home.search.SearchEffect
import com.plumemu.home.search.SearchState
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome

/** What the main screen's actions reach, for [FollowRouterActions]: the screen's state holders, read when an action comes. */
internal class HomeActionTargets(
    val state: StripState,
    val menu: GameMenuController,
    val search: SearchState,
    val feel: Feel,
    val gridSizeKeys: GridSizeKeys,
    val toggleView: (systemId: String) -> Unit,
    val markedNow: () -> HomeContent,
    val content: () -> HomeContent,
    val callbacks: () -> HomeCallbacks,
    val shoulders: () -> ShoulderKeys = { ShoulderKeys.Default },
    /** Game rows the list shows at once (`PlumLayout.listRowsThatFit`): L1 / R1's step in a list not in name order. */
    val pageRows: () -> Int = { 1 },
)

/**
 * Collects [router]'s actions only while the screen is started and hands each to the open game panel,
 * Search, the grid's size keys or the strip, in that order; [onEachAction] runs first for every one.
 */
@Composable
internal fun FollowRouterActions(
    router: InputRouter,
    targets: HomeActionTargets,
    onEachAction: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnEachAction by rememberUpdatedState(onEachAction)
    with(targets) {
        LaunchedEffect(router, state, menu, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                router.actions.collect { action ->
                    currentOnEachAction()
                    // An open game panel takes every action; search and the strip's focus wait underneath.
                    // Each of the three says what the action did ([Feel]) where it changes its state.
                    // The router hears at once what the screen now takes (a panel closing over typing gives B back to Search).
                    if (menu.onAction(action).also { router.textEntry = textEntryFor(search, menu) }) return@collect
                    if (search.isOpen) {
                        onSearchAction(action, search, router, feel, menu, markedNow)
                        return@collect
                    }
                    // From here the main screen's own reading: Switch system with trades the pairs.
                    val read = shoulders().read(action)
                    // L1 / R1 in a game grid is its size, in Apps' grid nothing.
                    if (gridSizeKeys.handle(read, state.focus, content(), feel)) return@collect
                    // An empty library: A asks for a folder (the same picker as Settings › Library › Add folder).
                    if (read == Action.Enter && content().asksForFolderAt(content().entries.getOrNull(state.focus.systemIndex))) {
                        feel.emit(Outcome.Activated)
                        callbacks().onAddFolder()
                        return@collect
                    }
                    val homeAction = homeAction(read)
                    feel.act {
                        val before = state.focus
                        val acted = state.dispatch(homeAction).map { intent -> handleIntent(intent, content(), state, callbacks(), menu, toggleView, search, markedNow, gridSizeKeys.gridIds, pageRows()) }
                        if (true in acted) feel.report(Outcome.Activated)
                        if (state.focus != before) feel.report(strip(homeAction, before, state.focus))
                    }
                }
            }
        }
    }
}

private fun handleIntent(
    intent: StripIntent,
    content: HomeContent,
    state: StripState,
    callbacks: HomeCallbacks,
    menu: GameMenuController,
    toggleView: (systemId: String) -> Unit,
    search: SearchState,
    markedNow: () -> HomeContent,
    gridIds: Set<String>,
    pageRows: Int,
): Boolean = when (intent) {
    is StripIntent.Play ->
        content.entry(intent.systemId)?.let { entry ->
            if (entry.kind == EntryKind.Apps) {
                entry.gameAt(intent.index)?.let(menu::openApp) == true
            } else {
                entry.gameAt(intent.index)?.let { menu.play(it, launchFromOf(entry, content.showsGrid(entry, gridIds))) } == true
            }
        } == true
    is StripIntent.OpenGameMenu ->
        content.entry(intent.systemId)?.let { entry ->
            if (entry.kind == EntryKind.Apps) {
                entry.gameAt(intent.index)?.let(menu::openAppMenu) == true
            } else {
                entry.gameAt(intent.index)?.let { menu.openMenu(it, fromRecent = entry.kind == EntryKind.Recent) } == true
            }
        } == true
    StripIntent.OpenSettings -> {
        callbacks.onSettings()
        true
    }
    is StripIntent.ToggleFavorite ->
        markedNow().entry(intent.systemId)?.let { entry ->
            if (entry.kind == EntryKind.Apps) {
                Log.d(TAG, "Favorite on an app: apps cannot be favorites")
                false
            } else {
                entry.rows.getOrNull(intent.index)?.let { row -> menu.setFavorite(GameRef(row.id, row.title), !row.isFavorite) } != null
            }
        } == true
    // Nothing to find in an empty library: X does nothing there (and has no hint).
    StripIntent.OpenSearch ->
        if (content.gameCount > 0) {
            search.open()
            true
        } else {
            Log.d(TAG, "Search: the library has no games")
            false
        }
    // The cursor move is told by the focus change, not here.
    // A list not in name order (Recent, sorted by last played or year) has no letters to jump to: a page instead.
    is StripIntent.JumpLetter -> {
        val entry = content.entry(intent.systemId)
        val rows = entry?.rows.orEmpty()
        val target =
            if (entry == null || entry.hasLetterRail) {
                letterJumpTarget(rows, intent.fromIndex, intent.forward)
            } else {
                pageJumpTarget(rows.size, intent.fromIndex, intent.forward, pageRows)
            }
        state.focusRow(target)
        false
    }
    // The reducer sends this with box art, and for Apps also without it (its icons need none).
    is StripIntent.ToggleView ->
        content.entry(intent.systemId).let { entry ->
            when {
                entry == null || !content.hasGridFor(entry) -> {
                    Log.d(TAG, "ToggleView for ${intent.systemId}: this entry has no grid view")
                    false
                }
                // An entry without games shows a list whatever is saved (HomeContent.showsGrid): a press would only rewrite the saved choice.
                entry.rows.isEmpty() -> {
                    Log.d(TAG, "ToggleView for ${intent.systemId}: this entry has no games, nothing to switch")
                    false
                }
                else -> {
                    toggleView(intent.systemId)
                    true
                }
            }
        }
}

/** The sound a strip focus change earns: B closing a column goes back, A or a tap opening one confirms, the rest moves. */
private fun strip(
    action: Action,
    before: StripFocus,
    after: StripFocus,
): Outcome =
    when {
        action == Action.Back -> Outcome.WentBack
        action == Action.Enter || action == Action.Play -> Outcome.Activated
        // A tap that opens a deeper column acts; one that focuses an item or steps back moves.
        action is Action.TapItem && after.column.ordinal > before.column.ordinal -> Outcome.Activated
        else -> Outcome.Moved
    }

/** An action while Search is open; the router hears at once what Search now takes (not a frame later: a B pressed right after START must already go back, not erase). */
private fun onSearchAction(
    action: Action,
    search: SearchState,
    router: InputRouter,
    feel: Feel,
    menu: GameMenuController,
    markedNow: () -> HomeContent,
) {
    search.onAction(action)?.let { effect ->
        feel.emit(if (handleSearchEffect(effect, markedNow(), menu, search)) Outcome.Activated else Outcome.Nothing)
    }
    router.textEntry = textEntryFor(search, menu)
}

/** A search result asked for something: the same Play, menu and favorite as on the strip. */
private fun handleSearchEffect(
    effect: SearchEffect,
    content: HomeContent,
    menu: GameMenuController,
    search: SearchState,
): Boolean = when (effect) {
    is SearchEffect.Play -> search.markResultOpened().let { menu.play(GameRef(effect.hit.row.id, effect.hit.row.title), LaunchFrom.Search) }
    is SearchEffect.OpenMenu -> search.markResultOpened().let { menu.openMenu(GameRef(effect.hit.row.id, effect.hit.row.title)) }
    is SearchEffect.ToggleFavorite -> {
        // The shown mark (a quick second Y reads the first one's star, not the index's copy).
        val row = (content.entries + content.hiddenEntries).firstOrNull { it.id == effect.hit.systemId }?.rows?.firstOrNull { it.id == effect.hit.row.id } ?: effect.hit.row
        menu.setFavorite(GameRef(row.id, row.title), !row.isFavorite)
        true
    }
}

/**
 * The main screen's own reading of the shoulder keys: L2 / R2, key or trigger,
 * switch the entry and L1 / R1 jump a letter (the grid cases of L1 / R1 are taken before this). core-input
 * keeps its mapping (L1 / R1 are [Action.PreviousSystem] / [Action.NextSystem], L2 / R2 the letter jumps)
 * because Settings, Search and the keyboard read those names for their own sections and carets.
 */
internal fun homeAction(action: Action): Action =
    when (action) {
        Action.JumpLetterPrevious -> Action.PreviousSystem
        Action.JumpLetterNext -> Action.NextSystem
        Action.PreviousSystem -> Action.JumpLetterPrevious
        Action.NextSystem -> Action.JumpLetterNext
        else -> action
    }

/** One page ([pageRows], at least one row) on from [fromIndex], kept inside a list of [count] rows. */
internal fun pageJumpTarget(
    count: Int,
    fromIndex: Int,
    forward: Boolean,
    pageRows: Int,
): Int {
    if (count <= 0) return 0
    val step = pageRows.coerceAtLeast(1)
    return (if (forward) fromIndex + step else fromIndex - step).coerceIn(0, count - 1)
}
