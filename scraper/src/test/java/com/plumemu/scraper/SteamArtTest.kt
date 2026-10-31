package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.fallback.SteamArt
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
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
import java.io.IOException
import java.io.InputStream

/**
 * Box art of a Steam game from Steam's image CDN ([SteamArt], through the real scraper): the app id
 * from the `.steam` file or from the store search, which pictures are asked, and that nothing of
 * ScreenScraper's ever goes to Steam.
 */
class SteamArtTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fx: ScraperFixture
    private lateinit var scraper: ScreenScraperScraper
    private var answer: (RecordedRequest) -> MockResponse = { status(404, "nothing") }
    private val seen = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()

    @Before fun setUp() {
        fx = ScraperFixture(tmp.root)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return answer(request)
            }
        }
        scraper = fx.scraper(fx.config())
    }

    @After fun tearDown() = fx.close()

    private fun steamFile(content: String, name: String = "Game.steam") = object : RomSource {
        override val key = "content://x/$name"
        override val fileName = name
        override val sizeBytes = content.length.toLong()
        override val lastModifiedMillis = 1L
        override fun open(): InputStream = content.byteInputStream()
    }

    private fun request(rom: RomSource, title: String = "Limbo", system: String = "steam") = fx.request(rom, systemId = system, title = title)

    private fun search(vararg items: Pair<Long, String>) = MockResponse(
        200,
        Headers.headersOf("Content-Type", "application/json"),
        """{"total": ${items.size}, "items": [${items.joinToString(",") { """{"type": "app", "id": ${it.first}, "name": "${it.second}", "price": {"final": 999}}""" }}]}""",
    )

    private fun found(result: FallbackArt) = (result as FallbackArt.Found).result

    @Test fun `an app id in the file gives the CDN pictures with no search`() = runTest {
        val result = found(scraper.fallbackArt(request(steamFile("274190\n"))))

        assertEquals(GameMatch("steam_274190", MatchedBy.Fallback), result.match)
        assertEquals(
            listOf("library_600x900.jpg", "library_600x900_2x.jpg", "header.jpg").map { "/steam-cdn/apps/274190/$it" },
            result.media.map { java.net.URI(it.url).path },
        )
        assertTrue(result.media.all { it.kind == MediaKind.BoxFront })
        assertNull(result.info.title)
        assertEquals("no request until a picture is downloaded", 0, seen.size)
    }

    @Test fun `an app id on the request gives the CDN pictures with no store search`() = runTest {
        // Some networks block store.steampowered.com; a pinned shortcut carries the id.
        val result = found(scraper.fallbackArt(request(steamFile("999\n"), title = "Ship of Fools").copy(steamAppId = 413150)))

        assertEquals(GameMatch("steam_413150", MatchedBy.Fallback), result.match)
        assertEquals("/steam-cdn/apps/413150/library_600x900.jpg", java.net.URI(result.media.first().url).path)
        assertEquals(0, seen.size)
    }

    @Test fun `a name that one app has gives that app, asked the way Steam's store search wants`() = runTest {
        answer = { search(19L to "Limbo", 20L to "Limbo Soundtrack") }

        val result = found(scraper.fallbackArt(request(steamFile("not an id"))))

        assertEquals("steam_19", result.match.screenScraperGameId)
        val url = seen.single().url
        assertEquals("/steam-store/", url.encodedPath)
        assertEquals(mapOf("term" to "Limbo", "l" to "english", "cc" to "US"), url.queryParameterNames.associateWith { url.queryParameter(it) })
    }

    @Test fun `a pinned shortcut that has no readable file is looked up by name`() = runTest {
        answer = { search(19L to "Limbo") }
        val unreadable = object : RomSource by steamFile("x", "Limbo.steam") {
            override fun open(): InputStream = throw IOException("no file")
        }

        assertEquals("steam_19", found(scraper.fallbackArt(request(unreadable))).match.screenScraperGameId)
    }

    @Test fun `of two apps the one named exactly like the title wins`() = runTest {
        answer = { search(19L to "Limbo (Special Edition)", 20L to "Limbo") }

        assertEquals("steam_20", found(scraper.fallbackArt(request(steamFile("x")))).match.screenScraperGameId)
    }

    @Test fun `two apps with the same name, or a remake, give nothing`() = runTest {
        answer = { search(1L to "Prey", 2L to "Prey") }
        assertEquals(FallbackArt.NotFound("steam-none"), scraper.fallbackArt(request(steamFile("x"), title = "Prey")))

        answer = { search(1L to "Prey", 2L to "Prey (2017)") }
        assertEquals(FallbackArt.NotFound("steam-none"), scraper.fallbackArt(request(steamFile("x"), title = "Prey")))
    }

    @Test fun `no app named like the title gives nothing`() = runTest {
        answer = { search(5L to "Something Else") }
        assertEquals(FallbackArt.NotFound("steam-none"), scraper.fallbackArt(request(steamFile("x"))))

        answer = { search() }
        assertEquals(FallbackArt.NotFound("steam-none"), scraper.fallbackArt(request(steamFile("x"))))
    }

    @Test fun `a store search that fails is a failure, not nothing, and is asked once`() = runTest {
        answer = { status(503, "down") }
        assertEquals(FallbackArt.Failed("steam-failed"), scraper.fallbackArt(request(steamFile("x"))))
        assertEquals(1, seen.size)

        answer = { MockResponse.Builder().code(200).body("{ not json").build() }
        assertEquals(FallbackArt.Failed("steam-failed"), scraper.fallbackArt(request(steamFile("x"))))

        answer = { MockResponse.Builder().code(200).body("{}").onResponseBody(SocketEffect.CloseSocket()).build() }
        assertEquals(FallbackArt.Failed("steam-failed"), scraper.fallbackArt(request(steamFile("x"))))
    }

    @Test fun `a store search the server refuses is not found, not a failure`() = runTest {
        answer = { status(403, "forbidden") }
        assertEquals(FallbackArt.NotFound("steam-none"), scraper.fallbackArt(request(steamFile("x"))))
        answer = { status(429, "slow down") }
        assertEquals(FallbackArt.Failed("steam-failed"), scraper.fallbackArt(request(steamFile("x"))))
    }

    @Test fun `a game of another system is not Steam's, so no request`() = runTest {
        // Other sources of the list may answer for this system; only Steam must stay silent.
        scraper.fallbackArt(request(steamFile("274190"), system = "switch"))
        assertEquals(0, seen.count { it.url.encodedPath.startsWith("/steam-") })
    }

    @Test fun `downloads, a 404 is a missing picture, a 5xx a fault, a body is saved`() = runTest {
        val ref = found(scraper.fallbackArt(request(steamFile("274190")))).media.first()
        val target = File(tmp.root, "box.jpg")

        answer = { status(404, "no") }
        assertEquals(MediaDownload.Broken, scraper.download(ref, target))
        answer = { status(502, "bad gateway") }
        assertEquals(MediaDownload.ServerError(502), scraper.download(ref, target))
        answer = { MockResponse.Builder().code(200).body("picture bytes").build() }
        assertEquals(MediaDownload.Done(target), scraper.download(ref, target))
        assertEquals("picture bytes", target.readText())
        answer = { MockResponse.Builder().code(200).body("0123456789").onResponseBody(SocketEffect.CloseSocket()).build() }
        assertEquals(MediaDownload.NetworkDown, scraper.download(ref, target))
        assertFalse(target.exists())
    }

    @Test fun `a picture that cannot be written is a storage failure, not a network one`() = runTest {
        val ref = found(scraper.fallbackArt(request(steamFile("274190")))).media.first()
        answer = { MockResponse.Builder().code(200).body("picture bytes").build() }
        val directory = File(tmp.root, "a-folder").apply { mkdirs() }

        assertEquals(MediaDownload.StorageFailed, scraper.download(ref, directory))
    }

    @Test fun `nothing of ScreenScraper's goes to Steam`() = runTest {
        answer = { if (it.url.encodedPath.startsWith("/steam-store/")) search(19L to "Limbo") else MockResponse.Builder().code(200).body("x").build() }
        val ref = found(scraper.fallbackArt(request(steamFile("not an id")))).media.first()
        scraper.download(ref, File(tmp.root, "box.jpg"))

        assertEquals(2, seen.size)
        for (sent in seen) {
            val everything = sent.url.toString() + sent.headers.toString()
            for (secret in ScraperFixture.SECRETS) assertFalse("$secret reached Steam", everything.contains(secret))
            for (param in listOf("devid", "devpassword", "ssid", "sspassword", "softname")) assertNull(param, sent.url.queryParameter(param))
            assertNull(sent.headers["Authorization"])
            assertNull(sent.headers["Cookie"])
        }
    }

    @Test fun `ScreenScraper's downloader still refuses a host that is not its own`() = runTest {
        val foreign = MediaRef(MediaKind.BoxFront, url = "https://cdn.akamai.steamstatic.com/steam/apps/1/header.jpg")

        assertEquals(MediaDownload.Broken, scraper.download(foreign, File(tmp.root, "box.jpg")))
        assertEquals(0, seen.size)
    }

    @Test fun `app id files hold digits only, byte order mark and UTF-16 allowed`() {
        assertEquals(274190L, SteamArt.parseAppId("274190".toByteArray()))
        assertEquals(274190L, SteamArt.parseAppId("\uFEFF 274190\r\n".toByteArray(Charsets.UTF_16LE)))
        assertEquals(274190L, SteamArt.parseAppId(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "274190\n".toByteArray()))
        for (bad in listOf("", "0", "abc", "12 34", "99999999999", "2147483648", "274190 Limbo")) assertNull(bad, SteamArt.parseAppId(bad.toByteArray()))
    }
}
