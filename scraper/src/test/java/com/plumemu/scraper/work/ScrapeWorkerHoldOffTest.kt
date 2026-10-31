package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.plumemu.scraper.ScraperFixture.Companion.json
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A run held for a game ends while the person may already be back: the return's request (KEEP)
 * was dropped meanwhile, so the worker queues the follow-up itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeWorkerHoldOffTest : WorkerTestBase() {
    @Test fun `a person back before the held run ends gets a follow-up at once`() = runTest {
        repeat(roms.size) { gameAnswers += json("jeuInfos-match.json") }
        val looks = AtomicInteger()
        // Playing at the run's first look, back by the worker's look at the end.
        host(virtualClock(start), holdOff = { looks.getAndIncrement() == 0 })

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val next = uniqueWork().single()
        assertEquals("the resume is not lost", WorkInfo.State.ENQUEUED, next.state)
        assertEquals(0L, next.initialDelayMillis)
        assertTrue(fx.logLines.any { it.contains("held off") })
    }

    @Test fun `a run held while the game is still played queues nothing`() = runTest {
        host(virtualClock(start), holdOff = { true })

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue("the app's return asks for the run", uniqueWork().isEmpty())
        assertTrue(source.deliveredKeys.isEmpty())
    }
}
