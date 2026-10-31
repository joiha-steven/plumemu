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
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** libretro's thumbnails as a source of box art once ScreenScraper has none, through a whole run. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class LibretroFallbackRunTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 300))

    private fun png() = MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(jpegBytes(600, 900))).build()

    /** A fake server that has the pictures named in [have] (HEAD answers no body, as a real one does). */
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
        repeat(4) { gameAnswers += status(404, NOT_FOUND_BODY) }
    }

    private fun timing() = fx.timingLines.filter { "pass=main" in it }.joinToString("\n")

    @Test fun `a game ScreenScraper does not know gets the libretro picture as its box front`() = runTest {
        game()
        serverHas("Metroid Prime - Hunters (USA).png")
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val delivery = source.deliveries.single()
        val result = delivery.result as ScrapeResult.Matched
        assertTrue(result.match.screenScraperGameId!!.startsWith("libretro_"))
        assertEquals(MatchedBy.Fallback, result.match.matchedBy)
        assertEquals(listOf(MediaKind.BoxFront), delivery.media.map { it.kind })
        assertEquals(listOf("HEAD", "GET"), libretroRequests.map { it.method })
        assertTrue(timing(), "outcome=matched-libretro" in timing())
        assertTrue(source.failedKeys.isEmpty())
    }

    @Test fun `no picture in libretro leaves the no-match as it was`() = runTest {
        game()
        serverHas()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertTrue(timing(), "outcome=libretro-none" in timing())
    }

    @Test fun `libretro down is logged, the game is not given up and the run does not crash`() = runTest {
        game()
        libretroAnswer = { status(503, "down") }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue("left for the next run, not delivered as a no-match", source.deliveries.isEmpty())
        assertTrue("no failure day recorded for the game", source.failedKeys.isEmpty())
        assertTrue(timing(), "outcome=libretro-failed" in timing())
        assertTrue(fx.logLines.any { it.startsWith("libretro art:") })
    }

    @Test fun `a game of a system libretro does not cover makes no libretro request`() = runTest {
        game(file = "Game.nsp", system = "switch", title = "Game")
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(0, libretroRequests.size)
    }

    @Test fun `names the server refuses never stop libretro, every game is delivered`() = runTest {
        source = FakeScrapeSource((1..4).map { fx.request(fx.rom("G$it.nds", "rom $it"), systemId = "nds", title = "Game $it") })
        repeat(12) { gameAnswers += status(404, NOT_FOUND_BODY) }
        libretroAnswer = { if (it.method == "HEAD") MockResponse.Builder().code(403).build() else status(403, "forbidden") }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(4, source.deliveries.size)
        assertTrue(source.deliveries.all { it.result == ScrapeResult.NoMatch })
        assertEquals("libretro was asked for every game", 4, libretroRequests.count { it.method == "HEAD" })
    }

    @Test fun `a game whose fallback failed in three runs in a row is delivered as a no-match`() = runTest {
        game()
        repeat(6) { gameAnswers += status(404, NOT_FOUND_BODY) }
        libretroAnswer = { status(503, "down") }
        host(virtualClock(start), sizes = grid)

        repeat(3) {
            runWorker()
            assertTrue("run ${it + 1}: left for the next run", source.deliveries.isEmpty())
        }
        runWorker()

        assertEquals(ScrapeResult.NoMatch, source.deliveries.single().result)
        assertTrue(source.failedKeys.isEmpty())
    }
}
