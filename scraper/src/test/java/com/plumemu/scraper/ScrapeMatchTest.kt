package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScrapeMatchTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    @Test fun `a known hash matches by hash and the request carries hashes, name, size and softname`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val scraper = fx.scraper(fx.config())

        val result = scraper.scrape(fx.request(fx.rom()))

        val matched = result as ScrapeResult.Matched
        assertEquals(GameMatch("1001", MatchedBy.Hash), matched.match)
        val url = fx.takeRequest().url
        assertEquals("/api2/jeuInfos.php", url.encodedPath)
        assertEquals("0D4A1185", url.queryParameter("crc"))
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", url.queryParameter("md5"))
        assertEquals("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed", url.queryParameter("sha1"))
        assertEquals("Sonic.md", url.queryParameter("romnom"))
        assertEquals("11", url.queryParameter("romtaille"))
        assertEquals("rom", url.queryParameter("romtype"))
        assertEquals("1", url.queryParameter("systemeid"))
        assertEquals("Plumemu", url.queryParameter("softname"))
        assertEquals("json", url.queryParameter("output"))
        assertEquals(ScraperFixture.DEV_ID, url.queryParameter("devid"))
        assertEquals(ScraperFixture.USER_NAME, url.queryParameter("ssid"))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `game info follows the region and language preference`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        val info = (fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched).info

        assertEquals(
            GameInfo(
                title = "Sonic The Hedgehog",
                synopsis = "Sonic must stop Dr. Robotnik.",
                year = 1991,
                genre = "Platform",
                developer = "Sonic Team",
                publisher = "Sega",
                players = "1",
                rating = 17,
            ),
            info,
        )
    }

    @Test fun `a French person gets the French synopsis and genre`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))
        val request = fx.request(fx.rom()).copy(regions = listOf("eu"), languages = listOf("fr"))

        val info = (fx.scraper(fx.config()).scrape(request) as ScrapeResult.Matched).info

        assertEquals("Sonic The Hedgehog (EU)", info.title)
        assertEquals("Plateforme", info.genre)
        assertTrue(info.synopsis!!.startsWith("Sonic doit arr\u00eater"))
    }

    @Test fun `box fronts come preferred region first and carry no credentials`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        val all = (fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched).media
        // Since cache v3 the fixture's screenshot is a reference too; box fronts come first.
        val media = all.filter { it.kind == MediaKind.BoxFront }

        assertEquals(listOf("us", "jp"), media.map { it.region })
        assertEquals(media, all.take(2))
        assertTrue(media.all { it.format == "png" })
        assertEquals("00000000000000000000000000000002", media.first().md5)
        all.forEach { ref ->
            for (param in listOf("devid", "devpassword", "ssid", "sspassword", "softname")) {
                assertFalse("${ref.url} carries $param", ref.url.contains("$param="))
            }
            ScraperFixture.SECRETS.forEach { assertFalse(ref.url.contains(it)) }
        }
        assertTrue(media.first().url.endsWith("systemeid=1&jeuid=1001&media=box-2D(us)"))
    }

    @Test fun `an unknown hash falls back to a name search`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(json("jeuRecherche-match.json"))
        val rom = fx.rom("Pokemon Puzzle Challenge (USA).gbc")

        val result = fx.scraper(fx.config()).scrape(fx.request(rom, systemId = "gbc", title = "Pokemon Puzzle Challenge"))

        val matched = result as ScrapeResult.Matched
        // The first result is an N64 game: only a result of the requested system counts.
        assertEquals(GameMatch("2002", MatchedBy.Name), matched.match)
        assertEquals("Pok\u00e9mon Puzzle Challenge", matched.info.title)
        assertEquals(2000, matched.info.year)
        assertEquals("jeuInfos.php", fx.takeRequest().url.pathSegments.last())
        val search = fx.takeRequest().url
        assertEquals("jeuRecherche.php", search.pathSegments.last())
        assertEquals("Pokemon Puzzle Challenge", search.queryParameter("recherche"))
        assertEquals("10", search.queryParameter("systemeid"))
    }

    @Test fun `no match by hash nor name is NoMatch and the second call is answered from the cache`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(json("jeuRecherche-empty.json"))
        val scraper = fx.scraper(fx.config())
        val request = fx.request(fx.rom("Unknown Homebrew.md"), title = "Unknown Homebrew")

        assertEquals(ScrapeResult.NoMatch, scraper.scrape(request))
        assertEquals(2, fx.server.requestCount)

        assertEquals(ScrapeResult.NoMatch, scraper.scrape(request))
        assertEquals(2, fx.server.requestCount)

        // A new scraper (next session) reads the same disk cache.
        assertEquals(ScrapeResult.NoMatch, fx.scraper(fx.config()).scrape(request))
        assertEquals(2, fx.server.requestCount)
    }

    @Test fun `an unknown system is looked up by hash without systemeid and never by name`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom("game.xyz"), systemId = "no-such-system"))

        assertEquals(ScrapeResult.NoMatch, result)
        assertNull(fx.takeRequest().url.queryParameter("systemeid"))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a compressed disc image is looked up by name and size only`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        fx.scraper(fx.config()).scrape(fx.request(fx.rom("Game.CHD"), systemId = "psx"))

        val url = fx.takeRequest().url
        assertNull(url.queryParameter("crc"))
        assertNull(url.queryParameter("md5"))
        assertNull(url.queryParameter("sha1"))
        assertEquals("Game.CHD", url.queryParameter("romnom"))
        assertEquals("11", url.queryParameter("romtaille"))
    }

    @Test fun `a ROM that cannot be read fails without asking the server`() = runTest {
        val rom = fx.rom()
        java.io.File(rom.key).delete()

        val result = fx.scraper(fx.config()).scrape(fx.request(rom))

        assertEquals(ScrapeResult.Failed(FailureReason.RomUnreadable), result)
        assertEquals(0, fx.server.requestCount)
    }
}
