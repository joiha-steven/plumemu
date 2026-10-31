package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A picture that fails for a reason that may pass (network, server, disk) keeps its game undelivered. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerMediaTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 200))

    /** A match whose box fronts point at [base] (this fake server unless said otherwise). */
    private fun matchPointingAt(base: String = fx.server.url("/").toString()) =
        json("jeuInfos-match.json") { it.replace("https://neoclone.screenscraper.fr/", base) }

    @Test fun `a picture cut off mid-body retries and leaves the game undelivered`() = runTest {
        mediaAnswer = {
            MockResponse.Builder().code(200)
                .body(Buffer().write(ByteArray(200_000)))
                .onResponseBody(SocketEffect.CloseSocket())
                .build()
        }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchPointingAt()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertEquals(1, mediaRequests.size)
    }

    @Test fun `picture server errors keep their games undelivered and end in a retry`() = runTest {
        mediaAnswer = { status(500, "media server unwell") }
        source = FakeScrapeSource(roms.take(3))
        repeat(3) { gameAnswers += matchPointingAt() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertEquals(3, mediaRequests.size)
    }

    @Test fun `a media folder that cannot be written stops the run without a picture request`() = runTest {
        mediaDir.parentFile!!.mkdirs()
        mediaDir.writeText("a file where the folder should be")
        source = FakeScrapeSource(roms.take(2))
        gameAnswers += matchPointingAt()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertTrue(mediaRequests.isEmpty())
    }

    @Test fun `a picture answering 410 is broken, its game is delivered without it and the run is done`() = runTest {
        mediaAnswer = { status(410, "gone") }
        source = FakeScrapeSource(roms.take(3))
        repeat(3) { gameAnswers += matchPointingAt() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(3, source.deliveries.size)
        assertTrue(source.deliveries.all { it.media.isEmpty() })
        // Two pictures tried per game, each one 410.
        assertEquals(6, mediaRequests.size)
        assertTrue(uniqueWork().none { it.state == androidx.work.WorkInfo.State.ENQUEUED })
    }

    @Test fun `a picture on a foreign host gets no request and no credentials`() = runTest {
        MockWebServer().use { foreign ->
            foreign.start()
            source = FakeScrapeSource(roms.take(1))
            gameAnswers += matchPointingAt(base = foreign.url("/").toString())
            host(virtualClock(start), sizes = grid)

            assertEquals(ListenableWorker.Result.success(), runWorker())

            assertEquals(0, foreign.requestCount)
            assertTrue(mediaRequests.isEmpty())
            assertTrue(source.deliveries.single().media.isEmpty())
            assertTrue(fx.logLines.any { it.contains("not ScreenScraper's") })
        }
    }
    @Test fun `box fronts are asked no bigger than the largest size and saved without the md5 check`() = runTest {
        // The JSON's md5 is that of the full-size picture; a scaled one never matches it.
        mediaAnswer = { imageResponse(pngBytes(300, 420)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchPointingAt()
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 240, 336), MediaSize("details", 300, 600)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(2, source.deliveries.single().media.size)
        val asked = mediaRequests.single().toHttpUrl()
        assertEquals("300", asked.queryParameter("maxwidth"))
        assertEquals("600", asked.queryParameter("maxheight"))
    }

    @Test fun `a box front is asked as a JPEG at most 720 tall and a JPEG body is saved at each size, never enlarged`() = runTest {
        mediaAnswer = { imageResponse(jpegBytes(500, 720)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchPointingAt()
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 560, 400), MediaSize("large", 1240, 1080)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val asked = mediaRequests.single().toHttpUrl()
        assertEquals("jpg", asked.queryParameter("outputformat"))
        assertEquals("720", asked.queryParameter("maxheight"))
        assertEquals("1240", asked.queryParameter("maxwidth"))
        val saved = source.deliveries.single().media
        assertEquals(listOf("grid", "large"), saved.map { it.sizeName })
        assertEquals("grid fits the 400 height", 278 to 400, saved[0].width to saved[0].height)
        assertEquals("large is the JPEG's own size, not enlarged", 500 to 720, saved[1].width to saved[1].height)
        assertTrue(saved.all { java.io.File(it.path).isFile })
    }

    @Test fun `a scaled answer that is not a picture is tried with the next box front`() = runTest {
        val answers = java.util.concurrent.ConcurrentLinkedQueue(
            listOf(MockResponse(200, okhttp3.Headers.headersOf(), "NOMEDIA"), imageResponse(pngBytes(200, 280))),
        )
        mediaAnswer = { answers.poll() ?: status(404, "media not found") }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchPointingAt()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(1, source.deliveries.single().media.size)
        assertEquals(2, mediaRequests.size)
    }
}
