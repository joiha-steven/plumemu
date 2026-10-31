package com.plumemu.scraper.work

import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.media.MediaSaver
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A game being played stops the run between games; the next run goes on with what is left. */
class ScrapeRunHoldOffTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private class Counting : Scraper {
        val asked = AtomicInteger()
        override val available = true

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult {
            asked.incrementAndGet()
            return ScrapeResult.NoMatch
        }

        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?) = MediaDownload.Broken

        override suspend fun checkAccount(): AccountCheck = AccountCheck.NoAccount
    }

    private fun work(scraper: Scraper, source: ScrapeSource, holdOff: suspend () -> Boolean) =
        ScraperWork(
            scraper, source, File(tmp.root, "media"), emptyList(),
            lowRamDevice = false, log = { fx.logLines += it }, holdOff = holdOff,
        )

    @Test fun `a game started mid-run stops it between games and the next run asks only what is left`() = runTest {
        val requests = (1..6).map { fx.request(fx.rom("Game$it.md", "rom $it")) }
        val source = FakeScrapeSource(requests)
        val scraper = Counting()
        val first = ScrapeRun(work(scraper, source) { scraper.asked.get() >= 2 }, MediaSaver(File(tmp.root, "media"), false, {}))

        assertEquals(ScrapeRun.End.HeldOff, first.run())
        val askedFirst = scraper.asked.get()
        assertTrue("stopped early: $askedFirst of 6", askedFirst in 2 until 6)
        assertEquals("every game asked was delivered", askedFirst, source.deliveredKeys.size)
        assertTrue(fx.logLines.any { it.contains("held off") })

        val second = ScrapeRun(work(scraper, source) { false }, MediaSaver(File(tmp.root, "media"), false, {}))
        assertEquals(ScrapeRun.End.Done, second.run())
        assertEquals("no game asked twice", 6, scraper.asked.get())
        assertEquals(requests.map { it.rom.key }.toSet(), source.deliveredKeys.toSet())
        assertEquals(6, source.deliveredKeys.size)
    }

    @Test fun `a run that begins while a game is played asks nothing`() = runTest {
        val source = FakeScrapeSource(listOf(fx.request(fx.rom("Game.md", "rom"))))
        val scraper = Counting()
        val run = ScrapeRun(work(scraper, source) { true }, MediaSaver(File(tmp.root, "media"), false, {}))
        assertEquals(ScrapeRun.End.HeldOff, run.run())
        assertEquals(0, scraper.asked.get())
    }
}
