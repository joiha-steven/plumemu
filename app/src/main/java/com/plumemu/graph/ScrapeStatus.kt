package com.plumemu.graph

import androidx.work.WorkInfo
import com.plumemu.account.AccountStatus
import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.ScrapeResult
import java.util.UUID

/**
 * The box-art run as Settings › Library will show it (Settings reads [AppGraph.scrapeStatus];
 *). One value at a time, the first that applies in the
 * order of [scrapeStatusOf].
 */
internal sealed interface ScrapeStatus {
    /** This build has no developer credentials: no account, no run. */
    data object NotInThisBuild : ScrapeStatus

    /** No ScreenScraper account: nothing is asked. */
    data object SignedOut : ScrapeStatus

    /** ScreenScraper refused the stored password (or it no longer decrypts): sign in again. */
    data object Refused : ScrapeStatus

    /**
     * The person paused box art (Settings; `library.box_art_paused`): no run starts or
     * goes on until they resume. First after the account, before everything about the run.
     */
    data class PausedByPerson(val counts: ScrapeCounts?) : ScrapeStatus

    /** No run waiting or going. */
    data class Idle(val counts: ScrapeCounts?) : ScrapeStatus

    /**
     * A run is going. [gamesPerMinute] is measured from this process's box-front deliveries (the main
     * pass, however many games run at once: covers per minute); null until
     * enough games were delivered in a row ([ScrapePace]).
     */
    data class Running(val counts: ScrapeCounts?, val gamesPerMinute: Double?) : ScrapeStatus

    /** Asked for, every condition met: WorkManager starts it in a moment. */
    data class Starting(val counts: ScrapeCounts?) : ScrapeStatus

    /** Asked for; waits for an unmetered network (Wi-Fi). */
    data class WaitingForWifi(val counts: ScrapeCounts?) : ScrapeStatus

    /** Asked for; waits for a battery that is not low (or a charger). */
    data class WaitingForBattery(val counts: ScrapeCounts?) : ScrapeStatus

    /** Nothing is asked before [untilMillis]: the daily quota, or ScreenScraper closed its API. */
    data class Paused(val counts: ScrapeCounts?, val untilMillis: Long, val reason: PauseReason) : ScrapeStatus

    /**
     * The last run of this work ended in a retry (no connection, an unwell server, a full disk);
     * WorkManager tries again at [nextAtMillis] (null: not known).
     */
    data class Retrying(val counts: ScrapeCounts?, val nextAtMillis: Long?) : ScrapeStatus
}

/**
 * The library's numbers from core-data's `ScrapedGameDao` counts.
 *
 * @property withBoxFront listed games whose stored result has a box front.
 * @property listed games the library lists (not hidden, not "not a game", primary copy).
 * @property pending listed games the run would still ask ([com.plumemu.library.scrape.LibraryScrapeSource.pending]).
 */
internal data class ScrapeCounts(val withBoxFront: Int, val listed: Int, val pending: Int) {
    /** Listed games already asked, matched or not (games still being named count here too). */
    val asked: Int get() = (listed - pending).coerceAtLeast(0)
}

/**
 * The unique run's WorkManager state that matters for the status line. [endedInRetry]: this work's
 * last run ended in `Result.retry()` (the app's [RetryMark]); WorkManager's `runAttemptCount`
 * cannot tell, it grows on every start, also after a stop for a lost constraint.
 */
internal data class RunSnapshot(val state: State, val endedInRetry: Boolean, val nextAtMillis: Long?) {
    enum class State { Running, Enqueued, Blocked }
}

/** What the device offers now, for a run that waits on its constraints. */
internal data class DeviceConditions(val unmeteredNetwork: Boolean, val batteryLow: Boolean)

/**
 * The run's state from what is known now. Order: the build, the account, the person's pause, a
 * saved pause still ahead, the WorkManager run (running, then waiting: Wi-Fi, battery, a retry,
 * about to start), else idle.
 */
internal fun scrapeStatusOf(
    account: AccountStatus,
    run: RunSnapshot?,
    pause: ScrapeResult.Paused?,
    counts: ScrapeCounts?,
    conditions: DeviceConditions,
    gamesPerMinute: Double?,
    now: Long,
    pausedByPerson: Boolean = false,
): ScrapeStatus {
    accountOnlyStatus(account)?.let { return it }
    if (pausedByPerson) return ScrapeStatus.PausedByPerson(counts)
    if (pause != null && pause.resumeAtMillis > now) return ScrapeStatus.Paused(counts, pause.resumeAtMillis, pause.reason)
    return when (run?.state) {
        null -> ScrapeStatus.Idle(counts)
        RunSnapshot.State.Running -> ScrapeStatus.Running(counts, gamesPerMinute)
        // A follow-up chained after a run that is no longer listed as running: about to start.
        RunSnapshot.State.Enqueued, RunSnapshot.State.Blocked ->
            when {
                !conditions.unmeteredNetwork -> ScrapeStatus.WaitingForWifi(counts)
                conditions.batteryLow -> ScrapeStatus.WaitingForBattery(counts)
                run.endedInRetry -> ScrapeStatus.Retrying(counts, run.nextAtMillis)
                else -> ScrapeStatus.Starting(counts)
            }
    }
}

/** The state when the account alone decides it (no build, signed out, refused); null when a run could go. */
internal fun accountOnlyStatus(account: AccountStatus): ScrapeStatus? =
    when (account) {
        AccountStatus.NotInThisBuild -> ScrapeStatus.NotInThisBuild
        AccountStatus.SignedOut -> ScrapeStatus.SignedOut
        is AccountStatus.SignedIn -> if (account.isRefused) ScrapeStatus.Refused else null
    }

/**
 * The unique run's most telling state among its WorkInfos (a running one and its follow-up can
 * both be listed): running, then enqueued, then blocked; null when every one has finished.
 */
internal fun runSnapshotOf(
    infos: List<WorkInfo>,
    retriedWorkId: UUID? = null,
): RunSnapshot? {
    val live = infos.filter { !it.state.isFinished }
    val chosen =
        live.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: live.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
            ?: live.firstOrNull { it.state == WorkInfo.State.BLOCKED }
            ?: return null
    val state =
        when (chosen.state) {
            WorkInfo.State.RUNNING -> RunSnapshot.State.Running
            WorkInfo.State.ENQUEUED -> RunSnapshot.State.Enqueued
            else -> RunSnapshot.State.Blocked
        }
    val next = chosen.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE && it > 0 }
    return RunSnapshot(state, endedInRetry = chosen.id == retriedWorkId, nextAtMillis = next)
}

/**
 * Games per minute from this process's deliveries, for the "about 5 h left". Keeps the
 * last [window] delivery times; a gap longer than [maxGapMillis] (a pause, a wait for Wi-Fi, a new
 * run hours later) starts a new series, so a rate never spans a stop. Null until [minSamples]
 * deliveries in a series.
 */
internal class ScrapePace(
    private val clock: () -> Long = System::currentTimeMillis,
    private val minSamples: Int = MIN_SAMPLES,
    private val window: Int = WINDOW,
    private val maxGapMillis: Long = MAX_GAP_MILLIS,
) {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun recordDelivery() {
        val now = clock()
        val last = times.lastOrNull()
        if (last != null && now - last > maxGapMillis) times.clear()
        times.addLast(now)
        while (times.size > window) times.removeFirst()
    }

    /** Null with fewer than [minSamples] deliveries in the current series, or when the last one is [maxGapMillis] old. */
    @Synchronized
    fun gamesPerMinute(): Double? {
        if (times.size < minSamples) return null
        val first = times.first()
        val last = times.last()
        if (last <= first || clock() - last > maxGapMillis) return null
        return (times.size - 1) * 60_000.0 / (last - first)
    }

    companion object {
        const val MIN_SAMPLES = 5
        const val WINDOW = 20
        const val MAX_GAP_MILLIS = 5L * 60 * 1000
    }
}

/**
 * The id of the work whose last run ended in a retry, or none: written by the run's end
 * ([com.plumemu.scraper.work.ScraperWork.onEnded]), cleared when a run starts. Outlives the process
 * (Settings may open long after the run).
 */
internal interface RetryMark {
    fun get(): UUID?

    fun set(workId: UUID?)
}
