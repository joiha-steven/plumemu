package com.plumemu.library.scrape

import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.DAY
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.scraper.ScrapeResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `ScrapeSource` contract over Room: what is pending, in which order, and that delivered stays delivered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScrapeSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private suspend fun pendingDocs(limit: Int = 100) = lib.source().pending(limit).map { it.rom.fileName }

    @Test
    fun aDeliveredGameNeverComesBackAlsoAfterReopeningTheDatabase() = runTest {
        lib.add(lib.game("a.sfc"), lib.game("b.sfc"), lib.game("c.sfc"))
        val source = lib.source()
        val (a, b) = source.pending(2)
        source.deliver(a, matched(), listOf(boxFront()))
        source.deliver(b, ScrapeResult.NoMatch, emptyList())
        assertEquals(listOf("c.sfc"), pendingDocs())

        lib.reopen()
        assertEquals("a new process sees the same", listOf("c.sfc"), pendingDocs())
        assertEquals(1, lib.db.scrapedGameDao().countPending(lib.now - 30 * DAY))
        assertEquals(1, lib.db.scrapedGameDao().countWithBoxFront())
        assertEquals(3, lib.db.scrapedGameDao().countListed())
    }

    @Test
    fun aNoMatchIsAskedAgainAfterThirtyDaysUnlessThePersonChoseIt() = runTest {
        lib.add(lib.game("a.sfc"), lib.game("b.sfc"))
        val source = lib.source()
        source.pending(10).forEach { source.deliver(it, ScrapeResult.NoMatch, emptyList()) }
        lib.sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = (SELECT fix_keys FROM game WHERE document_id = 'b.sfc')")
        lib.now += 29 * DAY
        assertEquals(emptyList<String>(), pendingDocs())
        lib.now += 2 * DAY
        assertEquals(listOf("a.sfc"), pendingDocs())
    }

    @Test
    fun recentThenFavoritesThenTheViewedSystemThenTheRest() = runTest {
        val ids = lib.add(
            lib.game("rest1.sfc"),
            lib.game("gba-viewed.gba", system = "gba"),
            lib.game("favorite.sfc", favorite = true),
            lib.game("played-long-ago.sfc"),
            lib.game("rest2.sfc"),
            lib.game("played-yesterday.gba", system = "gba"),
        )
        lib.played(ids[3], lib.now - 30 * DAY)
        lib.played(ids[5], lib.now - DAY)
        lib.currentSystem = "gba"
        assertEquals(
            listOf("played-yesterday.gba", "played-long-ago.sfc", "favorite.sfc", "gba-viewed.gba", "rest1.sfc", "rest2.sfc"),
            pendingDocs(),
        )
        lib.currentSystem = null
        assertEquals(listOf("played-yesterday.gba", "played-long-ago.sfc", "favorite.sfc", "rest1.sfc", "gba-viewed.gba", "rest2.sfc"), pendingDocs())
    }

    @Test
    fun pagesAreStableAndMoveOnOnlyByDelivering() = runTest {
        lib.add(*(1..7).map { lib.game("g$it.sfc") }.toTypedArray())
        val source = lib.source()
        assertEquals(pendingDocs(3), pendingDocs(3))
        val seen = mutableListOf<String>()
        while (true) {
            val page = source.pending(3)
            if (page.isEmpty()) break
            page.forEach { request ->
                assertTrue("never twice: ${request.rom.fileName}", seen.add(request.rom.fileName))
                source.deliver(request, ScrapeResult.NoMatch, emptyList())
            }
        }
        assertEquals((1..7).map { "g$it.sfc" }, seen)
        assertEquals(emptyList<String>(), pendingDocs(0))
    }

    @Test
    fun hiddenNotAGameOtherCopiesAndGamesStillToNameAreNotAsked() = runTest {
        val ids = lib.add(lib.game("shown.sfc"), lib.game("hidden.sfc"), lib.game("bios.sfc"), lib.game("eur-copy.sfc"), lib.game("new.sfc"))
        lib.sql("UPDATE game SET is_hidden = 1 WHERE id = ?", ids[1])
        lib.sql("UPDATE game SET is_not_game = 1 WHERE id = ?", ids[2])
        lib.sql("UPDATE game SET is_primary_copy = 0 WHERE id = ?", ids[3])
        lib.sql("UPDATE game SET needs_identify = 1 WHERE id = ?", ids[4])
        assertEquals(listOf("shown.sfc"), pendingDocs())
    }

    @Test
    fun twoCopiesOfOneFileAreAskedOnceAndBothHaveTheResult() = runTest {
        lib.add(lib.game("a.sfc", fixKeys = "crc:0000abcd:1024"), lib.game("copy-of-a.sfc", fixKeys = "crc:0000abcd:1024"))
        val source = lib.source()
        val page = source.pending(10)
        assertEquals(listOf("a.sfc"), page.map { it.rom.fileName })
        source.deliver(page.single(), matched(), listOf(boxFront()))
        assertEquals(emptyList<String>(), pendingDocs())
        assertEquals(2, lib.db.scrapedGameDao().countWithBoxFront())
        // No CRC or serial was sent (no fingerprint): a likely title, on both copies.
        val names = lib.db.scanDao().gamesInFolder(lib.folderId).map { it.title to it.nameTrust }
        assertEquals(listOf("Zork Quest" to "SCRAPER_LIKELY", "Zork Quest" to "SCRAPER_LIKELY"), names)
    }

    @Test
    fun aGameWithoutFixKeysIsStoredUnderItsRowKeyAndItGoesWithTheGame() = runTest {
        val (id) = lib.add(lib.game("plain.sfc", fixKeys = ""))
        val source = lib.source()
        val request = source.pending(1).single()
        source.deliver(request, ScrapeResult.NoMatch, emptyList())
        assertEquals(ScrapeOutcome.NO_MATCH, lib.db.scrapedGameDao().get("game:$id")?.outcome)
        lib.db.scanDao().forgetLibrary()
        assertEquals(null, lib.db.scrapedGameDao().get("game:$id"))
    }

    @Test
    fun aResultForAGameDeletedMeanwhileIsDroppedWithoutFailing() = runTest {
        lib.add(lib.game("plain.sfc", fixKeys = ""))
        val source = lib.source()
        val request = source.pending(1).single()
        lib.db.scanDao().forgetLibrary()
        source.deliver(request, matched(), listOf(boxFront()))
        lib.add(lib.game("other.sfc", fixKeys = ""))
        assertEquals("a reused id gets no stranger's art", listOf("other.sfc"), pendingDocs())
    }

    @Test
    fun tenThousandGamesPageFast() = runTest {
        val games = (0 until 10_000).map { lib.game("game$it.sfc", fixKeys = "crc:%08x:1024".format(it), system = if (it % 3 == 0) "gba" else "snes") }
        games.chunked(500).forEach { chunk -> lib.db.scanDao().applyBatch(ScanBatch(lib.folderId, ScrapeTestLibrary.SYSTEMS, chunk)) }
        lib.db.scanDao().finishScan(ScanFinish(lib.folderId))
        lib.sql("INSERT INTO play (game_id, last_played_at, total_seconds) SELECT id, id, 60 FROM game WHERE id % 97 = 0")
        lib.sql("UPDATE game SET is_favorite = 1 WHERE id % 89 = 0")
        // 9,000 already stored, as in a library mostly done.
        lib.sql(
            "INSERT INTO scraped_game (scrape_key, outcome, scraped_at, pictures) " +
                "SELECT fix_keys, 'MATCHED', ?, '' FROM game WHERE id % 10 != 0",
            lib.now,
        )
        lib.currentSystem = "gba"
        val source = lib.source()
        // A run already under way: 300 games delivered by this source before the pages measured.
        repeat(6) { source.pending(50).forEach { source.deliver(it, ScrapeResult.NoMatch, emptyList()) } }
        source.pending(50)
        val timings = (1..5).map {
            val started = System.nanoTime()
            val page = source.pending(50)
            val millis = (System.nanoTime() - started) / 1_000_000.0
            assertEquals(50, page.size)
            millis
        }
        val countStarted = System.nanoTime()
        assertEquals(700, lib.db.scrapedGameDao().countPending(lib.now - 30 * DAY))
        val countMillis = (System.nanoTime() - countStarted) / 1_000_000.0
        println("PENDING_TIMING pending(50) over 10,000 games after 300 delivered, ms: ${timings.joinToString { "%.1f".format(it) }}; countPending ms: ${"%.1f".format(countMillis)}")
        org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
        assertTrue("pending(50) took ${timings.minOrNull()} ms", (timings.minOrNull() ?: Double.MAX_VALUE) < 250.0)
    }
}
