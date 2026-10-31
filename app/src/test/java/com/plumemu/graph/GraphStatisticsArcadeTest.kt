package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.CoverImages
import com.plumemu.home.DetailsExtras
import com.plumemu.home.GameArt
import com.plumemu.home.HomeArtSource
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.library.naming.arcadeGameOf
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.StatisticsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Statistics on a large arcade folder: played sets are grouped by their game (library's
 * `arcadeGameOf`, the shipped arcade pack), and a played game none of whose read copies is shown
 * gets its shown set from the directory's shown rows, so the read must still fit the 10,000-game
 * budget of `GraphStatisticsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphStatisticsArcadeTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()

    private val art =
        object : HomeArtSource {
            override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flowOf(emptyMap())

            override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

            override val images: CoverImages = CoverImages.None
        }

    @After
    fun tearDown() = db.close()

    @Before
    fun needThePacks() = PrivateTestData.assumePacks()

    /** 10,000 real sets in one `arcade` directory (the pack's first ones, in short-name order: parents beside their clones), 300 played. */
    @Test
    fun aReadOfTenThousandArcadeSetsInOneFolderStaysUnderTheBudget() {
        assertEquals(0, readTenThousandSets("all shown", oneShownPerGame = false) { _, _ -> true })
    }

    /**
     * The same folder as the naming pass leaves it: a clone whose game's parent set is in the folder
     * is not shown, so a played clone's game is found through the shown rows of its directory.
     */
    @Test
    fun aReadWhereThePlayedClonesAreNotShownStaysUnderTheBudget() {
        val leftOut =
            readTenThousandSets("clones left out", oneShownPerGame = true) { name, game -> game == null || game == name || game !in this }
        assertTrue("no played set was left out of the lists: the shown-row pass was not exercised", leftOut > 0)
    }

    /**
     * Reads the figures, checks them against the oracle and their time, and gives the number of played
     * sets the lists leave out. With [oneShownPerGame] false (rows not grouped yet, as before the
     * naming pass ran), a game has several shown sets and which one stands for it may differ from the
     * oracle's; the game, its seconds and that the row is shown must not.
     */
    private fun readTenThousandSets(
        label: String,
        oneShownPerGame: Boolean,
        isShown: Set<String>.(name: String, game: String?) -> Boolean,
    ): Int =
        runBlocking {
            val naming = gameNamingFor(ApplicationProvider.getApplicationContext())
            val pack = checkNotNull(naming.packs.packFor(ArcadeSystems.PACK_ID)) { "the shipped arcade pack did not open" }
            val names = (0 until 10_000).map { pack.arcadeEntry(it).shortName }
            val inFolder = names.toSet()
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/arcade", displayName = "ROMs", addedAt = 0))
            val rows =
                names.map { name ->
                    GameEntity(
                        folderId = folderId, documentId = "root/arcade/$name.zip", parentDocumentId = "root/arcade",
                        relativePath = "arcade/$name.zip", fileName = "$name.zip", sizeBytes = 1, modifiedAt = 1, title = name,
                        sortName = name, regions = "", discGroup = null, discCount = 1, systemId = "arcade",
                        isPrimaryCopy = inFolder.isShown(name, arcadeGameOf(naming, "arcade", "$name.zip")),
                    )
                }
            db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("arcade", "Arcade")), newGames = rows))
            val stored = db.scanDao().gamesInFolder(folderId).sortedBy { it.fileName }
            val played = stored.filterIndexed { at, _ -> at % 33 == 0 }.take(300)
            played.forEachIndexed { at, game -> db.playDao().addSession(game.id, endedAt = at.toLong(), seconds = 60L * (at + 1)) }
            fun reader(arcadeGames: ArcadeGames) =
                GraphStatistics(
                    plays = { db.playDao() },
                    scraped = { db.scrapedGameDao() },
                    catalog = { LibraryHomeContentTest.catalog },
                    hasBoxArt = { false },
                    art = art,
                    io = Dispatchers.IO,
                    log = { },
                    arcadeGames = arcadeGames,
                )
            val read = reader(arcadeGamesOf { naming })
            LibraryHomeContentTest.catalog
            val first = timed { read.read() }
            val again = List(5) { timed { read.read() } }.sorted()
            val median = again[2]
            println("Statistics read on 10,000 arcade sets in one folder, $label: first ${first.ms} ms, median of five more ${median.ms} ms")
            val figures = (read.read() as StatisticsState.Read).statistics
            // Every played set is counted once under its game (a game with no shown row in the folder too).
            val games = played.map { arcadeGameOf(naming, "arcade", it.fileName) ?: it.fileName }
            assertEquals(games.toSet().size, figures.playedGames)
            // The oracle: every row of the directory read in full (as before the shortcut) and grouped with
            // every set looked up (no parent shortcut either). The read must find the same games, each with
            // the same shown row and seconds, and its figures must be the oracle's.
            val full = arcadeGamesOf { naming }
            val everySet =
                object : ArcadeGames {
                    override fun gameOf(systemId: String, fileName: String) = full.gameOf(systemId, fileName)

                    override fun parentOf(systemId: String, fileName: String) = full.gameOf(systemId, fileName)
                }
            val expected = playedGamesOf(db.playDao().playedCopiesOf(stored.map { it.id }), everySet)
            val actual = playedGamesOf(db.playDao(), listOf("arcade"), arcadeGamesOf { naming })
            fun nameOf(fileName: String) = if (oneShownPerGame) fileName else arcadeGameOf(naming, "arcade", fileName) ?: fileName
            fun playedOf(games: List<PlayedGame>) =
                games.filter { it.seconds > 0 }.map { "${it.shown?.fileName?.let(::nameOf)}:${it.shown?.isPrimaryCopy}:${it.seconds}" }.sorted()
            assertEquals(playedOf(expected), playedOf(actual))
            val listed = mostPlayedOf(expected)
            val fileOf = stored.associate { it.id to it.fileName }
            assertEquals(listed.size, figures.playedGames)
            assertEquals(
                listed.take(PlayStatistics.TOP_GAMES).map { nameOf(it.shown!!.fileName) to it.seconds },
                figures.topGames.map { nameOf(fileOf.getValue(it.id)) to it.seconds },
            )
            assertEquals(expected.any { it.isPlayed }, figures.hasPlayed)
            assertEquals(listed.sumOf { it.seconds }, figures.topSystem?.seconds ?: 0L)
            org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
            assertTrue("first read took ${first.ms} ms", first < BUDGET_NANOS)
            assertTrue("median read took ${median.ms} ms", median < BUDGET_NANOS)
            played.count { !it.isPrimaryCopy }
        }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private val Long.ms: String get() = "%.1f".format(this / 1_000_000.0)

    private companion object {
        const val BUDGET_NANOS = 100_000_000L
    }
}
