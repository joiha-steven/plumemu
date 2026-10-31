package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * L1 / R1 while a game grid (a system's, Recent's, Favorites'; not Apps') has the games focus change the grid size instead of the system
 * (the system is switched with L2 / R2 everywhere, see [homeAction]). core-input still maps the two buttons to system switches; the decision depends on
 * the screen's state (which systems are grids), so it is taken here, before the strip sees them.
 * In Apps' grid the two buttons are taken too and do nothing ([isAppsGridKey]).
 */
internal fun isGridSizeKey(
    action: Action,
    focus: StripFocus,
    content: HomeContent,
    gridIds: Set<String>,
): Boolean {
    if (action != Action.PreviousSystem && action != Action.NextSystem || focus.column != Column.Games) return false
    val entry = content.entries.getOrNull(focus.systemIndex)
    return entry?.kind != EntryKind.Apps && content.showsGrid(entry, gridIds)
}

/**
 * L1 / R1 while Apps' grid has the games focus. It has one fixed size and, in a grid, the
 * system is switched with L2 / R2, so the two buttons do nothing there.
 */
internal fun isAppsGridKey(
    action: Action,
    focus: StripFocus,
    content: HomeContent,
    gridIds: Set<String>,
): Boolean {
    if (action != Action.PreviousSystem && action != Action.NextSystem || focus.column != Column.Games) return false
    val entry = content.entries.getOrNull(focus.systemIndex)
    return entry?.kind == EntryKind.Apps && content.showsGrid(entry, gridIds)
}

/** The size after [action] (L1 smaller, R1 larger), or null at the end: Small on L1 and Large on R1 do nothing. */
internal fun GridSize.after(action: Action): GridSize? =
    GridSize.entries.getOrNull(ordinal + if (action == Action.NextSystem) 1 else -1)

/**
 * [GridSizeKeys] for the screen: [gridIds] as drawn now, [onSize] the write to the settings (told
 * if it could not be saved), [onWriteFailed] what the person is told then.
 */
@Composable
internal fun rememberGridSizeKeys(
    gridIds: Set<String>,
    onSize: (GridSize, onFailed: () -> Unit) -> Unit,
    onWriteFailed: () -> Unit,
): GridSizeKeys {
    val scope = rememberCoroutineScope()
    val keys = remember { GridSizeKeys(scope) }
    val shown = PlumTheme.layout.gridSize
    keys.themeSize = shown
    keys.gridIds = gridIds
    keys.onSize = onSize
    keys.onWriteFailed = onWriteFailed
    // Only the size asked for ends the wait: an earlier step of two quick presses showing first must not.
    LaunchedEffect(shown) { if (keys.requested == shown) keys.requested = null }
    return keys
}

/**
 * Takes L1 / R1 in a grid, and a tap on the Size hint. Keeps the size it asked for until the
 * theme shows it, so quick presses step from it instead of from the old size.
 */
internal class GridSizeKeys(private val scope: CoroutineScope) {
    var themeSize: GridSize = GridSize.Default
    var gridIds: Set<String> = emptySet()
    var onSize: (GridSize, onFailed: () -> Unit) -> Unit = { _, _ -> }
    var onWriteFailed: () -> Unit = {}
    var requested: GridSize? = null

    /** True when [action] was L1 / R1 in a grid and is dealt with here; at an end, and in Apps' grid, it does nothing (no sound). */
    fun handle(
        action: Action,
        focus: StripFocus,
        content: HomeContent,
        feel: Feel,
    ): Boolean {
        // Apps' grid: taken, nothing happens (no sound, no fade, no entry change).
        if (isAppsGridKey(action, focus, content, gridIds)) return true
        if (!isGridSizeKey(action, focus, content, gridIds)) return false
        feel.act { (requested ?: themeSize).after(action)?.let { choose(it, feel) } }
        return true
    }

    /** A tap on the Size hint: Small, Medium, Large, then Small again (a touch-only person has no other way back). */
    fun cycle(feel: Feel) {
        val size = GridSize.entries[((requested ?: themeSize).ordinal + 1) % GridSize.entries.size]
        feel.act { choose(size, feel) }
    }

    private fun choose(
        size: GridSize,
        feel: Feel,
    ) {
        requested = size
        // The write's failure comes from the settings' scope; the person is told on the main one.
        onSize(size) {
            scope.launch {
                requested = null
                onWriteFailed()
            }
        }
        feel.report(Outcome.Moved)
    }
}
