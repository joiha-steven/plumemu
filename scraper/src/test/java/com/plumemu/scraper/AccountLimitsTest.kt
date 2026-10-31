package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [Scraper.accountLimits] follows the `ssuser` of every answer. */
class AccountLimitsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun accountAnswer(ssuser: String) = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"header": {"APIversion": "2.0"}, "response": {"serveurs": {"closefornomember": "0"}, "ssuser": $ssuser}}""",
    )

    @Test fun `limits are unknown until an answer carries them`() = runTest {
        assertNull(fx.scraper(fx.config()).accountLimits.value)
    }

    @Test fun `a jeuInfos answer sets the limits`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        scraper.scrape(fx.request(fx.rom()))

        assertEquals(
            AccountLimits(
                maxThreads = 1,
                maxRequestsPerMinute = 300,
                requestsToday = 12,
                maxRequestsPerDay = 20_000,
                maxDownloadKilobytesPerSecond = 256,
            ),
            scraper.accountLimits.value,
        )
    }

    @Test fun `the sign-in check sets the limits and a later answer replaces them`() = runTest {
        fx.server.enqueue(
            accountAnswer(
                """{"id": "test-user", "numid": "1234", "maxthreads": "4", "requeststoday": "12", "maxrequestsperday": "20000", "maxdownloadspeed": "128"}""",
            ),
        )
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "numid": "1234", "requeststoday": "40"}"""))
        val scraper = fx.scraper(fx.config())

        scraper.checkAccount()
        assertEquals(AccountLimits(maxThreads = 4, requestsToday = 12, maxRequestsPerDay = 20_000, maxDownloadKilobytesPerSecond = 128), scraper.accountLimits.value)

        scraper.checkAccount()
        // The number the second answer left out stays from the first.
        assertEquals(AccountLimits(maxThreads = 4, requestsToday = 40, maxRequestsPerDay = 20_000, maxDownloadKilobytesPerSecond = 128), scraper.accountLimits.value)
    }

    @Test fun `an answer as anonymous sets no limits`() = runTest {
        fx.server.enqueue(accountAnswer("""{"id": "test-user", "numid": "0", "maxthreads": "1"}"""))
        val scraper = fx.scraper(fx.config())

        scraper.checkAccount()

        assertNull(scraper.accountLimits.value)
    }

    @Test fun `the limits show no name or id`() {
        val shown = AccountLimits(maxThreads = 1, requestsToday = 5).toString()

        assertFalse(shown.contains(ScraperFixture.USER_NAME))
    }
}
