package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which systems the main screen shows as a grid (SELECT). The stored setting
 * (`appearance.grid_systems`) is the truth; the presses made on this screen are laid over it, so a
 * press shows at once, before its write comes back, and a write that failed still leaves the
 * person's choice on screen. Held in a plain `remember`: when Settings replaces the main screen
 * (where Reset all settings and Restore… change the stored set) the screen starts again from what
 * is stored.
 *
 * Every press is "this system is a grid" or "this system is a list", never a flip of what is
 * stored: two quick presses written in order end where the screen shows, and an earlier write
 * landing between them cannot flash the other view (the press stays laid over it).
 */
@Stable
internal class GridViews(
    private val stored: State<Set<String>>,
    private val scope: CoroutineScope,
) {
    private val pressed = mutableStateMapOf<String, Boolean>()

    /** The systems shown as a grid now: [stored] with this screen's presses over it. Ids no system has are kept and ignored by `HomeContent.showsGrid`. */
    val ids: Set<String> by derivedStateOf {
        val base = stored.value
        if (pressed.isEmpty()) base else base.filterTo(mutableSetOf()) { pressed[it] != false } + pressed.filterValues { it }.keys
    }

    /**
     * SELECT on [systemId]: the other view than the one shown, at once, and one [write] of it.
     * When the write could not be saved (told on the writer's thread) the press stays shown and
     * [onWriteFailed] runs on the screen's thread.
     */
    fun toggle(
        systemId: String,
        write: (systemId: String, isGrid: Boolean, onFailed: () -> Unit) -> Unit,
        onWriteFailed: () -> Unit,
    ) {
        val isGrid = systemId !in ids
        pressed[systemId] = isGrid
        write(systemId, isGrid) { scope.launch { onWriteFailed() } }
    }
}

/** [GridViews] over [stored] (the settings' `appearance.grid_systems`), kept while the main screen is composed. */
@Composable
internal fun rememberGridViews(stored: Set<String>): GridViews {
    val current = rememberUpdatedState(stored)
    val scope = rememberCoroutineScope()
    return remember { GridViews(current, scope) }
}
