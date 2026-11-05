package com.plumemu.secondscreen

import com.plumemu.game.emulatorToStart
import com.plumemu.graph.AppGraph
import kotlinx.coroutines.flow.first

/** The system column cards' reads over the app graph: stored figures only, no network (benchmark builds read none). */
internal fun AppGraph.secondScreenEntryReads(): SecondScreenEntryReads =
    SecondScreenEntryReads(
        figures = systemFigures,
        games = { database.gameDao() },
        plays = { database.playDao() },
        ra = { database.raDao() },
        isRaSignedIn = { achievements.isSignedIn.first() },
        // A game id no row has: the system's own choice, as the launch resolves it.
        emulatorOf = { systemId -> catalog()?.system(systemId)?.let { emulatorToStart(NO_GAME, it) } },
        artOf = { keys -> homeArt.observe(keys).first() },
    )

private const val NO_GAME = -1L
