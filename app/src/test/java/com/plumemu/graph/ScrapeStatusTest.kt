package com.plumemu.graph

import androidx.work.Data
import androidx.work.WorkInfo
import com.plumemu.account.AccountStatus
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The mapping from what is known to the one state Settings shows. */
class ScrapeStatusTest {
    private val now = 1_000_000L
    private val counts = ScrapeCounts(withBoxFront = 12, listed = 40, pending = 25)
    private val signedIn = AccountStatus.SignedIn("plumtester", isRefused = false, dailyLimitUntilMillis = null)
    private val wifi = DeviceConditions(unmeteredNetwork = true, batteryLow = false)
    private val running = RunSnapshot(RunSnapshot.State.Running, endedInRetry = false, nextAtMillis = null)
    private val enqueued = RunSnapshot(RunSnapshot.State.Enqueued, endedInRetry = false, nextAtMillis = null)

    private fun status(
        account: AccountStatus = signedIn,
        run: RunSnapshot? = null,
        pause: ScrapeResult.Paused? = null,
        conditions: DeviceConditions = wifi,
        pace: Double? = null,
    ) = scrapeStatusOf(account, run, pause, counts, conditions, pace, now)

    @Test
    fun theBuildAndTheAccountComeFirst() {
        assertEquals(ScrapeStatus.NotInThisBuild, status(account = AccountStatus.NotInThisBuild, run = running))
        assertEquals(ScrapeStatus.SignedOut, status(account = AccountStatus.SignedOut, run = running))
        val refused = AccountStatus.SignedIn("plumtester", isRefused = true, dailyLimitUntilMillis = null)
        assertEquals(ScrapeStatus.Refused, status(account = refused, run = running, pause = ScrapeResult.Paused(now + 5, PauseReason.DailyQuota)))
    }

    @Test
    fun withNoRunItIsIdleWithTheCounts() {
        assertEquals(ScrapeStatus.Idle(counts), status())
        assertEquals(15, counts.asked)
    }

    @Test
    fun aPauseStillAheadWinsOverTheWaitingFollowUp() {
        val pause = ScrapeResult.Paused(now + 60_000, PauseReason.DailyQuota)
        assertEquals(ScrapeStatus.Paused(counts, now + 60_000, PauseReason.DailyQuota), status(run = enqueued, pause = pause))
        val closed = ScrapeResult.Paused(now + 1, PauseReason.ServerClosed)
        assertEquals(ScrapeStatus.Paused(counts, now + 1, PauseReason.ServerClosed), status(pause = closed))
    }

    @Test
    fun aPauseThatIsOverIsIgnored() {
        assertEquals(ScrapeStatus.Idle(counts), status(pause = ScrapeResult.Paused(now, PauseReason.DailyQuota)))
        assertEquals(ScrapeStatus.Running(counts, 3.5), status(run = running, pause = ScrapeResult.Paused(now - 1, PauseReason.DailyQuota), pace = 3.5))
    }

    @Test
    fun runningCarriesThePaceOrNull() {
        assertEquals(ScrapeStatus.Running(counts, null), status(run = running))
        assertEquals(ScrapeStatus.Running(counts, 6.0), status(run = running, pace = 6.0, conditions = DeviceConditions(false, true)))
    }

    @Test
    fun aWaitingRunSaysWhatItWaitsFor() {
        assertEquals(ScrapeStatus.WaitingForWifi(counts), status(run = enqueued, conditions = DeviceConditions(unmeteredNetwork = false, batteryLow = true)))
        assertEquals(ScrapeStatus.WaitingForBattery(counts), status(run = enqueued, conditions = DeviceConditions(unmeteredNetwork = true, batteryLow = true)))
        assertEquals(ScrapeStatus.Starting(counts), status(run = enqueued))
        assertEquals(ScrapeStatus.Starting(counts), status(run = enqueued.copy(state = RunSnapshot.State.Blocked)))
    }

    @Test
    fun aRunWaitingAfterAFailedAttemptIsRetrying() {
        val retry = RunSnapshot(RunSnapshot.State.Enqueued, endedInRetry = true, nextAtMillis = now + 120_000)
        assertEquals(ScrapeStatus.Retrying(counts, now + 120_000), status(run = retry))
        // Offline is first of all a wait for Wi-Fi.
        assertEquals(ScrapeStatus.WaitingForWifi(counts), status(run = retry, conditions = DeviceConditions(false, false)))
    }

    @Test
    fun countsThatCouldNotBeReadStayUnknown() {
        assertEquals(ScrapeStatus.Idle(null), scrapeStatusOf(signedIn, null, null, null, wifi, null, now))
        assertEquals(0, ScrapeCounts(withBoxFront = 0, listed = 3, pending = 5).asked)
    }

    @Test
    fun theRunningWorkInfoIsChosenOverItsFollowUp() {
        val followUp = info(WorkInfo.State.BLOCKED)
        val current = info(WorkInfo.State.RUNNING, attempts = 1)
        assertEquals(RunSnapshot(RunSnapshot.State.Running, false, null), runSnapshotOf(listOf(followUp, current)))
        assertEquals(RunSnapshot(RunSnapshot.State.Enqueued, false, now), runSnapshotOf(listOf(info(WorkInfo.State.SUCCEEDED), info(WorkInfo.State.ENQUEUED, next = now))))
        assertEquals(RunSnapshot(RunSnapshot.State.Blocked, false, null), runSnapshotOf(listOf(followUp)))
    }

    @Test
    fun onlyTheRetryMarkOfThisWorkMeansRetryingNotItsAttemptCount() {
        // WorkManager counts every start (WorkerWrapper.trySetRunning), also one stopped for a lost
        // constraint: an attempt count alone is no retry.
        val stoppedByWifi = info(WorkInfo.State.ENQUEUED, attempts = 1)
        assertEquals(false, runSnapshotOf(listOf(stoppedByWifi))?.endedInRetry)
        assertEquals(false, runSnapshotOf(listOf(stoppedByWifi), retriedWorkId = UUID.randomUUID())?.endedInRetry)
        assertEquals(ScrapeStatus.Starting(counts), status(run = runSnapshotOf(listOf(stoppedByWifi))))

        val retried = runSnapshotOf(listOf(stoppedByWifi), retriedWorkId = stoppedByWifi.id)
        assertEquals(true, retried?.endedInRetry)
        assertEquals(ScrapeStatus.Retrying(counts, null), status(run = retried))
    }

    @Test
    fun finishedRunsAreNoRun() {
        assertNull(runSnapshotOf(emptyList()))
        assertNull(runSnapshotOf(listOf(info(WorkInfo.State.SUCCEEDED), info(WorkInfo.State.FAILED), info(WorkInfo.State.CANCELLED))))
    }

    @Test
    fun thePaceNeedsFiveDeliveriesInARow() {
        var clock = 0L
        val pace = ScrapePace({ clock })
        repeat(4) {
            pace.recordDelivery()
            clock += 10_000
        }
        assertNull("four deliveries are not enough", pace.gamesPerMinute())
        pace.recordDelivery()
        // Five deliveries 10 s apart: 4 gaps in 40 s, 6 games a minute.
        assertEquals(6.0, pace.gamesPerMinute()!!, 1e-9)
    }

    @Test
    fun aLongGapStartsANewSeriesAndAnOldSeriesSaysNothing() {
        var clock = 0L
        val pace = ScrapePace({ clock })
        repeat(6) {
            pace.recordDelivery()
            clock += 10_000
        }
        clock += ScrapePace.MAX_GAP_MILLIS
        assertNull("the last delivery is too old to say a pace", pace.gamesPerMinute())
        pace.recordDelivery()
        assertNull("a new series after a pause starts from one", pace.gamesPerMinute())
    }

    @Test
    fun thePaceKeepsOnlyTheLatestWindow() {
        var clock = 0L
        val pace = ScrapePace({ clock })
        // 20 slow deliveries (one a minute), then 20 fast ones (one every 6 s): the old ones drop out.
        repeat(20) {
            pace.recordDelivery()
            clock += 60_000
        }
        repeat(20) {
            clock += 6_000
            pace.recordDelivery()
        }
        assertEquals(10.0, pace.gamesPerMinute()!!, 1e-9)
    }

    private fun info(
        state: WorkInfo.State,
        attempts: Int = 0,
        next: Long = Long.MAX_VALUE,
    ) = WorkInfo(
        UUID.randomUUID(), state, emptySet(), Data.EMPTY, Data.EMPTY, attempts, 0,
        nextScheduleTimeMillis = next,
    )
}
