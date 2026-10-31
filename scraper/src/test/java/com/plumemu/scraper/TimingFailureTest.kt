package com.plumemu.scraper

import com.plumemu.scraper.timing.GameTiming
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Timing lines of requests that got no answer, and a ROM name with no real extension. */
internal class TimingFailureTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    @Test fun `a request that never got headers logs headers dash and no body time`() = runBlocking<Unit> {
        val scraper = fx.scraper(fx.config())
        val request = fx.request(fx.rom("Sonic.md"))
        fx.server.close()

        scraper.scrape(request)

        val line = fx.timingLines.first { it.startsWith("req ") }
        println("PlumScrapeTiming: $line")
        assertTrue(line, line.contains("code=0"))
        assertTrue(line, Regex(" error=\\w+").containsMatchIn(line))
        assertTrue(line, line.contains(" headers=-"))
        assertTrue(line, !line.contains(" body="))
    }

    @Test fun `a file name without a real extension shows no piece of the title`() {
        val unknown = GameTiming("snes", "Dr. Mario", 1).summary(0)
        val known = GameTiming("snes", "Dr. Mario.SFC", 1).summary(0)
        assertTrue(unknown, unknown.contains(" ext=? "))
        assertTrue(known, known.contains(" ext=sfc "))
        assertTrue(unknown, !unknown.contains("Mario"))
    }

    @Test fun `a logger that throws does not break a scrape`() = runBlocking<Unit> {
        val scraper = fx.scraper(fx.config(timingLog = { error("the logger broke") }))
        fx.server.enqueue(ScraperFixture.status(404, ScraperFixture.NOT_FOUND_BODY))
        fx.server.enqueue(ScraperFixture.json("jeuRecherche-empty.json"))

        assertEquals(ScrapeResult.NoMatch, scraper.scrape(fx.request(fx.rom("Sonic.md"))))
    }
}
