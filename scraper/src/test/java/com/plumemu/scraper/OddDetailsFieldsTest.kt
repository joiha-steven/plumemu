package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A details field of an unexpected shape never costs a match: it reads as null
 * or empty, and the match, the picked values and the box fronts are what they were.
 */
class OddDetailsFieldsTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun scrape(edit: (String) -> String): ScrapeResult = ScraperFixture(tmp.newFolder()).use { fx ->
        fx.server.enqueue(json("jeuInfos-match.json", edit))
        fx.scraper(fx.config()).scrape(fx.request(fx.rom()))
    }

    private fun MediaRef.withoutSize() = copy(size = null)

    @Test fun `each odd details field reads as null or empty and the game still matches the same`() = runTest {
        val base = scrape { it } as ScrapeResult.Matched
        val cases: List<Triple<String, (String) -> String, (GameDetails) -> Boolean>> = listOf(
            Triple("rotation object", { it.replace(""""rotation": "0"""", """"rotation": {"a": 1}""") }, { it.rotation == null }),
            Triple("cloneof list", { it.replace(""""cloneof": "0"""", """"cloneof": []""") }, { it.cloneOf == null && it.notGame == false }),
            Triple("topstaff list", { it.replace(""""topstaff": "1"""", """"topstaff": [1]""") }, { it.topStaff == null }),
            Triple(
                "tag names a string",
                { it.replace(""""rotation": "0",""", """"rotation": "0", "modes": [{"id": "1", "noms": "x"}],""") },
                { it.modes == listOf(GameTag("1")) },
            ),
            Triple(
                "classification text an object",
                { it.replace(""""rotation": "0",""", """"rotation": "0", "classifications": [{"type": "ESRB", "text": {}}],""") },
                { it.classifications.isEmpty() },
            ),
            Triple(
                "colours an object",
                { it.replace(""""rotation": "0",""", """"rotation": "0", "couleurs": {"a": 1},""") },
                { it.buttonColours.isEmpty() },
            ),
            Triple("genre parent an object", { it.replace(""""parentid": "10"""", """"parentid": {}""") }, { d ->
                d.genres[1].parentId == null && d.genres[1].main
            }),
            Triple("media size an object", { it.replace(""""size": "1000"""", """"size": {}""") }, { d ->
                d.media.single { it.kind == MediaKind.BoxFront && it.region == "jp" }.size == null
            }),
        )
        for ((name, edit, oddFieldIsEmpty) in cases) {
            val result = scrape(edit)
            assertTrue("$name: $result", result is ScrapeResult.Matched)
            result as ScrapeResult.Matched
            assertEquals(name, base.match, result.match)
            assertEquals(name, base.info, result.info)
            assertEquals(
                name,
                base.media.filter { it.kind == MediaKind.BoxFront }.map { it.withoutSize() },
                result.media.filter { it.kind == MediaKind.BoxFront }.map { it.withoutSize() },
            )
            assertTrue("$name: ${result.details}", oddFieldIsEmpty(result.details))
        }
    }

    @Test fun `an odd game among the name results does not lose the search`() = runTest {
        val odd = """{"id": "3009", "noms": [{"region": "us", "text": "Comet Runner 2"}], "systeme": {"id": "1"},
            "rotation": {}, "couleurs": "1", "modes": "x", "classifications": [{"text": []}], "cloneof": [],
            "medias": [{"type": "ss", "url": "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=ss", "size": {}}]}"""
        val wanted = """{"id": "3001", "noms": [{"region": "us", "text": "Comet Runner"}], "systeme": {"id": "1"},
            "rotation": [0], "familles": [{"id": {}, "noms": [{"langue": "fr", "text": {}}, {"langue": "en", "text": "Comets"}]}]}"""
        val result = ScraperFixture(tmp.newFolder()).use { fx ->
            fx.server.enqueue(status(404, NOT_FOUND_BODY))
            fx.server.enqueue(
                MockResponse(
                    200,
                    Headers.headersOf("Content-Type", "application/json"),
                    """{"response": {"ssuser": {"id": "test-user", "numid": "1234"}, "jeux": [$odd, $wanted]}}""",
                ),
            )
            fx.scraper(fx.config()).scrape(fx.request(fx.rom("Comet Runner.md"), title = "Comet Runner"))
        }

        val matched = result as ScrapeResult.Matched
        assertEquals(GameMatch("3001", MatchedBy.Name), matched.match)
        assertNull(matched.details.rotation)
        assertEquals(listOf(GameTag(names = listOf(TextByLanguage("en", "Comets")))), matched.details.families)
    }
}
