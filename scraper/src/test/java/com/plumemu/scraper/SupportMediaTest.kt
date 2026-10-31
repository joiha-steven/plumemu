package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Disc or cartridge pictures ("support-2D") next to the box fronts. */
class SupportMediaTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun media(type: String, region: String) =
        """{"type": "$type", "parent": "jeu", "url": "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=test-dev-id&devpassword=test-dev-password&softname=Plumemu&ssid=test-user&sspassword=test-user-password&systemeid=1&jeuid=1001&media=$type($region)", "region": "$region", "md5": "0000000000000000000000000000000${region.length}", "format": "png"}"""

    /** The match fixture with supports as the real answers have them: some listed twice, plus a flat scan. */
    private fun withSupports() = json("jeuInfos-match.json") {
        val extra = listOf(
            media("support-2D", "jp"), media("support-2D", "eu"), media("support-2D", "us"),
            media("support-2D", "us"), media("support-2D", "eu"), media("support-texture", "us"),
        ).joinToString(", ")
        it.replace(""""medias": [""", """"medias": [$extra, """)
    }

    @Test fun `supports come after the box fronts, preferred region first, each once, no flat scan`() = runTest {
        fx.server.enqueue(withSupports())

        val media = (fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched).media

        assertEquals(
            // The fixture's screenshot reference comes after them (kept since cache v3).
            listOf(
                MediaKind.BoxFront to "us", MediaKind.BoxFront to "jp",
                MediaKind.Support to "us", MediaKind.Support to "eu", MediaKind.Support to "jp",
                MediaKind.Screenshot to "wor",
            ),
            media.map { it.kind to it.region },
        )
        assertTrue(media.none { it.url.contains("support-texture") })
        media.forEach { ref -> ScraperFixture.SECRETS.forEach { assertFalse(ref.url.contains(it)) } }
    }

    @Test fun `supports are kept in the trimmed cache and read back the same`() = runTest {
        fx.server.enqueue(withSupports())
        val fresh = fx.scraper(fx.config()).scrape(fx.request(fx.rom()))

        val stored = fx.responseFiles().single().readText()
        assertTrue(stored.contains("support-2D"))
        assertFalse(stored.contains("support-texture"))
        assertEquals(fresh, fx.scraper(fx.config()).scrape(fx.request(fx.rom())))
        assertEquals(1, fx.server.requestCount)
    }

    @Test fun `a game without supports has box fronts only`() = runTest {
        fx.server.enqueue(json("jeuInfos-match.json"))

        val media = (fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched).media

        // The fixture's screenshot reference is kept too since cache v3; no support.
        assertEquals(listOf(MediaKind.BoxFront, MediaKind.BoxFront, MediaKind.Screenshot), media.map { it.kind })
    }
}
