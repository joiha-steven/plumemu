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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerTest : WorkerTestBase() {
    @Test fun `a quota stop resumes after Paris midnight with only the games not yet done`() = runTest {
        gameAnswers += json("jeuInfos-match.json")
        gameAnswers += json("jeuInfos-match.json")
        gameAnswers += status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !")
        val firstClock = virtualClock(start)
        host(firstClock)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(roms.take(2).map { it.rom.key }, source.deliveredKeys)
        assertEquals(listOf("Game1.md", "Game2.md", "Game3.md"), asked.toList())
        assertEquals(ScrapeResult.Paused(resumeAt, PauseReason.DailyQuota), SharedPreferencesPauseStore(app()).load())
        val rerun = uniqueWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, rerun.state)
        assertEquals(resumeAt - firstClock(), rerun.initialDelayMillis)
        assertTrue(rerun.initialDelayMillis in (11 * HOUR)..(12 * HOUR))

        // A new process before midnight: the saved pause holds, nothing reaches the server.
        val requestsSoFar = fx.server.requestCount
        host({ resumeAt - 60_000 })
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(requestsSoFar, fx.server.requestCount)
        assertEquals("a second pause adds no second follow-up", 1, uniqueWork().size)
        assertEquals(2, source.deliveries.size)

        // A new process after midnight: games 3 to 5 only, and all of them delivered.
        asked.clear()
        repeat(3) { gameAnswers += json("jeuInfos-match.json") }
        host(virtualClock(resumeAt + 1_000))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(listOf("Game3.md", "Game4.md", "Game5.md"), asked.toList())
        assertEquals(roms.map { it.rom.key }, source.deliveredKeys)
        assertTrue(source.deliveries.all { it.result is ScrapeResult.Matched })
    }

    @Test fun `a run stopped mid-request leaves that game undelivered`() = runTest {
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val games = fx.server.dispatcher
        fx.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.queryParameter("romnom") == "Game2.md") {
                    reached.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                return games.dispatch(request)
            }
        }
        repeat(5) { gameAnswers += json("jeuInfos-match.json") }
        host(virtualClock(start))

        val run = launch { runWorker() }
        try {
            assertTrue(withContext(Dispatchers.IO) { reached.await(5, TimeUnit.SECONDS) })
            run.cancelAndJoin()
        } finally {
            release.countDown()
        }

        assertEquals(listOf(roms[0].rom.key), source.deliveredKeys)
    }

    @Test fun `no connection asks for a retry and delivers nothing`() = runTest {
        host(virtualClock(start))
        fx.server.close()

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        assertTrue(source.deliveries.isEmpty())
    }

    @Test fun `refused developer credentials fail the work after one request`() = runTest {
        gameAnswers += status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !")
        host(virtualClock(start))

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertEquals(1, fx.server.requestCount)
        assertTrue(source.deliveries.isEmpty())
        assertTrue(uniqueWork().isEmpty())
    }

    @Test fun `a build without developer credentials succeeds without a request`() = runTest {
        val unavailable = Scraper.create(fx.config(developer = DeveloperCredentials("", "")))
        app().work = ScraperWork(unavailable, source, mediaDir, emptyList(), log = { fx.logLines += it })

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(0, fx.server.requestCount)
        assertTrue(source.deliveries.isEmpty())
    }

    @Test fun `a host with nothing set up succeeds without a request`() = runTest {
        app().work = null

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(0, fx.server.requestCount)
    }

    @Test @Config(application = Application::class)
    fun `an app that is no scraper host succeeds without a request`() = runTest {
        assertFalse(ApplicationProvider.getApplicationContext<Application>() is ScraperHost)

        assertEquals(ListenableWorker.Result.success(), runWorker(ApplicationProvider.getApplicationContext()))

        assertEquals(0, fx.server.requestCount)
    }

    @Test fun `a matched game is delivered with its box front saved and no credential anywhere`() = runTest {
        val png = pngBytes(400, 560)
        mediaAnswer = { imageResponse(png) }
        source = FakeScrapeSource(roms.take(1))
        val base = fx.server.url("/").toString()
        gameAnswers += json("jeuInfos-match.json") {
            it.replace("https://neoclone.screenscraper.fr/", base).replace("00000000000000000000000000000002", md5Of(png))
        }
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 200, 200)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val first = source.deliveries.first()
        val saved = first.media.single()
        assertEquals(143 to 200, saved.width to saved.height)
        assertTrue(File(saved.path).isFile)
        assertTrue(mediaRequests.single().contains("devpassword=${ScraperFixture.DEV_PASSWORD}"))
        assertTrue(mediaRequests.single().contains("box-2D(us)"))
        val delivered = source.deliveries.joinToString("\n") { "${it.result} ${it.media}" }
        val stored = mediaDir.walkTopDown().joinToString("\n") { it.path }
        for (secret in ScraperFixture.SECRETS) {
            assertFalse(secret, delivered.contains(secret))
            assertFalse(secret, stored.contains(secret))
            assertFalse(secret, fx.logLines.any { line -> line.contains(secret) })
        }
        assertEquals(1, source.deliveries.size)
    }

    @Test fun `the daily quota on the picture leaves its game undelivered`() = runTest {
        mediaAnswer = { status(430, "Votre quota de scrape est d\u00e9pass\u00e9 pour aujourd'hui !") }
        source = FakeScrapeSource(roms.take(1))
        val base = fx.server.url("/").toString()
        gameAnswers += json("jeuInfos-match.json") { it.replace("https://neoclone.screenscraper.fr/", base) }
        host(virtualClock(start), sizes = listOf(MediaSize("grid", 200, 200)))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(source.deliveries.isEmpty())
        assertEquals(ParisTime.nextMidnight(start), SharedPreferencesPauseStore(app()).load()?.resumeAtMillis)
        assertEquals(1, mediaRequests.size)
        assertEquals(1, uniqueWork().size)
    }

    private companion object {
        const val HOUR: Long = 60L * 60 * 1000
    }
}
