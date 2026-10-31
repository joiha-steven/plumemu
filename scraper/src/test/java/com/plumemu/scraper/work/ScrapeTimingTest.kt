package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.ScraperFixture.Companion.fixture
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okio.Buffer
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The timing lines (tag PlumScrapeTiming): one per request, one per game, one for the account
 * limits. They answer "where did the time go" and must never show a URL or a credential.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeTimingTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 200))
    private val box = pngBytes(200, 280)
    private val disc = pngBytes(240, 240)

    /** The match fixture pointed at this server with a support, and the text it answers with. */
    private fun matchText(): String {
        val base = fx.server.url("/").toString()
        val support = """{"type": "support-2D", "url": "${base}api2/mediaJeu.php?systemeid=1&jeuid=1001&media=support-2D(us)", "region": "us", "format": "png"}"""
        return fixture("jeuInfos-match.json").replace("https://neoclone.screenscraper.fr/", base).replace(""""medias": [""", """"medias": [$support, """)
    }

    private fun field(line: String, name: String): String =
        Regex("(?:^| )$name=(\\S+)").find(line)?.groupValues?.get(1) ?: error("no $name in: $line")

    private fun ms(line: String, name: String): Long = field(line, name).removeSuffix("ms").toLong()

    private fun bytes(line: String, name: String): Long = field(line, name).removeSuffix("B").toLong()

    @Test fun `a hash match with box and disc logs each request and a game line per pass`() = runTest {
        mediaAnswer = { if (mediaRequests.last().contains("support-2D")) imageResponse(disc) else imageResponse(box) }
        source = FakeScrapeSource(roms.take(1))
        val text = matchText()
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), text)
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val lines = fx.timingLines.toList()
        lines.forEach { println("PlumScrapeTiming: $it") }
        val requests = lines.filter { it.startsWith("req ") }
        assertEquals(3, requests.size)
        val info = requests[0]
        assertEquals("jeuInfos", field(info, "endpoint"))
        assertEquals("200", field(info, "code"))
        assertEquals(text.toByteArray().size.toLong(), field(info, "bytes").toLong())
        assertEquals(0L, ms(info, "gateWait"))
        assertTrue(ms(info, "total") >= ms(info, "headers"))
        assertEquals(listOf("BoxFront", "Support"), requests.drop(1).map { field(it, "kind") })
        assertEquals(listOf("media", "media"), requests.drop(1).map { field(it, "endpoint") })
        assertEquals(box.size.toLong(), field(requests[1], "bytes").toLong())
        assertEquals(disc.size.toLong(), field(requests[2], "bytes").toLong())

        // The main pass: the lookup and the box front. The disc pass: the cached answer, the box reused, the disc.
        val (game, discGame) = lines.filter { it.startsWith("game ") }.also { assertEquals(2, it.size) }
        assertEquals("main", field(game, "pass"))
        assertEquals("megadrive", field(game, "sys"))
        assertEquals("md", field(game, "ext"))
        assertEquals("matched-hash", field(game, "outcome"))
        assertEquals("read-full", field(game, "hashSrc"))
        assertEquals(roms.first().rom.sizeBytes, bytes(game, "size"))
        assertEquals(text.toByteArray().size.toLong(), bytes(game, "lookupBytes"))
        assertEquals("1", field(game, "lookupReqs"))
        assertEquals(box.size.toLong(), bytes(game, "boxBytes"))
        assertEquals("saved", field(game, "box"))
        assertEquals("-", field(game, "disc"))
        assertEquals("0", field(game, "discReqs"))
        assertTrue(ms(game, "boxSave") >= 0 && ms(game, "deliver") >= 0)
        assertTrue(ms(game, "total") >= ms(game, "lookup"))

        assertEquals("disc", field(discGame, "pass"))
        assertEquals("matched-hash", field(discGame, "outcome"))
        assertEquals("0", field(discGame, "lookupReqs"))
        assertTrue(field(discGame, "cacheHits").startsWith("1/"))
        assertEquals("reused", field(discGame, "box"))
        assertEquals("0", field(discGame, "boxReqs"))
        assertEquals("saved", field(discGame, "disc"))
        assertEquals(disc.size.toLong(), bytes(discGame, "discBytes"))
        assertTrue(ms(discGame, "discSave") >= 0)

        // Once when first seen, then again after every later page line (logcat drops a long run's first lines).
        val limitLines = lines.filter { it.startsWith("limits ") }
        val limits = limitLines.first()
        assertEquals("256KBps", field(limits, "maxdownloadspeed"))
        assertEquals("300", field(limits, "maxrequestspermin"))
        assertEquals("1", field(limits, "slots"))
        assertEquals("12", field(limits, "requeststoday"))
        assertTrue(limitLines.all { it == limits })
        val pages = lines.indices.filter { lines[it].startsWith("page ") }
        assertTrue("a page before the first answer has no limits to show", lines.getOrNull(pages.first() + 1)?.startsWith("limits ") != true)
        val later = pages.drop(1)
        assertTrue(later.isNotEmpty())
        later.forEach { assertEquals(limits, lines[it + 1]) }
        assertEquals(1 + later.size, limitLines.size)
    }

    @Test fun `a game with no match logs both lookups and no pictures`() = runTest {
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += status(404, ScraperFixture.NOT_FOUND_BODY)
        val search = fixture("jeuRecherche-empty.json")
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), search)
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val lines = fx.timingLines.toList()
        lines.forEach { println("PlumScrapeTiming: $it") }
        val requests = lines.filter { it.startsWith("req ") }
        assertEquals(listOf("jeuInfos", "jeuRecherche"), requests.map { field(it, "endpoint") })
        assertEquals(listOf("404", "200"), requests.map { field(it, "code") })
        assertEquals(ScraperFixture.NOT_FOUND_BODY.toByteArray().size.toLong(), field(requests[0], "bytes").toLong())
        assertEquals(search.toByteArray().size.toLong(), field(requests[1], "bytes").toLong())
        val game = lines.single { it.startsWith("game ") }
        // A Mega Drive game: libretro's thumbnails were asked after ScreenScraper and had nothing (its outcome word wins).
        assertEquals("libretro-none", field(game, "outcome"))
        assertEquals("2", field(game, "lookupReqs"))
        assertEquals(0L, bytes(game, "boxBytes"))
        assertEquals("-", field(game, "box"))
        assertEquals(1, source.deliveries.size)
        assertEquals(emptyList<MediaKind>(), source.deliveries.single().media.map { it.kind })
    }

    @Test fun `no timing line shows a URL, a credential, a query or a file name`() = runTest {
        mediaAnswer = { imageResponse(box) }
        source = FakeScrapeSource(roms.take(2))
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), matchText())
        gameAnswers += status(500, "devid=${ScraperFixture.DEV_ID} ${ScraperFixture.USER_PASSWORD} unwell")
        host(virtualClock(start), sizes = grid)

        runWorker()

        val all = fx.timingLines.joinToString("\n")
        assertTrue(all.isNotBlank())
        val forbidden = ScraperFixture.SECRETS + listOf(
            ScraperFixture.DEV_ID, ScraperFixture.USER_NAME, "http", "://", "&", "/api2/", "mediaJeu", ".php",
            "romnom", "output=json", "Game1", "Game2", fx.server.hostName, fx.server.port.toString(), tmp.root.name,
        )
        forbidden.forEach { assertFalse("timing log shows '$it':\n$all", all.contains(it)) }
    }

    @Test fun `a picture cut off mid-body shows its error and the partial bytes`() = runTest {
        mediaAnswer = {
            MockResponse.Builder().code(200).body(Buffer().write(ByteArray(200_000))).onResponseBody(SocketEffect.CloseSocket()).build()
        }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), matchText())
        host(virtualClock(start), sizes = grid)

        runWorker()

        val media = fx.timingLines.first { it.startsWith("req endpoint=media") }
        println("PlumScrapeTiming: $media")
        assertTrue(media, Regex(" error=\\w+").containsMatchIn(media))
        assertTrue(media, "headers=-" !in media)
    }

    @Test fun `a thread-limit answer logs the event, the retry and the backoff`() = runTest {
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += status(429, "The maximum threads is already used")
        gameAnswers += status(404, ScraperFixture.NOT_FOUND_BODY)
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), fixture("jeuRecherche-empty.json"))
        host(virtualClock(start), sizes = grid)

        runWorker()

        val lines = fx.timingLines.toList()
        lines.forEach { println("PlumScrapeTiming: $it") }
        assertEquals(listOf("429", "404", "200"), lines.filter { it.startsWith("req ") }.map { field(it, "code") })
        assertEquals("event=thread-limit attempt=1 backoff=2000ms slots=1", lines.single { it.startsWith("event=") })
        assertEquals(2000L, ms(lines.single { it.startsWith("game ") }, "backoff"))
    }

    @Test fun `a logger that throws changes nothing about the run`() = runTest {
        mediaAnswer = { imageResponse(box) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += MockResponse(200, Headers.headersOf("Content-Type", "application/json"), matchText())
        host(virtualClock(start), sizes = grid, timingLog = { error("the logger broke") })

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(ScrapePass.Main, ScrapePass.Support), source.deliveries.map { it.pass })
        assertEquals(listOf(MediaKind.BoxFront, MediaKind.Support), source.supportDeliveries.single().media.map { it.kind })
    }
}
