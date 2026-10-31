package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.DetailsExtras
import com.plumemu.home.GameArt
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeArtSource
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.TopSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Statistics' read on an in-memory library: every figure, the covers through the
 * home screen's results, no box art at all, a failed read, and the time on 10,000 games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphStatisticsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val logged = mutableListOf<String>()
    private var signedIn = true
    private val asked = mutableListOf<List<String>>()

    /** The home screen's results: a saved box front for every key except those of games named "No art". */
    private val art =
        object : HomeArtSource {
            override fun observe(keys: List<String>): Flow<Map<String, GameArt>> {
                asked += keys
                return flowOf(keys.filter { it !in keysWithoutArt }.associateWith { GameArt(ArtPicture("/art/$it.png", 300, 420), null, GameDetails(null, "", "", "", "")) })
            }

            override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

            override val images: CoverImages = CoverImages.None
        }
    private val keysWithoutArt = mutableSetOf<String>()

    private var clock = 0L

    private fun reader(
        catalog: Boolean = true,
        arcadeGames: ArcadeGames = ArcadeGames.None,
    ) =
        GraphStatistics(
            plays = { db.playDao() },
            scraped = { db.scrapedGameDao() },
            catalog = { if (catalog) LibraryHomeContentTest.catalog else null },
            hasBoxArt = { signedIn },
            art = art,
            io = Dispatchers.IO,
            log = { logged += it },
            now = { clock },
            zone = { zone },
            locale = { locale },
            arcadeSystems = listOf("arcade"),
            arcadeGames = arcadeGames,
        )

    private var zone = TimeZone.getTimeZone("UTC")
    private var locale = Locale.US

    @After
    fun tearDown() = db.close()

    /** [count] games, the system of each picked by [systemOf]; returns their ids in order. */
    private suspend fun library(
        count: Int,
        systemOf: (Int) -> String = { "snes" },
    ): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf(SystemEntity("snes", "Super Famicom (stored)"), SystemEntity("gba", "gba"), SystemEntity("md", "md")),
                newGames = List(count) { game(folderId, systemOf(it), "Game %05d".format(it)) },
            ),
        )
        return db.scanDao().gamesInFolder(folderId).sortedBy { it.title }.map { it.id }
    }

    /** A saved box front for every game whose position passes [has]. */
    private fun boxFronts(
        ids: List<Long>,
        has: (Int) -> Boolean,
    ) {
        val sql = db.openHelper.writableDatabase
        ids.forEachIndexed { at, id ->
            if (has(at)) {
                sql.execSQL(
                    "INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, title, pictures, has_box_front) VALUES (?, ?, 'MATCHED', 1, 't', '', 1)",
                    arrayOf<Any>("game:$id", id),
                )
            }
        }
    }

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

    @Test
    fun everyFigureComesFromThePlayHistoryTheLibraryAndTheStoredBoxArt() =
        runBlocking {
            val ids = library(10) { if (it < 6) "snes" else "gba" }
            boxFronts(ids) { it % 2 == 0 }
            db.playDao().addSession(ids[0], endedAt = 1, seconds = 3_600)
            db.playDao().addSession(ids[6], endedAt = 1, seconds = 5_000)
            db.playDao().addSession(ids[7], endedAt = 1, seconds = 600)
            db.playDao().addSession(ids[1], endedAt = 1, seconds = 3_000)
            keysWithoutArt += "game:${ids[7]}"
            val state = reader().read() as StatisticsState.Read
            val figures = state.statistics
            assertEquals(12_200L, figures.totalSeconds)
            assertEquals(listOf("Game 00006", "Game 00000", "Game 00001"), figures.topGames.map { it.title })
            assertEquals(listOf(5_000L, 3_600L, 3_000L), figures.topGames.map { it.seconds })
            val gba = LibraryHomeContentTest.catalog.system("gba")?.displayName
            assertEquals("the name the main screen shows", gba, figures.topGames[0].systemName)
            assertEquals("the home grid's box front", ArtPicture("/art/game:${ids[6]}.png", 300, 420), figures.topGames[0].cover)
            assertEquals("snes: 6,600 s beats gba's 5,600", "snes", figures.topSystem?.id)
            assertEquals(6_600L, figures.topSystem?.seconds)
            assertEquals(4, figures.playedGames)
            assertEquals(10, figures.libraryGames)
            assertEquals(5, figures.gamesWithBoxArt)
            assertTrue(figures.hasBoxArt)
            assertTrue(figures.hasPlayed)
            assertEquals("one read of the three games' results", listOf(listOf("game:${ids[6]}", "game:${ids[0]}", "game:${ids[1]}")), asked)
        }

    @Test
    fun aGameWithoutAResultHasNoCoverAndSystemsWithoutPlatformDataKeepTheirStoredName() =
        runBlocking {
            val ids = library(3)
            db.playDao().addSession(ids[2], endedAt = 1, seconds = 120)
            keysWithoutArt += "game:${ids[2]}"
            val figures = (reader(catalog = false).read() as StatisticsState.Read).statistics
            assertNull(figures.topGames.single().cover)
            assertTrue("the slot stays: box art is set up", figures.hasBoxArt)
            assertEquals(TopSystem("snes", "Super Famicom (stored)", 120), figures.topSystem)
            assertEquals("the game names its system the same way", "Super Famicom (stored)", figures.topGames.single().systemName)
        }

    @Test
    fun withoutBoxArtSetUpNoResultIsReadAndCoverageIsZero() =
        runBlocking {
            signedIn = false
            val ids = library(4)
            // Saved from an earlier sign-in: still not counted, the line is not shown.
            boxFronts(ids) { true }
            db.playDao().addSession(ids[0], endedAt = 1, seconds = 120)
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertFalse(figures.hasBoxArt)
            assertEquals(emptyList<List<String>>(), asked)
            assertEquals(0, figures.gamesWithBoxArt)
            assertEquals(4, figures.libraryGames)
        }

    @Test
    fun nothingPlayedReadsAsAnEmptyHistoryWithTheLibraryCounted() =
        runBlocking {
            val ids = library(5)
            boxFronts(ids) { it < 2 }
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(PlayStatistics(libraryGames = 5, gamesWithBoxArt = 2, boxArtGames = 5, hasBoxArt = true), figures)
            assertFalse(figures.hasPlayed)
            assertEquals("no game, no result asked for", emptyList<List<String>>(), asked)
        }

    @Test
    fun aPinnedShortcutCountsInTheLibraryAndOnlyASteamOneInTheBoxArtCoverage() =
        runBlocking {
            val ids = library(5)
            boxFronts(ids) { true }
            // Steam shortcuts are asked by name like .steam files; other shortcuts are not asked.
            db.pinnedShortcutDao().add(NewPinnedShortcut("app.gamenative", "game_1", 0, "Broforce", "broforce", "steam", "Steam", addedAt = 1))
            db.pinnedShortcutDao().add(NewPinnedShortcut("com.winlator", "game_2", 0, "Celeste", "celeste", "windows", "Windows", addedAt = 1))
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(7, figures.libraryGames)
            assertEquals("the steam shortcut waits for its art; the windows one is not counted", listOf(5, 6), listOf(figures.gamesWithBoxArt, figures.boxArtGames))
        }

    /** The regional copies of one game in one directory, the first one the copy the lists show. */
    private suspend fun copies(vararg fileNames: String): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/copies", displayName = "ROMs", addedAt = 0))
        val rows =
            fileNames.mapIndexed { at, file ->
                game(folderId, "snes", file).copy(fileName = file, title = "Zelda", sortName = "zelda", fileSortName = "zelda", isPrimaryCopy = at == 0)
            }
        db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = rows))
        val byFile = db.scanDao().gamesInFolder(folderId).associate { it.fileName to it.id }
        return fileNames.map { byFile.getValue(it) }
    }

    @Test
    fun aGameCountsByItsCopiesSoPlayStaysWhenAnotherCopyBecomesTheOneShown() =
        runBlocking {
            val (usa, europe, beta) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc", "Zelda (USA) (Beta).sfc")
            // The Beta is another variant: its own game, so the cataloguer made it a primary too.
            db.openHelper.writableDatabase.execSQL("UPDATE game SET is_primary_copy = 1 WHERE id = ?", arrayOf<Any>(beta))
            db.playDao().addSession(usa, endedAt = 1, seconds = 600)
            db.playDao().addSession(europe, endedAt = 2, seconds = 120)
            db.playDao().addSession(beta, endedAt = 3, seconds = 61)
            val before = (reader().read() as StatisticsState.Read).statistics
            assertEquals("both copies' time under the one shown", listOf(usa to 720L, beta to 61L), before.topGames.map { it.id to it.seconds })
            assertEquals(2, before.playedGames)
            // A rescan (another preferred region) makes the European copy the one the lists show.
            db.openHelper.writableDatabase.execSQL("UPDATE game SET is_primary_copy = (id = ?) WHERE id IN (?, ?)", arrayOf<Any>(europe, usa, europe))
            val after = (reader().read() as StatisticsState.Read).statistics
            assertEquals("the time stays, under the copy now shown", listOf(europe to 720L, beta to 61L), after.topGames.map { it.id to it.seconds })
            assertEquals(2, after.playedGames)
            assertEquals(781L, after.topSystem?.seconds)
        }

    @Test
    fun timeOnACopyKeptInASiblingGameFolderCountsUnderTheShownCopy() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/folders", displayName = "ROMs", addedAt = 0))
            // One game per folder (audit item 18): the scan shows the USA copy of each disc and hides the European one.
            fun row(name: String, shown: Boolean) =
                game(folderId, "psx", name).copy(
                    documentId = "root/psx/$name/$name.cue",
                    parentDocumentId = "root/psx/$name",
                    relativePath = "psx/$name/$name.cue",
                    fileName = "$name.cue",
                    title = "Final Fantasy VII",
                    sortName = "final fantasy vii",
                    fileSortName = "final fantasy vii",
                    isPrimaryCopy = shown,
                )
            val names = listOf("Final Fantasy VII (USA) (Disc 1)", "Final Fantasy VII (Europe) (Disc 1)", "Final Fantasy VII (USA) (Disc 2)")
            db.scanDao().applyBatch(
                ScanBatch(folderId, systems = listOf(SystemEntity("psx", "psx")), newGames = names.map { row(it, shown = "Europe" !in it) }),
            )
            val byFile = db.scanDao().gamesInFolder(folderId).associate { it.fileName to it.id }
            val (usaDisc1, europeDisc1, usaDisc2) = names.map { byFile.getValue("$it.cue") }
            db.playDao().addSession(europeDisc1, endedAt = 1, seconds = 5 * 3600)
            db.playDao().addSession(usaDisc2, endedAt = 2, seconds = 600)
            val read = (reader().read() as StatisticsState.Read).statistics
            assertEquals(
                "the hidden European disc's time is under the USA disc shown; the other disc stays its own game",
                listOf(usaDisc1 to 5 * 3600L, usaDisc2 to 600L),
                read.topGames.map { it.id to it.seconds },
            )
            assertEquals(2, read.playedGames)
        }

    @Test
    fun timesPlayedAddsTheCopiesAndLastPlayedIsTheNewestDayInTheDeviceZone() =
        runBlocking {
            val (usa, europe) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc")
            zone = TimeZone.getTimeZone("Asia/Tokyo")
            clock = at("2026-10-21T12:00:00Z")
            // 23:30 UTC the day before is already today in Tokyo (08:30); the other copy's play is 3 days back.
            db.playDao().addSession(usa, endedAt = at("2026-10-18T10:00:00Z"), seconds = 600)
            db.playDao().addSession(europe, endedAt = at("2026-10-20T23:30:00Z"), seconds = 600)
            val game = (reader().read() as StatisticsState.Read).statistics.topGames.single()
            assertEquals(2, game.timesPlayed)
            assertEquals("08:30 the same Tokyo day as now (in UTC it would be yesterday)", 0, game.lastPlayedDaysAgo)
        }

    /** Arcade sets of one game have other file names (`dino.zip`, `dinou.zip`): they count by the game library's `arcadeGameOf` names. */
    @Test
    fun arcadeSetsOfOneGameCountTogetherUnderTheShownSet() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/arcade", displayName = "ROMs", addedAt = 0))
            fun set(name: String, shown: Boolean) =
                game(folderId, "arcade", name).copy(fileName = "$name.zip", title = "Cadillacs and Dinosaurs", fileSortName = name, isPrimaryCopy = shown)
            val rows = listOf(set("dinou", shown = true), set("dino", shown = false), set("pang", shown = true))
            db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("arcade", "Arcade")), newGames = rows))
            val byFile = db.scanDao().gamesInFolder(folderId).associate { it.fileName to it.id }
            db.playDao().addSession(byFile.getValue("dino.zip"), endedAt = 1, seconds = 600)
            db.playDao().addSession(byFile.getValue("dinou.zip"), endedAt = 2, seconds = 120)
            db.playDao().addSession(byFile.getValue("pang.zip"), endedAt = 3, seconds = 90)
            val games =
                object : ArcadeGames {
                    override fun gameOf(systemId: String, fileName: String) = if (fileName.startsWith("dino")) "dino" else fileName.substringBefore('.')

                    override fun parentOf(systemId: String, fileName: String) = if (fileName == "dinou.zip") "dino" else null
                }
            val read = (reader(arcadeGames = games).read() as StatisticsState.Read).statistics
            assertEquals(
                "both sets' time under the one shown",
                listOf(byFile.getValue("dinou.zip") to 720L, byFile.getValue("pang.zip") to 90L),
                read.topGames.map { it.id to it.seconds },
            )
            assertEquals(2, read.playedGames)
            // Without the arcade grouping the left-out set's time would not count in the list.
            val apart = (reader().read() as StatisticsState.Read).statistics
            assertEquals(listOf(byFile.getValue("dinou.zip") to 120L, byFile.getValue("pang.zip") to 90L), apart.topGames.map { it.id to it.seconds })
        }

    @Test
    fun gamesOpenedAndQuitAtOnceAreNotPlayedAnywhereOnThePage() =
        runBlocking {
            val ids = library(412)
            db.playDao().addSession(ids[0], endedAt = 1, seconds = 20)
            db.playDao().addSession(ids[1], endedAt = 2, seconds = 15)
            db.playDao().addSession(ids[1], endedAt = 3, seconds = 15)
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertFalse("under a minute each: nothing played yet", figures.hasPlayed)
            assertEquals("never \"Nothing played yet\" above \"Games played 2 of 412\"", 0, figures.playedGames)
            assertEquals(emptyList<Any>(), figures.topGames)
            assertNull(figures.topSystem)
            assertEquals(50L, figures.totalSeconds)
            // A minute over the copies counts.
            db.playDao().addSession(ids[1], endedAt = 4, seconds = 30)
            val now = (reader().read() as StatisticsState.Read).statistics
            assertTrue(now.hasPlayed)
            assertEquals(1, now.playedGames)
            assertEquals(listOf(ids[1]), now.topGames.map { it.id })
        }

    @Test
    fun aDatabaseThatCannotBeReadComesBackFailedAndIsLoggedByClassOnly() =
        runBlocking {
            library(2)
            db.close()
            assertEquals(StatisticsState.Failed, reader().read())
            assertEquals(1, logged.size)
            assertTrue(logged.single(), logged.single().startsWith("Settings: statistics could not be read ("))
        }

    /**
     * The budget: under 100 ms on 10,000 games, 300 of them
     * played, 7,000 with box art, on the JVM. Prints the first read and the median of five more.
     */
    @Test
    fun aReadOfTenThousandGamesStaysUnderItsBudget() =
        runBlocking {
            val ids = library(10_000) { listOf("snes", "gba", "md")[it % 3] }
            boxFronts(ids) { it % 10 < 7 }
            ids.take(300).forEachIndexed { at, id -> db.playDao().addSession(id, endedAt = at.toLong(), seconds = 60L * (at + 1)) }
            // A thousand recent sessions: "this week" reads them in the same pass.
            clock = 10 * DAY
            repeat(1_000) { db.playDao().addSession(ids[it], endedAt = clock - it * 60_000L, seconds = 30L) }
            val read = reader()
            // The platform data is loaded at startup in the app; here its first load is the fixture's cost, not the read's.
            LibraryHomeContentTest.catalog
            val first = timed { read.read() }
            val again = List(5) { timed { read.read() } }.sorted()
            val median = again[2]
            println("Statistics read on 10,000 games: first ${first.ms} ms, median of five more ${median.ms} ms")
            val figures = (read.read() as StatisticsState.Read).statistics
            assertEquals(10_000, figures.libraryGames)
            assertEquals(7_000, figures.gamesWithBoxArt)
            assertEquals(300, figures.playedGames)
            org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
            assertTrue("first read took ${first.ms} ms", first < BUDGET_NANOS)
            assertTrue("median read took ${median.ms} ms", median < BUDGET_NANOS)
        }

    private fun at(
        day: String,
        zoneId: String = "UTC",
    ): Long = java.time.ZonedDateTime.parse("${day}[$zoneId]").toInstant().toEpochMilli()

    @Test
    fun theWeekStartsOnTheLocalesFirstDayAtMidnightInTheDeviceTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val wednesday = at("2026-10-21T15:30:00Z")
        assertEquals("en-US: Sunday", at("2026-10-18T00:00:00Z"), weekStartOf(wednesday, utc, Locale.US))
        assertEquals("en-GB: Monday", at("2026-10-19T00:00:00Z"), weekStartOf(wednesday, utc, Locale.UK))
        assertEquals("de: Monday", at("2026-10-19T00:00:00Z"), weekStartOf(wednesday, utc, Locale.GERMANY))
        // The boundary: Sunday 00:00 starts a US week, but still ends a UK one.
        val sunday = at("2026-10-18T00:00:00Z")
        assertEquals(sunday, weekStartOf(sunday, utc, Locale.US))
        assertEquals(sunday - 6 * DAY, weekStartOf(sunday, utc, Locale.UK))
        assertEquals("a millisecond before", sunday - 7 * DAY, weekStartOf(sunday - 1, utc, Locale.US))
        // The device's zone decides the midnight: 23:30 UTC Sunday is already Monday in Tokyo.
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        assertEquals(at("2026-10-19T00:00:00+09:00", "Asia/Tokyo"), weekStartOf(at("2026-10-18T23:30:00Z"), tokyo, Locale.UK))
    }

    @Test
    fun thisWeekCountsFromTheWeekStartAndASessionAcrossItCountsOnlyItsPart() =
        runBlocking {
            val ids = library(2)
            clock = at("2026-10-21T12:00:00Z")
            val weekStart = at("2026-10-18T00:00:00Z")
            // 30 minutes, 10 of them after the (US) week start: a third of 1,800 s.
            db.playDao().addSession(ids[0], endedAt = weekStart + 10 * 60_000L, seconds = 1_800, startedAt = weekStart - 20 * 60_000L)
            db.playDao().addSession(ids[1], endedAt = clock, seconds = 600)
            val us = (reader().read() as StatisticsState.Read).statistics
            assertEquals(600L + 600L, us.thisWeekSeconds)
            assertTrue(us.hasSessions)
            locale = Locale.UK
            val uk = (reader().read() as StatisticsState.Read).statistics
            assertEquals("Monday 19th: only the second session", 600L, uk.thisWeekSeconds)
        }

    @Test
    fun sessionsOutsideTheWeekGiveZeroButStillCountAsSessions() =
        runBlocking {
            val ids = library(1)
            clock = at("2026-10-21T12:00:00Z")
            db.playDao().addSession(ids[0], endedAt = at("2026-10-10T12:00:00Z"), seconds = 3_600)
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(0L, figures.thisWeekSeconds)
            assertTrue(figures.hasSessions)
        }

    @Test
    fun noSessionsMeansNoWeekFigureAtAll() =
        runBlocking {
            library(1)
            clock = at("2026-10-21T12:00:00Z")
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertFalse(figures.hasSessions)
            assertEquals(0L, figures.thisWeekSeconds)
        }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private val Long.ms: String get() = "%.1f".format(this / 1_000_000.0)

    private companion object {
        const val BUDGET_NANOS = 100_000_000L
        const val DAY = 86_400_000L
    }
}
