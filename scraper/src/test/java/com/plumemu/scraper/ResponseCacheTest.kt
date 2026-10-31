package com.plumemu.scraper

import com.plumemu.scraper.cache.ResponseCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResponseCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000L
    private val dir get() = File(tmp.root, "responses")
    private fun cache() = ResponseCache(dir, { now }, Dispatchers.IO, { })

    @Test fun `an answer is served for 30 days, then dropped and deleted`() = runTest {
        val cache = cache()
        cache.putFound("k", "{\"a\":1}")
        cache.putNotFound("gone")

        now += ResponseCache.TTL_MILLIS
        assertEquals(ResponseCache.Hit.Found("{\"a\":1}"), cache.get("k"))
        assertEquals(ResponseCache.Hit.NotFound, cache.get("gone"))

        now += 1
        assertNull(cache.get("k"))
        assertNull(cache.get("gone"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun `an answer saved in the future (clock moved back) is not trusted`() = runTest {
        cache().putFound("k", "{}")
        now -= 60_000

        assertNull(cache().get("k"))
    }

    @Test fun `a damaged file is ignored and deleted`() = runTest {
        cache().putFound("k", "{}")
        dir.listFiles()!!.single().writeText("garbage")

        assertNull(cache().get("k"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun `a file of the older format v2 is a miss and is deleted`() = runTest {
        cache().putFound("k", "{}")
        val file = dir.listFiles()!!.single()
        file.writeText("v2 $now 200\n{}")

        assertNull(cache().get("k"))
        assertFalse(file.exists())
    }

    @Test fun `keys hold the game parameters only, and file names are hashes`() = runTest {
        val key = ResponseCache.keyOf("jeuInfos.php", mapOf("romnom" to "Sonic.md", "crc" to "0D4A1185"))
        assertEquals("jeuInfos.php?crc=0D4A1185&romnom=Sonic.md", key)

        cache().putFound(key, "{}")
        val name = dir.listFiles()!!.single().name
        assertTrue(name, name.matches(Regex("[0-9a-f]{64}\\.resp")))
    }

    @Test fun `a name holding & or = cannot share a key with another request`() {
        val oneParam = ResponseCache.keyOf("jeuRecherche.php", mapOf("recherche" to "Tom&systemeid=1"))
        val twoParams = ResponseCache.keyOf("jeuRecherche.php", mapOf("recherche" to "Tom", "systemeid" to "1"))

        assertNotEquals(oneParam, twoParams)
    }

    @Test fun `a scrape stores no credential in keys or bodies`() = runTest {
        ScraperFixture(tmp.newFolder()).use { fx ->
            fx.server.enqueue(ScraperFixture.json("jeuInfos-match.json"))
            fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

            val stored = fx.responseFiles().single().readText()
            // The trimmed answer is re-encoded compactly; its media URLs came with credentials.
            assertTrue(stored.contains("\"id\":\"1001\""))
            assertTrue(stored.contains("mediaJeu.php"))
            for (secret in ScraperFixture.SECRETS) assertFalse(secret, stored.contains(secret))
        }
    }
}
