package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `Scraper.search`: the person's own query on `jeuRecherche.php`, every game listed;
 * and `Scraper.searchExactTitle` (the lookup after a rename): only the games a run's name search accepts.
 */
class SearchTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private val regions = listOf("us", "wor", "eu", "jp")

    /** A search answer shaped like the real one (header, serveurs, ssuser, jeux); [numid] "0" is anonymous. */
    private fun answer(vararg games: String, numid: String = "1234") = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0", "commandRequested": "https://api.screenscraper.fr/api2/jeuRecherche.php?devid=test-dev-id&devpassword=test-dev-password&ssid=test-user&sspassword=test-user-password"},
            "response": {"serveurs": {"closefornomember": "0"},
            "ssuser": {"id": "test-user", "numid": "$numid", "maxthreads": "1"}, "jeux": [${games.joinToString(",")}]}}""",
    )

    private fun boxFront(id: String, region: String) =
        """{"type": "box-2D", "url": "${fx.server.url("/api2/mediaJeu.php")}?devid=test-dev-id&devpassword=test-dev-password&ssid=test-user&sspassword=test-user-password&jeuid=$id&media=box-2D($region)", "region": "$region", "format": "png"}"""

    private val sonic2 by lazy {
        """{"id": "3002", "noms": [{"region": "jp", "text": "Sonic 2 (JP)"}, {"region": "us", "text": "Sonic The Hedgehog 2"}],
        "systeme": {"id": "1", "text": "Megadrive"}, "editeur": {"text": "Sega"},
        "dates": [{"region": "jp", "text": "1992-11-21"}, {"region": "us", "text": "1992-11-24"}],
        "couleurs": "1", "medias": [${boxFront("3002", "jp")}, ${boxFront("3002", "us")}]}"""
    }
    private val sonic1 = """{"id": "3001", "noms": [{"region": "eu", "text": "Sonic The Hedgehog"}], "systeme": {"id": "1", "text": "Megadrive"}}"""

    private suspend fun search(title: String = "sonic", systemId: String = "megadrive", config: ScraperConfig = fx.config()) =
        fx.scraper(config).search(title, systemId, regions)

    @Test fun `every game of the answer is listed in its order with name, year, publisher and box front`() = runTest {
        fx.server.enqueue(answer(sonic2, sonic1, "{}", sonic2))

        val outcome = search("Sonic the Hedgehog")

        val hits = (outcome as SearchOutcome.Found).games
        assertEquals("the empty game and the repeated one are dropped", listOf("3002", "3001"), hits.map { it.screenScraperGameId })
        val first = hits[0]
        assertEquals("Sonic The Hedgehog 2", first.name)
        assertEquals(1992, first.year)
        assertEquals("Sega", first.publisher)
        assertEquals("Megadrive", first.systemName)
        assertEquals("us", first.boxFront?.region)
        assertEquals(MediaKind.BoxFront, first.boxFront?.kind)
        val url = first.boxFront!!.url
        ScraperFixture.SECRETS.forEach { assertFalse("no credential in $url", url.contains(it)) }
        assertEquals(SearchHit("3001", "Sonic The Hedgehog", null, null, "Megadrive", null), hits[1])

        val request = fx.takeRequest().url
        assertEquals("/api2/jeuRecherche.php", request.encodedPath)
        assertEquals("Sonic the Hedgehog", request.queryParameter("recherche"))
        assertEquals("1", request.queryParameter("systemeid"))
    }

    @Test fun `a search is never cached`() = runTest {
        fx.server.enqueue(answer(sonic1))
        fx.server.enqueue(answer(sonic1))
        val scraper = fx.scraper(fx.config())

        scraper.search("sonic", "megadrive", regions)
        scraper.search("sonic", "megadrive", regions)

        assertEquals(2, fx.server.requestCount)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `a system without a ScreenScraper id searches every system`() = runTest {
        fx.server.enqueue(answer(sonic1))

        search(systemId = "no-such-system")

        assertNull(fx.takeRequest().url.queryParameter("systemeid"))
    }

    @Test fun `an empty list or a 404 is nothing found`() = runTest {
        fx.server.enqueue(answer("{}"))
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        val scraper = fx.scraper(fx.config())

        assertEquals(SearchOutcome.NothingFound, scraper.search("zzz", "megadrive", regions))
        assertEquals(SearchOutcome.NothingFound, scraper.search("zzz", "megadrive", regions))
    }

    @Test fun `a blank title asks nothing`() = runTest {
        assertEquals(SearchOutcome.NothingFound, search("   "))
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `refused developer credentials stop the scraper`() = runTest {
        fx.server.enqueue(status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !  "))
        val scraper = fx.scraper(fx.config())

        assertEquals(SearchOutcome.Unavailable(UnavailableReason.BadDeveloperCredentials), scraper.search("sonic", "megadrive", regions))
        assertEquals(SearchOutcome.Unavailable(UnavailableReason.BadDeveloperCredentials), scraper.search("sonic", "megadrive", regions))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `an answer as anonymous with an account set is a refused password and stops the scraper`() = runTest {
        fx.server.enqueue(answer(sonic1, numid = "0"))
        val scraper = fx.scraper(fx.config())

        assertEquals(SearchOutcome.Unavailable(UnavailableReason.BadUserCredentials), scraper.search("sonic", "megadrive", regions))
        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), scraper.scrape(fx.request(fx.rom())))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `the daily limit pauses until midnight in Paris`() = runTest {
        val noonInParis = utc(2026, 10, 21, 10, 0)
        fx.server.enqueue(status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !"))
        val scraper = fx.scraper(fx.config(clock = { noonInParis }))
        val paused = SearchOutcome.Paused(utc(2026, 10, 21, 22, 0), PauseReason.DailyQuota)

        assertEquals(paused, scraper.search("sonic", "megadrive", regions))
        assertEquals("held by the gate, nothing asked", paused, scraper.search("sonic", "megadrive", regions))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `no connection fails as Network`() = runTest {
        fx.server.close()

        assertEquals(SearchOutcome.Failed(FailureReason.Network), search())
    }

    @Test fun `a body that is not the expected JSON fails as a server error`() = runTest {
        fx.server.enqueue(MockResponse(200, Headers.headersOf("Content-Type", "text/html"), "<html>maintenance</html>"))

        assertEquals(SearchOutcome.Failed(FailureReason.Server(200)), search())
    }

    @Test fun `a build without developer credentials searches nothing`() = runTest {
        val scraper = Scraper.create(fx.config(developer = DeveloperCredentials("", "")))

        assertEquals(SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials), scraper.search("sonic", "megadrive"))
        assertEquals(ScrapeResult.Unavailable(UnavailableReason.NoDeveloperCredentials), scraper.byGameId("3001", fx.request(fx.rom())))
        assertEquals(0, fx.server.requestCount)
    }

    private val sonic1Remake = """{"id": "3009", "noms": [{"region": "us", "text": "Sonic the Hedgehog"}], "systeme": {"id": "1", "text": "Megadrive"}}"""
    private val sonic1OnGameGear = """{"id": "4001", "noms": [{"region": "us", "text": "Sonic The Hedgehog"}], "systeme": {"id": "21", "text": "Game Gear"}}"""

    @Test fun `an exact title search keeps only the system's games whose name equals the title`() = runTest {
        fx.server.enqueue(answer(sonic2, "{}", sonic1OnGameGear, sonic1, sonic1))

        val outcome = fx.scraper(fx.config()).searchExactTitle("The Sonic the hedgehog", "megadrive", regions)

        assertEquals("not Sonic 2, not the Game Gear game, the repeat once", listOf("3001"), (outcome as SearchOutcome.Found).games.map { it.screenScraperGameId })
        val request = fx.takeRequest().url
        assertEquals("/api2/jeuRecherche.php", request.encodedPath)
        assertEquals("1", request.queryParameter("systemeid"))
        assertTrue("never cached", fx.responseFiles().isEmpty())
    }

    @Test fun `an exact title search lists two games with the same title, so the caller can tell it is not certain`() = runTest {
        fx.server.enqueue(answer(sonic1, sonic1Remake))

        val outcome = fx.scraper(fx.config()).searchExactTitle("Sonic The Hedgehog", "megadrive", regions)

        assertEquals(listOf("3001", "3009"), (outcome as SearchOutcome.Found).games.map { it.screenScraperGameId })
    }

    @Test fun `an exact title search with only loose results finds nothing`() = runTest {
        fx.server.enqueue(answer(sonic2, sonic1OnGameGear))

        assertEquals(SearchOutcome.NothingFound, fx.scraper(fx.config()).searchExactTitle("Sonic The Hedgehog", "megadrive", regions))
    }

    @Test fun `an exact title search on a system without a ScreenScraper id or with a blank title asks nothing`() = runTest {
        val scraper = fx.scraper(fx.config())

        assertEquals(SearchOutcome.NothingFound, scraper.searchExactTitle("Sonic The Hedgehog", "no-such-system", regions))
        assertEquals(SearchOutcome.NothingFound, scraper.searchExactTitle("  ", "megadrive", regions))
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `an exact title search stops like a search`() = runTest {
        val noonInParis = utc(2026, 10, 21, 10, 0)
        fx.server.enqueue(status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !"))
        val scraper = fx.scraper(fx.config(clock = { noonInParis }))
        val paused = SearchOutcome.Paused(utc(2026, 10, 21, 22, 0), PauseReason.DailyQuota)

        assertEquals(paused, scraper.searchExactTitle("Sonic The Hedgehog", "megadrive", regions))
        assertEquals("held by the gate, nothing asked", paused, scraper.searchExactTitle("Sonic The Hedgehog", "megadrive", regions))
        assertEquals(1, fx.server.requestCount)
        assertEquals(
            SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials),
            Scraper.create(fx.config(developer = DeveloperCredentials("", ""))).searchExactTitle("Sonic The Hedgehog", "megadrive"),
        )
    }

    @Test fun `a full answer of 30 games is marked truncated, a shorter one is not`() = runTest {
        val others = (1..29).map { """{"id": "${5000 + it}", "noms": [{"region": "us", "text": "Sonic Spinoff $it"}], "systeme": {"id": "1", "text": "Megadrive"}}""" }
        fx.server.enqueue(answer(*(others + sonic1).toTypedArray()))
        fx.server.enqueue(answer(*(others.drop(1) + sonic1).toTypedArray()))
        val scraper = fx.scraper(fx.config())

        val full = scraper.searchExactTitle("Sonic The Hedgehog", "megadrive", regions) as SearchOutcome.Found
        val short = scraper.searchExactTitle("Sonic The Hedgehog", "megadrive", regions) as SearchOutcome.Found

        assertEquals(listOf("3001"), full.games.map { it.screenScraperGameId })
        assertTrue("30 results: more may exist", full.truncated)
        assertFalse("29 results: the whole list", short.truncated)
    }
}
