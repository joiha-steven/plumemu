package com.plumemu.game

import kotlinx.coroutines.CancellationException

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
