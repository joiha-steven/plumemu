package com.plumemu.scraper.live

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.RomHints
import com.plumemu.scraper.RomSource
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.ScreenScraperScraper
import com.plumemu.scraper.api.ScreenScraperApi
import com.plumemu.scraper.media.MediaSize
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource
import com.plumemu.scraper.work.ScrapeWorker
import com.plumemu.scraper.work.ScraperWork
import com.plumemu.scraper.work.TestHostApp
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
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

/**
 * One known ROM against the real ScreenScraper, opt-in only: skipped unless Gradle
 * is given `-Pscreenscraper.live=true` and the build has developer credentials; CI and
 * `scripts/verify.sh` never pass the property. Developer credentials only (no account). Runs the
 * real worker, engine and picture saving into a temp folder, and counts every HTTP request on the
 * engine's own client (retries included). Here, not in `app`, because only this module can hand
 * the engine a client; the sizes are the app's (`ScrapeMediaSizes`, kept equal by hand).
 *
 * `./gradlew :scraper:testDebugUnitTest --tests '*ScreenScraperLiveSmokeTest*' -Pscreenscraper.live=true`;
 * the line it prints is in `scraper/build/test-results/testDebugUnitTest/TEST-*ScreenScraperLiveSmokeTest.xml`.
 * Nothing printed carries a credential: request paths only, every line through [printSafe].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenScraperLiveSmokeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app: TestHostApp get() = ApplicationProvider.getApplicationContext()

    /** The path of every HTTP request the engine sent ("jeuInfos.php", "mediaJeu.php"), no query. */
    private val requests = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test
    fun superMarioBrosIsMatchedByHashWithItsBoxFrontAndAnEnglishSynopsisInThreeRequests() = runBlocking<Unit> {
        assumeTrue("live tests run only with -Pscreenscraper.live=true", System.getProperty("screenscraper.live") == "true")
        assumeTrue("live tests need a build with developer credentials", DeveloperCredentials.buildHasCredentials)
        val http = ScreenScraperApi.defaultHttpClient(::printSafe).newBuilder()
            .addNetworkInterceptor { chain ->
                requests += chain.request().url.encodedPath.substringAfterLast('/')
                chain.proceed(chain.request())
            }
            .build()
        val scraper = ScreenScraperScraper(ScraperConfig(cacheDir = tmp.newFolder("cache"), user = null, log = ::printSafe, timingLog = com.plumemu.scraper.timing.ScrapeTimingLog.Off), http)
        val source = OneRequest(
            ScrapeRequest(
                rom = HintedRom("live:nes:3337EC46", "Super Mario Bros. (World).nes", 40_976),
                systemId = "nes",
                title = "Super Mario Bros. (World)",
                regions = listOf("us", "eu", "jp", "wor", "ss"),
                languages = listOf("en"),
                hints = RomHints(crc32 = "3337EC46", sizeBytes = 40_976),
            ),
        )
        app.work = ScraperWork(scraper, source, tmp.root.resolve("media"), APP_SIZES, lowRamDevice = false, log = ::printSafe)

        val started = System.currentTimeMillis()
        val result = TestListenableWorkerBuilder.from(app, ScrapeWorker::class.java).build().doWork()
        val seconds = (System.currentTimeMillis() - started) / 1000.0

        // The disc pass's delivery holds the box front and the cartridge.
        assertEquals(listOf(ScrapePass.Main, ScrapePass.Support), source.delivered.map { it.pass })
        val delivery = source.delivered.last()
        val matched = delivery.result as ScrapeResult.Matched
        val boxFronts = delivery.media.filter { it.kind == MediaKind.BoxFront }
        val english = matched.details.synopses.firstOrNull { it.language == "en" }?.text.orEmpty()
        printSafe(
            "LIVE smoke: $result, game ${matched.match.screenScraperGameId} by ${matched.match.matchedBy}, " +
                "${requests.size} HTTP requests ${requests.joinToString(" ")}, " +
                "pictures ${delivery.media.joinToString { "${it.kind}-${it.sizeName} ${it.width}x${it.height}" }}, " +
                "English synopsis ${english.length} characters, ${"%.1f".format(Locale.ROOT, seconds)} s",
        )
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("1245", matched.match.screenScraperGameId)
        assertEquals(MatchedBy.Hash, matched.match.matchedBy)
        assertEquals(APP_SIZES.map { it.name }, boxFronts.map { it.sizeName })
        assertTrue(boxFronts.all { File(it.path).length() > 0 })
        assertTrue("an English synopsis", english.isNotBlank())
        assertTrue("at most a lookup, a box front and a disc picture: $requests", requests.size <= 3)
    }

    /** A ROM known by its hints only: the run never opens it. */
    private class HintedRom(override val key: String, override val fileName: String, override val sizeBytes: Long) : RomSource {
        override val lastModifiedMillis: Long = 0

        override fun open(): InputStream = error("a request with hints is never read")
    }

    /** One game: its main pass (lookup and box front), then its disc pass (the cartridge). */
    private class OneRequest(private val request: ScrapeRequest) : ScrapeSource {
        class Delivery(val result: ScrapeResult, val media: List<SavedMedia>, val pass: ScrapePass)

        val delivered = CopyOnWriteArrayList<Delivery>()

        override suspend fun pending(limit: Int): List<ScrapeRequest> =
            if (delivered.none { it.pass == ScrapePass.Main }) listOf(request) else emptyList()

        override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> =
            if (delivered.any { it.pass == ScrapePass.Main } && delivered.none { it.pass == ScrapePass.Support }) listOf(request) else emptyList()

        override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
            delivered += Delivery(result, media, pass)
        }
    }

    private companion object {
        /** The app's `ScrapeMediaSizes.all` (app/.../graph/ScrapeMediaSizes.kt). */
        // Must equal the app's ScrapeMediaSizes.all (app/.../graph/ScrapeMediaSizes.kt); nothing checks it.
        val APP_SIZES = listOf(MediaSize("grid", 560, 400), MediaSize("large", 1240, 1080))

        private val secrets: List<String> by lazy {
            DeveloperCredentials.fromBuildConfig().let { listOf(it.devId, it.devPassword) }.filter { it.isNotBlank() }.sortedByDescending { it.length }
        }

        /** Prints [line] with any developer credential blanked: a safety net over the engine's own redaction. */
        fun printSafe(line: String) {
            println(secrets.fold(line) { text, secret -> text.replace(secret, "***") })
        }
    }
}
