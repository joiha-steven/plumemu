package com.plumemu.scraper.work

import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.plumemu.scraper.ScraperFixture.Companion.json
import com.plumemu.scraper.ScraperFixture.Companion.status
import com.plumemu.scraper.gate.ParisTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** The follow-up runs: after a pause, and after the time budget, through WorkManager itself. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHostApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ScrapeChainTest : WorkerTestBase() {

    /** Enqueues a run as the app does, lets WorkManager run it, waits for its end. */
    private fun runThroughWorkManager(): Pair<WorkInfo, List<WorkInfo>> = runBlocking {
        ScrapeScheduler.enqueue(app())
        val id: UUID = uniqueWork().single().id
        WorkManagerTestInitHelper.getTestDriver(app())!!.setAllConstraintsMet(id)
        val done = withTimeout(20_000) {
            WorkManager.getInstance(app()).getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }!!
        }
        done to uniqueWork().filter { it.id != id }
    }

    @Test fun `a run that hits the quota succeeds and its follow-up waits until Paris midnight`() {
        gameAnswers += status(430, "quota")
        host(System::currentTimeMillis)
        val before = System.currentTimeMillis()

        val (run, followUps) = runThroughWorkManager()

        assertEquals(WorkInfo.State.SUCCEEDED, run.state)
        val next = followUps.single()
        assertEquals(WorkInfo.State.ENQUEUED, next.state)
        val expected = ParisTime.nextMidnight(before) - before
        assertTrue("${next.initialDelayMillis} vs $expected", next.initialDelayMillis in (expected - 20_000)..expected)
    }

    @Test fun `a run that used its time budget succeeds and its follow-up starts at once`() {
        repeat(5) { gameAnswers += json("jeuInfos-match.json") }
        // The run's own clock moves 5 minutes per look: one game, then the 8-minute budget is used.
        val runTime = AtomicLong(0)
        host(System::currentTimeMillis, runClock = { runTime.getAndAdd(5 * MINUTE) })

        val (run, followUps) = runThroughWorkManager()

        assertEquals(WorkInfo.State.SUCCEEDED, run.state)
        assertEquals(listOf(roms[0].rom.key), source.deliveredKeys)
        val next = followUps.single()
        assertEquals(WorkInfo.State.ENQUEUED, next.state)
        assertEquals(0L, next.initialDelayMillis)
    }

    @Test fun `runs cut by the budget carry on and deliver every game once`() = runTest {
        repeat(5) { gameAnswers += json("jeuInfos-match.json") }
        // Requests start 1.2 s apart in virtual time; a 2 s budget fits three per run.
        host(virtualClock(start), runBudgetMillis = 2_000)

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(roms.take(3).map { it.rom.key }, source.deliveredKeys)
        assertEquals(0L, uniqueWork().single().initialDelayMillis)

        host(virtualClock(start), runBudgetMillis = 2_000)
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(roms.map { it.rom.key }, source.deliveredKeys)
    }

    private companion object {
        const val MINUTE: Long = 60_000
    }
}
