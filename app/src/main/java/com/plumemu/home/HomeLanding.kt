package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.saveable.Saver
import com.plumemu.collections.HomeTarget
import com.plumemu.collections.homeTarget
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.GameMenuController
import com.plumemu.home.search.SearchState

/**
 * Where the Home button, a cold start and the end of first-run setup land: Favorites' first game, else the
 * Favorites entry on its empty line. Never Recent or a system.
 */
internal fun HomeContent.homeTarget(): HomeTarget = homeTarget(entries.firstOrNull { it.kind == EntryKind.Favorites }?.rows?.firstOrNull()?.id)

/**
 * A game the person chose elsewhere (Statistics' most-played) for the main screen to land on:
 * [gameId] is the game row's id inside the entry [systemId]. A target whose system is gone lands as usual;
 * whose game is gone lands on that system's first game.
 */
@Immutable
internal data class HomeLandingTarget(
    val systemId: String,
    val gameId: String,
)

internal val HomeLandingTargetSaver: Saver<HomeLandingTarget?, List<String>> =
    Saver(
        save = { target -> target?.let { listOf(it.systemId, it.gameId) } ?: emptyList() },
        restore = { saved -> if (saved.size == 2) HomeLandingTarget(saved[0], saved[1]) else null },
    )

/**
 * Search and the game panel close once, when [request] is first seen. The request is spent at once: Favorites is always there to
 * land on (its first game, or its empty line), so nothing waits for a scan; a later change of content never moves the focus
 * under the person ([obeyed] is also set by the action handler).
 */
@Composable
internal fun LandWhenAsked(
    request: Int,
    obeyed: MutableIntState,
    state: StripState,
    menu: GameMenuController,
    search: SearchState,
    target: () -> HomeLandingTarget?,
    content: () -> HomeContent,
) {
    LaunchedEffect(request) {
        if (request == obeyed.intValue) return@LaunchedEffect
        menu.close()
        search.close()
        obeyed.intValue = request
        // A game asked for by id (Statistics) when its system is on the screen; otherwise Favorites' first game, or the Favorites entry on its empty line.
        val asked = target()?.takeIf { want -> content().entries.any { it.id == want.systemId } }
        if (asked != null) {
            state.landOn(asked.systemId, asked.gameId)
        } else {
            when (val home = content().homeTarget()) {
                is HomeTarget.Game -> state.landOn(home.entryId, home.gameId)
                HomeTarget.EmptyFavorites -> state.landOn(HomeContent.FAVORITES_ID)
            }
        }
    }
}

