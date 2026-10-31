package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.SharedPreferencesPauseStore
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.gate.ParisTime
import com.plumemu.scraper.media.MediaSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The disc or cartridge picture comes in the run's disc pass, after every box front, and never holds its game back: the main pass delivers the box front, the disc pass
 * delivers the game again with its disc or cartridge, or without it when that fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerSupportTest : WorkerTestBase() {
    private val grid = listOf(MediaSize("grid", 200, 200))

    /** The match fixture pointed at this server, with a us and a jp support. */
    private fun matchWithSupports(): MockResponse {
        val base = fx.server.url("/").toString()
        val supports = listOf("jp", "us").joinToString(", ") { region ->
            """{"type": "support-2D", "url": "${base}api2/mediaJeu.php?systemeid=1&jeuid=1001&media=support-2D($region)", "region": "$region", "format": "png"}"""
        }
        return json("jeuInfos-match.json") {
            it.replace("https://neoclone.screenscraper.fr/", base).replace(""""medias": [""", """"medias": [$supports, """)
        }
    }

    /** Pictures for box fronts; [support] answers the disc or cartridge requests. */
    private fun answers(support: () -> MockResponse) {
        mediaAnswer = { if (mediaRequests.last().contains("support-2D")) support() else imageResponse(pngBytes(200, 280)) }
    }

    @Test fun `the support is saved after the box front, in the preferred region, through the same resize`() = runTest {
        answers { imageResponse(pngBytes(240, 240)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchWithSupports()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(ScrapePass.Main, ScrapePass.Support), source.deliveries.map { it.pass })
        assertEquals(listOf(MediaKind.BoxFront), source.mainDeliveries.single().media.map { it.kind })
        val media = source.supportDeliveries.single().media
        assertEquals(listOf(MediaKind.BoxFront, MediaKind.Support), media.map { it.kind })
        assertEquals("the disc pass reads the cached answer", 1, asked.size)
        assertTrue(File(media[1].path).name.startsWith("support-grid-"))
        assertEquals(200 to 200, media[1].width to media[1].height)
        assertEquals(2, mediaRequests.size)
        assertTrue(mediaRequests[0].contains("box-2D(us)"))
        assertTrue(mediaRequests[1].contains("support-2D(us)"))
        assertTrue(mediaRequests[1].contains("maxwidth=200"))
        assertTrue("a box front is asked as a JPEG", mediaRequests[0].contains("outputformat=jpg"))
        assertFalse("a disc stays PNG", mediaRequests[1].contains("outputformat"))
        assertEquals("the box front is reused, not downloaded again", source.mainDeliveries.single().media.single().path, media[0].path)
    }

    @Test fun `only a box front is capped at 720 and asked as a JPEG, a disc keeps the full size and PNG`() = runTest {
        answers { imageResponse(pngBytes(240, 240)) }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchWithSupports()
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 560, 400), MediaSize("large", 1240, 1080)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val box = mediaRequests.single { it.contains("box-2D") }.toHttpUrl()
        assertEquals("720", box.queryParameter("maxheight"))
        assertEquals("jpg", box.queryParameter("outputformat"))
        val disc = mediaRequests.single { it.contains("support-2D") }.toHttpUrl()
        assertEquals("1080", disc.queryParameter("maxheight"))
        assertEquals(null, disc.queryParameter("outputformat"))
    }

    @Test fun `a game without a support is delivered with its box front only`() = runTest {
        answers { error("no support should be asked") }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += json("jeuInfos-match.json") { it.replace("https://neoclone.screenscraper.fr/", fx.server.url("/").toString()) }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(MediaKind.BoxFront), source.deliveries.single().media.map { it.kind })
        assertEquals(1, mediaRequests.size)
    }

    @Test fun `a support server error still delivers the game with its box front`() = runTest {
        answers { status(500, "media server unwell") }
        source = FakeScrapeSource(roms.take(2))
        repeat(2) { gameAnswers += matchWithSupports() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(ScrapePass.Main, ScrapePass.Main, ScrapePass.Support, ScrapePass.Support), source.deliveries.map { it.pass })
        source.deliveries.forEach { assertEquals(listOf(MediaKind.BoxFront), it.media.map { saved -> saved.kind }) }
        // Both games are the fixture's game 1001: the second reuses the first one's box front files.
        assertEquals("the box front first, once", listOf(true), mediaRequests.filter { !it.contains("support-2D") }.map { mediaRequests.first() == it })
        assertTrue(fx.logLines.any { it.contains("without its disc or cartridge picture") })
    }

    @Test fun `a support cut off mid-body still delivers the game with its box front`() = runTest {
        answers {
            MockResponse.Builder().code(200).body(Buffer().write(ByteArray(200_000))).onResponseBody(SocketEffect.CloseSocket()).build()
        }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchWithSupports()
        host(virtualClock(start), sizes = grid)

        // Delivered without it; one network error among deliveries is no stop (the library asks the disc again later).
        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(MediaKind.BoxFront), source.supportDeliveries.single().media.map { it.kind })
    }

    @Test fun `the daily quota on a support delivers its game, then pauses the run before the next game`() = runTest {
        answers { status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !") }
        source = FakeScrapeSource(roms.take(2))
        repeat(2) { gameAnswers += matchWithSupports() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        // Both box fronts came first; the first disc met the quota and nothing more was asked.
        assertEquals(listOf(ScrapePass.Main, ScrapePass.Main, ScrapePass.Support), source.deliveries.map { it.pass })
        assertEquals(listOf(MediaKind.BoxFront), source.supportDeliveries.single().media.map { it.kind })
        assertEquals(2, asked.size)
        // One box front (both games are the fixture's game 1001, so the second reuses it), one disc.
        assertEquals(2, mediaRequests.size)
        assertEquals(ParisTime.nextMidnight(start), SharedPreferencesPauseStore(app()).load()?.resumeAtMillis)
        // The follow-up run waits for the pause.
        assertEquals(1, uniqueWork().size)
    }

    @Test fun `refused credentials on a support deliver its game, then stop the run before the next game`() = runTest {
        answers { status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !") }
        source = FakeScrapeSource(roms.take(2))
        repeat(2) { gameAnswers += matchWithSupports() }
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertEquals(listOf(ScrapePass.Main, ScrapePass.Main, ScrapePass.Support), source.deliveries.map { it.pass })
        assertEquals(listOf(MediaKind.BoxFront), source.supportDeliveries.single().media.map { it.kind })
        assertEquals(2, asked.size)
        assertEquals("one box front (shared by both games' game 1001), one disc, nothing after the refusal", 2, mediaRequests.size)
    }

    @Test fun `supports that are not pictures are tried twice at most, and the game keeps its box front`() = runTest {
        answers { MockResponse(200, okhttp3.Headers.headersOf(), "NOMEDIA") }
        source = FakeScrapeSource(roms.take(1))
        gameAnswers += matchWithSupports()
        host(virtualClock(start), sizes = grid)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(MediaKind.BoxFront), source.supportDeliveries.single().media.map { it.kind })
        assertEquals(3, mediaRequests.size)
    }
}
