package com.plumemu.scraper.media

import android.graphics.Bitmap
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.work.imageResponse
import com.plumemu.scraper.work.md5Of
import com.plumemu.scraper.work.pngBytes
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaSaveTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture
    private lateinit var mediaDir: File
    private val encoded = mutableListOf<Bitmap.Config?>()
    private val sizes = listOf(MediaSize("grid", 200, 200), MediaSize("details", 300, 600))

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
        mediaDir = File(tmp.root, "media")
    }

    @After fun tearDown() = fx.close()

    private fun saver(lowRam: Boolean) = MediaSaver(mediaDir, lowRam, { fx.logLines += it }) { bitmap, file ->
        encoded += bitmap.config
        MediaSaver.encodeWebp(bitmap, file)
    }

    private fun ref(md5: String?) = MediaRef(
        kind = MediaKind.BoxFront,
        region = "us",
        url = fx.server.url("/api2/mediaJeu.php?systemeid=1&jeuid=1001&media=box-2D(us)").toString(),
        format = "png",
        md5 = md5,
    )

    private suspend fun download(answer: MockResponse, md5: String?): Pair<MediaDownload, File> {
        fx.server.enqueue(answer)
        val target = File(tmp.root, "download.part")
        val scraper = fx.scraper(fx.config(clock = { 0L }))
        return scraper.download(ref(md5), target) to target
    }

    @Test fun `a 400x560 PNG is saved as WebP at each size, aspect kept, never enlarged`() = runTest {
        val png = pngBytes(400, 560)
        val (result, file) = download(imageResponse(png), md5Of(png))
        assertTrue(result is MediaDownload.Done)

        val saved = saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, sizes + MediaSize("huge", 2000, 2000)).files()

        assertEquals(listOf(143 to 200, 300 to 420, 400 to 560), saved.map { it.width to it.height })
        for (media in saved) {
            val path = File(media.path)
            assertEquals(File(mediaDir, "1001"), path.parentFile)
            val size = (sizes + MediaSize("huge", 2000, 2000)).single { it.name == media.sizeName }
            assertEquals("box-front-${size.name}-${size.maxWidth}x${size.maxHeight}.webp", path.name)
            assertEquals(media.width to media.height, MediaSaver.bounds(path))
            assertEquals("RIFF", path.readBytes().copyOfRange(0, 4).decodeToString())
            assertEquals(400.0 / 560, media.width.toDouble() / media.height, 0.01)
        }
        assertTrue(encoded.all { it == Bitmap.Config.ARGB_8888 })
        assertTrue(mediaDir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun `a low-RAM device decodes and scales in RGB_565`() = runTest {
        val png = pngBytes(400, 560)
        val (_, file) = download(imageResponse(png), md5 = null)

        val saved = saver(lowRam = true).save(file, "1001", MediaKind.BoxFront, sizes).files()

        assertEquals(2, saved.size)
        assertEquals(listOf(Bitmap.Config.RGB_565, Bitmap.Config.RGB_565), encoded)
    }

    @Test fun `the sample size is the largest power of two that stays at or above the target`() {
        assertEquals(1, MediaSaver.sampleSize(400, 560, 300, 420))
        assertEquals(2, MediaSaver.sampleSize(400, 560, 143, 200))
        assertEquals(4, MediaSaver.sampleSize(1600, 2240, 300, 420))
        assertEquals(1, MediaSaver.sampleSize(100, 100, 100, 100))
    }

    @Test fun `a wrong md5 is ignored and the older copy stays`() = runTest {
        val older = olderCopy()

        val (result, file) = download(imageResponse(pngBytes(400, 560)), md5 = "0123456789abcdef0123456789abcdef")

        assertEquals(MediaDownload.Broken, result)
        assertFalse(file.exists())
        assertArrayEquals(OLD_BYTES, older.readBytes())
        assertTrue(fx.logLines.any { it.contains("md5 mismatch") })
    }

    @Test fun `a body that is not an image saves nothing and the older copy stays`() = runTest {
        val older = olderCopy()
        val (result, file) = download(MockResponse(200, okhttp3.Headers.headersOf(), "NOMEDIA"), md5 = null)
        assertTrue(result is MediaDownload.Done)

        val saved = saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, sizes)

        assertEquals(MediaSaver.Saved.NotAnImage, saved)
        assertArrayEquals(OLD_BYTES, older.readBytes())
        assertEquals(listOf(older.name), older.parentFile!!.list()!!.toList())
        assertTrue(fx.logLines.any { it.contains("not an image") })
    }

    @Test fun `an error answer is a broken download, not a crash`() = runTest {
        val (result, file) = download(status(404, "Erreur : Media non trouve"), md5 = null)

        assertEquals(MediaDownload.Broken, result)
        assertFalse(file.exists())
    }

    @Test fun `a game id that is not a plain id never becomes a path`() = runTest {
        val png = pngBytes(40, 56)
        val (_, file) = download(imageResponse(png), md5 = null)

        val saved = saver(lowRam = false).save(file, "../escape", MediaKind.BoxFront, sizes)

        assertEquals(MediaSaver.Saved.NotAnImage, saved)
        assertFalse(File(tmp.root, "escape").exists())
    }

    @Test fun `credentials reach the server in the request only, never the result or a log line`() = runTest {
        val png = pngBytes(400, 560)
        val (_, file) = download(imageResponse(png), md5Of(png))

        val sent = fx.takeRequest().url
        assertEquals(ScraperFixture.DEV_ID, sent.queryParameter("devid"))
        assertEquals(ScraperFixture.DEV_PASSWORD, sent.queryParameter("devpassword"))
        assertEquals(ScraperFixture.USER_PASSWORD, sent.queryParameter("sspassword"))
        assertEquals("box-2D(us)", sent.queryParameter("media"))
        val saved = saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, sizes).toString()
        val kept = saved + ref(null) + fx.logLines.joinToString("\n")
        for (secret in ScraperFixture.SECRETS) assertFalse(secret, kept.contains(secret))
    }

    @Test fun `new pixels for a size name are saved again and the old file goes`() = runTest {
        val png = pngBytes(400, 560)
        val (_, file) = download(imageResponse(png), md5 = null)
        val grid = MediaSize("grid", 200, 200)
        val smallerGrid = MediaSize("grid", 100, 100)
        val first = saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, listOf(grid)).files().single()

        assertEquals(first.path, saver(lowRam = false).existing("1001", MediaKind.BoxFront, listOf(grid))?.single()?.path)
        assertEquals(null, saver(lowRam = false).existing("1001", MediaKind.BoxFront, listOf(smallerGrid)))
        val second = saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, listOf(smallerGrid)).files().single()

        assertEquals(71 to 100, second.width to second.height)
        assertEquals(listOf(File(second.path).name), File(mediaDir, "1001").list()!!.toList())
    }

    @Test fun `a temp file left by a killed process goes when the folder is written again`() = runTest {
        // Named like no size that is saved below, so only the cleanup can remove it.
        val leftover = File(File(mediaDir, "1001"), "box-front-old-1x1.webp.tmp")
        leftover.parentFile!!.mkdirs()
        leftover.writeText("half a picture")
        val (_, file) = download(imageResponse(pngBytes(40, 56)), md5 = null)

        saver(lowRam = false).save(file, "1001", MediaKind.BoxFront, sizes).files()

        assertFalse(leftover.exists())
        assertTrue(mediaDir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun `a picture that cannot be written is a storage failure, the older copy stays`() = runTest {
        val older = olderCopy()
        val (_, file) = download(imageResponse(pngBytes(400, 560)), md5 = null)
        val full = MediaSaver(mediaDir, lowRam = false, { fx.logLines += it }) { _, _ -> false }

        assertEquals(MediaSaver.Saved.StorageFailed, full.save(file, "1001", MediaKind.BoxFront, sizes))
        assertArrayEquals(OLD_BYTES, older.readBytes())
        assertTrue(mediaDir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun `a second size that cannot be written keeps both older copies and leaves no temp`() = runTest {
        val olderGrid = olderCopy()
        val olderDetails = File(olderGrid.parentFile, "box-front-details-300x600.webp").apply { writeBytes(OLD_BYTES) }
        val (_, file) = download(imageResponse(pngBytes(400, 560)), md5 = null)
        var calls = 0
        val secondFails = MediaSaver(mediaDir, lowRam = false, { fx.logLines += it }) { bitmap, target ->
            if (++calls == 2) false else MediaSaver.encodeWebp(bitmap, target)
        }

        assertEquals(MediaSaver.Saved.StorageFailed, secondFails.save(file, "1001", MediaKind.BoxFront, sizes))

        assertEquals(2, calls)
        assertArrayEquals("the grid size is not replaced alone", OLD_BYTES, olderGrid.readBytes())
        assertArrayEquals(OLD_BYTES, olderDetails.readBytes())
        assertTrue(mediaDir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    private fun olderCopy(): File {
        val older = File(File(mediaDir, "1001"), "box-front-grid-200x200.webp")
        older.parentFile!!.mkdirs()
        older.writeBytes(OLD_BYTES)
        return older
    }

    private fun MediaSaver.Saved.files(): List<SavedMedia> = (this as MediaSaver.Saved.Files).media

    private companion object {
        val OLD_BYTES = "an older saved picture".toByteArray()
    }
}
