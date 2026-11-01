package com.plumemu.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver

/**
 * The entry the Home button, and the end of first-run setup, land on: Recent
 * when it has games (its first is the last game played, so A plays it again), else the first
 * system in the column's order ([com.plumemu.content.SystemOrder] already arranged it), else null:
 * with no game anywhere the content is empty or not final yet (a scan is running), and the caller
 * waits for a target instead of landing on Apps.
 */
internal fun HomeContent.landingEntryId(): String? =
    entries.firstOrNull { it.kind == EntryKind.Recent && it.count > 0 }?.id
        ?: entries.firstOrNull { it.kind == EntryKind.System && it.count > 0 }?.id

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
