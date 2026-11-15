package com.plumemu.content

import com.plumemu.battery.BatteryPace
import com.plumemu.battery.batteryPaceOf
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.daysAgo
import com.plumemu.game.emulatorToStart
import com.plumemu.game.fileNameForEmulator
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
    /** The emulator Play would start for a game id and whether it is the game's own choice, from one read; called on [io]. */
    private val emulatorOf: suspend (Long) -> EmulatorRead,
    /** The game's usual battery pace (`batteryPaceOf`); null for a game never measured. */
    private val paceOf: suspend (Long) -> BatteryPace? = { null },
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HomePlayFactsSource {
    override suspend fun read(gameId: Long): GamePlay? =
        withContext(io) {
            if (games().game(gameId) == null) return@withContext null
            val play = plays().play(gameId)
            val emulator = emulatorOf(gameId)
            GamePlay(
                lastPlayedDaysAgo = play?.let { daysAgo(it.lastPlayedAt, now()) },
                playTimeMinutes = play?.let { TimeUnit.SECONDS.toMinutes(it.totalSeconds).toInt() } ?: 0,
                emulator = emulator.emulator,
                hasOwnEmulator = emulator.isOwn,
                batteryPace = paceOf(gameId),
            )
        }
}

/** The library's play facts for the home screen's details column, from [AppGraph]'s database (stateless: one per Activity is fine). */
internal fun AppGraph.homePlayFacts(): HomePlayFactsSource =
    LibraryPlayFacts(plays = { database.playDao() }, games = { database.gameDao() }, emulatorOf = { id -> emulatorRead(id) }, paceOf = { id -> batteryPaceOf(id) })

/** The emulator Play would start (null: none installed or the game unknown) and whether it is the game's own choice. */
internal data class EmulatorRead(val emulator: EmulatorToStart?, val isOwn: Boolean = false)

/**
 * [gameId]'s emulator for the details column, from one read of the game row, the catalog and the file name: what Play would start, and whether the
 * game's own choice changes it ([com.plumemu.launcher.choice.EmulatorChoices.hasOwnChoice]: the launch's resolution with and without it); the
 * column names the emulator only then. Call off the main thread.
 */
internal suspend fun AppGraph.emulatorRead(gameId: Long): EmulatorRead {
    val game = database.gameDao().game(gameId) ?: return EmulatorRead(null)
    val system = catalog()?.system(game.systemId) ?: return EmulatorRead(null)
    val fileName = fileNameForEmulator(gameId, system, game.fileName)
    val emulator = emulatorToStart(gameId, system, fileName)
    val own = launching()?.choices?.hasOwnChoice(gameId, system, fileName) ?: false
    return EmulatorRead(emulator, own)
}
