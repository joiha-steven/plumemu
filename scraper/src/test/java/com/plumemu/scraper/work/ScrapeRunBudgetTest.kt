package com.plumemu.scraper.work

import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.FailureReason
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.media.MediaSaver
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** The run's end when its time budget is used: Continue only if it got something done. */
class ScrapeRunBudgetTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    /** Every scrape fails as RomUnreadable and takes [step] ms on the run's clock. */
    private class SlowUnreadable(private val time: AtomicLong, private val step: Long) : Scraper {
        override val available = true
        override suspend fun scrape(request: ScrapeRequest): ScrapeResult {
            time.addAndGet(step)
            return ScrapeResult.Failed(FailureReason.RomUnreadable)
        }
        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?) =
            MediaDownload.Broken
        override suspend fun checkAccount(): AccountCheck = AccountCheck.NoAccount
    }

    @Test fun `a run that used its budget on unreadable ROMs retries instead of continuing at once`() = runTest {
        val requests = (1..10).map { fx.request(fx.rom("Game$it.md", "rom $it")) }
        val time = AtomicLong(0)
        val work = ScraperWork(
            SlowUnreadable(time, step = 3 * 60_000), FakeScrapeSource(requests), File(tmp.root, "media"), emptyList(),
            lowRamDevice = false, clock = { time.get() }, log = { fx.logLines += it }, runBudgetMillis = 8 * 60_000,
        )
        val run = ScrapeRun(work, MediaSaver(File(tmp.root, "media"), false, {}))

        assertEquals(ScrapeRun.End.Retry, run.run())
    }
}
