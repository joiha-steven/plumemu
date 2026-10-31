package com.plumemu.graph

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.RomHints
import com.plumemu.scraper.RomSource
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.timing.ScrapeTimingLog
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource
import com.plumemu.scraper.work.ScrapeWorker
import com.plumemu.scraper.work.ScraperHost
import com.plumemu.scraper.work.ScraperWork
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Application of the live tests: a [ScraperHost] whose work each test sets. */
class LiveHostApp : Application(), ScraperHost {
    @Volatile var work: ScraperWork? = null

    override suspend fun scraperWork(): ScraperWork? = work
}

/**
 * The timing run against the real ScreenScraper, opt-in only. Skipped unless Gradle
 * is given `-Pscreenscraper.live.timing=true` and the build has developer credentials; CI and
 * `scripts/verify.sh` never pass it. Developer credentials only (no account: one request at a
 * time); the real worker, engine and picture saving at the app's [ScrapeMediaSizes] into a temp
 * folder. Calls are counted at the scraper's door (a name search or a 429 retry inside the engine
 * is not counted); the smoke test that counts HTTP requests is the scraper module's
 * `ScreenScraperLiveSmokeTest`. Nothing printed carries a credential: every line goes through
 * [printSafe].
 *
 * `./gradlew :app:testDebugUnitTest --tests '*ScreenScraperLiveTest*' -Pscreenscraper.live.timing=true`;
 * the printed lines are in `app/build/test-results/testDebugUnitTest/TEST-*ScreenScraperLiveTest.xml`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], application = LiveHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenScraperLiveTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app: LiveHostApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun assumeAsked(property: String) {
        // First, so that a moved file fails in the private repository even when the live run is not asked for.
        PrivateTestData.assumePresent(TIMING_GAMES)
        assumeTrue("live tests run only with -P$property=true", System.getProperty(property) == "true")
        assumeTrue("live tests need a build with developer credentials", DeveloperCredentials.buildHasCredentials)
    }

    @Test
    fun timingOverTwentyOneGames() = runBlocking<Unit> {
        assumeAsked("screenscraper.live.timing")
        val requests = timingGames()
        val scraper = countingScraper()
        val source = TimedSource(requests)

        val started = System.currentTimeMillis()
        val result = runWorker(scraper, source)
        val totalSeconds = (System.currentTimeMillis() - started) / 1000.0

        printSafe("LIVE timing: worker result $result")
        // Seconds per game: from the previous box front to this one (the main pass; discs come after every box front).
        printSafe("LIVE timing: system | file | outcome | box front | disc | seconds")
        var previous = started
        for (request in requests) {
            val delivery = source.deliveries.firstOrNull { it.request === request && it.pass == ScrapePass.Main }
            if (delivery == null) {
                printSafe("LIVE timing: ${request.systemId} | ${request.rom.fileName} | not delivered | - | - | -")
                continue
            }
            val outcome = (delivery.result as? ScrapeResult.Matched)?.let { "game ${it.match.screenScraperGameId} (${it.match.matchedBy})" } ?: "no match"
            val box = delivery.media.any { it.kind == MediaKind.BoxFront }
            val disc = source.deliveries.any { d -> d.request === request && d.media.any { it.kind == MediaKind.Support } }
            val seconds = (delivery.atMillis - previous) / 1000.0
            previous = delivery.atMillis
            printSafe("LIVE timing: ${request.systemId} | ${request.rom.fileName} | $outcome | ${yes(box)} | ${yes(disc)} | ${"%.1f".format(Locale.ROOT, seconds)}")
        }
        val main = source.deliveries.filter { it.pass == ScrapePass.Main }
        val delivered = main.size
        val matched = main.count { it.result is ScrapeResult.Matched }
        val boxes = main.count { d -> d.media.any { it.kind == MediaKind.BoxFront } }
        val discs = requests.count { r -> source.deliveries.any { d -> d.request === r && d.media.any { it.kind == MediaKind.Support } } }
        val bytes = tmp.root.resolve("media").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        printSafe(
            "LIVE timing: ${requests.size} games, $delivered delivered, $matched matched, $boxes box fronts, $discs discs; " +
                "door calls ${scraper.calls()} (lookups ${scraper.lookups.get()}, pictures ${scraper.downloads.get()}); " +
                "saved ${bytes / 1024} KB",
        )
        printSafe(
            "LIVE timing: total ${"%.1f".format(Locale.ROOT, totalSeconds)} s, " +
                "${"%.1f".format(Locale.ROOT, totalSeconds / requests.size)} s per game, " +
                "${"%.1f".format(Locale.ROOT, totalSeconds / requests.size * 100 / 60)} minutes per 100 games",
        )
        assertEquals(ListenableWorker.Result.success(), result)
    }

    private suspend fun runWorker(scraper: Scraper, source: ScrapeSource): ListenableWorker.Result {
        app.work =
            ScraperWork(
                scraper = scraper,
                source = source,
                mediaDir = tmp.root.resolve("media"),
                mediaSizes = ScrapeMediaSizes.all,
                lowRamDevice = false,
                log = ::printSafe,
                runBudgetMillis = RUN_BUDGET_MILLIS,
                timingLog = TIMING_LOG,
            )
        return TestListenableWorkerBuilder.from(app, ScrapeWorker::class.java).build().doWork()
    }

    private fun countingScraper(): CountingScraper =
        CountingScraper(Scraper.create(ScraperConfig(cacheDir = tmp.newFolder("cache"), user = null, log = ::printSafe, timingLog = TIMING_LOG)))

    private fun timingGames(): List<ScrapeRequest> {
        val text = checkNotNull(javaClass.getResourceAsStream(TIMING_GAMES)) { "missing $TIMING_GAMES" }.bufferedReader().readText()
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (system, crc, size, name) = line.split('\t')
            request(system, crc, size.toLong(), name)
        }
    }

    private fun request(system: String, crc: String, size: Long, fileName: String) =
        ScrapeRequest(
            rom = HintedRom("live:$system:$crc", fileName, size),
            systemId = system,
            title = fileName.substringBeforeLast('.'),
            regions = listOf("us", "eu", "jp", "wor", "ss"),
            languages = listOf("en"),
            hints = RomHints(crc32 = crc, sizeBytes = size),
        )

    private fun yes(value: Boolean) = if (value) "yes" else "no"

    /** A ROM known by its hints only: the run never opens it. */
    private class HintedRom(override val key: String, override val fileName: String, override val sizeBytes: Long) : RomSource {
        override val lastModifiedMillis: Long = 0

        override fun open(): InputStream = error("a request with hints is never read")
    }

    /**
     * The requests in order; one the main pass delivered leaves [pending] and, matched without its
     * disc or cartridge, goes to the disc pass once. Records when each delivery came.
     */
    private class TimedSource(private val requests: List<ScrapeRequest>) : ScrapeSource {
        class Delivery(val request: ScrapeRequest, val result: ScrapeResult, val media: List<SavedMedia>, val pass: ScrapePass, val atMillis: Long)

        val deliveries = CopyOnWriteArrayList<Delivery>()

        override suspend fun pending(limit: Int): List<ScrapeRequest> =
            requests.filter { r -> deliveries.none { it.request === r && it.pass == ScrapePass.Main } }.take(limit)

        override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> =
            requests.filter { r ->
                val main = deliveries.firstOrNull { it.request === r && it.pass == ScrapePass.Main }
                main != null && deliveries.none { it.request === r && it.pass == ScrapePass.Support } &&
                    (main.result as? ScrapeResult.Matched)?.media?.any { it.kind == MediaKind.Support } == true &&
                    main.media.none { it.kind == MediaKind.Support }
            }.take(limit)

        override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
            deliveries += Delivery(request, result, media, pass, System.currentTimeMillis())
        }
    }

    /**
     * Counts what the run asks at the scraper's door: one lookup per game (a name search adds one
     * inside), one more per matched game with a disc (the disc pass, answered from the cache, no
     * HTTP), one call per picture downloaded.
     */
    private class CountingScraper(private val real: Scraper) : Scraper {
        val lookups = AtomicInteger()
        val downloads = AtomicInteger()

        fun calls() = lookups.get() + downloads.get()

        override val available: Boolean get() = real.available

        // The run works on as many games at once as the real scraper's slots.
        override val requestSlots get() = real.requestSlots

        override suspend fun scrape(request: ScrapeRequest): ScrapeResult {
            lookups.incrementAndGet()
            return real.scrape(request)
        }

        override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload {
            downloads.incrementAndGet()
            return real.download(media, target, maxWidth, maxHeight)
        }

        override suspend fun checkAccount(): AccountCheck = real.checkAccount()
    }

    private companion object {
        const val TIMING_GAMES = "/screenscraper-live/timing-games.tsv"
        const val RUN_BUDGET_MILLIS = 30L * 60 * 1000

        /**
         * The scraper's timing lines (tag PlumScrapeTiming), printed as "LIVE timing: ..." with the
         * rest: one `limits` line (account limits, gate slots and spacing), then per game its `req`
         * lines (endpoint, code, bytes, total, headers and body ms, gate wait) and its `game` line
         * (hash, lookup, box and disc download and save, delivery, gate wait). They never carry a
         * URL or a credential; [printSafe] is a second net.
         */
        val TIMING_LOG = ScrapeTimingLog { printSafe("LIVE timing: $it") }

        private val secrets: List<String> by lazy {
            DeveloperCredentials.fromBuildConfig().let { listOf(it.devId, it.devPassword) }.filter { it.isNotBlank() }.sortedByDescending { it.length }
        }

        /** Prints [line] with any developer credential blanked: a safety net over the engine's own redaction. */
        fun printSafe(line: String) {
            println(secrets.fold(line) { text, secret -> text.replace(secret, "***") })
        }
    }
}
