package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.plumemu.coreinput.strip.StripState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells [onShown] the system the person is looking at (the focused entry when it is a system;
 * null on Recent, Favorites and Apps), once per change: a box-art run asks that system's games
 * right after Recent and Favorites (`AppGraph.viewedSystem`). Read in a snapshot
 * flow, so a move recomposes nothing.
 */
@Composable
internal fun FollowShownSystem(
    content: HomeContent,
    state: StripState,
    onShown: (String?) -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val latestOnShown = rememberUpdatedState(onShown)
    LaunchedEffect(state) {
        snapshotFlow { shownSystemId(latestContent.value, state.focus.systemIndex) }
            .distinctUntilChanged()
            .collect { latestOnShown.value(it) }
    }
}

/** The id of entry [index] of [content] when it is a system, else null. */
internal fun shownSystemId(
    content: HomeContent,
    index: Int,
): String? = content.entries.getOrNull(index)?.takeIf { it.kind == EntryKind.System }?.id
