package com.plumemu.scraper.work

import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.MediaSize
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

/**
 * One save at a time in the process: a person's pick and a run saving the same game must not
 * delete each other's temp files, and a run's start must not delete a download in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class RunPicturesLockTest {
    @get:Rule val tmp = TemporaryFolder()

    private val mediaDir: File get() = File(tmp.root, "media")
    private val sizes = listOf(MediaSize("grid", 200, 200))

    /** A scraper whose picture downloads wait for [release]; [downloads] counts them. */
    private inner class HangingScraper : Scraper {
        val release = CompletableDeferred<Unit>()
        var downloads = 0
        override val available: Boolean = true

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult = ScrapeResult.NoMatch

        override suspend fun download(media: com.plumemu.scraper.MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload {
            downloads++
            release.await()
            target.writeBytes(pngBytes(300, 400))
            return MediaDownload.Done(target)
        }

        override suspend fun checkAccount(): AccountCheck = AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials)
    }

    private val result = ScrapeResult.Matched(
        match = GameMatch("1001", MatchedBy.PersonChoice),
        info = GameInfo("Zork", null, null, null, null, null, null, null),
        media = listOf(MediaRef(MediaKind.BoxFront, "us", "https://example.invalid/box")),
        details = GameDetails(),
    )

    private fun TestScope.pictures(scraper: Scraper) =
        RunPictures(scraper, mediaDir, sizes, MediaSaver(mediaDir, false, {}), {}, StandardTestDispatcher(testScheduler))

    private fun partFiles() = mediaDir.listFiles { file -> file.name.endsWith(".part") }.orEmpty().toList()

    @Test fun `a run's start leaves a download in flight alone and removes a dead process's leftover`() = runTest {
        val scraper = HangingScraper()
        val pictures = pictures(scraper)
        val save = async { pictures.save(result, MediaKind.BoxFront, fresh = true) }
        runCurrent()
        assertEquals("the download is in flight", 1, partFiles().size)
        val leftover = File(mediaDir, "download-dead-process.part").apply { writeBytes(byteArrayOf(1)) }

        pictures(scraper).removeLeftoverDownloads()
        assertFalse("a killed process's download is removed", leftover.exists())
        assertEquals("the download in flight is not deleted", 1, partFiles().size)

        scraper.release.complete(Unit)
        val saved = save.await() as PictureOutcome.Saved
        assertEquals(1, saved.media.size)
        assertTrue(File(saved.media.single().path).isFile)
        assertEquals("the finished download's temp file is gone", 0, partFiles().size)
    }

    /** Two ROMs of one ScreenScraper game (USA and USA Rev 1) at once: one download, both get its files. */
    @Test fun `two saves of the same game at once share one download`() = runTest {
        val scraper = HangingScraper()
        val first = async { pictures(scraper).save(result, MediaKind.BoxFront, fresh = false) }
        runCurrent()
        val second = async { pictures(scraper).save(result, MediaKind.BoxFront, fresh = false) }
        runCurrent()
        assertEquals("the second waits for the first's download", 1, scraper.downloads)

        scraper.release.complete(Unit)
        val a = first.await() as PictureOutcome.Saved
        val b = second.await() as PictureOutcome.Saved
        assertEquals(1, scraper.downloads)
        assertEquals("the same files", a.media, b.media)
        assertTrue(a.media.all { File(it.path).isFile })
        assertEquals("no temp file left", 0, partFiles().size)
    }

    @Test fun `a save waiting on a cancelled download downloads itself`() = runTest {
        val scraper = HangingScraper()
        val first = async { pictures(scraper).save(result, MediaKind.BoxFront, fresh = false) }
        runCurrent()
        val second = async { pictures(scraper).save(result, MediaKind.BoxFront, fresh = false) }
        runCurrent()
        first.cancel()
        runCurrent()
        assertEquals("the waiter started its own download", 2, scraper.downloads)

        scraper.release.complete(Unit)
        assertTrue(second.await() is PictureOutcome.Saved)
        assertEquals(0, partFiles().size)
    }

    /**
     * The run's games download together, and only the save (decode, downsize,
     * write) takes the process-wide lock, which a person's pick ("Fix box art...", its own
     * [RunPictures]) shares.
     */
    @Test fun `downloads overlap while saves wait for the one save lock, a pick's included`() = runTest {
        val scraper = HangingScraper()
        val holder = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        launch { RunPictures.holdingSaveLock { held.complete(Unit); holder.await() } }
        runCurrent()
        held.await()
        scraper.release.complete(Unit)
        val run = async { pictures(scraper).save(result, MediaKind.BoxFront, fresh = true) }
        val pick = async { pictures(scraper).save(result.copy(match = GameMatch("1002", MatchedBy.PersonChoice)), MediaKind.BoxFront, fresh = true) }
        runCurrent()
        assertEquals("both downloads ran while the lock was held", 2, scraper.downloads)
        assertFalse("the run's save waits for the lock", run.isCompleted)
        assertFalse("the pick's save waits for the lock", pick.isCompleted)

        holder.complete(Unit)
        assertTrue(run.await() is PictureOutcome.Saved)
        assertTrue(pick.await() is PictureOutcome.Saved)
    }
}
