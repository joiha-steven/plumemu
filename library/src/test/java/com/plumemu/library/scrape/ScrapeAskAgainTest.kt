package com.plumemu.library.scrape

import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.DAY
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.support
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.work.ScrapePass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' Download missing and Re-download through [LibraryScrapeSource.askAgain]:
 * which games come back from `pending`, per system, never a person's choice, the stored rows kept
 * until replaced, and the games the source could not store offered again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeAskAgainTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private suspend fun pendingDocs(source: LibraryScrapeSource) = source.pending(100).map { it.rom.fileName }.sorted()

    private fun keyOf(doc: String) = "(SELECT fix_keys FROM game WHERE document_id = '$doc')"

    /**
     * Five SGBA games and one GBA game, each with a stored result: a match with its box front, a
     * match without it, a fresh no-match, a person's choice; the GBA game a match.
     */
    private suspend fun storedLibrary(source: LibraryScrapeSource) {
        lib.add(lib.game("art.sfc"), lib.game("noart.sfc"), lib.game("none.sfc"), lib.game("mine.sfc"), lib.game("adv.gba", system = "gba"))
        for (request in source.pending(100)) {
            when (request.rom.fileName) {
                "art.sfc", "adv.gba", "mine.sfc" -> source.deliver(request, matched(), listOf(boxFront()))
                "noart.sfc" -> source.deliver(request, matched(withSupport = false), emptyList())
                else -> source.deliver(request, ScrapeResult.NoMatch, emptyList())
            }
        }
        lib.sql("UPDATE scraped_game SET is_person_choice = 1 WHERE scrape_key = ${keyOf("mine.sfc")}")
        assertEquals(emptyList<String>(), pendingDocs(source))
    }

    @Test
    fun downloadMissingAsksAgainTheGamesWithoutABoxFrontIncludingAFreshNoMatch() = runTest {
        val source = lib.source()
        storedLibrary(source)
        assertEquals(2, source.askAgain(AskAgain.Missing))
        assertEquals("the 30-day rule does not hold back an explicit ask", listOf("noart.sfc", "none.sfc"), pendingDocs(source))
        source.pending(100).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        assertEquals(emptyList<String>(), pendingDocs(source))
        assertEquals(5, lib.db.scrapedGameDao().countWithBoxFront())
    }

    @Test
    fun reDownloadAllAsksEveryGameAgainButAPersonsChoiceAndKeepsWhatIsShown() = runTest {
        val source = lib.source()
        storedLibrary(source)
        assertEquals(4, source.askAgain(AskAgain.All))
        assertEquals(listOf("adv.gba", "art.sfc", "noart.sfc", "none.sfc"), pendingDocs(source))
        val dao = lib.db.scrapedGameDao()
        val chosen = lib.db.openHelper.readableDatabase.query("SELECT scraped_at, ask_again FROM scraped_game WHERE scrape_key = ${keyOf("mine.sfc")}").use { c ->
            c.moveToFirst()
            c.getLong(0) to c.getInt(1)
        }
        assertEquals("the person's choice is untouched", lib.now to 0, chosen)
        assertEquals("the box fronts stay until replaced", 3, dao.countWithBoxFront())
        // ScreenScraper answers "not found" this time: the matches stay matches, with their pictures.
        lib.now += 1
        source.pending(100).forEach { source.deliver(it, ScrapeResult.NoMatch, emptyList()) }
        assertEquals(emptyList<String>(), pendingDocs(source))
        assertEquals(3, dao.countWithBoxFront())
        lib.db.openHelper.readableDatabase.query("SELECT outcome FROM scraped_game WHERE scrape_key = ${keyOf("art.sfc")}").use { c ->
            c.moveToFirst()
            assertEquals(ScrapeOutcome.MATCHED.name, c.getString(0))
        }
    }

    @Test
    fun chosenSystemsAskOnlyTheirGamesAgain() = runTest {
        val source = lib.source()
        storedLibrary(source)
        assertEquals(1, source.askAgain(AskAgain.All, setOf("gba")))
        assertEquals(listOf("adv.gba"), pendingDocs(source))
        assertEquals("the GBA game has its box front", 0, lib.source().askAgain(AskAgain.Missing, setOf("gba")))
        assertEquals("no system chosen asks nothing", 0, source.askAgain(AskAgain.All, emptySet()))
        assertEquals(listOf("adv.gba"), pendingDocs(source))
        assertEquals(2, source.askAgain(AskAgain.Missing, setOf("snes", "psx")))
        assertEquals(listOf("adv.gba", "noart.sfc", "none.sfc"), pendingDocs(source))
    }

    @Test
    fun askingAgainOffersTheGamesTheSourceCouldNotStoreAgain() = runTest {
        lib.add(lib.game("a.sfc"), lib.game("b.sfc"))
        val source = lib.source()
        val a = source.pending(100).single { it.rom.fileName == "a.sfc" }
        // The file changed between pending and deliver: nothing is stored and the source skips it.
        lib.sql("UPDATE game SET modified_at = 8 WHERE document_id = 'a.sfc'")
        source.deliver(a, matched(), listOf(boxFront()))
        assertEquals(listOf("b.sfc"), pendingDocs(source))
        source.askAgain(AskAgain.Missing)
        assertEquals(listOf("a.sfc", "b.sfc"), pendingDocs(source))
    }

    /** Which requests [LibraryScrapeSource.pending] marks fresh: only rows asked for again. */
    private suspend fun freshByDoc(source: LibraryScrapeSource) = source.pending(100).associate { it.rom.fileName to it.fresh }

    @Test
    fun onlyRowsAskedForAgainAreSentFreshForDownloadMissing() = runTest {
        val source = lib.source()
        storedLibrary(source)
        lib.add(lib.game("new.sfc"))
        assertEquals("a game never asked is not fresh", mapOf("new.sfc" to false), freshByDoc(source))
        source.askAgain(AskAgain.Missing)
        assertEquals(mapOf("new.sfc" to false, "noart.sfc" to true, "none.sfc" to true), freshByDoc(source))
        // Delivered, the mark is cleared: a later ask of the same game is not fresh unless asked again.
        source.pending(100).forEach { source.deliver(it, ScrapeResult.NoMatch, emptyList()) }
        lib.now += ScrapedGameDao.NO_MATCH_RETRY_MILLIS + 1
        assertEquals("a no-match retried after 30 days is not fresh", mapOf("new.sfc" to false, "none.sfc" to false), freshByDoc(source))
    }

    @Test
    fun everyRowAskedForAgainIsSentFreshForReDownloadButNotAPersonsChoice() = runTest {
        val source = lib.source()
        storedLibrary(source)
        source.askAgain(AskAgain.All)
        assertEquals(
            mapOf("adv.gba" to true, "art.sfc" to true, "noart.sfc" to true, "none.sfc" to true),
            freshByDoc(source),
        )
        // Asked again: the main pass takes them first, the disc pass waits for it.
        assertEquals(emptyList<String>(), source.pendingSupport(100).map { it.rom.fileName })
        // The main pass delivers art.sfc with its new box front and the disc saved before, adv.gba
        // with a box front only: the disc pass fetches the first again (fresh), the second for the first time.
        val asked = source.pending(100).associateBy { it.rom.fileName }
        source.deliver(asked.getValue("art.sfc"), matched(), listOf(boxFront(), support()))
        source.deliver(asked.getValue("adv.gba"), matched(), listOf(boxFront()))
        assertEquals(
            mapOf("adv.gba" to false, "art.sfc" to true),
            source.pendingSupport(100).associate { it.rom.fileName to it.fresh },
        )
    }

    private fun askAgainOf(doc: String): Boolean =
        lib.db.openHelper.readableDatabase.query("SELECT ask_again FROM scraped_game WHERE scrape_key = ${keyOf(doc)}").use { c ->
            c.moveToFirst()
            c.getInt(0) == 1
        }

    /** Re-download pressed while a game's lookup or its disc is in flight: the delivery keeps the mark. */
    @Test
    fun aMarkSetWhileTheGameIsInFlightSurvivesItsDeliveryInEitherPass() = runTest {
        lib.add(lib.game("old.sfc"), lib.game("disc.sfc"))
        val source = lib.source()
        val first = source.pending(10).associateBy { it.rom.fileName }
        source.deliver(first.getValue("old.sfc"), ScrapeResult.NoMatch, emptyList())
        source.deliver(first.getValue("disc.sfc"), matched(), listOf(boxFront()))
        // old.sfc: a no-match 31 days old, back in the main pass (not fresh); disc.sfc in the disc pass.
        lib.now += 31 * DAY
        val lookup = source.pending(10).single()
        assertEquals("old.sfc" to false, lookup.rom.fileName to lookup.fresh)
        val disc = source.pendingSupport(10).single()
        assertEquals("disc.sfc", disc.rom.fileName)

        source.askAgain(AskAgain.All)
        source.deliver(lookup, matched(), listOf(boxFront()))
        source.deliver(disc, matched(), listOf(boxFront(), support()), ScrapePass.Support)

        assertTrue("the main pass's delivery keeps the mark", askAgainOf("old.sfc"))
        assertTrue("the disc pass's delivery keeps the mark", askAgainOf("disc.sfc"))
        assertEquals(mapOf("disc.sfc" to true, "old.sfc" to true), freshByDoc(source))
        // The next main pass asks them fresh, and that answer clears the mark.
        source.pending(10).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        assertFalse(askAgainOf("old.sfc") || askAgainOf("disc.sfc"))
        assertEquals(emptyMap<String, Boolean>(), freshByDoc(source))
    }
}
