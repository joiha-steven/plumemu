package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.UnavailableReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The host hears why a run stopped on an unavailable scraper (the app marks a refused account) and
 * how every run ended, with the work's id (the app's "retrying" state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
internal class ScrapeWorkerHostHooksTest : WorkerTestBase() {
    private val heard = CopyOnWriteArrayList<UnavailableReason>()
    private val ends = CopyOnWriteArrayList<Pair<ScrapeRunEnd, UUID>>()

    /** [host]'s work with an [ScraperWork.onUnavailable] that records, or runs [hook]. */
    private fun hostHearing(clock: () -> Long, hook: suspend (UnavailableReason) -> Unit = { heard += it }): ScraperWork {
        val base = host(clock)
        return ScraperWork(
            base.scraper, base.source, base.mediaDir, base.mediaSizes, base.lowRamDevice, base.clock, base.log,
            base.runBudgetMillis, onUnavailable = hook, onEnded = { end, id -> ends += end to id },
        ).also { app().work = it }
    }

    /** Runs the worker and returns its result with the id it reported under. */
    private suspend fun runWorkerWithId(): Pair<ListenableWorker.Result, UUID> {
        val worker = TestListenableWorkerBuilder.from(app(), ScrapeWorker::class.java).build()
        return worker.doWork() to worker.id
    }

    @Test fun `a run with no connection tells the host it ends in a retry, under its work id`() = runTest {
        hostHearing(virtualClock(start))
        fx.server.close()

        val (result, id) = runWorkerWithId()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf(ScrapeRunEnd.Retry to id), ends.toList())
    }

    @Test fun `a run that delivers everything tells the host it is done`() = runTest {
        repeat(5) { gameAnswers += json("jeuInfos-match.json") }
        hostHearing(virtualClock(start))

        val (result, id) = runWorkerWithId()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(ScrapeRunEnd.Done to id), ends.toList())
    }

    @Test fun `a refused run tells the host it stopped`() = runTest {
        gameAnswers += status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !")
        hostHearing(virtualClock(start))

        runWorker()

        assertEquals(listOf(ScrapeRunEnd.Stopped), ends.map { it.first })
    }

    @Test fun `an end hook that fails is logged and the result stands`() = runTest {
        hostHearing(virtualClock(start))
        app().work = app().work!!.let {
            ScraperWork(it.scraper, it.source, it.mediaDir, it.mediaSizes, it.lowRamDevice, it.clock, it.log, it.runBudgetMillis, onEnded = { _, _ -> error("the host broke") })
        }
        fx.server.close()

        assertEquals(ListenableWorker.Result.retry(), runWorker())
        assertTrue(fx.logLines.any { it.contains("end hook failed (IllegalStateException)") })
    }

    @Test fun `an answer as anonymous with an account set tells the host the user was refused`() = runTest {
        gameAnswers += json("jeuInfos-match.json") { it.replace("\"numid\": \"1234\"", "\"numid\": \"0\"") }
        hostHearing(virtualClock(start))

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertEquals(listOf(UnavailableReason.BadUserCredentials), heard)
        assertEquals(1, fx.server.requestCount)
        assertTrue(source.deliveries.isEmpty())
    }

    @Test fun `refused developer credentials tell the host that reason`() = runTest {
        gameAnswers += status(403, "Erreur de login : V\u00e9rifier vos identifiants d\u00e9veloppeur !")
        hostHearing(virtualClock(start))

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertEquals(listOf(UnavailableReason.BadDeveloperCredentials), heard)
    }

    @Test fun `a hook that fails is logged and the run still stops`() = runTest {
        gameAnswers += json("jeuInfos-match.json") { it.replace("\"numid\": \"1234\"", "\"numid\": \"0\"") }
        hostHearing(virtualClock(start)) { error("the host broke") }

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        assertTrue(fx.logLines.any { it.contains("unavailable hook failed (IllegalStateException)") })
        assertTrue(source.deliveries.isEmpty())
    }

    @Test fun `a run that ends normally never calls the hook`() = runTest {
        repeat(5) { gameAnswers += json("jeuInfos-match.json") }
        hostHearing(virtualClock(start))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(5, source.deliveries.size)
        assertTrue(heard.isEmpty())
    }
}
