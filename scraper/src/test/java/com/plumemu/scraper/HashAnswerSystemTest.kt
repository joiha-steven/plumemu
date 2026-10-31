package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ScreenScraper matches a serial across systems: a hash answer counts only for the asked system. */
class HashAnswerSystemTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private fun game(id: String, name: String, systemId: String?): String {
        val systeme = systemId?.let { ""","systeme":{"id":"$it","text":"S"}""" }.orEmpty()
        return """{"id":"$id","noms":[{"region":"wor","text":"$name"}]$systeme}"""
    }

    private fun info(game: String) = MockResponse(
        200, Headers.headersOf("Content-Type", "application/json"),
        """{"response":{"jeu":$game}}""",
    )

    private fun search(vararg games: String) = MockResponse(
        200, Headers.headersOf("Content-Type", "application/json"),
        """{"response":{"jeux":[${games.joinToString(",")}]}}""",
    )

    private fun request(systemId: String, title: String) = fx.request(fx.rom("g.rom"), systemId, title)
        .copy(hints = RomHints(crc32 = null, serial = "ACTE", sizeBytes = 100))

    @Test fun `a serial answered by a 3DS game is rejected and the name search finds the right game`() = runTest {
        fx.server.enqueue(info(game("900", "Puppies 3D", "17")))
        fx.server.enqueue(search(game("42", "Chrono Trigger", "4")))

        val result = fx.scraper(fx.config()).scrape(request("snes", "Chrono Trigger"))

        assertEquals(GameMatch("42", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertEquals("4", fx.takeRequest().url.queryParameter("systemeid"))
        assertEquals("/api2/jeuRecherche.php", fx.takeRequest().url.encodedPath)
        assertTrue(fx.logLines.any { "asked system 4, answered 17" in it })
    }

    @Test fun `an answer on the asked system is still a hash match`() = runTest {
        fx.server.enqueue(info(game("42", "Chrono Trigger", "4")))
        val result = fx.scraper(fx.config()).scrape(request("snes", "Chrono Trigger"))
        assertEquals(GameMatch("42", MatchedBy.Hash), (result as ScrapeResult.Matched).match)
    }

    @Test fun `a Game Boy Color request answered with a Game Boy game is accepted`() = runTest {
        fx.server.enqueue(info(game("77", "Croc", "9")))
        val result = fx.scraper(fx.config()).scrape(request("gbc", "Croc"))
        assertEquals(GameMatch("77", MatchedBy.Hash), (result as ScrapeResult.Matched).match)
    }

    @Test fun `a ZZZ notgame answer on the right system is not a match`() = runTest {
        fx.server.enqueue(info(game("5", "ZZZ(notgame):EZ-Flash V", "4")))
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        val result = fx.scraper(fx.config()).scrape(request("snes", "Chrono Trigger"))
        assertEquals(ScrapeResult.NoMatch, result)
    }

    @Test fun `a ZZZ notgame game is not a name match either`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("5", "ZZZ(notgame):Chrono Trigger", "4")))
        // The title has a colon, so its two variants are asked too; they answer the same junk game.
        repeat(2) { fx.server.enqueue(search(game("5", "ZZZ(notgame):Chrono Trigger", "4"))) }
        val result = fx.scraper(fx.config()).scrape(request("snes", "ZZZ(notgame):Chrono Trigger"))
        assertEquals(ScrapeResult.NoMatch, result)
    }

    @Test fun `an answer without a system falls to the name search`() = runTest {
        fx.server.enqueue(info(game("900", "Chrono Trigger", null)))
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        val result = fx.scraper(fx.config()).scrape(request("snes", "Chrono Trigger"))
        assertEquals(ScrapeResult.NoMatch, result)
    }

    @Test fun `a wrong answer cached earlier is rejected on the next ask`() = runTest {
        fx.server.enqueue(info(game("900", "Puppies 3D", "17")))
        fx.server.enqueue(search(game("42", "Chrono Trigger", "4")))
        val scraper = fx.scraper(fx.config())
        scraper.scrape(request("snes", "Chrono Trigger"))
        val before = fx.server.requestCount

        val again = scraper.scrape(request("snes", "Chrono Trigger"))

        assertEquals(GameMatch("42", MatchedBy.Name), (again as ScrapeResult.Matched).match)
        assertEquals(before, fx.server.requestCount)
    }

    @Test fun `a lookup with a crc only, answered on another system, is still accepted`() = runTest {
        fx.server.enqueue(info(game("900", "Some Port", "17")))
        val withCrc = request("snes", "Chrono Trigger").copy(hints = RomHints(crc32 = "0d4a1185", serial = null, sizeBytes = 100))
        val result = fx.scraper(fx.config()).scrape(withCrc)
        assertEquals(GameMatch("900", MatchedBy.Hash), (result as ScrapeResult.Matched).match)
    }

    /**
     * Seen on a Nova and a Thor: a fan translation's CRC is in no database, but its SNES
     * game code ("ARFJ") is also a 3DS game's; with the crc sent too, ScreenScraper answered the
     * 3DS game and the answer was kept as a hash match ("Fish Eyes 3D" for "Star Ocean").
     */
    @Test fun `a lookup with a crc and a serial answered on another system is rejected`() = runTest {
        fx.server.enqueue(info(game("900", "Puppies 3D", "17")))
        fx.server.enqueue(search(game("42", "Chrono Trigger", "4")))
        val withBoth = request("snes", "Chrono Trigger").copy(hints = RomHints(crc32 = "0d4a1185", serial = "ACTE", sizeBytes = 100))

        val result = fx.scraper(fx.config()).scrape(withBoth)

        assertEquals(GameMatch("42", MatchedBy.Name), (result as ScrapeResult.Matched).match)
        assertTrue(fx.logLines.any { "asked system 4, answered 17" in it })
    }

    /**
     * Folders that hold another system's files. A `.gbc` in `gba/` (asks 12),
     * a Game Boy file in `sgb/` (127), an `.sfc` in `snesmsu1/` (210), a `.md` in `genesismsu/` (301):
     * the crc+serial answer comes on the format's own system and is right.
     */
    @Test fun `a serial answer on the system of the serial's own format is accepted`() = runTest {
        val cases = listOf(
            Triple("gba", "gb", "10"), Triple("gba", "gb", "9"), Triple("sgb", "gb", "9"),
            Triple("snesmsu1", "snes", "4"), Triple("genesismsu", "megadrive", "1"),
            Triple("megadrive", "megadrive", "19"), Triple("sega32x", "megadrive", "1"),
        )
        cases.forEachIndexed { index, (folder, format, answered) ->
            fx.server.enqueue(info(game("900", "Zork Quest", answered)))
            // Each case its own crc and serial: answers are cached by them.
            val asked = request(folder, "Zork Quest").copy(hints = RomHints(crc32 = "0d4a118$index", serial = "AZQ$index", sizeBytes = 100, serialSystemId = format))
            val result = fx.scraper(fx.config()).scrape(asked)
            assertEquals("$folder answered $answered: ${fx.logLines}", GameMatch("900", MatchedBy.Hash), (result as? ScrapeResult.Matched)?.match)
        }
    }

    @Test fun `a SNES translation in snes answered as a 3DS game is still rejected with its format known`() = runTest {
        fx.server.enqueue(info(game("900", "Puppies 3D", "17")))
        fx.server.enqueue(search(game("42", "Chrono Trigger", "4")))
        val asked = request("snes", "Chrono Trigger").copy(hints = RomHints(crc32 = "0d4a1185", serial = "ACTE", sizeBytes = 100, serialSystemId = "snes"))
        val result = fx.scraper(fx.config()).scrape(asked)
        assertEquals(GameMatch("42", MatchedBy.Name), (result as ScrapeResult.Matched).match)
    }

    @Test fun `a name search keeps the exact system rule, so a Game Boy title is no Game Boy Color match`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(search(game("77", "Croc", "9")))
        val result = fx.scraper(fx.config()).scrape(request("gbc", "Croc").copy(hints = null))
        assertEquals(ScrapeResult.NoMatch, result)
    }
}
