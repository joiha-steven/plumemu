package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `ScrapeRequest.fresh`: the person asked for a game again, so its answer is fetched
 * again even when the 30-day cache holds one; the new answer replaces the cached one.
 */
class FreshRequestTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun renamed(title: String) = json("jeuInfos-match.json") { it.replace("\"Sonic The Hedgehog\"}", "\"$title\"}") }

    private fun titleOf(result: ScrapeResult) = (result as ScrapeResult.Matched).info.title

    @Test fun `a request that is not fresh is answered from the cache`() = runTest {
        fx.server.enqueue(renamed("First Answer"))
        val scraper = fx.scraper(fx.config())
        val request = fx.request(fx.rom())

        assertEquals("First Answer", titleOf(scraper.scrape(request)))
        assertEquals("First Answer", titleOf(scraper.scrape(request)))

        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a fresh request skips the cached answer and stores the new one`() = runTest {
        fx.server.enqueue(renamed("First Answer"))
        fx.server.enqueue(renamed("Second Answer"))
        val scraper = fx.scraper(fx.config())
        val request = fx.request(fx.rom())

        assertEquals("First Answer", titleOf(scraper.scrape(request)))
        assertEquals("Second Answer", titleOf(scraper.scrape(request.copy(fresh = true))))
        assertEquals(2, fx.server.requestCount)

        // The fresh answer replaced the cached one: the next plain request reads it, no request.
        assertEquals("Second Answer", titleOf(scraper.scrape(request)))
        assertEquals(2, fx.server.requestCount)
        assertEquals(1, fx.responseFiles().size)
    }

    @Test fun `a fresh request that fails keeps the cached answer`() = runTest {
        fx.server.enqueue(renamed("First Answer"))
        fx.server.enqueue(status(500, "server unwell"))
        val scraper = fx.scraper(fx.config())
        val request = fx.request(fx.rom())

        titleOf(scraper.scrape(request))
        assertEquals(ScrapeResult.Failed(FailureReason.Server(500)), scraper.scrape(request.copy(fresh = true)))

        assertEquals("First Answer", titleOf(scraper.scrape(request)))
        assertEquals(2, fx.server.requestCount)
    }
}
