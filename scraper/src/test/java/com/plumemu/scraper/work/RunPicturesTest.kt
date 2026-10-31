package com.plumemu.scraper.work

import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The one way pictures are saved: what the run uses, called directly as the
 * person's pick ("Fix box art...") calls it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class RunPicturesTest : WorkerTestBase() {
    private val sizes = listOf(MediaSize("grid", 200, 200), MediaSize("large", 400, 400))

    private fun file(size: String) = File(mediaDir, "1001/box-front-$size.webp")

    private suspend fun TestScope.matched(): Pair<ScrapeResult.Matched, RunPictures> {
        gameAnswers += json("jeuInfos-match.json") { it.replace("https://neoclone.screenscraper.fr/", fx.server.url("/").toString()) }
        val work = host(virtualClock(start), sizes = sizes)
        val result = work.scraper.scrape(roms[0]) as ScrapeResult.Matched
        return result to RunPictures(work.scraper, mediaDir, sizes, lowRamDevice = false, log = { fx.logLines += it })
    }

    @Test fun `a box front is saved at every size`() = runTest {
        mediaAnswer = { imageResponse(pngBytes(300, 600)) }
        val (result, pictures) = matched()

        val saved = pictures.save(result, MediaKind.BoxFront, fresh = true) as PictureOutcome.Saved

        assertEquals(listOf("grid", "large"), saved.media.map { it.sizeName })
        assertEquals(100 to 200, MediaSaver.bounds(file("grid-200x200")))
        assertEquals(200 to 400, MediaSaver.bounds(file("large-400x400")))
    }

    @Test fun `saved files are reused unless fresh, and a fresh save replaces them`() = runTest {
        mediaAnswer = { imageResponse(pngBytes(300, 600)) }
        val (result, pictures) = matched()
        pictures.save(result, MediaKind.BoxFront, fresh = false)
        val asked = mediaRequests.size

        pictures.save(result, MediaKind.BoxFront, fresh = false)
        assertEquals("not fresh: nothing downloaded", asked, mediaRequests.size)

        mediaAnswer = { imageResponse(pngBytes(600, 300)) }
        val again = pictures.save(result, MediaKind.BoxFront, fresh = true) as PictureOutcome.Saved
        assertEquals(asked + 1, mediaRequests.size)
        assertEquals(200 to 100, MediaSaver.bounds(file("grid-200x200")))
        assertEquals(listOf(200 to 100, 400 to 200), again.media.map { it.width to it.height })
    }

    @Test fun `a server error is told, and the saved files stay`() = runTest {
        mediaAnswer = { imageResponse(pngBytes(300, 600)) }
        val (result, pictures) = matched()
        pictures.save(result, MediaKind.BoxFront, fresh = false)

        mediaAnswer = { status(500, "unwell") }
        val outcome = pictures.save(result, MediaKind.BoxFront, fresh = true)

        assertTrue("got $outcome", outcome is PictureOutcome.Server)
        assertEquals(100 to 200, MediaSaver.bounds(file("grid-200x200")))
    }

    @Test fun `a game with no picture of the kind saves nothing`() = runTest {
        mediaAnswer = { imageResponse(pngBytes(300, 600)) }
        val (result, pictures) = matched()

        val outcome = pictures.save(result.copy(media = emptyList()), MediaKind.BoxFront, fresh = true)

        assertEquals(PictureOutcome.Saved(emptyList()), outcome)
        assertEquals(0, mediaRequests.size)
    }
}
