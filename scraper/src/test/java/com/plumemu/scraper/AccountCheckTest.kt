package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.gate.ParisTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A wrong user password: `jeuInfos.php` answers 200 as anonymous (`numid` "0") instead of an
 * error, and `ssuserInfos.php` answers 403 "identifiants utilisateurs".
 */
class AccountCheckTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    /** The match as a made-up account gets it: HTTP 200, the name echoed in `ssuser.id`, `numid` "0". */
    private fun anonymousAnswer(edit: (String) -> String = { it.replace("\"numid\": \"1234\"", "\"numid\": \"0\"") }) =
        json("jeuInfos-match.json", edit)

    private fun accountAnswer(ssuser: String, serveurs: String = """{"closefornomember": "0"}""") = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0"}, "response": {"serveurs": $serveurs, "ssuser": $ssuser}}""",
    )

    // --- scrape: an answer as anonymous while an account is set ---

    @Test fun `an answer as anonymous with an account set stops the scraper and caches nothing`() = runTest {
        fx.server.enqueue(anonymousAnswer())
        val scraper = fx.scraper(fx.config())

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), scraper.scrape(fx.request(fx.rom())))
        assertTrue(fx.responseFiles().isEmpty())

        // Stopped like any refusal: the next game asks nothing.
        assertEquals(
            ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials),
            scraper.scrape(fx.request(fx.rom("Other.md", "other rom"))),
        )
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `an ssuser without numid with an account set is not a refusal`() = runTest {
        fx.server.enqueue(anonymousAnswer { it.replace("\"numid\": \"1234\",", "") })

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertTrue(result is ScrapeResult.Matched)
    }

    @Test fun `a blank numid with an account set is not a refusal`() = runTest {
        fx.server.enqueue(anonymousAnswer { it.replace("\"numid\": \"1234\"", "\"numid\": \"\"") })

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertTrue(result is ScrapeResult.Matched)
    }

    @Test fun `an answer as anonymous without an account set is a match`() = runTest {
        fx.server.enqueue(anonymousAnswer())

        val result = fx.scraper(fx.config(user = null)).scrape(fx.request(fx.rom()))

        assertTrue(result is ScrapeResult.Matched)
    }

    // --- checkAccount ---

    @Test fun `a known account is Ok with its numbers, and the check is not cached`() = runTest {
        fx.server.enqueue(
            accountAnswer("""{"id": "test-user", "numid": "1234", "maxthreads": "4", "requeststoday": "12", "maxrequestsperday": "20000"}"""),
        )
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "numid": "1234", "maxthreads": "4"}"""))
        val scraper = fx.scraper(fx.config())

        assertEquals(AccountCheck.Ok(maxThreads = 4, requestsToday = 12, maxRequestsPerDay = 20_000), scraper.checkAccount())
        val url = fx.takeRequest().url
        assertEquals("/api2/ssuserInfos.php", url.encodedPath)
        assertEquals(ScraperFixture.USER_NAME, url.queryParameter("ssid"))
        assertEquals(ScraperFixture.DEV_ID, url.queryParameter("devid"))
        assertEquals(AccountCheck.Ok(maxThreads = 4, requestsToday = null, maxRequestsPerDay = null), scraper.checkAccount())
        assertEquals(2, fx.server.requestCount)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `a wrong user password is refused and stops scraping with those credentials`() = runTest {
        fx.server.enqueue(status(403, "Erreur de login : V\u00e9rifier les identifiants utilisateurs !               "))
        val scraper = fx.scraper(fx.config())

        assertEquals(AccountCheck.Refused(UnavailableReason.BadUserCredentials), scraper.checkAccount())
        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadUserCredentials), scraper.scrape(fx.request(fx.rom())))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `wrong developer credentials are refused as the app's`() = runTest {
        fx.server.enqueue(status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !               "))

        assertEquals(AccountCheck.Refused(UnavailableReason.BadDeveloperCredentials), fx.scraper(fx.config()).checkAccount())
    }

    @Test fun `an account answer without numid or with a blank one is Ok`() = runTest {
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "maxthreads": "2"}"""))
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "numid": "", "maxthreads": "2"}"""))
        val scraper = fx.scraper(fx.config())

        assertEquals(AccountCheck.Ok(maxThreads = 2, requestsToday = null, maxRequestsPerDay = null), scraper.checkAccount())
        assertEquals(AccountCheck.Ok(maxThreads = 2, requestsToday = null, maxRequestsPerDay = null), scraper.checkAccount())
    }

    @Test fun `a JSON 200 without ssuser is a server error and refuses nothing`() = runTest {
        fx.server.enqueue(MockResponse(200, Headers.headersOf("Content-Type", "application/json"), """{"header": {}, "response": {"serveurs": {}}}"""))
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        assertEquals(AccountCheck.ServerError(200), scraper.checkAccount())
        assertTrue(scraper.scrape(fx.request(fx.rom())) is ScrapeResult.Matched)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `a 200 as anonymous is a refused account`() = runTest {
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "numid": "0", "maxthreads": "1"}"""))

        assertEquals(AccountCheck.Refused(UnavailableReason.BadUserCredentials), fx.scraper(fx.config()).checkAccount())
    }

    @Test fun `a 200 as anonymous while the API is closed to non-members is ServerClosed`() = runTest {
        fx.server.enqueue(accountAnswer("""{"id": "", "numid": "0"}""", serveurs = """{"closefornomember": "1"}"""))

        assertEquals(AccountCheck.ServerClosed, fx.scraper(fx.config()).checkAccount())
    }

    @Test fun `no developer credentials is refused without a request`() = runTest {
        val scraper = Scraper.create(fx.config(developer = DeveloperCredentials("", "")))

        assertEquals(AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials), scraper.checkAccount())
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `no account set asks nothing`() = runTest {
        assertEquals(AccountCheck.NoAccount, fx.scraper(fx.config(user = null)).checkAccount())
        assertEquals(AccountCheck.NoAccount, fx.scraper(fx.config(user = UserCredentials("someone", ""))).checkAccount())
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `a server that is down is Offline`() = runTest {
        val config = fx.config()
        fx.server.close()

        assertEquals(AccountCheck.Offline, fx.scraper(config).checkAccount())
    }

    @Test fun `a closed API is ServerClosed`() = runTest {
        fx.server.enqueue(status(401, "API closed for non-members"))
        assertEquals(AccountCheck.ServerClosed, fx.scraper(fx.config()).checkAccount())
    }

    @Test fun `a fully closed API is ServerClosed and pauses the scraper`() = runTest {
        fx.server.enqueue(status(423, "API closed"))
        val scraper = fx.scraper(fx.config())

        assertEquals(AccountCheck.ServerClosed, scraper.checkAccount())
        assertTrue(scraper.scrape(fx.request(fx.rom())) is ScrapeResult.Paused)
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a used up daily quota says when to ask again`() = runTest {
        val now = utc(2026, 10, 21, 10, 0)
        fx.server.enqueue(status(430, "quota"))

        val check = fx.scraper(fx.config(clock = { now })).checkAccount()

        assertEquals(AccountCheck.DailyQuota(ParisTime.nextMidnight(now)), check)
    }

    @Test fun `too many requests is a server error at once, not retried`() = runTest {
        fx.server.enqueue(status(429, "Le nombre de threads est atteint"))

        assertEquals(AccountCheck.ServerError(429), fx.scraper(fx.config()).checkAccount())
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a 200 that is not JSON is a server error`() = runTest {
        fx.server.enqueue(status(200, "<html>maintenance</html>"))

        assertEquals(AccountCheck.ServerError(200), fx.scraper(fx.config()).checkAccount())
    }
}
