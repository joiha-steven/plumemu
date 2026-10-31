package com.plumemu.content

import com.plumemu.battery.BatteryPace
import com.plumemu.battery.batteryPaceOf
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.daysAgo
import com.plumemu.game.emulatorToStart
import com.plumemu.graph.AppGraph
import com.plumemu.home.GamePlay
import com.plumemu.home.HomePlayFactsSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The details column's play facts from the library: the play history ([PlayDao.play]) and the emulator A would start
 * ([com.plumemu.game.emulatorToStart], the launch's own resolution, the Info panel's answer), read
 * on [io] for one rested game at a time. Days are counted like the Info panel ([daysAgo]: calendar
 * days in the device's time zone). A game never played reads as never played with 0 minutes; a game
 * the library no longer has (a row left from before a rescan) reads as null, so no table shows. What
 * the database throws reaches the screen, which logs it and shows the column without the table.
 */
internal class LibraryPlayFacts(
    /** The play history; called on [io], so opening the database stays off the main thread. */
    private val plays: () -> PlayDao,
    /** The library's games, to tell a game it no longer has; called on [io]. */
    private val games: () -> GameDao,
    /** The emulator Play would start for a game id; null when none is installed or the game is unknown. */
    private val emulatorOf: suspend (Long) -> EmulatorToStart?,
    /** The game's usual battery pace (`batteryPaceOf`); null for a game never measured. */
    private val paceOf: suspend (Long) -> BatteryPace? = { null },
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HomePlayFactsSource {
    override suspend fun read(gameId: Long): GamePlay? =
        withContext(io) {
            if (games().game(gameId) == null) return@withContext null
            val play = plays().play(gameId)
            GamePlay(
                lastPlayedDaysAgo = play?.let { daysAgo(it.lastPlayedAt, now()) },
                playTimeMinutes = play?.let { TimeUnit.SECONDS.toMinutes(it.totalSeconds).toInt() } ?: 0,
                emulator = emulatorOf(gameId),
                batteryPace = paceOf(gameId),
            )
        }
}

/** The library's play facts for the home screen's details column, from [AppGraph]'s database (stateless: one per Activity is fine). */
internal fun AppGraph.homePlayFacts(): HomePlayFactsSource =
    LibraryPlayFacts(plays = { database.playDao() }, games = { database.gameDao() }, emulatorOf = { id -> emulatorToStart(id) }, paceOf = { id -> batteryPaceOf(id) })
