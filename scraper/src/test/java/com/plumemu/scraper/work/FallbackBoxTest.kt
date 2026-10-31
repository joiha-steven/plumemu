package com.plumemu.scraper.work

import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.FileRomSource
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.MediaSize
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The run's fallback step: the per-game memory of failed runs ([FallbackMemory]). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class FallbackBoxTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A scraper whose fallback always fails; counts the questions. */
    private class FailingScraper : Scraper {
        var asked = 0
        override val available: Boolean = true
        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = ScrapeResult.NoMatch
        override suspend fun checkAccount(): AccountCheck = AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials)
        override suspend fun download(media: com.plumemu.scraper.MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload =
            MediaDownload.Broken
        override suspend fun fallbackArt(request: ScrapeRequest, stopped: Set<String>): FallbackArt {
            asked++
            return FallbackArt.Failed("libretro-failed")
        }
    }

    @Test fun `a game that failed in three runs is delivered without art once, then asked again`() = runTest {
        val scraper = FailingScraper()
        val request = ScrapeRequest(FileRomSource(File(tmp.root, "Game.nds")), "nds", "Game", listOf("us"), listOf("en"))
        val dir = File(tmp.root, "media")
        // One FallbackBox per run, as ScrapeRun makes them; the memory is the scraper's.
        fun run() = FallbackBox(scraper, RunPictures(scraper, dir, listOf(MediaSize("grid", 200, 200)), MediaSaver(dir, false, {}), {}, StandardTestDispatcher(testScheduler)), {})

        repeat(3) { assertEquals(FallbackBox.Result.Failed, run().fetch(request)) }
        assertEquals(FallbackBox.Result.None, run().fetch(request))
        assertEquals("the fourth run did not ask", 3, scraper.asked)

        // The count was cleared: a later run asks again, and fails once more.
        assertEquals(FallbackBox.Result.Failed, run().fetch(request))
        assertEquals(4, scraper.asked)
    }
}
