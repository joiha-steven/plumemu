package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.fixture
import com.plumemu.scraper.gate.RequestGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RequestGateTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun matchBody(maxThreads: Int) =
        fixture("jeuInfos-match.json").replace("\"maxthreads\": \"1\"", "\"maxthreads\": \"$maxThreads\"")

    @Test fun `with maxthreads 1 two parallel scrapes never overlap and start 1_2 s apart`() = runTest {
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(150) // long enough that an overlap would be seen
                return MockResponse(200, Headers.headersOf(), matchBody(1))
            }
        }
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))

        val results = listOf(fx.rom("A.md", "a"), fx.rom("B.md", "b"))
            .map { rom -> async { scraper.scrape(fx.request(rom)) } }.awaitAll()

        assertTrue(results.all { it is ScrapeResult.Matched })
        assertEquals(1, fx.maxInFlight.get())
        val (first, second) = fx.requestStarts.toList()
        assertTrue("starts $first and $second", second - first >= RequestGate.MIN_SPACING_MILLIS)
    }

    @Test fun `with maxthreads 2 two parallel scrapes run together`() = runTest {
        val bothArrived = CountDownLatch(2)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.queryParameter("romnom") != "Warmup.md") {
                    bothArrived.countDown()
                    bothArrived.await(5, TimeUnit.SECONDS)
                }
                return MockResponse(200, Headers.headersOf(), matchBody(2))
            }
        }
        val scraper = fx.scraper(fx.config(clock = { testScheduler.currentTime }))
        // The first answer tells the scraper the account allows 2 threads.
        scraper.scrape(fx.request(fx.rom("Warmup.md", "w")))

        val results = listOf(fx.rom("A.md", "a"), fx.rom("B.md", "b"))
            .map { rom -> async { scraper.scrape(fx.request(rom)) } }.awaitAll()

        assertTrue(results.all { it is ScrapeResult.Matched })
        assertEquals(0L, bothArrived.count)
        assertEquals(2, fx.maxInFlight.get())
    }

    @Test fun `a slot waits its spacing between two of its requests`() = runTest {
        val gate = RequestGate(clock = { testScheduler.currentTime })
        val starts = mutableListOf<Long>()

        repeat(3) { gate.run(stopped = { error("no stop expected") }) { starts += testScheduler.currentTime; delay(500) } }

        assertEquals(listOf(0L, 1_200L, 2_400L), starts)
    }

    @Test fun `the thread limit follows the account but drops to 1 after a thread-limit answer`() = runTest {
        val gate = RequestGate(clock = { testScheduler.currentTime })
        assertEquals(1, gate.threadLimit)
        gate.updateFromAccount(maxThreads = 4, maxRequestsPerMinute = null)
        assertEquals(4, gate.threadLimit)
        gate.updateFromAccount(maxThreads = 0, maxRequestsPerMinute = null)
        assertEquals(4, gate.threadLimit)
        gate.onThreadLimit()
        gate.updateFromAccount(maxThreads = 4, maxRequestsPerMinute = null)
        assertEquals(1, gate.threadLimit)
    }

    @Test fun `a tight per-minute limit widens the spacing`() = runTest {
        val gate = RequestGate(clock = { testScheduler.currentTime })
        gate.updateFromAccount(maxThreads = 2, maxRequestsPerMinute = 30)

        // 2 slots share 30 requests a minute: each slot starts one every 4 s.
        assertEquals(4_000L, gate.spacingMillis)
    }

    @Test fun `with three slots the fourth request waits for a free one`() = runTest {
        val gate = RequestGate(clock = { testScheduler.currentTime })
        gate.updateFromAccount(maxThreads = 3, maxRequestsPerMinute = null)
        var running = 0
        var most = 0

        val jobs = List(4) {
            launch {
                gate.run(stopped = { error("no stop expected") }) {
                    running++
                    most = maxOf(most, running)
                    delay(1_000)
                    running--
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals(3, most)
    }

    @Test fun `a cancelled request gives its slot back`() = runTest {
        val gate = RequestGate(clock = { testScheduler.currentTime })
        val stuck = launch { gate.run(stopped = { error("no stop expected") }) { delay(Long.MAX_VALUE) } }
        testScheduler.advanceTimeBy(10)
        stuck.cancel()

        var ran = false
        gate.run(stopped = { error("no stop expected") }) { ran = true }

        assertTrue(ran)
    }

    @Test fun `after the limit drops, requests already running still hold the slots`() = runTest {
        val gate = RequestGate({ testScheduler.currentTime }, minSpacingMillis = 0)
        gate.updateFromAccount(maxThreads = 4, maxRequestsPerMinute = null)
        val finish = List(4) { CompletableDeferred<Unit>() }
        var running = 0
        val first = finish.map { done ->
            launch { gate.run(stopped = { error("no stop expected") }) { running++; done.await(); running-- } }
        }
        runCurrent()
        assertEquals(4, running)

        gate.onThreadLimit()
        var fifthRan = false
        val fifth = launch { gate.run(stopped = { error("no stop expected") }) { fifthRan = true } }
        finish.take(3).forEach { it.complete(Unit) }
        runCurrent()
        assertEquals(1, running)
        assertFalse(fifthRan)

        finish.last().complete(Unit)
        (first + fifth).joinAll()
        assertTrue(fifthRan)
    }
}
