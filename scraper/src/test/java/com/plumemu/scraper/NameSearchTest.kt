package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `jeuRecherche.php` returns loosely related games in no useful order: only an exact title counts. */
class NameSearchTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun game(id: String, system: String, vararg names: String): String {
        val noms = names.joinToString(",") { """{"region": "us", "text": "$it"}""" }
        return """{"id": "$id", "noms": [$noms], "systeme": {"id": "$system", "text": "System $system"}, "synopsis": [{"langue": "en", "text": "Game $id."}]}"""
    }

    /** A search answer, shaped like the real one (header, serveurs, ssuser, jeux). */
    private fun search(vararg games: String) = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0", "success": "true"}, "response": {"serveurs": {"closefornomember": "0"},
            "ssuser": {"id": "test-user", "numid": "1234", "maxthreads": "1"}, "jeux": [${games.joinToString(",")}]}}""",
    )

    private suspend fun scrapeByName(title: String, systemId: String = "megadrive"): ScrapeResult {
        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom("$title.bin"), systemId = systemId, title = title))
        assertEquals(2, fx.server.requestCount)
        return result
    }

    /** A `.steam` file: opening it fails the test, since a Steam game must be neither hashed nor read. */
    private val steamFile = object : RomSource {
        override val key = "content://x/Broforce.steam"
        override val fileName = "Broforce.steam"
        override val sizeBytes = 7L
        override val lastModifiedMillis = 1L
        override fun open(): java.io.InputStream = error("a .steam file must never be opened")
    }

    @Test fun `a Steam game is looked up by name only, under PC Windows`() = runTest {
        fx.server.enqueue(search(game("97000", "138", "Broforce")))

        val result = fx.scraper(fx.config()).scrape(fx.request(steamFile, systemId = "steam", title = "Broforce"))

        assertEquals(GameMatch("97000", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals("one request: no hash lookup first", 1, fx.server.requestCount)
        val url = fx.takeRequest().url
        assertEquals("/api2/jeuRecherche.php", url.encodedPath)
        assertEquals("Broforce", url.queryParameter("recherche"))
        assertEquals("138", url.queryParameter("systemeid"))
        for (sent in listOf("crc", "md5", "sha1", "romnom", "romtaille")) assertEquals(sent, null, url.queryParameter(sent))
    }

    @Test fun `a Steam game with the library's hints is still looked up by name only`() = runTest {
        fx.server.enqueue(search(game("97000", "138", "Broforce")))
        val request = fx.request(steamFile, systemId = "steam", title = "Broforce").copy(hints = RomHints(sizeBytes = 7L))

        val result = fx.scraper(fx.config()).scrape(request)

        assertEquals(GameMatch("97000", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals(1, fx.server.requestCount)
        assertEquals("/api2/jeuRecherche.php", fx.takeRequest().url.encodedPath)
    }

    @Test fun `a Steam game named like a Windows game on another system is NoMatch`() = runTest {
        fx.server.enqueue(search(game("97001", "135", "Broforce")))

        assertEquals(ScrapeResult.NoMatch, fx.scraper(fx.config()).scrape(fx.request(steamFile, systemId = "steam", title = "Broforce")))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `the exact title is picked even when a sequel comes first`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(
            search(
                game("3002", "1", "Sonic The Hedgehog 2"),
                game("3009", "1", "Sonic The Hedgehog Spinball"),
                game("3001", "1", "Sonic The Hedgehog"),
            ),
        )

        val result = scrapeByName("Sonic the Hedgehog")

        assertEquals(GameMatch("3001", MatchedBy.Name), (result as ScrapeResult.Matched).match)
    }

    @Test fun `no result with the exact title is NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("3002", "1", "Sonic The Hedgehog 2"), game("3003", "1", "Sonic The Hedgehog 3")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Sonic the Hedgehog"))
    }

    @Test fun `the exact title on another system is NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("4001", "2", "Sonic The Hedgehog")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Sonic the Hedgehog"))
    }

    @Test fun `any of a game's names may match, with accents, brackets and a moved The`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(
            search(
                game("5002", "1", "Pok\u00e9mon Stadium"),
                game("5001", "1", "Zelda (JP)", "Legend of Zelda - Pok\u00e9mon Edition, The (Rev 1)"),
            ),
        )

        val result = scrapeByName("The Legend of Zelda - Pokemon Edition [!]")

        assertEquals(GameMatch("5001", MatchedBy.Name), (result as ScrapeResult.Matched).match)
    }

    @Test fun `two different games with the exact title are NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        // Both carry the exact name (only the case differs): the tie-break cannot tell them apart.
        fx.server.enqueue(search(game("6001", "1", "Sonic The Hedgehog"), game("6002", "1", "sonic the hedgehog")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Sonic the Hedgehog"))
    }

    @Test fun `a loser with a year in brackets is another release, so the tie stays NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("7001", "1", "Prey"), game("7002", "1", "Prey (2017)")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Prey"))
    }

    @Test fun `a Remake loser keeps the tie NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("6001", "1", "Sonic The Hedgehog"), game("6002", "1", "Sonic the Hedgehog (Remake)")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Sonic the Hedgehog"))
    }

    @Test fun `with no exact name among several accepted games the result is NoMatch`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("6001", "1", "Sonic The Hedgehog (USA)"), game("6002", "1", "Sonic the Hedgehog (Europe)")))

        assertEquals(ScrapeResult.NoMatch, scrapeByName("Sonic the Hedgehog"))
    }

    private fun matchedId(result: ScrapeResult) = (result as ScrapeResult.Matched).match.screenScraperGameId

    @Test fun `Steam LIMBO, the game wins over its Special Edition`() = runTest {
        fx.server.enqueue(search(game("314264", "138", "Limbo (Special Edition)"), game("314260", "138", "Limbo")))

        val result = fx.scraper(fx.config()).scrape(fx.request(steamFile, systemId = "steam", title = "LIMBO"))

        assertEquals("314260", matchedId(result))
    }

    @Test fun `Steam Stardew Valley, the game wins over its Collector's Edition`() = runTest {
        fx.server.enqueue(search(game("341556", "138", "Stardew Valley (Collector's Edition)"), game("341554", "138", "Stardew Valley")))

        val result = fx.scraper(fx.config()).scrape(fx.request(steamFile, systemId = "steam", title = "Stardew Valley"))

        assertEquals("341554", matchedId(result))
    }

    @Test fun `DS Metroid Prime Hunters, a colon name is not the dash title, so First Hunt loses`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(
            search(
                game("264398", "15", "Metroid Prime - Hunters - First Hunt", "Metroid Prime : Hunters"),
                game("5807", "15", "Metroid Prime - Hunters", "Metroid Prime : Hunters"),
            ),
        )

        assertEquals("5807", matchedId(scrapeByName("Metroid Prime - Hunters", systemId = "nds")))
    }

    @Test fun `the same game listed twice is still one match`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("6001", "1", "Sonic The Hedgehog"), game("6001", "1", "Sonic The Hedgehog")))

        assertEquals(GameMatch("6001", MatchedBy.Name), (scrapeByName("Sonic the Hedgehog") as ScrapeResult.Matched).match)
    }

    @Test fun `only the accepted result is cached and the cached search gives the same result`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(
            search(game("3002", "1", "Sonic The Hedgehog 2"), game("3001", "1", "Sonic The Hedgehog"), game("3003", "1", "Sonic 3")),
        )
        val fresh = scrapeByName("Sonic the Hedgehog")

        val stored = fx.cachedText()
        assertTrue(stored.contains("\"3001\""))
        assertFalse(stored.contains("\"3002\""))
        assertFalse(stored.contains("\"3003\""))
        val cached = fx.scraper(fx.config()).scrape(fx.request(fx.rom("Sonic the Hedgehog.bin"), title = "Sonic the Hedgehog"))
        assertEquals(2, fx.server.requestCount)
        assertEquals(fresh, cached)
    }
}
