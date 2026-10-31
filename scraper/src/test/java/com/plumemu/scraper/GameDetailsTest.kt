package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every text field of a `jeuInfos.php` answer is kept. Fixtures follow the
 * shapes of real answers with invented texts.
 */
class GameDetailsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
    }

    @After fun tearDown() = fx.close()

    private suspend fun scrape(): ScrapeResult.Matched =
        fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched

    private fun url(system: Int, game: Int, type: String, region: String) =
        "https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=$system&jeuid=$game&media=$type($region)"

    private fun tag(id: String, shortName: String?, main: Boolean, vararg names: Pair<String, String>) =
        GameTag(id, shortName, main, parentId = if (id == "7") "10" else "0", names.map { TextByLanguage(it.first, it.second) })

    private val console = GameDetails(
        names = listOf(
            TextByRegion("ss", "Comet Runner (SS)"),
            TextByRegion("us", "Comet Runner"),
            TextByRegion("eu", "Comet Runner: Star Edition"),
            TextByRegion("jp", "Komet Ran\u00e2"),
        ),
        synopses = listOf(
            TextByLanguage("en", " Pilot a comet through ten belts. "),
            TextByLanguage("fr", "Pilotez une com\u00e8te \u00e0 travers dix ceintures."),
            TextByLanguage("de", "Steuere einen Kometen."),
        ),
        dates = listOf(TextByRegion("us", "1993-04-02"), TextByRegion("eu", "1993"), TextByRegion("jp", "1992-12-18")),
        system = IdName("1", "Megadrive"),
        publisher = IdName("50", "Nimbus Soft"),
        developer = IdName("51", "Nimbus Team"),
        players = "1-2",
        rating = 15,
        genres = listOf(
            tag("10", "Action", false, "en" to "Action", "fr" to "Action"),
            tag("7", "Shooter", true, "en" to "Shoot'em up", "fr" to "Shoot'em up", "de" to "Ballerspiel"),
        ),
        modes = listOf(tag("102", null, true, "en" to "Single player", "fr" to "Un joueur"), tag("103", null, false, "en" to "Co-op")),
        families = listOf(tag("2720", null, false, "fr" to "Comet Runner")),
        themes = listOf(tag("401", null, true, "en" to "Space", "fr" to "Espace")),
        styles = listOf(tag("501", null, false, "en" to "Vertical scrolling")),
        classifications = listOf(Classification("PEGI", "7"), Classification("ESRB", "E")),
        rotation = 0,
        cloneOf = null,
        notGame = false,
        topStaff = true,
        media = listOf(
            MediaRef(MediaKind.BoxFront, "us", url(1, 3001, "box-2D", "us"), "png", "00000000000000000000000000000a01", 200_000),
            MediaRef(MediaKind.BoxFront, "eu", url(1, 3001, "box-2D", "eu"), "png", "00000000000000000000000000000e01", 180_000),
            MediaRef(MediaKind.Support, "us", url(1, 3001, "support-2D", "us"), "png", "00000000000000000000000000000a02", 90_000),
            MediaRef(MediaKind.Screenshot, "wor", url(1, 3001, "ss", "wor"), "png", "00000000000000000000000000000b01", 40_000),
            MediaRef(MediaKind.TitleScreen, "wor", url(1, 3001, "sstitle", "wor"), "png", "00000000000000000000000000000b02", 30_000),
            MediaRef(MediaKind.Manual, "us", url(1, 3001, "manuel", "us"), "pdf", "00000000000000000000000000000c01", 2_500_000),
        ),
    )

    private val arcade = GameDetails(
        names = listOf(TextByRegion("ss", "Thunder Lane (set 2)"), TextByRegion("wor", "Thunder Lane")),
        synopses = listOf(
            TextByLanguage("en", "Race down the lane before the storm."),
            TextByLanguage("fr", "Foncez avant l'orage."),
        ),
        dates = listOf(TextByRegion("wor", "1989-03-01")),
        system = IdName("75", "Mame"),
        publisher = IdName("60", "Volt Games"),
        developer = IdName("61", "Volt R&D"),
        players = "2",
        rating = 16,
        genres = listOf(tag("14", "Race", true, "en" to "Racing", "fr" to "Course")),
        modes = listOf(tag("104", null, false, "en" to "Versus", "de" to "Gegeneinander")),
        families = listOf(tag("2721", null, false, "fr" to "Thunder")),
        classifications = listOf(Classification("PEGI", "12")),
        rotation = 270,
        resolution = "224x288",
        controls = 6,
        buttonColours = listOf(
            ButtonColour("P1_COIN", "FFFFFF"),
            ButtonColour("P1_BUTTON1", "FF0000"),
            ButtonColour("P1_BUTTON2", "0000FF"),
        ),
        cloneOf = "4000",
        notGame = false,
        topStaff = false,
        media = listOf(
            MediaRef(MediaKind.BoxFront, "wor", url(75, 4001, "box-2D", "wor"), "png", "00000000000000000000000000000f03", 150_000),
            MediaRef(MediaKind.Screenshot, "wor", url(75, 4001, "ss", "wor"), "png", "00000000000000000000000000000f01", 25_000),
            MediaRef(MediaKind.TitleScreen, "wor", url(75, 4001, "sstitle", "wor"), "png", "00000000000000000000000000000f02", 20_000),
        ),
    )

    @Test fun `a console game comes out with every field, per region and per language`() = runTest {
        fx.server.enqueue(json("jeuInfos-console-full.json"))

        val result = scrape()

        assertEquals(console, result.details)
        assertEquals(result.media, result.details.media)
        // The picked values are unchanged by the details next to them.
        assertEquals(
            GameInfo("Comet Runner", "Pilot a comet through ten belts.", 1993, "Shoot'em up", "Nimbus Team", "Nimbus Soft", "1-2", 15),
            result.info,
        )
    }

    @Test fun `an arcade game comes out with rotation, resolution, controls and button colours`() = runTest {
        fx.server.enqueue(json("jeuInfos-arcade-full.json"))

        assertEquals(arcade, scrape().details)
    }

    @Test fun `the cached copy gives an equal result and keeps the references, not the rest`() = runTest {
        for (fixture in listOf("jeuInfos-console-full.json", "jeuInfos-arcade-full.json")) {
            fx.cacheDir.deleteRecursively()
            fx.server.enqueue(json(fixture))
            val before = fx.server.requestCount

            val fresh = scrape()
            val cached = scrape()

            assertEquals(fixture, before + 1, fx.server.requestCount)
            assertEquals(fixture, fresh, cached)
            val stored = fx.responseFiles().single().readText()
            assertTrue(stored.startsWith("v3 "))
            for (kept in listOf("\"type\":\"box-2D\"", "\"type\":\"ss\"", "\"type\":\"sstitle\"")) {
                assertTrue("$fixture: $kept is not cached", stored.contains(kept))
            }
            val dropped = listOf(
                "\"header\"", "\"ssuser\"", "\"serveurs\"", "\"roms\"", "\"rom\"", "\"hacks\"", "\"tips\"", "\"actions\"",
                "fanart", "video", "flyer", "wheel", "support-texture",
            )
            for (gone in dropped) assertFalse("$fixture: $gone is cached", stored.contains(gone))
            for (secret in ScraperFixture.SECRETS) assertFalse("$fixture: $secret is stored", stored.contains(secret))
        }
    }

    @Test fun `every reference kept carries no credentials`() = runTest {
        fx.server.enqueue(json("jeuInfos-console-full.json"))

        val media = scrape().media

        assertEquals(MediaKind.entries.toSet(), media.map { it.kind }.toSet())
        for (ref in media) {
            for (param in listOf("devid", "devpassword", "ssid", "sspassword", "softname")) {
                assertFalse("${ref.url} carries $param", ref.url.contains("$param="))
            }
            ScraperFixture.SECRETS.forEach { assertFalse(ref.url.contains(it)) }
        }
    }

    /** The arcade fixture grown like a real answer: big roms, hacks, actions and other media. */
    private fun bigArcadeAnswer(): String {
        val hack = """{"id": "77", "nom": "${"h".repeat(900)}", "crc": "ABCDEF01", "md5": "${"0".repeat(32)}"}"""
        val rom = """{"id": "88", "romfilename": "${"r".repeat(400)}", "romsize": "524288", "romcrc": "0D4A1185"}"""
        val action = """{"id": "3933", "controle": [{"langue": "en", "text": "${"a".repeat(200)}", "recalboxtext": "A"}]}"""
        val video = """{"type": "video", "parent": "jeu", "url": "https://neoclone.screenscraper.fr/api2/mediaVideoJeu.php?media=${"v".repeat(300)}", "region": "wor", "format": "mp4"}"""
        return ScraperFixture.fixture("jeuInfos-arcade-full.json")
            .replace(""""roms": []""", """"roms": [${List(200) { rom }.joinToString(",")}], "hacks": [${List(1_000) { hack }.joinToString(",")}]""")
            .replace(""""actions": [""", """"actions": [${List(300) { action }.joinToString(",")}, """)
            .replace(""""medias": [""", """"medias": [${List(300) { video }.joinToString(",")}, """)
    }

    @Test fun `a 1 MB answer still trims to under 30 KB with every field kept`() = runTest {
        val body = bigArcadeAnswer()
        assertTrue("body is ${body.length} chars", body.length > 1_000_000)
        fx.server.enqueue(json("jeuInfos-arcade-full.json") { body })

        val result = scrape()

        val file = fx.responseFiles().single()
        assertTrue("cache file is ${file.length()} bytes", file.length() < 30_000)
        assertEquals(arcade, result.details)
        assertEquals(result, scrape())
    }

    private fun obj(vararg fields: Pair<String, Any>): JsonObject = JsonObject(
        fields.associate { (name, value) -> name to if (value is JsonElement) value else JsonPrimitive(value.toString()) },
    )

    /**
     * The arcade fixture with a kept part as big as a rich real game: 6 synopses of about 1,200
     * characters, 34 button colours, 10 box fronts, 8 supports, a screenshot, a title screen and
     * 5 manuals with full URLs, 6 genres named in 6 languages; plus 1 MB of what is dropped.
     */
    private fun richArcadeAnswer(): String {
        val root = Json.parseToJsonElement(ScraperFixture.fixture("jeuInfos-arcade-full.json")).jsonObject
        val response = root.getValue("response").jsonObject
        val languages = listOf("en", "fr", "de", "es", "it", "pt")
        val regions = listOf("wor", "us", "eu", "jp", "fr", "de", "es", "it", "br", "au")
        var md5 = 0
        fun media(type: String, region: String, format: String = "png") = obj(
            "type" to type, "parent" to "jeu", "region" to region, "format" to format, "size" to "612345",
            "url" to "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=test-dev-id&devpassword=test-dev-password" +
                "&softname=Plumemu&ssid=test-user&sspassword=test-user-password&systemeid=75&jeuid=4001&media=$type($region)",
            "crc" to "ABCD0123", "md5" to "%032x".format(++md5), "sha1" to "%040x".format(md5),
        )
        val medias = regions.map { media("box-2D", it) } + regions.take(8).map { media("support-2D", it) } +
            media("ss", "wor") + media("sstitle", "wor") + regions.take(5).map { media("manuel", it, "pdf") } +
            List(40) { media("fanart", "wor", "jpg") }
        val synopsis = languages.map { obj("langue" to it, "text" to "The storm rolls over the lane again. ".repeat(33)) }
        val colours = (1..34).map { obj("id" to "${31000 + it}", "controle" to "P${it % 2 + 1}_BUTTON$it", "hexa" to "FF00FF") }
        val genres = (1..6).map { id ->
            obj(
                "id" to "$id", "nomcourt" to "Genre $id", "principale" to if (id == 1) "1" else "0", "parentid" to "0",
                "noms" to JsonArray(languages.map { obj("langue" to it, "text" to "Racing genre $id") }),
            )
        }
        val hack = obj("id" to "77", "nom" to "h".repeat(900), "crc" to "ABCDEF01")
        val jeu = JsonObject(
            response.getValue("jeu").jsonObject + mapOf(
                "synopsis" to JsonArray(synopsis), "couleurs" to JsonArray(colours), "genres" to JsonArray(genres),
                "medias" to JsonArray(medias), "hacks" to JsonArray(List(1_100) { hack }),
            ),
        )
        return JsonObject(root + ("response" to JsonObject(response + ("jeu" to jeu)))).toString()
    }

    @Test fun `a rich game with 1 MB around it is cached in under 18 KB with every kept part`() = runTest {
        val body = richArcadeAnswer()
        assertTrue("body is ${body.length} chars", body.length > 1_000_000)
        fx.server.enqueue(json("jeuInfos-arcade-full.json") { body })

        val details = scrape().details

        val size = fx.responseFiles().single().length()
        println("v3 cache file of a rich game: $size bytes")
        assertTrue("cache file is $size bytes", size < RICH_GAME_CACHE_BOUND)
        assertEquals(6, details.synopses.size)
        assertEquals(34, details.buttonColours.size)
        assertEquals(6, details.genres.size)
        assertEquals(36, details.genres.sumOf { it.names.size })
        assertEquals(
            mapOf(MediaKind.BoxFront to 10, MediaKind.Support to 8, MediaKind.Screenshot to 1, MediaKind.TitleScreen to 1, MediaKind.Manual to 5),
            details.media.groupingBy { it.kind }.eachCount(),
        )
    }

    /** A name search answer (jeuRecherche.php) with arcade fields written as strings. */
    private fun searchWithStringColours() = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"response": {"ssuser": {"id": "test-user", "numid": "1234"}, "jeux": [{"id": "5001",
            "noms": [{"region": "us", "text": "Comet Runner"}], "systeme": {"id": "1", "text": "Megadrive"},
            "rotation": "0", "controles": "2", "couleurs": "1", "classifications": "", "modes": {"id": "1"},
            "medias": [{"type": "box-2D", "url": "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(us)", "region": "us"}]}]}}""",
    )

    @Test fun `a list field written as a string or an object reads as empty and the name result still counts`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(searchWithStringColours())

        val result = fx.scraper(fx.config()).scrape(fx.request(fx.rom("Comet Runner.bin"), title = "Comet Runner"))

        val details = (result as ScrapeResult.Matched).details
        assertEquals(GameMatch("5001", MatchedBy.Name), result.match)
        assertEquals(emptyList<ButtonColour>(), details.buttonColours)
        assertEquals(emptyList<Classification>(), details.classifications)
        assertEquals(emptyList<GameTag>(), details.modes)
        assertEquals(2, details.controls)
        assertEquals(0, details.rotation)
    }

    @Test fun `a game with few fields has empty lists and nulls, not a failure`() = runTest {
        fx.server.enqueue(status(404, NOT_FOUND_BODY))
        fx.server.enqueue(json("jeuRecherche-match.json"))

        val result = fx.scraper(fx.config())
            .scrape(fx.request(fx.rom("Puzzle.gbc"), systemId = "gbc", title = "Pok\u00e9mon Puzzle Challenge"))

        val details = (result as ScrapeResult.Matched).details
        assertEquals(
            GameDetails(
                names = listOf(TextByRegion("us", "Pok\u00e9mon Puzzle Challenge"), TextByRegion("eu", "Pok\u00e9mon Puzzle Challenge (EU)")),
                dates = listOf(TextByRegion("us", "2000-12-04")),
                system = IdName("10", "Game Boy Color"),
                publisher = IdName(null, "Nintendo"),
                media = result.media,
            ),
            details,
        )
    }

    private companion object {
        /** Just above the 16,834 bytes measured on a real answer. */
        const val RICH_GAME_CACHE_BOUND = 18_000L
    }
}
