package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream

/** `Scraper.byGameId`: the game the person picked, fetched by `jeuInfos.php?gameid=`. */
class ByGameIdTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    /** The person's game: never read, whatever its hints. */
    private object UnreadRom : RomSource {
        override val key = "content://tree/doc/Sonic.md"
        override val fileName = "Sonic.md"
        override val sizeBytes = 524_288L
        override val lastModifiedMillis = 0L

        override fun open(): InputStream = error("byGameId never reads the ROM")
    }

    private val request = ScrapeRequest(UnreadRom, "megadrive", "Sonic", regions = listOf("us", "wor", "eu", "jp"), languages = listOf("en"))

    @Test fun `the picked game is answered like a scrape match, trusted as the person's choice`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        val matched = fx.scraper(fx.config()).byGameId(" 1001 ", request) as ScrapeResult.Matched

        assertEquals(GameMatch("1001", MatchedBy.PersonChoice), matched.match)
        assertEquals("Sonic The Hedgehog", matched.info.title)
        assertEquals("Sonic must stop Dr. Robotnik.", matched.info.synopsis)
        assertEquals(listOf("us", "jp"), matched.media.filter { it.kind == MediaKind.BoxFront }.map { it.region })
        assertEquals(matched.media, matched.details.media)
        assertTrue(matched.details.names.isNotEmpty())
        val url = fx.takeRequest().url
        assertEquals("/api2/jeuInfos.php", url.encodedPath)
        assertEquals("1001", url.queryParameter("gameid"))
        assertNull("no ROM facts are sent", url.queryParameter("romnom"))
        assertNull(url.queryParameter("crc"))
    }

    @Test fun `the same game is answered from the cache unless the request is fresh`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        scraper.byGameId("1001", request)
        scraper.byGameId("1001", request)
        assertEquals(1, fx.server.requestCount)
        scraper.byGameId("1001", request.copy(fresh = true))
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `an id ScreenScraper does not know is no match`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(json("jeuInfos-match.json") { it.replace("\"id\": \"1001\",", "") })
        val scraper = fx.scraper(fx.config())

        assertEquals(ScrapeResult.NoMatch, scraper.byGameId("999999", request))
        assertEquals("a game without an id is no game", ScrapeResult.NoMatch, scraper.byGameId("1001", request))
    }

    @Test fun `an id that is not a number asks nothing`() = runTest {
        val scraper = fx.scraper(fx.config())

        assertEquals(ScrapeResult.NoMatch, scraper.byGameId("", request))
        assertEquals(ScrapeResult.NoMatch, scraper.byGameId("1001&romnom=x", request))
        assertEquals(ScrapeResult.NoMatch, scraper.byGameId("../1001", request))
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `an answer as anonymous with an account set is a refused password, not cached`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json") { it.replace("\"numid\": \"1234\"", "\"numid\": \"0\"") })
        val scraper = fx.scraper(fx.config())

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), scraper.byGameId("1001", request))
        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), scraper.byGameId("1001", request))
        assertEquals(1, fx.server.requestCount)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `without an account an answer as anonymous is a match`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json") { it.replace("\"numid\": \"1234\"", "\"numid\": \"0\"") })

        val result = fx.scraper(fx.config(user = null)).byGameId("1001", request)

        assertEquals(GameMatch("1001", MatchedBy.PersonChoice), (result as ScrapeResult.Matched).match)
    }
}
