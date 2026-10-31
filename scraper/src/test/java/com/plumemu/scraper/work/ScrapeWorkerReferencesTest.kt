package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots, title screens and manuals are kept as references only: the run downloads the box
 * front and the disc or cartridge, nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerReferencesTest : WorkerTestBase() {
    @Test fun `screenshot, title screen and manual references are delivered but never requested`() = runTest {
        mediaAnswer = { imageResponse(pngBytes(200, 280)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += json("jeuInfos-console-full.json") {
            it.replace("https://neoclone.screenscraper.fr/", fx.server.url("/").toString())
        }
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 200, 200)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        // The disc pass's delivery holds both pictures.
        val delivery = source.supportDeliveries.single()
        assertEquals(listOf(MediaKind.BoxFront, MediaKind.Support), delivery.media.map { it.kind })
        assertEquals(2, mediaRequests.size)
        assertTrue(mediaRequests[0].contains("media=box-2D(us)"))
        assertTrue(mediaRequests[1].contains("media=support-2D(us)"))
        for (type in listOf("media=ss(", "media=sstitle(", "media=manuel(")) {
            assertTrue("$type was requested", mediaRequests.none { it.contains(type) })
        }
        val references = (delivery.result as ScrapeResult.Matched).details.media.map { it.kind }
        assertTrue(references.containsAll(listOf(MediaKind.Screenshot, MediaKind.TitleScreen, MediaKind.Manual)))
    }
}
