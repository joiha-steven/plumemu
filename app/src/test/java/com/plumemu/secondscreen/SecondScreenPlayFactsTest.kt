package com.plumemu.secondscreen

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** The play facts read after the settle, from an in-memory play history: a game, a system, Favorites and Recent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenPlayFactsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()

    /** Today at noon, so a few calendar days back stay whole days in any time zone. */
    private val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }.timeInMillis
    private val facts = SecondScreenPlayFacts(plays = { db.playDao() }, now = { now })
    private lateinit var ids: Map<String, Long>

    private fun daysBack(days: Int): Long = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis

    @Before
    fun setUp() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            db.scanDao().applyBatch(
                ScanBatch(
                    folderId,
                    systems = listOf(SystemEntity("snes", "snes"), SystemEntity("md", "md")),
                    newGames = listOf(game(folderId, "snes", "Super Metroid"), game(folderId, "snes", "Zelda"), game(folderId, "md", "Sonic")),
                ),
            )
            ids = db.scanDao().gamesInFolder(folderId).associate { it.title to it.id }
            db.playDao().addSession(ids.getValue("Super Metroid"), endedAt = daysBack(3), seconds = 200 * 60L + 59)
            db.playDao().addSession(ids.getValue("Zelda"), endedAt = daysBack(1), seconds = 10 * 60L)
            db.gameDao().setFavorite(ids.getValue("Super Metroid"), true)
        }

    @After
    fun tearDown() = db.close()

    private fun game(
        folderId: Long,
        system: String,
        title: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = "root/$system/$title",
        parentDocumentId = "root/$system",
        relativePath = "$system/$title",
        fileName = title,
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = true,
    )

    private fun card(title: String) = SecondScreenContent.Game(title, "Super Nintendo", gameId = ids.getValue(title), isPlayKnown = false)

    @Test
    fun aPlayedGameGetsItsDaysAndWholeMinutes() =
        runBlocking {
            assertEquals(
                card("Super Metroid").copy(isPlayKnown = true, lastPlayedDaysAgo = 3, playTimeMinutes = 200),
                facts.fill(card("Super Metroid")),
            )
        }

    @Test
    fun aGameNeverPlayedIsKnownAsNeverPlayed() =
        runBlocking {
            assertEquals(card("Sonic").copy(isPlayKnown = true), facts.fill(card("Sonic")))
        }

    @Test
    fun aGameWithAnIdAndAKeyGetsItsFactsAndItsPictureInTheSameFill() =
        runBlocking {
            val front = com.plumemu.home.ArtPicture("/media/1/front.webp", 1240, 886)
            val details = com.plumemu.home.GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", players = "1", description = "")
            val result = com.plumemu.home.GameArt(grid = null, large = front, details = details)
            val both = SecondScreenPlayFacts(plays = { db.playDao() }, now = { now }, resultOf = { if (it == "crc:1") result else null })
            val asked = card("Super Metroid").copy(scrapeKey = "crc:1")
            assertEquals(asked.copy(isPlayKnown = true, lastPlayedDaysAgo = 3, playTimeMinutes = 200, art = front, details = details), both.fill(asked))
        }

    @Test
    fun theUsualBatteryPaceIsReadWithThePlayFactsAndOnlyForAGameWithOne() =
        runBlocking {
            val asked = mutableListOf<Long>()
            val withPace = SecondScreenPlayFacts(plays = { db.playDao() }, now = { now }, paceOf = { asked += it; if (it == ids.getValue("Zelda")) null else com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0) })
            assertEquals(com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0), (withPace.fill(card("Super Metroid")) as SecondScreenContent.Game).batteryPace)
            assertEquals("never measured: none", null, (withPace.fill(card("Zelda")) as SecondScreenContent.Game).batteryPace)
            assertEquals(listOf(ids.getValue("Super Metroid"), ids.getValue("Zelda")), asked)
            // No reader (the benchmark): none.
            assertEquals(null, (facts.fill(card("Super Metroid")) as SecondScreenContent.Game).batteryPace)
            // A card whose play is known is not read again.
            assertEquals(null, withPace.fill(card("Sonic").copy(isPlayKnown = true)))
            assertEquals(2, asked.size)
        }

    @Test
    fun theEmulatorPlayWouldStartIsReadWithThePlayFacts() =
        runBlocking {
            val asked = mutableListOf<Long>()
            val duck = com.plumemu.game.EmulatorToStart("DuckStation", isInstalled = true)
            val withEmulator = SecondScreenPlayFacts(plays = { db.playDao() }, now = { now }, emulatorOf = { asked += it; if (it == ids.getValue("Zelda")) null else duck })
            val metroid = withEmulator.fill(card("Super Metroid")) as SecondScreenContent.Game
            assertEquals(EmulatorFact(duck), metroid.emulator)
            assertEquals("none installed is known as none", EmulatorFact(null), (withEmulator.fill(card("Zelda")) as SecondScreenContent.Game).emulator)
            assertEquals(listOf(ids.getValue("Super Metroid"), ids.getValue("Zelda")), asked)
            assertNull("known play: nothing read again", withEmulator.fill(metroid))
        }

    /**
     * A game of several files: the library keeps one row per game, the file Play launches (library's
     * `DiscGrouping`: a `.cue` hides its `.bin`, an `.m3u` its discs, a disc set shows its first
     * disc; `SwitchGrouping`: the base, not its update or DLC; tested there). The hidden files get
     * no row of their own (the scan keeps them only in `DirectoryCatalogue.partOf`, and folds a row
     * an older scan made for one into the game's row), so the database has nothing else to read for
     * the game. Stored as the scan stores them, the card gets the game row's file, the very one
     * `GameDao.launchTarget` starts, without its extension.
     */
    @Test
    fun aMultiFileGameGetsTheLaunchedFilesNameWithoutItsExtension() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/more", displayName = "More", addedAt = 0))
            val launched =
                mapOf(
                    "Crash Bandicoot" to ("psx" to "Crash Bandicoot (USA).cue"),
                    "Final Fantasy VII" to ("psx" to "Final Fantasy VII (USA).m3u"),
                    "Metal Gear Solid" to ("psx" to "Metal Gear Solid (USA) (Disc 1).chd"),
                    "Star Rover" to ("switch" to "Star Rover [0100AAAA00010000].nsp"),
                )
            db.scanDao().applyBatch(
                ScanBatch(
                    folderId,
                    systems = listOf(SystemEntity("psx", "psx"), SystemEntity("switch", "switch")),
                    newGames = launched.map { (title, file) -> game(folderId, file.first, title).copy(fileName = file.second, documentId = "root/${file.first}/${file.second}") },
                ),
            )
            val games = db.scanDao().gamesInFolder(folderId).associate { it.title to it.id }
            val withFileName =
                SecondScreenPlayFacts(plays = { db.playDao() }, now = { now }, fileNameOf = { id -> db.gameDao().launchedFileName(id) })
            val shown =
                mapOf(
                    "Crash Bandicoot" to "Crash Bandicoot (USA)",
                    "Final Fantasy VII" to "Final Fantasy VII (USA)",
                    "Metal Gear Solid" to "Metal Gear Solid (USA) (Disc 1)",
                    "Star Rover" to "Star Rover [0100AAAA00010000]",
                )
            for ((title, line) in shown) {
                val id = games.getValue(title)
                val card = withFileName.fill(SecondScreenContent.Game(title, "PlayStation", gameId = id, isPlayKnown = false)) as SecondScreenContent.Game
                assertEquals("$title: the file Play launches", db.gameDao().launchTarget(id)?.fileName, card.fileName)
                assertEquals("$title: the row's file", launched.getValue(title).second, card.fileName)
                assertEquals("$title: the card's line", line, fileNameWithoutExtension(requireNotNull(card.fileName)))
            }
        }

    @Test
    fun noFileNameReaderOrAGameGoneFromTheLibraryGivesNoFileLine() =
        runBlocking {
            // The benchmark build has no reader; a game deleted by a rescan has no row.
            assertNull((facts.fill(card("Zelda")) as SecondScreenContent.Game).fileName)
            val gone = SecondScreenPlayFacts(plays = { db.playDao() }, now = { now }, fileNameOf = { id -> db.gameDao().launchedFileName(id) })
            assertNull((gone.fill(SecondScreenContent.Game("Gone", "Super Nintendo", gameId = 999_999, isPlayKnown = false)) as SecondScreenContent.Game).fileName)
        }

    @Test
    fun aGameOutsideTheDatabaseOrAlreadyKnownHasNothingToRead() =
        runBlocking {
            assertNull(facts.fill(SecondScreenContent.Game("Fake", "Super Nintendo", isPlayKnown = false)))
            assertNull(facts.fill(card("Zelda").copy(isPlayKnown = true)))
        }

    @Test
    fun aSystemGetsItsNewestPlay() =
        runBlocking {
            val snes = SecondScreenContent.System("Super Nintendo", 2, lastPlayFrom = LastPlayLookup.InSystem("snes"))
            assertEquals(snes.copy(lastGameTitle = "Zelda", lastPlayedDaysAgo = 1), facts.fill(snes))
        }

    @Test
    fun aSystemNeverPlayedHasNoLastGame() =
        runBlocking {
            assertNull(facts.fill(SecondScreenContent.System("Mega Drive", 1, lastPlayFrom = LastPlayLookup.InSystem("md"))))
        }

    @Test
    fun favoritesGetTheNewestFavouritePlay() =
        runBlocking {
            val favorites = SecondScreenContent.System("Favorites", 1, lastPlayFrom = LastPlayLookup.AmongFavorites)
            assertEquals(favorites.copy(lastGameTitle = "Super Metroid", lastPlayedDaysAgo = 3), facts.fill(favorites))
        }

    @Test
    fun recentGetsItsFirstGamesPlay() =
        runBlocking {
            val recent = SecondScreenContent.System("Recent", 2, lastPlayFrom = LastPlayLookup.FirstGame(ids.getValue("Zelda"), "Zelda"))
            assertEquals(recent.copy(lastGameTitle = "Zelda", lastPlayedDaysAgo = 1), facts.fill(recent))
        }

    @Test
    fun cardsWithoutPlayFactsHaveNothingToRead() =
        runBlocking {
            assertNull(facts.fill(SecondScreenContent.Apps(3)))
            assertNull(facts.fill(SecondScreenContent.System("Super Nintendo", 2)))
            assertNull(facts.fill(SecondScreenContent.Calm))
        }

    @Test(expected = IllegalStateException::class)
    fun aClosedDatabaseThrowsForTheFeedToLog() {
        db.close()
        runBlocking { facts.fill(card("Zelda")) }
    }
}
