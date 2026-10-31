package com.plumemu.scraper

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A scaled picture no longer matches the JSON's md5: checked only at full size. */
class MediaResizeTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private val wrongMd5 = "0123456789abcdef0123456789abcdef"

    private fun ref(url: String = fx.server.url("/api2/mediaJeu.php?systemeid=1&jeuid=1001&media=box-2D(us)").toString()) =
        MediaRef(MediaKind.BoxFront, "us", url, "png", wrongMd5)

    private fun picture() = MockResponse.Builder().code(200)
        .addHeader("Content-Type", "image/png")
        .body(Buffer().write(ByteArray(2_000) { 7 }))
        .build()

    @Test fun `a scaled download sends maxwidth and maxheight with the credentials and skips the md5`() = runTest {
        fx.server.enqueue(picture())
        val target = File(tmp.root, "box.png")

        val result = fx.scraper(fx.config()).download(ref(), target, maxWidth = 320, maxHeight = 600)

        assertEquals(MediaDownload.Done(target), result)
        assertEquals(2_000L, target.length())
        val url = fx.takeRequest().url
        assertEquals("320", url.queryParameter("maxwidth"))
        assertEquals("600", url.queryParameter("maxheight"))
        assertEquals("box-2D(us)", url.queryParameter("media"))
        assertEquals(ScraperFixture.DEV_PASSWORD, url.queryParameter("devpassword"))
        assertEquals(ScraperFixture.USER_PASSWORD, url.queryParameter("sspassword"))
    }

    @Test fun `a full-size download still checks the md5`() = runTest {
        fx.server.enqueue(picture())
        val target = File(tmp.root, "box.png")

        val result = fx.scraper(fx.config()).download(ref(), target)

        assertEquals(MediaDownload.Broken, result)
        assertTrue(!target.exists())
        val url = fx.takeRequest().url
        assertNull(url.queryParameter("maxwidth"))
        assertNull(url.queryParameter("maxheight"))
        assertTrue(fx.logLines.any { it.contains("md5 mismatch") })
    }

    @Test fun `a scaled box front asks for a JPEG, a disc or a full-size download does not`() = runTest {
        repeat(3) { fx.server.enqueue(picture()) }
        val scraper = fx.scraper(fx.config())

        scraper.download(ref(), File(tmp.root, "a.part"), maxWidth = 1240, maxHeight = 720)
        assertEquals("jpg", fx.takeRequest().url.queryParameter("outputformat"))

        scraper.download(ref().copy(kind = MediaKind.Support), File(tmp.root, "b.part"), maxWidth = 1240, maxHeight = 1080)
        assertNull("a disc may be round or transparent", fx.takeRequest().url.queryParameter("outputformat"))

        scraper.download(ref(), File(tmp.root, "c.part"))
        assertNull(fx.takeRequest().url.queryParameter("outputformat"))
    }

    @Test fun `a thumbnail asks for a JPEG`() = runTest {
        fx.server.enqueue(picture())

        fx.scraper(fx.config()).download(ref(), File(tmp.root, "t.part"), maxWidth = 160, maxHeight = 160)

        val url = fx.takeRequest().url
        assertEquals("jpg", url.queryParameter("outputformat"))
        assertEquals("160", url.queryParameter("maxheight"))
    }

    @Test fun `a scaled download from a foreign host is still not sent`() = runTest {
        val result = fx.scraper(fx.config())
            .download(ref("https://example.com/api2/mediaJeu.php?media=box-2D(us)"), File(tmp.root, "box.png"), 320, 320)

        assertEquals(MediaDownload.Broken, result)
        assertEquals(0, fx.server.requestCount)
    }
}
