package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Download missing / Re-download: a fresh request downloads its pictures again and
 * replaces the saved files; on any failure the old file stays as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerFreshTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 200))

    private fun match() = json("jeuInfos-match.json") { it.replace("https://neoclone.screenscraper.fr/", fx.server.url("/").toString()) }

    private val boxFrontFile: File get() = File(mediaDir, "1001/box-front-grid-200x200.webp")

    /** A first run saves a 300x400 box front as 150x200; the game is then asked again as [fresh]. */
    private suspend fun TestScope.firstRunThenAskAgain(fresh: Boolean): Int {
        mediaAnswer = { imageResponse(pngBytes(300, 400)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += match()
        host(virtualClock(start), sizes = grid)
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(150 to 200, MediaSaver.bounds(boxFrontFile))
        val requestsBefore = asked.size + mediaRequests.size
        source = FakeScrapeSource(listOf(roms[0].copy(fresh = fresh)))
        host(virtualClock(start), sizes = grid)
        return requestsBefore
    }

    /** The match with a us disc picture; disc requests are told apart by their `media` value. */
    private fun matchWithSupport() = json("jeuInfos-match.json") {
        val base = fx.server.url("/").toString()
        val support = """{"type": "support-2D", "url": "${base}api2/mediaJeu.php?systemeid=1&jeuid=1001&media=support-2D(us)", "region": "us", "format": "png"}"""
        it.replace("https://neoclone.screenscraper.fr/", base).replace(""""medias": [""", """"medias": [$support, """)
    }

    @Test fun `a fresh request whose disc picture fails on the server keeps the earlier disc picture`() = runTest {
        var discAnswer: () -> MockResponse = { imageResponse(pngBytes(300, 300)) }
        mediaAnswer = { if (mediaRequests.last().contains("support-2D")) discAnswer() else imageResponse(pngBytes(300, 400)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchWithSupport()
        host(virtualClock(start), sizes = grid)
        assertEquals(ListenableWorker.Result.success(), runWorker())
        val disc = source.supportDeliveries.single().media.single { it.kind == MediaKind.Support }

        source = FakeScrapeSource(listOf(roms[0].copy(fresh = true)))
        host(virtualClock(start), sizes = grid)
        gameAnswers += matchWithSupport()
        discAnswer = { status(500, "media server unwell") }
        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(
            "the main pass keeps the earlier disc with the new box front",
            listOf(MediaKind.BoxFront, MediaKind.Support),
            source.mainDeliveries.single().media.map { it.kind },
        )
        val media = source.supportDeliveries.single().media
        assertEquals(listOf(MediaKind.BoxFront, MediaKind.Support), media.map { it.kind })
        assertEquals("the earlier disc picture is delivered again", disc, media[1])
        assertEquals("the disc pass read the answer from the cache", 2, asked.size)
        assertTrue(File(disc.path).isFile)
        assertTrue("its disc request did go out", mediaRequests.count { it.contains("support-2D") } == 2)
    }

    @Test fun `a request that is not fresh keeps the saved picture and asks nothing`() = runTest {
        val before = firstRunThenAskAgain(fresh = false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(before, asked.size + mediaRequests.size)
        val media = source.deliveries.single().media.single()
        assertEquals(150 to 200, media.width to media.height)
    }

    @Test fun `a fresh request asks the game and its picture again and replaces the saved file`() = runTest {
        firstRunThenAskAgain(fresh = true)
        gameAnswers += match()
        mediaAnswer = { imageResponse(pngBytes(400, 300)) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals("the game was asked again, not read from the cache", 2, asked.size)
        assertEquals(2, mediaRequests.size)
        val media = source.deliveries.single().media.single()
        assertEquals(MediaKind.BoxFront, media.kind)
        assertEquals(200 to 150, media.width to media.height)
        assertEquals(boxFrontFile.absolutePath, media.path)
        assertEquals(200 to 150, MediaSaver.bounds(boxFrontFile))
        assertEquals(listOf(boxFrontFile.name), boxFrontFile.parentFile!!.list()!!.toList())
    }

    @Test fun `a fresh request whose new pictures are broken keeps and delivers the saved file`() = runTest {
        firstRunThenAskAgain(fresh = true)
        gameAnswers += match()
        mediaAnswer = { status(404, "media not found") }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val media = source.deliveries.single().media.single()
        assertEquals(boxFrontFile.absolutePath, media.path)
        assertEquals(150 to 200, media.width to media.height)
        assertEquals(150 to 200, MediaSaver.bounds(boxFrontFile))
    }

    @Test fun `a fresh picture cut off mid-download leaves the saved file whole and the game undelivered`() = runTest {
        firstRunThenAskAgain(fresh = true)
        gameAnswers += match()
        mediaAnswer = {
            MockResponse.Builder().code(200)
                .body(Buffer().write(ByteArray(200_000)))
                .onResponseBody(SocketEffect.CloseSocket())
                .build()
        }

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertEquals(150 to 200, MediaSaver.bounds(boxFrontFile))
        assertEquals(listOf(boxFrontFile.name), boxFrontFile.parentFile!!.list()!!.toList())
    }
}
