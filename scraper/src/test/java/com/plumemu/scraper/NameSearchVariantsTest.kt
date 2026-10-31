package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ScreenScraper's search answers an empty entry for a title with a colon ("Captain Toad: Treasure
 * Tracker"); the scraper asks again without punctuation and for the part before the subtitle,
 * judging every answer against the original title.
 */
class NameSearchVariantsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private val toad = """{"id": "196203", "noms": [{"region": "ss", "text": "Captain Toad Treasure Tracker"}, {"region": "us", "text": "Captain Toad : Treasure Tracker"}], "systeme": {"id": "225", "text": "Switch"}}"""
    private fun other(id: String, name: String) = """{"id": "$id", "noms": [{"region": "us", "text": "$name"}], "systeme": {"id": "225", "text": "Switch"}}"""

    private fun search(vararg games: String) = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0", "success": "true"}, "response": {"serveurs": {"closefornomember": "0"},
            "ssuser": {"id": "test-user", "numid": "1234", "maxthreads": "1"}, "jeux": [${games.joinToString(",")}]}}""",
    )

    private suspend fun scrape(title: String) =
        fx.scraper(fx.config()).scrape(fx.request(fx.rom("$title.xci"), systemId = "switch", title = title))

    private fun searchTexts(count: Int) = (1..count).map { fx.takeRequest().url.queryParameter("recherche") }

    @Test fun `Captain Toad is found by the title without its colon`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(toad))

        val result = scrape("Captain Toad: Treasure Tracker")

        assertEquals(GameMatch("196203", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals(3, fx.server.requestCount)
        fx.takeRequest()
        assertEquals(listOf("Captain Toad: Treasure Tracker", "Captain Toad Treasure Tracker"), searchTexts(2))
    }

    @Test fun `the part before the subtitle is asked last, and its answer is still judged against the whole title`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(other("1", "Captain Toad"), toad))

        val result = scrape("Captain Toad: Treasure Tracker")

        assertEquals("not the game named only Captain Toad", "196203", (result as ScrapeResult.Matched).match.screenScraperGameId)
        assertEquals(4, fx.server.requestCount)
        fx.takeRequest()
        assertEquals(listOf("Captain Toad: Treasure Tracker", "Captain Toad Treasure Tracker", "Captain Toad"), searchTexts(3))
    }

    @Test fun `a title whose variants all fail costs the hash lookup, the search and two variants`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        repeat(3) { fx.server.enqueue(search("{}")) }

        assertEquals(ScrapeResult.NoMatch, scrape("Captain Toad: Treasure Tracker"))
        assertEquals(4, fx.server.requestCount)
    }

    @Test fun `a title with no punctuation asks no variant`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))

        assertEquals(ScrapeResult.NoMatch, scrape("Captain Toad Treasure Tracker"))
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a variant identical to one already sent is skipped`() = runTest {
        assertEquals(listOf("Super Mario Land", "Super Mario"), TitleMatch.searchVariants("Super Mario - Land"))
        assertEquals("a single word before the colon is no variant", listOf("Zelda Echoes"), TitleMatch.searchVariants("Zelda: Echoes"))
        assertEquals(emptyList<String>(), TitleMatch.searchVariants("Plain Title"))
    }

    @Test fun `in a variant's answer the game with the exact title wins over its edition`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(other("196205", "Captain Toad Treasure Tracker (Deluxe Edition)"), other("196206", "Captain Toad: Treasure Tracker")))

        val result = scrape("Captain Toad: Treasure Tracker")

        assertEquals(GameMatch("196206", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals(3, fx.server.requestCount)
    }

    @Test fun `two games with the same title in a variant's answer stay NoMatch`() = runTest {
        val twin = toad.replace("196203", "196204")
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(toad, twin))

        assertEquals(ScrapeResult.NoMatch, scrape("Captain Toad: Treasure Tracker"))
        assertEquals("the ambiguity ends the search", 3, fx.server.requestCount)
    }

    private fun ds(id: String, name: String) = """{"id": "$id", "noms": [{"region": "us", "text": "$name"}], "systeme": {"id": "15", "text": "DS"}}"""

    private suspend fun scrapeDs(title: String) =
        fx.scraper(fx.config()).scrape(fx.request(fx.rom("$title.nds"), systemId = "nds", title = title))

    @Test fun `a series prefix is accepted when a variant answers one game that holds the whole title`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(ds("5831", "999 : Nine Hours Nine Persons Nine Doors")))

        val result = scrapeDs("Nine Hours, Nine Persons, Nine Doors")

        assertEquals(GameMatch("5831", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals(3, fx.server.requestCount)
    }

    @Test fun `containment is refused when the variant's answer holds two games`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(ds("5831", "999 : Nine Hours Nine Persons Nine Doors"), ds("5832", "Nine Hours Nine Persons Nine Doors Remix")))
        fx.server.enqueue(search("{}"))

        assertEquals(ScrapeResult.NoMatch, scrapeDs("Nine Hours, Nine Persons, Nine Doors"))
    }

    @Test fun `containment is refused for a title of two words`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(ds("77", "Big Brain Academy")))

        assertEquals(ScrapeResult.NoMatch, scrapeDs("Brain, Academy"))
    }

    @Test fun `containment never applies to the answer for the title as given`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(ds("5831", "999 : Nine Hours Nine Persons Nine Doors")))
        repeat(2) { fx.server.enqueue(search("{}")) }

        assertEquals(ScrapeResult.NoMatch, scrapeDs("Nine Hours Nine Persons Nine Doors"))
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `the person's search tries the variants too, and an empty entry is ignored`() = runTest {
        fx.server.enqueue(search("{}"))
        fx.server.enqueue(search(toad))

        val outcome = fx.scraper(fx.config()).searchExactTitle("Captain Toad: Treasure Tracker", "switch", emptyList())

        assertEquals(listOf("196203"), (outcome as SearchOutcome.Found).games.map { it.screenScraperGameId })
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `the person's search with no result costs the title and two variants`() = runTest {
        repeat(3) { fx.server.enqueue(search("{}")) }

        assertEquals(SearchOutcome.NothingFound, fx.scraper(fx.config()).searchExactTitle("Captain Toad: Treasure Tracker", "switch", emptyList()))
        assertEquals(3, fx.server.requestCount)
    }
}
