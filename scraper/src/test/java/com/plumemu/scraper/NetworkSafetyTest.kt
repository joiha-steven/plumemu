package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Where and how the network is read, and what a log line may show. Real time: real threads. */
class NetworkSafetyTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    @Test fun `cancelling while the body streams stops the call`() = runBlocking {
        val slowBody = MockResponse.Builder()
            .code(200)
            .body(Buffer().writeUtf8("x".repeat(1_000_000)))
            .throttleBody(1_024, 1, TimeUnit.SECONDS)
            .build()
        fx.server.enqueue(slowBody)
        val scraper = fx.scraper(fx.config())

        val job = launch(Dispatchers.Default) { scraper.scrape(fx.request(fx.rom())) }
        withContext(Dispatchers.IO) { fx.takeRequest() }
        delay(300) // headers are in; the body has 1000 s to go
        job.cancel()

        assertNotNull("the scrape did not stop", withTimeoutOrNull(3_000) { job.join() })
        assertTrue(fx.logLines.toString(), fx.logLines.none { it.contains("network error") })
    }

    @Test fun `the body is read on OkHttp's thread, not on the caller's`() = runBlocking {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val readThreads = CopyOnWriteArrayList<String>()
        val scraper = fx.scraper(fx.config()) {
            addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = response.body
                val spied = object : ForwardingSource(body.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        readThreads += Thread.currentThread().name
                        return super.read(sink, byteCount)
                    }
                }
                response.newBuilder().body(spied.buffer().asResponseBody(body.contentType(), body.contentLength())).build()
            }
        }
        val caller = Executors.newSingleThreadExecutor { Thread(it, "caller-thread") }.asCoroutineDispatcher()

        val result = caller.use { withContext(it) { scraper.scrape(fx.request(fx.rom())) } }

        assertTrue(result is ScrapeResult.Matched)
        assertTrue(readThreads.isNotEmpty())
        assertFalse(readThreads.toString(), readThreads.contains("caller-thread"))
    }

    @Test fun `an error body is redacted before it is cut, so no part of a password shows`() = runBlocking {
        val body = "x".repeat(150) + ScraperFixture.DEV_PASSWORD + " identifiants developpeur"
        fx.server.enqueue(status(403, body))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertEquals(ScrapeResult.Unavailable(UnavailableReason.BadDeveloperCredentials), result)
        assertTrue(fx.logLines.any { it.contains("HTTP 403") })
        val start = ScraperFixture.DEV_PASSWORD.take(8)
        assertFalse(fx.logLines.toString(), fx.logLines.any { it.contains(start) })
    }

    @Test fun `the username is kept out of log lines and the stored answer`() = runBlocking {
        fx.server.enqueue(status(500, "user ${ScraperFixture.USER_NAME} is unwell"))
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        scraper.scrape(fx.request(fx.rom()))
        scraper.scrape(fx.request(fx.rom("Other.md", "other rom")))

        val stored = fx.cachedText()
        // The stored answer is trimmed: no ssuser object at all, so not its id either.
        assertTrue(stored.contains("\"jeu\""))
        assertFalse(stored.contains("\"ssuser\""))
        assertFalse(stored.contains(ScraperFixture.USER_NAME))
        assertFalse(fx.logLines.toString(), fx.logLines.any { it.contains(ScraperFixture.USER_NAME) })
    }
}
