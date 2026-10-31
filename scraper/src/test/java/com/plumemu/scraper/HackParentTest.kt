package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A `[Hack]` file ScreenScraper has no box art for takes its parent game's (PS1 "Crash Bandicoot - N. Sane
 * Trilogy [Hack]" and "Crash Team Racing - Nitro Kart [Hack]"); the hack keeps its own title.
 */
class HackParentTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private val box = """"medias": [{"type": "box-2D", "url": "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(us)", "region": "us"}]"""

    private fun game(id: String, name: String, withBox: Boolean, extra: String = "") =
        """{"id": "$id", "noms": [{"region": "us", "text": "$name"}], "systeme": {"id": "57", "text": "PlayStation"}, ${if (withBox) "$box," else ""} $extra "dates": []}"""

    private fun search(vararg games: String) = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0", "success": "true"}, "response": {"serveurs": {"closefornomember": "0"},
            "ssuser": {"id": "test-user", "numid": "1234", "maxthreads": "1"}, "jeux": [${games.joinToString(",")}]}}""",
    )

    private suspend fun scrape(name: String, title: String, hack: Boolean = true) =
        fx.scraper(fx.config()).scrape(fx.request(fx.rom(name), systemId = "psx", title = title).copy(hack = hack))

    private val crashTitle = "Crash Bandicoot - N. Sane Trilogy"
    private val crashFile = "Crash Bandicoot - N. Sane Trilogy (USA) [Hack].pbp"
    private val ctrTitle = "Crash Team Racing - Nitro Kart"
    private val ctrFile = "Crash Team Racing - Nitro Kart (USA) [Hack].pbp"

    private fun matched(result: ScrapeResult) = result as ScrapeResult.Matched

    @Test fun `a hack that matched an entry with no box front takes the parent's box and keeps its own title and info`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        // The name search answers the hack's own entry, which has no box front and a year.
        fx.server.enqueue(search(game("900", crashTitle, withBox = false, extra = """"editeur": {"text": "Own Publisher"},""")))
        fx.server.enqueue(search(game("42", "Crash Bandicoot", withBox = true, extra = """"editeur": {"text": "Sony"}, "synopsis": [{"langue": "en", "text": "Parent text"}],""")))

        val result = matched(scrape(crashFile, crashTitle))

        assertEquals(GameMatch("42", MatchedBy.Parent), result.match)
        assertEquals(crashTitle, result.info.title)
        assertEquals("the hack entry's own publisher stays", "Own Publisher", result.info.publisher)
        assertEquals("Parent text", result.info.synopsis)
        assertTrue(result.media.any { it.kind == MediaKind.BoxFront })
        assertEquals(3, fx.server.requestCount)
        fx.takeRequest()
        fx.takeRequest()
        assertEquals("Crash Bandicoot", fx.takeRequest().url.queryParameter("recherche"))
    }

    @Test fun `a hack no search finds takes the parent game`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(2) { fx.server.enqueue(search("{}")) }
        // The third answer is the search of the title's head, "Crash Team Racing": the parent lookup reads it from the cache.
        fx.server.enqueue(search(game("43", "Crash Team Racing", withBox = true, extra = """"editeur": {"text": "Sony"},""")))

        val result = matched(scrape(ctrFile, ctrTitle))

        assertEquals(GameMatch("43", MatchedBy.Parent), result.match)
        assertEquals(ctrTitle, result.info.title)
        assertEquals("Sony", result.info.publisher)
        assertEquals("the parent is the head variant already asked: no extra request", 4, fx.server.requestCount)
    }

    @Test fun `two different parents leave the result as it was`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(2) { fx.server.enqueue(search("{}")) }
        fx.server.enqueue(search(game("43", "Crash Team Racing", true), game("44", "Crash Team Racing", true)))

        assertEquals(ScrapeResult.NoMatch, scrape(ctrFile, ctrTitle))
    }

    @Test fun `a parent without a box front leaves the result as it was`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(2) { fx.server.enqueue(search("{}")) }
        fx.server.enqueue(search(game("43", "Crash Team Racing", false)))

        assertEquals(ScrapeResult.NoMatch, scrape(ctrFile, ctrTitle))
    }

    @Test fun `a file without the hack tag searches no parent`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(3) { fx.server.enqueue(search("{}")) }

        assertEquals(ScrapeResult.NoMatch, scrape("Crash Team Racing - Nitro Kart (USA).pbp", ctrTitle, hack = false))
        assertEquals(4, fx.server.requestCount)
    }

    @Test fun `a quota stop during the parent search is the result, not NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(2) { fx.server.enqueue(search("{}")) }
        fx.server.enqueue(status(430, "quota"))

        val result = scrape(ctrFile, ctrTitle)

        assertTrue(result.toString(), result is ScrapeResult.Paused)
    }

    @Test fun `a hack matched without a box front is not delivered when the parent search is stopped`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("900", crashTitle, withBox = false)))
        fx.server.enqueue(status(430, "quota"))

        val result = scrape(crashFile, crashTitle)

        assertTrue(result.toString(), result is ScrapeResult.Paused)
    }

    @Test fun `network loss during the parent search fails the game, not NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(2) { fx.server.enqueue(search("{}")) }
        repeat(4) { fx.server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }

        val result = scrape(ctrFile, ctrTitle)

        assertTrue(result.toString(), result is ScrapeResult.Failed)
    }

    @Test fun `a hack whose search finds a box front asks nothing more`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("900", crashTitle, withBox = true)))

        val result = matched(scrape(crashFile, crashTitle))

        assertEquals(GameMatch("900", MatchedBy.Name), result.match)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a title without a separator takes the parent from the file name`() = runTest {
        // The library row's title became ScreenScraper's own name, which has no " - ".
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("900", "Crash Bandicoot N. Sane Trilogy", withBox = false)))
        fx.server.enqueue(search(game("42", "Crash Bandicoot", withBox = true)))

        val result = matched(scrape(crashFile, "Crash Bandicoot N. Sane Trilogy"))

        assertEquals(GameMatch("42", MatchedBy.Parent), result.match)
        assertEquals("Crash Bandicoot N. Sane Trilogy", result.info.title)
    }

    @Test fun `a hack with no parent title anywhere logs why`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))

        scrape("Crash Hack (USA) [Hack].pbp", "Crash Hack")

        assertTrue(fx.logLines.toString(), fx.logLines.any { it.contains("outcome=no-parent-title") })
    }

    @Test fun `a hack title with no subtitle has no parent`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))

        assertEquals(ScrapeResult.NoMatch, scrape("Crash Hack (USA) [Hack].pbp", "Crash Hack"))
        assertEquals(2, fx.server.requestCount)
    }
}
