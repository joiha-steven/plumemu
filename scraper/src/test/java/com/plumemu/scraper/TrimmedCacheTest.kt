package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.cache.ResponseCache
import com.plumemu.scraper.hash.toLowerHex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** Real answers are 1-2 MB; the cache keeps only what the scraper reads. */
class TrimmedCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    /**
     * The match fixture grown like a real answer: big `hacks` and `roms` lists, and each box
     * front listed twice, identical, as ScreenScraper does.
     */
    private fun bigAnswer(): String {
        val hack = """{"id": "77", "nom": "${"h".repeat(900)}", "crc": "ABCDEF01", "md5": "${"0".repeat(32)}"}"""
        val rom = """{"id": "88", "romfilename": "${"r".repeat(400)}", "romsize": "524288", "romcrc": "0D4A1185"}"""
        val hacks = List(1_000) { hack }.joinToString(",")
        val roms = List(200) { rom }.joinToString(",")
        return ScraperFixture.fixture("jeuInfos-match.json")
            .replace(""""roms": []""", """"roms": [$roms], "hacks": [$hacks]""")
            .replace(Regex("""\{"type": "box-2D"[^}]*\}""")) { "${it.value}, ${it.value}" }
    }

    @Test fun `a 1 MB answer is cached in under 20 KB and read back to the same result without a request`() = runTest {
        val body = bigAnswer()
        assertTrue("body is ${body.length} chars", body.length > 1_000_000)
        fx.server.enqueue(json("jeuInfos-match.json") { body })

        val fresh = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        val file = fx.responseFiles().single()
        assertTrue("cache file is ${file.length()} bytes", file.length() < 20_000)
        val stored = file.readText()
        for (dropped in listOf("\"header\"", "\"ssuser\"", "\"serveurs\"", "\"hacks\"", "\"roms\"")) {
            assertFalse("$dropped is cached", stored.contains(dropped))
        }
        // Since cache v3 the screenshot is kept as a reference, never downloaded.
        assertTrue(stored.contains("\"type\":\"ss\""))
        for (secret in ScraperFixture.SECRETS) assertFalse(secret, stored.contains(secret))

        // A new scraper (next process) answers from the trimmed copy, the same as the fresh answer.
        val cached = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))
        assertEquals(1, fx.server.requestCount)
        assertEquals(fresh, cached)
        // Duplicate box fronts are dropped from the fresh answer too.
        val boxFronts = (fresh as ScrapeResult.Matched).media.filter { it.kind == MediaKind.BoxFront }
        assertEquals(listOf("us", "jp"), boxFronts.map { it.region })
    }

    @Test fun `a raw answer cached in the old format is not used, removed, and asked again`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val params = mapOf(
            "crc" to "0D4A1185",
            "md5" to "5eb63bbbe01eeed093cb22bb8f5acdc3",
            "sha1" to "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed",
            "systemeid" to "1",
            "romtype" to "rom",
            "romnom" to "Sonic.md",
            "romtaille" to "11",
        )
        val old = cacheFileOf(ResponseCache.keyOf("jeuInfos.php", params))
        old.parentFile!!.mkdirs()
        old.writeText("v1 ${System.currentTimeMillis()} 200\n" + ScraperFixture.fixture("jeuInfos-match.json"))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        assertTrue(result is ScrapeResult.Matched)
        assertEquals(1, fx.server.requestCount)
        val stored = old.readText()
        assertTrue(stored.startsWith("v3 "))
        assertFalse(stored.contains("\"header\""))
    }

    private fun cacheFileOf(key: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).toLowerHex() + ".resp"
        return File(File(fx.cacheDir, ScreenScraperScraper.RESPONSES_DIR), name)
    }

    /** What an earlier version cached for the console fixture: a narrower trim, without the details. */
    @Test fun `an answer trimmed in format v2 is not used and the game is asked again with its details`() = runTest {
        fx.server.enqueue(json("jeuInfos-console-full.json"))
        fx.scraper(fx.config()).scrape(fx.request(fx.rom()))
        val file = fx.responseFiles().single()
        val v2Body = """{"response":{"jeu":{"id":"3001","noms":[{"region":"us","text":"Comet Runner"}]}}}"""
        file.writeText("v2 ${System.currentTimeMillis()} 200\n$v2Body")
        fx.server.enqueue(json("jeuInfos-console-full.json"))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched

        assertEquals(2, fx.server.requestCount)
        assertEquals(4, result.details.names.size)
        assertTrue(file.readText().startsWith("v3 "))
    }

    @Test fun `a password equal to a title word leaves the cached title whole and no credential stored`() = runTest {
        // The person's password is a word of the title; the answer's media URLs echo it.
        val user = UserCredentials(ScraperFixture.USER_NAME, "Hedgehog")
        fx.server.enqueue(json("jeuInfos-match.json") { it.replace(ScraperFixture.USER_PASSWORD, "Hedgehog") })

        val fresh = fx.scraper(fx.config(user = user)).scrape(fx.request(fx.rom()))
        val cached = fx.scraper(fx.config(user = user)).scrape(fx.request(fx.rom()))

        assertEquals(1, fx.server.requestCount)
        assertEquals(fresh, cached)
        assertEquals("Sonic The Hedgehog", (cached as ScrapeResult.Matched).info.title)
        val stored = fx.responseFiles().single().readText()
        for (param in listOf("devid=", "devpassword=", "ssid=", "sspassword=", "softname=")) {
            assertFalse("$param is stored", stored.contains(param))
        }
        assertFalse(stored.contains(ScraperFixture.DEV_PASSWORD))
        assertFalse(stored.contains(ScraperFixture.DEV_ID))
    }
}
