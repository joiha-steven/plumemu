package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScrapeErrorsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    @Test fun `a server that is down fails as Network and caches nothing`() = runTest {
        val config = fx.config()
        fx.server.close()

        val result = fx.scraper(config).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Failed(FailureReason.Network), result)
        assertTrue(fx.responseFiles().isEmpty())
        assertTrue(fx.logLines.any { it.contains("network error") })
    }

    @Test fun `a server error fails with its code, is not cached and is asked again next time`() = runTest {
        fx.server.enqueue(status(500, "Internal error"))
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        assertEquals(ScrapeResult.Failed(FailureReason.Server(500)), scraper.scrape(fx.request(fx.rom())))
        assertTrue(fx.responseFiles().isEmpty())
        assertTrue(scraper.scrape(fx.request(fx.rom())) is ScrapeResult.Matched)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a 200 that is not JSON fails and is not cached`() = runTest {
        fx.server.enqueue(status(200, "<html>maintenance</html>"))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Failed(FailureReason.Server(200)), result)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `refused developer credentials make the scraper unavailable for the session`() = runTest {
        fx.server.enqueue(status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !"))
        val scraper = fx.scraper(fx.config())

        val first = scraper.scrape(fx.request(fx.rom()))
        val second = scraper.scrape(fx.request(fx.rom("Other.md")))

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadDeveloperCredentials), first)
        assertEquals(first, second)
        assertEquals(1, fx.server.requestCount)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `refused user credentials say so`() = runTest {
        fx.server.enqueue(status(403, "Erreur de login : V\u00e9rifier vos identifiants utilisateur !"))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), result)
    }

    @Test fun `a blocked software says so`() = runTest {
        fx.server.enqueue(status(426, "Le logiciel est blacklist\u00e9"))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.SoftwareBlocked), result)
    }

    @Test fun `a build without developer credentials is unavailable and makes no request`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        for (developer in listOf(DeveloperCredentials("", ""), DeveloperCredentials("id", " "), DeveloperCredentials(" ", "pw"))) {
            val scraper = Scraper.create(fx.config(developer = developer))

            assertFalse(scraper.available)
            assertEquals(
                ScrapeResult.Unavailable(UnavailableReason.NoDeveloperCredentials),
                scraper.scrape(fx.request(fx.rom())),
            )
        }
        assertEquals(0, fx.server.requestCount)
        assertTrue(Scraper.create(fx.config()).available)
    }

    @Test fun `without a user account the request carries no ssid`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        fx.scraper(fx.config(user = null)).scrape(fx.request(fx.rom()))

        val url = fx.takeRequest().url
        assertEquals(null, url.queryParameter("ssid"))
        assertEquals(null, url.queryParameter("sspassword"))
    }

    @Test fun `log lines and stored files never hold credentials`() = runTest {
        fx.server.enqueue(status(404, ScraperFixture.NOT_FOUND_BODY))
        fx.server.enqueue(json("jeuRecherche-match.json"))
        fx.server.enqueue(status(500, "error for devpassword=${ScraperFixture.DEV_PASSWORD}"))
        val scraper = fx.scraper(fx.config())

        scraper.scrape(fx.request(fx.rom("Pokemon.gbc"), systemId = "gbc", title = "Pokemon Puzzle Challenge"))
        scraper.scrape(fx.request(fx.rom("Other.md")))

        assertTrue(fx.logLines.isNotEmpty())
        val stored = fx.cachedText()
        // The stored search answer: trimmed (no header echoing the request), its media URL kept
        // without the credentials it came with.
        assertTrue(stored.contains("jeuid=2002"))
        for (secret in ScraperFixture.SECRETS) {
            fx.logLines.forEach { assertFalse("log line leaks $secret: $it", it.contains(secret)) }
            assertFalse("cache leaks $secret", stored.contains(secret))
        }
    }

    @Test fun `credentials never show in toString`() {
        assertFalse(ScraperFixture.DEVELOPER.toString().contains(ScraperFixture.DEV_PASSWORD))
        assertFalse(ScraperFixture.DEVELOPER.toString().contains(ScraperFixture.DEV_ID))
        assertFalse(ScraperFixture.USER.toString().contains(ScraperFixture.USER_PASSWORD))
    }
}
