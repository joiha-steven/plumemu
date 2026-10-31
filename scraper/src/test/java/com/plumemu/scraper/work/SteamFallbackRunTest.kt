package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperFixture.Companion.NOT_FOUND_BODY
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Steam's image CDN as the next source of box art once ScreenScraper has none, through a whole run. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class SteamFallbackRunTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 300))

    private fun jpeg() = MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").body(Buffer().write(jpegBytes(600, 900))).build()

    /** One Steam game whose ScreenScraper name search finds nothing. */
    private fun steamGame(appIdFile: String = "98765") {
        source = FakeScrapeSource(listOf(fx.request(fx.rom("Limbo.steam", appIdFile), systemId = "steam", title = "Limbo")))
        gameAnswers += status(404, NOT_FOUND_BODY)
    }

    private fun timing() = fx.timingLines.filter { "pass=main" in it }.joinToString("\n")

    @Test fun `a Steam game ScreenScraper does not know gets the CDN portrait as its box front`() = runTest {
        steamGame()
        steamAnswer = { jpeg() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val delivery = source.deliveries.single()
        val result = delivery.result as ScrapeResult.Matched
        assertEquals("steam_98765", result.match.screenScraperGameId)
        assertEquals(MatchedBy.Fallback, result.match.matchedBy)
        assertEquals(listOf(MediaKind.BoxFront), delivery.media.map { it.kind })
        assertTrue(File(delivery.media.single().path).exists() || File(mediaDir, delivery.media.single().path).exists())
        assertEquals(listOf("/steam-cdn/apps/98765/library_600x900.jpg"), steamRequests.map { it.url.encodedPath })
        assertTrue(timing(), "outcome=matched-steam-cdn" in timing())
        assertTrue(source.failedKeys.isEmpty())
    }

    @Test fun `no portrait on the CDN, the header is used`() = runTest {
        steamGame()
        steamAnswer = { if (it.url.encodedPath.endsWith("header.jpg")) jpeg() else status(404, "no") }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(
            listOf("library_600x900.jpg", "library_600x900_2x.jpg", "header.jpg"),
            steamRequests.map { it.url.encodedPath.substringAfterLast('/') },
        )
        assertTrue(source.deliveries.single().result is ScrapeResult.Matched)
        assertEquals(1, source.deliveries.single().media.size)
    }

    @Test fun `no picture at all on the CDN leaves the no-match as it was`() = runTest {
        steamGame()
        steamAnswer = { status(404, "no") }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertTrue(source.deliveries.single().media.isEmpty())
        assertTrue(timing(), "outcome=steam-none" in timing())
    }

    @Test fun `a game found by name through the store search gets its box`() = runTest {
        steamGame(appIdFile = "not an app id")
        steamAnswer = {
            if (it.url.encodedPath.startsWith("/steam-store/")) {
                MockResponse.Builder().code(200).body("""{"total":1,"items":[{"type":"app","id":19,"name":"LIMBO"}]}""").build()
            } else {
                jpeg()
            }
        }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals("steam_19", (source.deliveries.single().result as ScrapeResult.Matched).match.screenScraperGameId)
        assertEquals(listOf("/steam-store/", "/steam-cdn/apps/19/library_600x900.jpg"), steamRequests.map { it.url.encodedPath })
    }

    @Test fun `an ambiguous name saves nothing`() = runTest {
        steamGame(appIdFile = "not an app id")
        steamAnswer = { MockResponse.Builder().code(200).body("""{"items":[{"type":"app","id":1,"name":"Limbo"},{"type":"app","id":2,"name":"Limbo"}]}""").build() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertEquals(1, steamRequests.size)
    }

    @Test fun `Steam down is logged and the game is left for the next run, not given up and not stored as no-match`() = runTest {
        steamGame()
        steamAnswer = { MockResponse.Builder().code(200).body("x").onResponseBody(SocketEffect.CloseSocket()).build() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue("nothing delivered: a no-match row would wait 30 days", source.deliveries.isEmpty())
        assertTrue("no failure day recorded for the game", source.failedKeys.isEmpty())
        assertTrue(timing(), "outcome=steam-failed" in timing())
        assertTrue(fx.logLines.any { it.startsWith("steam art:") })
    }

    @Test fun `three Steam failures in a row stop asking Steam but not the run, ScreenScraper games go on`() = runTest {
        val steamGames = (1..4).map { fx.request(fx.rom("G$it.steam", "$it"), systemId = "steam", title = "Game $it") }
        source = FakeScrapeSource(steamGames + roms.take(1))
        repeat(8) { gameAnswers += status(404, NOT_FOUND_BODY) }
        steamAnswer = { status(503, "down") }
        host(virtualClock(start), sizes = grid)

        assertEquals("not a retry of the whole run", ListenableWorker.Result.success(), runWorker())

        assertEquals("the fourth Steam game was not even tried", 3, steamRequests.size)
        assertEquals("the megadrive game behind them was delivered", listOf(roms[0].rom.key), source.deliveredKeys)
        assertTrue(fx.logLines.any { it.startsWith("fallback art: 3 failures in a row") })
    }

    @Test fun `a game of another system never asks Steam`() = runTest {
        source = FakeScrapeSource(roms.take(1))
        repeat(2) { gameAnswers += status(404, NOT_FOUND_BODY) }
        steamAnswer = { jpeg() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertTrue(steamRequests.isEmpty())
    }

    @Test fun `no request to Steam carries ScreenScraper credentials`() = runTest {
        steamGame(appIdFile = "not an app id")
        steamAnswer = {
            if (it.url.encodedPath.startsWith("/steam-store/")) {
                MockResponse.Builder().code(200).body("""{"items":[{"type":"app","id":19,"name":"Limbo"}]}""").build()
            } else {
                jpeg()
            }
        }
        host(virtualClock(start), sizes = grid)
        runWorker()

        assertTrue(steamRequests.size >= 2)
        for (sent in steamRequests) {
            val everything = sent.url.toString() + sent.headers.toString()
            for (secret in com.plumemu.scraper.ScraperFixture.SECRETS) assertTrue("$secret reached Steam", !everything.contains(secret))
        }
    }
}
