package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.fallback.LibretroArt
import com.plumemu.scraper.fallback.LibretroSystems
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.net.URLDecoder

/** Box art from libretro's thumbnail server ([LibretroArt], through the real scraper), against a fake server. */
class LibretroArtTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture
    private lateinit var scraper: ScreenScraperScraper
    private var now = 1_000_000L
    private val seen = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()

    /** File names (with `.png`) the fake server has; its folder listing is the HTML index of them. */
    private var pictures: List<String> = emptyList()
    private var listingStatus = 200

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val path = request.url.encodedPath
                if (path.endsWith("/Named_Boxarts/")) {
                    return if (listingStatus == 200) MockResponse(200, Headers.headersOf("Content-Type", "text/html"), listing()) else status(listingStatus, "no")
                }
                val name = URLDecoder.decode(path.substringAfterLast('/'), "UTF-8")
                val has = name in pictures
                // A real server sends no body to a HEAD request.
                if (request.method == "HEAD") return MockResponse.Builder().code(if (has) 200 else 404).build()
                return if (has) MockResponse.Builder().code(200).body("png bytes").build() else status(404, "no")
            }
        }
        scraper = fx.scraper(fx.config(clock = { now }))
    }

    @After fun tearDown() = fx.close()

    private fun listing() = "<html><body><pre>\n" + pictures.joinToString("\n") { name ->
        """<a href="${name.replace("&", "&amp;").replace(" ", "%20").replace("(", "%28").replace(")", "%29")}">$name</a>   2020-01-01 10:00  100K"""
    } + "\n</pre></body></html>"

    private fun request(file: String, title: String, system: String = "nds", regions: List<String> = listOf("us", "wor", "eu", "jp")) =
        fx.request(fx.rom(file, "x"), systemId = system, title = title).copy(regions = regions)

    private fun foundUrl(result: FallbackArt) = java.net.URI((result as FallbackArt.Found).result.media.single().url).path

    private val paths get() = seen.map { it.url.encodedPath }

    @Test fun `the picture named like the file is found with one request`() = runTest {
        pictures = listOf("Metroid Prime - Hunters (USA) (Rev 1).png")

        val result = scraper.fallbackArt(request("Metroid Prime - Hunters (USA) (Rev 1).nds", "Metroid Prime - Hunters"))

        assertEquals("/libretro/Nintendo - Nintendo DS/Named_Boxarts/Metroid Prime - Hunters (USA) (Rev 1).png", URLDecoder.decode(foundUrl(result), "UTF-8"))
        assertEquals(1, seen.size)
        assertEquals("HEAD", seen.single().method)
        val matched = (result as FallbackArt.Found).result
        assertEquals(MatchedBy.Fallback, matched.match.matchedBy)
        assertTrue(matched.match.screenScraperGameId!!.matches(Regex("libretro_[0-9a-f]{20}")))
        assertNull(matched.info.title)
    }

    @Test fun `characters libretro replaces are replaced in the name asked`() = runTest {
        pictures = listOf("Zelda_ Link's Awakening (USA).png")

        val result = scraper.fallbackArt(request("Zelda: Link's Awakening (USA).gb", "Zelda: Link's Awakening", system = "gb"))

        assertTrue(result is FallbackArt.Found)
        assertEquals(1, seen.size)
        assertTrue(URLDecoder.decode(paths.single(), "UTF-8").endsWith("/Named_Boxarts/Zelda_ Link's Awakening (USA).png"))
        assertEquals("Zelda_ a_b_c_d_e_f_g_h_i_j_k", LibretroArt.sanitise("Zelda: a&b*c/d\\e`f<g>h?i|j\"k"))
    }

    @Test fun `no picture by file name, the listing gives the preferred region`() = runTest {
        pictures = listOf("Advance Wars (Europe).png", "Advance Wars (Japan).png", "Advance Wars (USA).png", "Advance Wars 2 (USA).png")

        val usa = scraper.fallbackArt(request("Advance Wars.gba", "Advance Wars", system = "gba", regions = listOf("us", "eu")))
        val europe = scraper.fallbackArt(request("Advance Wars.gba", "Advance Wars", system = "gba", regions = listOf("eu", "us")))

        assertTrue(URLDecoder.decode(foundUrl(usa), "UTF-8").endsWith("/Advance Wars (USA).png"))
        assertTrue(URLDecoder.decode(foundUrl(europe), "UTF-8").endsWith("Advance Wars (Europe).png"))
        assertEquals("the listing is asked once for both games", 1, paths.count { it.endsWith("/Named_Boxarts/") })
    }

    @Test fun `titles that differ beyond region and revision give nothing`() = runTest {
        pictures = listOf("Prey (2006) (USA).png", "Prey (2017) (USA).png")
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("Prey.nds", "Prey")))

        pictures = listOf("Prey (USA).png", "Prey (2017) (USA).png")
        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        assertEquals("a remake among the entries: no guess", FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("Prey.nds", "Prey")))

        pictures = listOf("Prey (USA).png", "Prey (USA) (Beta).png")
        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        assertTrue("the plain release beats a beta", URLDecoder.decode(foundUrl(scraper.fallbackArt(request("Prey.nds", "Prey"))), "UTF-8").endsWith("/Prey (USA).png"))
    }

    @Test fun `a refusal of one name or of the listing is a miss, not a failure`() = runTest {
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return if (request.method == "HEAD") MockResponse.Builder().code(403).build() else status(403, "forbidden")
            }
        }
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("A.nds", "Game")))
        assertEquals("the picture, then the listing", 2, seen.size)
    }

    @Test fun `a throttled name is a failure`() = runTest {
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(429).build()
        }
        assertEquals(FallbackArt.Failed("libretro-failed"), scraper.fallbackArt(request("A.nds", "Game")))
    }

    @Test fun `a multi-disc game takes its first disc, a game with only demos and betas takes nothing`() = runTest {
        pictures = listOf("Game (USA) (Disc 2).png", "Game (USA) (Disc 1).png")
        assertTrue(URLDecoder.decode(foundUrl(scraper.fallbackArt(request("Game.nds", "Game"))), "UTF-8").endsWith("/Game (USA) (Disc 1).png"))

        pictures = listOf("Other (USA) (Demo).png", "Other (Europe) (Beta 1).png")
        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("Other.nds", "Other")))
    }

    @Test fun `an empty listing is remembered however the systems interleave, and asked again after fourteen days`() = runTest {
        pictures = emptyList()
        scraper.fallbackArt(request("A.nds", "Game"))
        assertTrue(fx.logLines.single { it.contains("no usable folder listing") }.contains("warning"))
        // gba has a listing (a different folder: the fake serves the same pictures list, so give it one).
        pictures = listOf("Game (USA).png")
        scraper.fallbackArt(request("A.gba", "Game", system = "gba"))
        scraper.fallbackArt(request("B.nds", "Game"))
        scraper.fallbackArt(request("B.gba", "Game", system = "gba"))
        scraper.fallbackArt(request("C.nds", "Game"))

        assertEquals("nds listing once, gba listing once", 2, paths.count { it.endsWith("/Named_Boxarts/") })

        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        scraper.fallbackArt(request("D.nds", "Game"))
        assertEquals(3, paths.count { it.endsWith("/Named_Boxarts/") })
    }

    @Test fun `a request flagged as a hack takes only hack entries whatever its file name looks like`() = runTest {
        pictures = listOf("Super Mario Bros (USA).png")
        val flagged = request("Super Mario Bros [h1].nes", "Super Mario Bros", system = "nes").copy(hack = true)
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(flagged))
    }

    @Test fun `nothing named like the title gives nothing`() = runTest {
        pictures = listOf("Something Else (USA).png")
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("Limbo.nds", "Limbo")))
    }

    @Test fun `a hack file does not take its parent's picture, a hack entry is fine`() = runTest {
        pictures = listOf("Super Mario Bros (USA).png")
        assertEquals(FallbackArt.NotFound("libretro-none"), scraper.fallbackArt(request("Super Mario Bros [Hack].nes", "Super Mario Bros [Hack]", system = "nes")))

        pictures = listOf("Super Mario Bros (USA).png", "Super Mario Bros (Hack).png")
        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        val result = scraper.fallbackArt(request("Super Mario Bros [Hack] (x).nes", "Super Mario Bros [Hack]", system = "nes"))
        assertTrue(URLDecoder.decode(foundUrl(result), "UTF-8").endsWith("Super Mario Bros (Hack).png"))
    }

    @Test fun `a system libretro does not cover makes no request`() = runTest {
        for (system in listOf("switch", "android", "windows")) {
            assertEquals(FallbackArt.NotApplicable, scraper.fallbackArt(request("Game.nsp", "Game", system = system)))
        }
        assertEquals(0, seen.size)
    }

    @Test fun `the listing is cached in memory and on disk, and asked again after fourteen days`() = runTest {
        pictures = listOf("Game (USA).png")
        scraper.fallbackArt(request("A.nds", "Game"))
        scraper.fallbackArt(request("B.nds", "Game"))
        assertEquals(1, paths.count { it.endsWith("/Named_Boxarts/") })

        // A new scraper (another process) reads the file in the cache folder.
        val second = fx.scraper(fx.config(clock = { now }))
        second.fallbackArt(request("C.nds", "Game"))
        assertEquals(1, paths.count { it.endsWith("/Named_Boxarts/") })
        assertTrue(File(fx.cacheDir, LibretroArt.INDEX_DIR).listFiles().orEmpty().isNotEmpty())

        now += LibretroArt.INDEX_MAX_AGE_MILLIS + 1
        second.fallbackArt(request("D.nds", "Game"))
        assertEquals(2, paths.count { it.endsWith("/Named_Boxarts/") })
    }

    @Test fun `a failure is a failure, is not cached, and nothing of ScreenScraper's is sent`() = runTest {
        listingStatus = 503
        assertEquals(FallbackArt.Failed("libretro-failed"), scraper.fallbackArt(request("A.nds", "Game")))
        listingStatus = 200
        pictures = listOf("Game (USA).png")
        assertTrue("the failed listing was not remembered", scraper.fallbackArt(request("A.nds", "Game")) is FallbackArt.Found)
        assertTrue(fx.logLines.any { it.startsWith("libretro art:") })

        for (sent in seen) {
            val everything = sent.url.toString() + sent.headers.toString()
            for (secret in ScraperFixture.SECRETS) assertFalse("$secret reached libretro", everything.contains(secret))
            assertNull(sent.headers["Authorization"])
            assertNull(sent.headers["Cookie"])
        }
    }

    @Test fun `a server that cannot be reached is a failure`() = runTest {
        val config = ScraperConfig(
            cacheDir = fx.cacheDir, user = null, developer = ScraperFixture.DEVELOPER,
            libretroThumbnailsUrl = "http://127.0.0.1:1/", log = { fx.logLines += it },
        )
        val unreachable = fx.scraper(config)

        assertEquals(FallbackArt.Failed("libretro-failed"), unreachable.fallbackArt(request("A.nds", "Game")))
        assertTrue(fx.logLines.any { it.startsWith("libretro art: network error") })
    }

    @Test fun `downloads, a 404 is a missing picture, a 5xx a fault, a body is saved`() = runTest {
        pictures = listOf("Game (USA).png")
        val ref = (scraper.fallbackArt(request("Game (USA).nds", "Game")) as FallbackArt.Found).result.media.first()
        val target = File(tmp.root, "box.png")

        pictures = emptyList()
        assertEquals(MediaDownload.Broken, scraper.download(ref, target))
        pictures = listOf("Game (USA).png")
        assertEquals(MediaDownload.Done(target), scraper.download(ref, target))
        assertEquals("png bytes", target.readText())
    }

    @Test fun `every system in the table is a Plumemu system`() {
        val file = File("../platforms/src/main/assets/platforms.json")
        val ids = Json.parseToJsonElement(file.readText()).jsonObject.getValue("systems").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        assertTrue("not Plumemu systems: ${LibretroSystems.ids - ids}", (LibretroSystems.ids - ids).isEmpty())
    }

    @Test fun `every folder name in the table is a folder of the live server`() {
        // libretro-root-folders.txt: the folders of https://thumbnails.libretro.com/ as fetched when the file was made.
        val folders = File("src/test/resources/libretro-root-folders.txt").readLines().filter { it.isNotBlank() }.toSet()
        assertTrue("folders not on the server: ${LibretroSystems.playlists.toSet() - folders}", (LibretroSystems.playlists.toSet() - folders).isEmpty())
    }

    @Test fun `listing parser undoes percent and HTML escapes`() {
        val html = """<a href="Tom%20%26%20Jerry%20%28USA%29.png">x</a> <a href="A&amp;B+C.png">y</a><a href="sub/">d</a>"""
        val names: List<String> = LibretroArt.parseListing(html.byteInputStream() as InputStream)
        assertEquals(listOf("Tom & Jerry (USA)", "A&B+C"), names)
    }
}
