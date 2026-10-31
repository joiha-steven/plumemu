package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.gate.ParisTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ScrapeLimitsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    @Test fun `a used-up daily quota pauses until Paris midnight and the next call asks nothing`() = runTest {
        // 10:00 UTC = 12:00 in Paris (CEST); next Paris midnight is 22:00 UTC.
        val start = utc(2026, 10, 21, 10, 0)
        val clock = { start + testScheduler.currentTime }
        fx.server.enqueue(status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !"))
        val scraper = fx.scraper(fx.config(clock = clock))

        val first = scraper.scrape(fx.request(fx.rom()))
        val second = scraper.scrape(fx.request(fx.rom("Other.md")))

        val resumeAt = utc(2026, 10, 21, 22, 0)
        assertEquals(ScrapeResult.Paused(resumeAt, PauseReason.DailyQuota), first)
        assertEquals(ScrapeResult.Paused(resumeAt, PauseReason.DailyQuota), second)
        assertEquals(1, fx.server.requestCount)
        assertTrue(fx.responseFiles().isEmpty())
    }

    @Test fun `quota of unknown ROMs (431) pauses like the daily quota`() = runTest {
        fx.server.enqueue(status(431, "quota KO"))

        val result = fx.scraper(fx.config(clock = { 0L })).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Paused(ParisTime.nextMidnight(0L), PauseReason.DailyQuota), result)
    }

    @Test fun `the pause ends at its resume time`() = runTest {
        fx.server.enqueue(status(430, "quota"))
        fx.server.enqueue(json("jeuInfos-match.json"))
        var now = utc(2026, 10, 21, 10, 0)
        val scraper = fx.scraper(fx.config(clock = { now }))

        val paused = scraper.scrape(fx.request(fx.rom())) as ScrapeResult.Paused
        now = paused.resumeAtMillis

        assertTrue(scraper.scrape(fx.request(fx.rom())) is ScrapeResult.Matched)
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `an account that reports today's requests used up pauses before the next request`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json") { it.replace("\"requeststoday\": \"12\"", "\"requeststoday\": \"20000\"") })
        val scraper = fx.scraper(fx.config(clock = { 0L }))

        assertTrue(scraper.scrape(fx.request(fx.rom())) is ScrapeResult.Matched)
        val next = scraper.scrape(fx.request(fx.rom("Other.md")))

        assertEquals(ScrapeResult.Paused(ParisTime.nextMidnight(0L), PauseReason.DailyQuota), next)
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a closed API pauses for an hour`() = runTest {
        for (code in listOf(401, 423)) {
            val fixture = ScraperFixture(tmp.newFolder())
            fixture.server.enqueue(status(code, "API ferm\u00e9e"))
            val scraper = fixture.scraper(fixture.config(clock = { 1_000L }))

            assertEquals(
                ScrapeResult.Paused(1_000L + 60 * 60 * 1000, PauseReason.ServerClosed),
                scraper.scrape(fixture.request(fixture.rom())),
            )
            assertTrue(scraper.scrape(fixture.request(fixture.rom())) is ScrapeResult.Paused)
            assertEquals(1, fixture.server.requestCount)
            fixture.close()
        }
    }

    @Test fun `a thread limit is retried after 2 s and 4 s then matches`() = runTest {
        fx.server.enqueue(status(429, "The maximum threads is already used"))
        fx.server.enqueue(status(429, "The maximum threads is already used"))
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val result = scraper.scrape(fx.request(fx.rom()))

        assertTrue(result is ScrapeResult.Matched)
        assertEquals(listOf(0L, 2_000L, 6_000L), fx.requestStarts.toList())
        assertEquals(6_000L, testScheduler.currentTime)
    }

    @Test fun `five thread limits in a row give up as a server error`() = runTest {
        repeat(5) { fx.server.enqueue(status(429, "Too many requests")) }
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val result = scraper.scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Failed(FailureReason.Server(429)), result)
        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L, 30_000L), fx.requestStarts.toList())
        assertTrue(fx.responseFiles().isEmpty())
    }
}

internal fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).run {
        clear()
        set(year, month - 1, day, hour, minute, 0)
        timeInMillis
    }
