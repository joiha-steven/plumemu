package com.plumemu.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.home.EntryKind
import com.plumemu.home.HomeContent
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The main screen's content from a real (in-memory) library database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LibraryHomeContentTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val apps = MutableStateFlow(listOf(LaunchableApp("Browser", "org.browser", "org.browser.Main")))
    private val showApps = MutableStateFlow(true)
    private var folderId = 0L

    private val source = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default)

    @Before
    fun setUp() {
        runBlocking { seed() }
    }

    private suspend fun seed() {
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        // The scan stores system ids as names; the screen must show the platform data's names.
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf("snes", "3do", "gba", "nes").map { SystemEntity(it, it) },
                newGames = listOf(
                    game("snes/zelda.sfc", "snes", "Zelda", "USA,EUR"),
                    game("snes/actraiser.sfc", "snes", "ActRaiser", "JPN"),
                    game("snes/mario-eur.sfc", "snes", "Mario", "EUR", isPrimaryCopy = false),
                    game("snes/mario.sfc", "snes", "Mario", "USA"),
                    game("3do/gex.iso", "3do", "Gex", ""),
                    game("gba/metroid.gba", "gba", "Metroid Fusion", "USA"),
                ),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    /** the apps listed as games leave the Apps entry (grid and list share its rows); null (no sync yet) shows all; unmarking brings one back. */
    @Test
    fun appsListedAsGamesLeaveTheAppsEntryAndComeBack() = runBlocking {
        val two = MutableStateFlow(listOf(LaunchableApp("Browser", "org.browser", "org.browser.Main"), LaunchableApp("Celeste", "com.celeste", "com.celeste.Main")))
        val marked = MutableStateFlow<Map<String, String>?>(null)
        val withMarks = LibraryHomeContent(db.gameDao(), { catalog }, two, showApps, Dispatchers.Default, appGames = marked)
        fun HomeContent.appTitles() = entries.first { it.kind == EntryKind.Apps }.rows.map { it.title }

        assertEquals("before the first sync: all", listOf("Browser", "Celeste"), withTimeout(TIMEOUT_MS) { withMarks.content.first() }.appTitles())
        marked.value = mapOf("com.celeste" to "android")
        assertEquals(listOf("Browser"), withTimeout(TIMEOUT_MS) { withMarks.content.first { c -> c.appTitles().size == 1 } }.appTitles())
        marked.value = emptyMap()
        assertEquals("Not a game: it is back", listOf("Browser", "Celeste"), withTimeout(TIMEOUT_MS) { withMarks.content.first { c -> c.appTitles().size == 2 } }.appTitles())
    }

    /** the grid, the details column and the years only while box art is set up; rows carry their scrape key. */
    @Test
    fun boxArtFollowsTheAccountAndRowsCarryTheirScrapeKey() = runBlocking {
        val hasBoxArt = MutableStateFlow(false)
        val withArt = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, hasBoxArt = hasBoxArt)
        val without = withTimeout(TIMEOUT_MS) { withArt.content.first { it.systemCount == 3 } }
        assertEquals("no account: no box art", false, without.hasBoxArt)
        hasBoxArt.value = true
        val with = withTimeout(TIMEOUT_MS) { withArt.content.first { it.hasBoxArt } }
        val zelda = with.entries.first { it.id == "snes" }.rows.first { it.title == "Zelda" }
        // Not yet named (no fix keys): the row key, as ScrapedGameDao.SCRAPE_KEY gives it.
        assertEquals("game:${zelda.id}", zelda.scrapeKey)
        assertTrue("apps have no scrape key", with.entries.first { it.kind == EntryKind.Apps }.rows.all { it.scrapeKey == null })
    }

    /** Recent and Favorites mix systems, so their rows carry the system's short name; a system's own rows still show region/year. */
    @Test
    fun recentAndFavoritesRowsCarryTheirSystemsShortName() = runBlocking {
        val first = withTimeout(TIMEOUT_MS) { source.content.first { it.systemCount == 3 } }
        val zelda = first.entries.first { it.id == "snes" }.rows.first { it.title == "Zelda" }
        val metroid = first.entries.first { it.id == "gba" }.rows.first()
        db.playDao().addSession(zelda.id.toLong(), endedAt = 100, seconds = 30)
        db.playDao().addSession(metroid.id.toLong(), endedAt = 200, seconds = 30)
        db.gameDao().setFavorite(zelda.id.toLong(), true)
        val content = withTimeout(TIMEOUT_MS) { source.content.first { c -> c.entries.first { it.kind == EntryKind.Recent }.rows.size == 2 && c.entries.first { it.kind == EntryKind.Favorites }.rows.size == 1 } }
        val recent = content.entries.first { it.kind == EntryKind.Recent }
        assertEquals(listOf("GBA", "SNES"), recent.rows.map { it.systemAbbr })
        // Their grid draws each game in its own system's cover shape.
        assertEquals(listOf("gba", "snes"), recent.rows.map { it.systemId })
        assertEquals(recent.rows.map { r -> content.entries.first { it.id == r.systemId }.coverShape }, recent.rows.map(recent::shapeOf))
        assertEquals(listOf("GBA", "SNES"), recent.rows.map { it.trailing(hasBoxArt = false, showsSystem = recent.kind.mixesSystems) })
        val favorites = content.entries.first { it.kind == EntryKind.Favorites }
        assertEquals(listOf("SNES"), favorites.rows.map { it.trailing(hasBoxArt = false, showsSystem = favorites.kind.mixesSystems) })
        val snes = content.entries.first { it.id == "snes" }
        assertEquals("a system list keeps the region", "USA", snes.rows.first { it.title == "Zelda" }.trailing(hasBoxArt = false, showsSystem = snes.kind.mixesSystems))
        assertTrue("no abbreviation in Apps", content.entries.first { it.kind == EntryKind.Apps }.rows.all { it.systemAbbr.isEmpty() })
    }

    /** The letter rail's letters follow the stored sort name the list is ordered by: "The Legend of Zelda" sorts as L. */
    @Test
    fun rowsCarryTheSortNameSoTheRailFilesTheLegendUnderL() = runBlocking {
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                listOf(SystemEntity("nes", "nes")),
                listOf(
                    game("nes/lemmings.nes", "nes", "Lemmings", ""),
                    game("nes/zelda.nes", "nes", "The Legend of Zelda", "", sortName = "legend of zelda"),
                    game("nes/lost.nes", "nes", "Lost Vikings", ""),
                    game("nes/kart.nes", "nes", "Mario Kart", ""),
                    game("nes/tetris.nes", "nes", "Tetris", ""),
                ),
            ),
        )
        val nes = withTimeout(TIMEOUT_MS) { source.content.first { c -> c.entries.any { it.id == "nes" } } }.entries.first { it.id == "nes" }
        assertEquals(listOf("The Legend of Zelda", "Lemmings", "Lost Vikings", "Mario Kart", "Tetris"), nes.rows.map { it.title })
        assertEquals(listOf('L', 'M', 'T'), nes.railLetters)
    }

    /** Settings › Appearance › Left edge: each system carries the platform data's code, and the setting reaches the content (default Game counts). */
    @Test
    fun systemsCarryTheirCodeAndTheLeftEdgeSettingReachesTheContent() = runBlocking {
        val first = withTimeout(TIMEOUT_MS) { source.content.first { it.systemCount == 3 } }
        assertEquals(LeftEdgeSetting.Counts, first.leftEdge)
        assertEquals(listOf("GBA", "SNS"), listOf("gba", "snes").map { id -> first.entries.first { it.id == id }.code }.sorted())
        val edge = MutableStateFlow<LeftEdgeSetting?>(null)
        val withEdge = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, leftEdge = edge)
        assertEquals("not picked is Game counts", LeftEdgeSetting.Counts, withTimeout(TIMEOUT_MS) { withEdge.content.first { it.entries.any { e -> e.id == "snes" } } }.leftEdge)
        edge.value = LeftEdgeSetting.Codes
        assertEquals(LeftEdgeSetting.Codes, withTimeout(TIMEOUT_MS) { withEdge.content.first { it.leftEdge == LeftEdgeSetting.Codes } }.leftEdge)
        edge.value = LeftEdgeSetting.Letters
        assertEquals(LeftEdgeSetting.Letters, withTimeout(TIMEOUT_MS) { withEdge.content.first { it.leftEdge == LeftEdgeSetting.Letters } }.leftEdge)
    }

    /** Each system's cover shape comes from the platform data, then from the person's own covers. */
    @Test
    fun eachSystemGetsItsCoverShapeFromThePlatformDataAndThenFromItsCovers() = runBlocking {
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("nds", "nds")), listOf(game("nds/zelda.nds", "nds", "Phantom Hourglass", "USA"))))
        val measured = MutableStateFlow(emptyMap<String, Float>())
        val hasBoxArt = MutableStateFlow(true)
        val source = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, hasBoxArt = hasBoxArt, coverRatios = measured)
        fun HomeContent.shapes() = entries.filter { it.kind == EntryKind.System }.associate { it.id to it.coverShape }
        val fromData = withTimeout(TIMEOUT_MS) { source.content.first { it.systemCount == 4 } }.shapes()
        // Measured US boxes: SNES 1.37 (landscape), DS 1.12 (square), GBA 1.0; 3DO not measured: portrait's 0.71.
        assertEquals(mapOf("snes" to 1.37f, "nds" to 1.12f, "gba" to 1f, "3do" to 0.71f), fromData.mapValues { it.value.widthOverHeight })
        assertEquals(listOf(CoverClass.Landscape, CoverClass.Square, CoverClass.Tall), listOf("snes", "nds", "3do").map { fromData.getValue(it).coverClass })
        // The person's Japanese SNES boxes are tall: their median wins; DS keeps the data's.
        measured.value = mapOf("snes" to 0.72f)
        val fromCovers = withTimeout(TIMEOUT_MS) { source.content.first { it.shapes()["snes"]?.widthOverHeight == 0.72f } }.shapes()
        assertEquals(CoverClass.Tall, fromCovers.getValue("snes").coverClass)
        assertEquals(1.12f, fromCovers.getValue("nds").widthOverHeight)
        // Without box art there is no grid: the covers' median is not read.
        hasBoxArt.value = false
        val noArt = withTimeout(TIMEOUT_MS) { source.content.first { !it.hasBoxArt } }.shapes()
        assertEquals(1.37f, noArt.getValue("snes").widthOverHeight)
    }

    @Test
    fun coverShapesThatCannotBeReadKeepThePlatformDataSayWhyAndAreReadAgain() = runBlocking {
        val logged = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val shownFromData = CompletableDeferred<Unit>()
        // The first read fails (the database was busy, say); the next one gives the covers' median,
        // held until the screen has shown the platform data's shape.
        val flaky =
            flow {
                if (reads.incrementAndGet() == 1) throw IllegalStateException("closed")
                shownFromData.await()
                emit(mapOf("snes" to 0.72f))
            }
        val source =
            LibraryHomeContent(
                db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, log = { logged += it },
                hasBoxArt = MutableStateFlow(true), coverRatios = flaky, coverRetryMillis = 50,
            )
        val seen = Channel<HomeContent>(Channel.UNLIMITED)
        val watching = launch { source.content.collect { seen.send(it) } }
        fun HomeContent.snes() = entries.singleOrNull { it.id == "snes" }?.coverShape?.widthOverHeight
        val first = withTimeout(TIMEOUT_MS) {
            var content = seen.receive()
            while (content.snes() == null) content = seen.receive()
            content
        }
        assertEquals("the platform data's shape while the covers cannot be read", 1.37f, first.snes())
        shownFromData.complete(Unit)
        val later = withTimeout(TIMEOUT_MS) {
            var content = seen.receive()
            while (content.snes() != 0.72f) content = seen.receive()
            content
        }
        assertEquals(0.72f, later.snes())
        assertEquals(listOf("Cover shapes could not be read (IllegalStateException)"), logged.toList())
        assertEquals(2, reads.get())
        watching.cancel()
    }

    @Test
    fun systemsWithGamesTheirCountsAndNames() = runBlocking {
        val content = first { it.systemCount == 3 }

        val systems = content.entries.filter { it.kind == EntryKind.System }
        // A to Z by the platform data's `displayName` (System order's default;
        // before, the data's `order`); NES has no games and is not shown.
        assertEquals(listOf("3do", "gba", "snes"), systems.map { it.id })
        assertEquals(listOf("3DO", "Game Boy Advance", "Super Nintendo"), systems.map { it.name })
        assertEquals(listOf(1, 1, 3), systems.map { it.count })
        assertEquals(5, content.gameCount)
        val snes = systems.single { it.id == "snes" }
        assertEquals("by sort name, one copy per regional group", listOf("ActRaiser", "Mario", "Zelda"), snes.rows.map { it.title })
        assertEquals("the first region stands in for the year", listOf("JPN", "USA", "USA"), snes.rows.map { it.region })
        assertEquals(listOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID), content.entries.take(3).map { it.id })
    }

    @Test
    fun recentIsNewestFirstAndFavoritesFollowTheMarks() = runBlocking {
        val ids = gameIdsByTitle()
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 1_000, seconds = 60)
        db.playDao().addSession(ids.getValue("Gex"), endedAt = 3_000, seconds = 60)
        db.playDao().addSession(ids.getValue("Metroid Fusion"), endedAt = 2_000, seconds = 60)
        db.gameDao().setFavorite(ids.getValue("Mario"), true)
        db.gameDao().setFavorite(ids.getValue("ActRaiser"), true)

        val content = first { it.entry(HomeContent.RECENT_ID).count == 3 && it.entry(HomeContent.FAVORITES_ID).count == 2 }

        assertEquals(listOf("Gex", "Metroid Fusion", "Zelda"), content.entry(HomeContent.RECENT_ID).rows.map { it.title })
        assertEquals(listOf("ActRaiser", "Mario"), content.entry(HomeContent.FAVORITES_ID).rows.map { it.title })
        assertEquals("Recent and Favorites repeat games and do not count", 5, content.gameCount)

        db.gameDao().setFavorite(ids.getValue("Mario"), false)
        assertEquals(listOf("ActRaiser"), first { it.entry(HomeContent.FAVORITES_ID).count == 1 }.entry(HomeContent.FAVORITES_ID).rows.map { it.title })
    }

    @Test
    fun aFinishedPlayMovesTheGameToTheTopOfRecentAndARescanKeepsIt() = runBlocking {
        val ids = gameIdsByTitle()
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 1_000, seconds = 60)
        db.playDao().addSession(ids.getValue("Gex"), endedAt = 2_000, seconds = 60)
        assertEquals(listOf("Gex", "Zelda"), first { it.entry(HomeContent.RECENT_ID).count == 2 }.entry(HomeContent.RECENT_ID).rows.map { it.title })

        // The game returns: the play session is recorded and Recent follows, newest first.
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 3_000, seconds = 60)
        first { it.entry(HomeContent.RECENT_ID).rows.map { r -> r.title } == listOf("Zelda", "Gex") }

        // A rescan that rewrites the game keeps its row and its place.
        val stored = db.scanDao().gamesInDirectory(folderId, "root/snes").single { it.fields.title == "Zelda" }
        db.scanDao().applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(sizeBytes = 2)))))
        val afterRescan = first { it.entry(HomeContent.RECENT_ID).count == 2 }
        assertEquals(listOf("Zelda", "Gex"), afterRescan.entry(HomeContent.RECENT_ID).rows.map { it.title })

        // Hidden leaves Recent at once.
        db.gameDao().setHidden(ids.getValue("Zelda"), true)
        assertEquals(listOf("Gex"), first { it.entry(HomeContent.RECENT_ID).count == 1 }.entry(HomeContent.RECENT_ID).rows.map { it.title })
    }

    @Test
    fun theSortSettingReordersSystemsAndFavoritesButNotRecent() = runBlocking {
        val sort = MutableStateFlow(SortGamesSetting.Name)
        val sorted = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, sort = sort)
        val ids = gameIdsByTitle()
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 1_000, seconds = 60)
        db.playDao().addSession(ids.getValue("Mario"), endedAt = 2_000, seconds = 60)
        db.gameDao().setFavorite(ids.getValue("ActRaiser"), true)
        db.gameDao().setFavorite(ids.getValue("Zelda"), true)

        withTimeout(TIMEOUT_MS) {
            val byName = sorted.content.first { it.entry(HomeContent.FAVORITES_ID).count == 2 && it.entry(HomeContent.RECENT_ID).count == 2 }
            assertEquals(listOf("ActRaiser", "Mario", "Zelda"), byName.entry("snes").rows.map { it.title })
            assertEquals(listOf("ActRaiser", "Zelda"), byName.entry(HomeContent.FAVORITES_ID).rows.map { it.title })
            assertEquals("the star comes from the mark", listOf(true, false, true), byName.entry("snes").rows.map { it.isFavorite })

            sort.value = SortGamesSetting.LastPlayed
            val byLastPlayed = sorted.content.first { it.entry("snes").rows.first().title == "Mario" }
            assertEquals(listOf("Mario", "Zelda", "ActRaiser"), byLastPlayed.entry("snes").rows.map { it.title })
            assertEquals(listOf("Zelda", "ActRaiser"), byLastPlayed.entry(HomeContent.FAVORITES_ID).rows.map { it.title })
            assertEquals("Recent is always newest first", listOf("Mario", "Zelda"), byLastPlayed.entry(HomeContent.RECENT_ID).rows.map { it.title })
        }
    }

    @Test
    fun theSystemColumnFollowsTheOrderAndHidesHiddenSystemsAtOnce() = runBlocking {
        val arrangement = MutableStateFlow(SystemArrangement())
        val source = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, arrangement = arrangement)
        val ids = gameIdsByTitle()
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 1_000, seconds = 60)
        db.gameDao().setFavorite(ids.getValue("Zelda"), true)
        fun HomeContent.systemIds() = entries.filter { it.kind == EntryKind.System }.map { it.id }

        withTimeout(TIMEOUT_MS) {
            val byName = source.content.first { it.entry(HomeContent.FAVORITES_ID).count == 1 && it.entry(HomeContent.RECENT_ID).count == 1 }
            assertEquals(listOf("3do", "gba", "snes"), byName.systemIds())

            // Maker & year: Nintendo's SNES (1990) and GBA (2001), then Panasonic's 3DO.
            arrangement.value = SystemArrangement(SystemOrderSetting.Maker)
            assertEquals(listOf("snes", "gba", "3do"), source.content.first { it.systemIds().first() == "snes" }.systemIds())

            arrangement.value = SystemArrangement(SystemOrderSetting.Manual, manual = listOf("gba", "3do"))
            assertEquals("the stored list, then the system it lacks", listOf("gba", "3do", "snes"), source.content.first { it.systemIds().first() == "gba" }.systemIds())

            arrangement.value = arrangement.value.copy(hidden = setOf("snes"))
            val hidden = source.content.first { "snes" !in it.systemIds() }
            assertEquals(listOf("gba", "3do"), hidden.systemIds())
            assertEquals("not counted in the top bar", 2, hidden.systemCount)
            assertEquals("Recent and Favorites still hold its game", "Zelda", hidden.entry(HomeContent.RECENT_ID).rows.single().title)
            assertEquals("Zelda", hidden.entry(HomeContent.FAVORITES_ID).rows.single().title)
            assertEquals("Search still has its games", listOf("snes"), hidden.hiddenEntries.map { it.id })
            assertEquals(3, hidden.hiddenEntries.single().count)
            assertEquals("Recent, Favorites and Apps never move", listOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID), hidden.entries.take(3).map { it.id })

            arrangement.value = arrangement.value.copy(hidden = setOf("snes", "gba", "3do"))
            val all = source.content.first { it.systemIds().size == 3 && it.hiddenEntries.isEmpty() }
            assertEquals("hiding every system shows them all", listOf("gba", "3do", "snes"), all.systemIds())
        }
    }

    /** A language change re-sorts A to Z at once (the content outlives the recreated Activity). */
    @Test
    fun aToZFollowsALanguageChange() = runBlocking {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/more", displayName = "More", addedAt = 0))
        // Systems the platform data does not know show their stored names: "\u00D6vrigt" sorts with the O's in English, after Z in Swedish.
        db.scanDao().applyBatch(
            ScanBatch(
                folder,
                systems = listOf(SystemEntity("om", "Omega"), SystemEntity("ov", "\u00D6vrigt"), SystemEntity("ze", "Zeta")),
                newGames = listOf(game("om/a.bin", "om", "A", ""), game("ov/b.bin", "ov", "B", ""), game("ze/c.bin", "ze", "C", "")).map { it.copy(folderId = folder) },
            ),
        )
        val locale = MutableStateFlow(Locale.ENGLISH)
        val source = LibraryHomeContent(db.gameDao(), { null }, apps, showApps, Dispatchers.Default, locale = locale)
        fun HomeContent.mine() = entries.filter { it.kind == EntryKind.System && it.id in setOf("om", "ov", "ze") }.map { it.id }
        withTimeout(TIMEOUT_MS) {
            assertEquals(listOf("om", "ov", "ze"), source.content.first { it.mine().size == 3 }.mine())
            locale.value = Locale.forLanguageTag("sv")
            assertEquals(listOf("om", "ze", "ov"), source.content.first { it.mine() == listOf("om", "ze", "ov") }.mine())
        }
    }

    @Test
    fun recentKeepsTheTwentyLatest() = runBlocking {
        val many = (1..25).map { game("snes/g$it.sfc", "snes", "Game %02d".format(it), "USA") }
        db.scanDao().applyBatch(ScanBatch(folderId, newGames = many))
        val ids = gameIdsByTitle()
        (1..25).forEach { db.playDao().addSession(ids.getValue("Game %02d".format(it)), endedAt = it * 1_000L, seconds = 30) }

        val recent = first { it.entry(HomeContent.RECENT_ID).count == RecentSizeSetting.Twenty.games }.entry(HomeContent.RECENT_ID)

        assertEquals("Game 25", recent.rows.first().title)
        assertEquals("Game 06", recent.rows.last().title)
    }

    @Test
    fun recentFollowsTheRecentSizeSettingAtOnce() = runBlocking<Unit> {
        val many = (1..60).map { game("snes/g$it.sfc", "snes", "Game %02d".format(it), "USA") }
        db.scanDao().applyBatch(ScanBatch(folderId, newGames = many))
        val ids = gameIdsByTitle()
        (1..60).forEach { db.playDao().addSession(ids.getValue("Game %02d".format(it)), endedAt = it * 1_000L, seconds = 30) }
        val size = MutableStateFlow(RecentSizeSetting.Ten)
        val sized = LibraryHomeContent(db.gameDao(), { catalog }, apps, showApps, Dispatchers.Default, recentSize = size)

        withTimeout(TIMEOUT_MS) {
            val ten = sized.content.first { it.entry(HomeContent.RECENT_ID).count == 10 }.entry(HomeContent.RECENT_ID)
            assertEquals("Game 60", ten.rows.first().title)
            assertEquals("Game 51", ten.rows.last().title)
            size.value = RecentSizeSetting.Fifty
            val fifty = sized.content.first { it.entry(HomeContent.RECENT_ID).count == 50 }.entry(HomeContent.RECENT_ID)
            assertEquals("Game 11", fifty.rows.last().title)
        }
    }

    @Test
    fun aGameRemovedFromRecentLeavesTheEntryAndClearRecentEmptiesIt() = runBlocking<Unit> {
        val ids = gameIdsByTitle()
        db.playDao().addSession(ids.getValue("Zelda"), endedAt = 1_000, seconds = 30)
        db.playDao().addSession(ids.getValue("ActRaiser"), endedAt = 2_000, seconds = 30)
        withTimeout(TIMEOUT_MS) {
            source.content.first { it.entry(HomeContent.RECENT_ID).count == 2 }
            db.playDao().removeFromRecent(ids.getValue("ActRaiser"))
            assertEquals(listOf("Zelda"), source.content.first { it.entry(HomeContent.RECENT_ID).count == 1 }.entry(HomeContent.RECENT_ID).rows.map { it.title })
            db.playDao().clearRecent()
            source.content.first { it.entry(HomeContent.RECENT_ID).count == 0 }
        }
    }

    @Test
    fun appsFollowTheShowAppsSetting() = runBlocking {
        val shown = first { it.entry(HomeContent.APPS_ID).count == 1 }
        assertEquals("org.browser/org.browser.Main", shown.entry(HomeContent.APPS_ID).rows.single().id)
        assertEquals("Browser", shown.entry(HomeContent.APPS_ID).rows.single().title)

        // Off: the entry leaves the column (not "Apps 0") and the systems move up by one.
        showApps.value = false
        val hidden = first { content -> content.entries.none { it.id == HomeContent.APPS_ID } }
        assertEquals(listOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID), hidden.entries.take(2).map { it.id })
        assertEquals(2, hidden.firstSystemIndex)

        showApps.value = true
        apps.value = apps.value + LaunchableApp("Camera", "org.camera", "org.camera.Main")
        assertEquals(listOf("Browser", "Camera"), first { it.entry(HomeContent.APPS_ID).count == 2 }.entry(HomeContent.APPS_ID).rows.map { it.title })
    }

    @Test
    fun aBatchInOneSystemLeavesTheOtherSystemsRowsAlone() = runBlocking {
        // One subscription, as the screen has: each new subscription queries afresh.
        val emitted = Channel<HomeContent>(Channel.UNLIMITED)
        val collecting = launch(Dispatchers.Default) { source.content.collect { emitted.send(it) } }
        suspend fun next(predicate: (HomeContent) -> Boolean) = withTimeout(TIMEOUT_MS) {
            var c = emitted.receive()
            while (!predicate(c)) c = emitted.receive()
            c
        }
        val before = next { it.systemCount == 3 }

        db.scanDao().applyBatch(ScanBatch(folderId, newGames = listOf(game("snes/earthbound.sfc", "snes", "EarthBound", "USA"))))
        val after = next { it.entry("snes").count == 4 }
        collecting.cancel()

        assertSame("GBA's rows are the same list: nothing to redraw", before.entry("gba").rows, after.entry("gba").rows)
        assertSame(before.entry("3do").rows, after.entry("3do").rows)
        assertNotSame(before.entry("snes").rows, after.entry("snes").rows)
    }

    @Test
    fun anEmptyLibraryStillHasRecentFavoritesAndApps() = runBlocking {
        val empty = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
        try {
            val content = LibraryHomeContent(empty.gameDao(), { catalog }, apps, showApps, Dispatchers.Default)
                .content.firstMatching { true }
            assertEquals(true, content.isLibraryEmpty)
            assertEquals(1, content.entry(HomeContent.APPS_ID).count)
        } finally {
            empty.close()
        }
    }

    @Test
    fun aLibraryThatCannotBeReadBecomesAMessageNotACrash() = runBlocking {
        val broken = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
        val dao = broken.gameDao()
        broken.close()
        val logged = mutableListOf<String>()

        val state = withTimeout(TIMEOUT_MS) {
            LibraryHomeContent(dao, { catalog }, apps, showApps, Dispatchers.Default, log = { logged += it }).states.first()
        }

        assertEquals(HomeState.Failed, state)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().startsWith("Main screen content failed (IllegalStateException"))
    }

    @Test
    fun withoutPlatformDataSystemsShowTheirStoredNames() = runBlocking {
        val content = LibraryHomeContent(db.gameDao(), { null }, apps, showApps, Dispatchers.Default)
            .content.firstMatching { it.systemCount == 3 }
        assertEquals(listOf("3do", "gba", "snes"), content.entries.filter { it.kind == EntryKind.System }.map { it.name })
    }

    private suspend fun first(predicate: (HomeContent) -> Boolean): HomeContent = source.content.firstMatching(predicate)

    private suspend fun Flow<HomeContent>.firstMatching(predicate: (HomeContent) -> Boolean): HomeContent =
        withTimeout(TIMEOUT_MS) { first(predicate) }

    private fun HomeContent.entry(id: String) = entries.single { it.id == id }

    private suspend fun gameIdsByTitle(): Map<String, Long> =
        listOf("snes", "3do", "gba").flatMap { db.gameDao().observeSystemGames(it).first() }.associate { it.title to it.id }

    private fun game(path: String, system: String, title: String, regions: String, isPrimaryCopy: Boolean = true, sortName: String = title.lowercase()) = GameEntity(
        folderId = folderId,
        documentId = "root/$path",
        parentDocumentId = "root/${path.substringBefore('/')}",
        relativePath = path,
        fileName = path.substringAfter('/'),
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = sortName,
        regions = regions,
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = isPrimaryCopy,
    )

    companion object {
        private const val TIMEOUT_MS = 10_000L

        val catalog: PlatformCatalog by lazy {
            val text = File("../platforms/src/main/assets/platforms.json").readText()
            (PlatformLoader.load(text, "shipped") as PlatformLoadResult.Loaded).catalog
        }
    }
}
