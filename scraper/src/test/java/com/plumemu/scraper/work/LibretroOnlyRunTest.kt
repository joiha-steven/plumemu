package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Box art only": a whole run with the libretro-only scraper, signed in or not. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class LibretroOnlyRunTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 300))

    private fun png() = MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(jpegBytes(600, 900))).build()

    private fun serverHas(vararg have: String) {
        libretroAnswer = { request ->
            val name = java.net.URLDecoder.decode(request.url.encodedPath.substringAfterLast('/'), "UTF-8")
            val found = name in have
            when {
                request.method == "HEAD" -> MockResponse.Builder().code(if (found) 200 else 404).build()
                found -> png()
                else -> status(404, "no")
            }
        }
    }

    private fun game(file: String = "Metroid Prime - Hunters (USA).nds", system: String = "nds", title: String = "Metroid Prime - Hunters") {
        source = FakeScrapeSource(listOf(fx.request(fx.rom(file, "rom bytes"), systemId = system, title = title)))
    }

    private fun assertOnlyLibretroWasAsked() {
        assertTrue("ScreenScraper was asked: $asked", asked.isEmpty())
        assertTrue("a media request went to ScreenScraper", mediaRequests.isEmpty())
        assertTrue("Steam was asked", steamRequests.isEmpty())
        val sent = libretroRequests.joinToString("\n") { it.url.toString() + " " + it.headers }
        for (secret in listOf("devid", "devpassword", "ssid", "sspassword", "test-user", "test-dev", "softname")) {
            assertTrue("a credential reached libretro ($secret): $sent", secret !in sent)
        }
    }

    @Test fun `with no account libretro gives the box front, downsized, and nothing else is asked`() = runTest {
        game()
        serverHas("Metroid Prime - Hunters (USA).png")
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val delivery = source.deliveries.single()
        val result = delivery.result as ScrapeResult.Matched
        assertEquals(MatchedBy.Fallback, result.match.matchedBy)
        assertTrue(result.match.screenScraperGameId!!.startsWith("libretro_"))
        val saved = delivery.media.single()
        assertEquals(MediaKind.BoxFront, saved.kind)
        assertTrue("downsized from 600x900: ${saved.width}x${saved.height}", saved.width <= 200 && saved.height <= 300)
        assertEquals(listOf("HEAD", "GET"), libretroRequests.map { it.method })
        assertOnlyLibretroWasAsked()
    }

    @Test fun `signed in with Box art only still never calls ScreenScraper`() = runTest {
        game()
        serverHas("Metroid Prime - Hunters (USA).png")
        host(virtualClock(start), sizes = grid, libretroOnly = true)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(1, source.deliveries.size)
        assertOnlyLibretroWasAsked()
    }

    @Test fun `a build without developer credentials still gets libretro box art`() = runTest {
        game()
        serverHas("Metroid Prime - Hunters (USA).png")
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null, developer = com.plumemu.scraper.DeveloperCredentials("", ""))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(source.deliveries.single().result is ScrapeResult.Matched)
    }

    @Test fun `a 404 for every picture keeps the poster cover and is delivered as no match`() = runTest {
        game()
        serverHas()
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val delivery = source.deliveries.single()
        assertEquals(ScrapeResult.NoMatch, delivery.result)
        assertTrue(delivery.media.isEmpty())
        assertOnlyLibretroWasAsked()
    }

    @Test fun `a server error leaves the game for the next run, then gives it up after three runs`() = runTest {
        game()
        libretroAnswer = { status(503, "down") }
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null)

        repeat(3) {
            assertEquals(ListenableWorker.Result.success(), runWorker())
            assertTrue("run ${it + 1}: left for the next run", source.deliveries.isEmpty())
        }
        runWorker()

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertTrue(source.failedKeys.isEmpty())
        assertTrue(fx.logLines.any { it.startsWith("libretro art:") })
    }

    @Test fun `no network leaves the poster cover, does not crash and does not loop`() = runTest {
        game()
        fx.server.close()
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null)

        val result = runWorker()

        assertTrue("the run ends: $result", result is ListenableWorker.Result)
        assertTrue("not delivered with a picture", source.deliveries.none { it.result is ScrapeResult.Matched })
        assertTrue(fx.logLines.any { it.startsWith("libretro art:") })
    }

    @Test fun `a system libretro does not cover makes no request at all`() = runTest {
        game(file = "Game.nsp", system = "switch", title = "Game")
        host(virtualClock(start), sizes = grid, libretroOnly = true, user = null)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertEquals(0, libretroRequests.size)
        assertOnlyLibretroWasAsked()
    }
}
