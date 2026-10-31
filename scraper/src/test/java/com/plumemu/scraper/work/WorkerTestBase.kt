package com.plumemu.scraper.work

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperFixture
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.SharedPreferencesPauseStore
import com.plumemu.scraper.gate.ParisTime
import com.plumemu.scraper.media.MediaSize
import com.plumemu.scraper.utc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingDeque

/** Shared setup of the worker tests: a fake server for games and pictures, five ROMs, a host. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class WorkerTestBase {
    @get:Rule val tmp = TemporaryFolder()
    protected lateinit var fx: ScraperFixture
    protected lateinit var roms: List<com.plumemu.scraper.ScrapeRequest>
    protected lateinit var source: FakeScrapeSource

    /** `romnom` of every game lookup that reached the server, in order. */
    protected val asked = CopyOnWriteArrayList<String>()
    protected val gameAnswers = LinkedBlockingDeque<MockResponse>()
    protected val mediaRequests = CopyOnWriteArrayList<String>()
    protected val mediaDir: File get() = File(tmp.root, "media")
    protected var mediaAnswer: () -> MockResponse = { status(404, "media not found") }

    /** Every request that reached the fake Steam store search or image CDN, in order. */
    protected val steamRequests = CopyOnWriteArrayList<RecordedRequest>()
    protected var steamAnswer: (RecordedRequest) -> MockResponse = { status(404, "steam not found") }

    /** Every request that reached the fake libretro thumbnail server, in order. */
    protected val libretroRequests = CopyOnWriteArrayList<RecordedRequest>()
    protected var libretroAnswer: (RecordedRequest) -> MockResponse =
        // A real server sends no body to a HEAD request.
        { if (it.method == "HEAD") MockResponse.Builder().code(404).build() else status(404, "libretro not found") }

    // 10:00 UTC is noon in Paris; the quota resets at 22:00 UTC.
    protected val start = utc(2026, 10, 21, 10, 0)
    protected val resumeAt = utc(2026, 10, 21, 22, 0)

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        fx = ScraperFixture(tmp.root)
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath.startsWith("/libretro/")) {
                    libretroRequests += request
                    return libretroAnswer(request)
                }
                if (request.url.encodedPath.startsWith("/steam-")) {
                    steamRequests += request
                    return steamAnswer(request)
                }
                if (request.url.encodedPath.endsWith("mediaJeu.php")) {
                    mediaRequests += request.url.toString()
                    return mediaAnswer()
                }
                asked += request.url.queryParameter("romnom").orEmpty()
                return gameAnswers.pollFirst() ?: status(500, "no answer queued")
            }
        }
        roms = (1..5).map { fx.request(fx.rom("Game$it.md", content = "rom number $it")) }
        source = FakeScrapeSource(roms)
    }

    @After fun tearDown() = fx.close()

    protected fun app(): TestHostApp = ApplicationProvider.getApplicationContext()

    /** A new scraper, as a new process makes one: only the pause file and caches carry over. */
    protected fun host(
        clock: () -> Long,
        sizes: List<MediaSize> = emptyList(),
        developer: DeveloperCredentials = ScraperFixture.DEVELOPER,
        runClock: () -> Long = clock,
        runBudgetMillis: Long = ScraperWork.DEFAULT_RUN_BUDGET_MILLIS,
        timingLog: com.plumemu.scraper.timing.ScrapeTimingLog = com.plumemu.scraper.timing.ScrapeTimingLog { fx.timingLines += it },
        holdOff: suspend () -> Boolean = { false },
    ): ScraperWork {
        val pauses = SharedPreferencesPauseStore(app()) { fx.logLines += it }
        val scraper = fx.scraper(fx.config(clock = clock, developer = developer, pauseStore = pauses, timingLog = timingLog))
        return ScraperWork(
            scraper, source, mediaDir, sizes, lowRamDevice = false, runClock,
            log = { fx.logLines += it }, runBudgetMillis = runBudgetMillis,
            timingLog = timingLog, holdOff = holdOff,
        )
            .also { app().work = it }
    }

    protected suspend fun runWorker(context: android.content.Context = app()): ListenableWorker.Result =
        TestListenableWorkerBuilder.from(context, ScrapeWorker::class.java).build().doWork()

    protected fun uniqueWork(): List<WorkInfo> =
        WorkManager.getInstance(app()).getWorkInfosForUniqueWork(ScrapeScheduler.UNIQUE_WORK_NAME).get()

    protected fun TestScope.virtualClock(from: Long): () -> Long = { from + testScheduler.currentTime }
}
