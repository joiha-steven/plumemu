package com.plumemu.secondscreen

import com.plumemu.battery.BatteryPace
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.daysAgo
import com.plumemu.graph.StoredAchievements
import com.plumemu.home.GameArt
import java.util.concurrent.TimeUnit

/**
 * The play facts of a card, read from the play history once the cursor has rested
 * ([SecondScreenFeed]), off the main thread. The
 * home rows carry no play facts without box art, so they are never taken from there. Days are
 * counted like the game's Info panel ([daysAgo]: calendar days in the device's time zone).
 */
internal class SecondScreenPlayFacts(
    /** The play history; called on the reading thread, so opening the database stays off the main thread. */
    private val plays: () -> PlayDao,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * The saved result under a scrape key (its facts and description; [cardResult]),
     * or null (read off the main thread); null: cards carry no details.
     */
    private val resultOf: (suspend (String) -> GameArt?)? = null,
    /**
     * The emulator Play would start for a game id ([com.plumemu.game.emulatorToStart], the launch's
     * own resolution), read with the play facts; null: the played line names none.
     */
    private val emulatorOf: (suspend (Long) -> EmulatorToStart?)? = null,
    /**
     * The game row's file name for a game id (the file Play launches: the row is the launched file
     * of a `.cue`, `.m3u`, disc set or Switch group), read with the play facts; null: the details
     * card shows no file line.
     */
    private val fileNameOf: (suspend (Long) -> String?)? = null,
    /**
     * A game id's usual battery pace (`batteryPaceOf`), read with the play facts; null: the card
     * shows no battery line. Null for a game never measured too.
     */
    private val paceOf: (suspend (Long) -> BatteryPace?)? = null,
    /**
     * A game id's stored RetroAchievements progress, points and latest unlocks (`GraphAchievements.storedFactsOf`: the database only,
     * never the network), read with the play facts; null: no achievements tile.
     */
    private val achievementsOf: (suspend (Long) -> StoredAchievements?)? = null,
    /** The figures of the system column's cards (a system, Recent, Favorites); null: those cards show their header only. */
    private val entries: SecondScreenEntryReads? = null,
    /** Whether the window has room for the card's description; false never opens the result read. */
    private val isArtShown: () -> Boolean = { true },
) {
    /** [content] with its facts filled in; null when it has none to read (it is shown as it is). Throws what the database throws. */
    suspend fun fill(content: SecondScreenContent): SecondScreenContent? =
        when (content) {
            is SecondScreenContent.Game -> {
                val id = content.gameId
                val key = content.scrapeKey
                val needsPlay = id != null && !content.isPlayKnown
                val readArt = resultOf
                // A card with its details (the fakes' rows, or the same card again) has nothing left to read.
                val needsResult = key != null && readArt != null && content.details == null && isArtShown()
                if (!needsPlay && !needsResult) {
                    null
                } else {
                    val play = if (needsPlay && id != null) plays().play(id) else null
                    val stored = if (needsPlay) achievementsOf?.let { read -> read(requireNotNull(id)) } else null
                    val withPlay =
                        if (needsPlay) {
                            content.copy(
                                isPlayKnown = true,
                                lastPlayedDaysAgo = play?.let { daysAgo(it.lastPlayedAt, now()) },
                                playTimeMinutes = play?.let { TimeUnit.SECONDS.toMinutes(it.totalSeconds).toInt() },
                                emulator = emulatorOf?.let { read -> EmulatorFact(read(requireNotNull(id))) },
                                fileName = fileNameOf?.let { read -> read(requireNotNull(id)) },
                                batteryPace = paceOf?.let { read -> read(requireNotNull(id)) },
                                achievements = stored?.count,
                                ra = stored,
                                raLatestDaysAgo = stored?.recent?.firstOrNull()?.let { daysAgo(it.earnedAt, now()) },
                                weeks = playWeeksOf(plays().sessions(requireNotNull(id)), now()),
                            )
                        } else {
                            content
                        }
                    if (needsResult && key != null && readArt != null) {
                        withPlay.copy(details = readArt(key)?.details)
                    } else {
                        withPlay
                    }
                }
            }
            is EntryContent -> entries?.fill(content)
            else -> null
        }
}

/**
 * The file Play launches for the library's game [gameId], with its extension: the game row's own
 * file, the one [GameDao.launchTarget] starts and the Info panel's "File" row shows. For a game of
 * several files the scan keeps one row, the launched file (a `.cue`, not its `.bin`; an `.m3u`, not
 * its discs; a disc set's first disc; a Switch game's base, not its update or DLC). Null when the
 * game is not in the library. Call off the main thread. The app passes the graph's
 * `launchedFileName` instead, which names a version chosen in the game menu (Other versions).
 */
internal suspend fun GameDao.launchedFileName(gameId: Long): String? = game(gameId)?.fileName
