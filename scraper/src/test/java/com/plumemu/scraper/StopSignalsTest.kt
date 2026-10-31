package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/** Calls queued behind the one that got a stop (quota, refusal) send nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopSignalsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture
    private val answers = LinkedBlockingDeque<MockResponse>()

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
        // An empty queue answers 500 instead of hanging, so an extra request shows in the count.
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers.pollFirst() ?: status(500, "no answer queued")
        }
    }

    @After fun tearDown() = fx.close()

    private fun requests(count: Int) = (1..count).map { fx.request(fx.rom("Game$it.md", "rom $it")) }

    @Test fun `three parallel scrapes after a quota answer make one request`() = runTest {
        answers += status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !")
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val results = requests(3).map { async { scraper.scrape(it) } }.awaitAll()

        assertEquals(1, fx.server.requestCount)
        assertTrue(results.toString(), results.all { it is ScrapeResult.Paused })
    }

    @Test fun `three parallel scrapes after refused developer credentials make one request`() = runTest {
        answers += status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !")
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val results = requests(3).map { async { scraper.scrape(it) } }.awaitAll()

        assertEquals(1, fx.server.requestCount)
        val refused = ScrapeResult.Unavailable(UnavailableReason.BadDeveloperCredentials)
        assertEquals(List(3) { refused }, results)
    }

    @Test fun `a thread-limit retry waiting while another call gets the quota does not send`() = runTest {
        answers += status(429, "The maximum threads is already used")
        answers += status(430, "quota")
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val results = requests(2).map { async { scraper.scrape(it) } }.awaitAll()

        assertEquals(2, fx.server.requestCount)
        assertTrue(results.toString(), results.all { it is ScrapeResult.Paused })
    }

    @Test fun `a picture download after a quota answer makes no request`() = runTest {
        answers += status(430, "quota")
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))
        assertTrue(scraper.scrape(requests(1).single()) is ScrapeResult.Paused)
        val ref = MediaRef(MediaKind.BoxFront, "us", fx.server.url("/api2/mediaJeu.php?media=box-2D(us)").toString(), "png", null)

        val download = scraper.download(ref, File(tmp.root, "download.part"))

        assertTrue(download.toString(), download is MediaDownload.Paused)
        assertEquals(1, fx.server.requestCount)
    }

    private class MemoryPauseStore(var saved: ScrapeResult.Paused?) : PauseStore {
        override fun load() = saved
        override fun save(pause: ScrapeResult.Paused) {
            saved = pause
        }
        override fun clear() {
            saved = null
        }
    }

    @Test fun `parallel scrapes on a fresh scraper wait for a slow saved-pause load and send nothing`() = runTest {
        val now = 1_000_000L
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = object : PauseStore {
            val saved = ScrapeResult.Paused(now + 20L * 60 * 60 * 1000, PauseReason.DailyQuota)
            override fun load(): ScrapeResult.Paused {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
                return saved
            }
            override fun save(pause: ScrapeResult.Paused) = Unit
            override fun clear() = Unit
        }
        val scraper = fx.scraper(fx.config(clock = { now }, pauseStore = slow, ioDispatcher = Dispatchers.IO))

        val results = requests(2).map { async { scraper.scrape(it) } }
        withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) }
        // The first call is inside load(); the second must be waiting for it, not past it.
        withContext(Dispatchers.IO) { Thread.sleep(300) }
        release.countDown()

        assertEquals(List(2) { slow.saved }, results.awaitAll())
        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `a saved pause more than a day away is dropped, one within a day holds`() = runTest {
        val now = 1_000_000L
        val wrongClock = MemoryPauseStore(ScrapeResult.Paused(now + 48L * 60 * 60 * 1000, PauseReason.DailyQuota))
        answers += ScraperFixture.json("jeuInfos-match.json")

        val first = fx.scraper(fx.config(clock = { now }, pauseStore = wrongClock)).scrape(requests(1).single())

        assertTrue(first.toString(), first is ScrapeResult.Matched)
        assertEquals(null, wrongClock.saved)

        val tonight = MemoryPauseStore(ScrapeResult.Paused(now + 20L * 60 * 60 * 1000, PauseReason.DailyQuota))
        val second = fx.scraper(fx.config(clock = { now }, pauseStore = tonight)).scrape(requests(2).last())

        assertEquals(tonight.saved, second)
        assertEquals(1, fx.server.requestCount)
    }
}
