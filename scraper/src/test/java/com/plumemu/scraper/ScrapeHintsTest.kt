package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/** With the library's hints the scraper never reads the ROM a second time. */
class ScrapeHintsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    /** A ROM that must not be read: every open() is counted and fails. */
    private class UnopenableRom(override val fileName: String, override val sizeBytes: Long) : RomSource {
        val opens = AtomicInteger()
        override val key: String = "content://roms/$fileName"
        override val lastModifiedMillis: Long = 1_000
        override fun open(): InputStream {
            opens.incrementAndGet()
            throw AssertionError("the ROM was opened")
        }
    }

    @Test fun `with hints the ROM is never opened and the request carries crc, serial, name and the hinted size`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val rom = UnopenableRom("Sonic.md", sizeBytes = 999)
        val request = fx.request(rom).copy(hints = RomHints(crc32 = "0d4a1185", serial = "MK-1079", sizeBytes = 524_288))

        val result = fx.scraper(fx.config()).scrape(request)

        assertEquals(GameMatch("1001", MatchedBy.Hash), (result as ScrapeResult.Matched).match)
        assertEquals(0, rom.opens.get())
        val url = fx.takeRequest().url
        assertEquals("/api2/jeuInfos.php", url.encodedPath)
        assertEquals("0D4A1185", url.queryParameter("crc"))
        assertEquals("MK-1079", url.queryParameter("serialnum"))
        assertEquals("Sonic.md", url.queryParameter("romnom"))
        assertEquals("524288", url.queryParameter("romtaille"))
        assertEquals("1", url.queryParameter("systemeid"))
        assertEquals("rom", url.queryParameter("romtype"))
        assertNull(url.queryParameter("md5"))
        assertNull(url.queryParameter("sha1"))
        // The hash cache was not touched either.
        assertFalse(File(fx.cacheDir, ScreenScraperScraper.HASHES_DIR).exists())
    }

    @Test fun `hints without crc, serial or size send the name and the file's own size only`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val rom = UnopenableRom("Game.chd", sizeBytes = 700_000_000)
        val request = fx.request(rom, systemId = "psx").copy(hints = RomHints(crc32 = " ", serial = null))

        fx.scraper(fx.config()).scrape(request)

        assertEquals(0, rom.opens.get())
        val url = fx.takeRequest().url
        assertNull(url.queryParameter("crc"))
        assertNull(url.queryParameter("serialnum"))
        assertEquals("Game.chd", url.queryParameter("romnom"))
        assertEquals("700000000", url.queryParameter("romtaille"))
        assertEquals("57", url.queryParameter("systemeid"))
    }

    @Test fun `without hints the ROM is hashed as before`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        val url = fx.takeRequest().url
        assertEquals("0D4A1185", url.queryParameter("crc"))
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", url.queryParameter("md5"))
        assertNull(url.queryParameter("serialnum"))
    }
}
