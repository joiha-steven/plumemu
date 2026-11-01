package com.plumemu.game

import com.plumemu.graph.AchievementsError
import com.plumemu.graph.AchievementsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Runs [block]; a failed read is logged by class only (messages may hold paths) and gives null. */
internal suspend fun <T : Any> readLogged(
    log: (String) -> Unit,
    what: String,
    game: GameRef,
    block: suspend () -> T?,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("$what: game ${game.key} could not be read (${e.javaClass.simpleName})")
        null
    }

/**
 * Info's achievements: signed out [read] answers null and nothing more happens (no section). Signed in, each state the
 * flow gives (Loading, then the answer) goes to [onState]; a flow that breaks ends as a plain "bad answer" line.
 */
internal suspend fun readAchievementStates(
    log: (String) -> Unit,
    game: GameRef,
    read: suspend () -> Flow<AchievementsState>?,
    onState: (AchievementsState) -> Unit,
) {
    val states = readLogged(log, "achievements", game, read) ?: return
    try {
        states.collect(onState)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Achievements of game ${game.key} could not be read (${e.javaClass.simpleName})")
        onState(AchievementsState.Error(AchievementsError.BadAnswer))
    }
}
