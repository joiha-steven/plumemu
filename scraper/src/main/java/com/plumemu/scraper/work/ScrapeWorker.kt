package com.plumemu.scraper.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.media.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * The background scrape: walks [ScrapeSource.pending] with the host's scraper (see [ScrapeRun]).
 *
 * - No [ScraperHost], nothing set up, or a build without developer credentials: success, no
 *   network, one log line.
 * - A pause (daily quota, server closed): the same unique work again after the resume time;
 *   success. The scraper saved the pause, so a new process asks nothing before then.
 * - The time budget used (Android stops jobs after about 10 minutes): the same work again at
 *   once, so the next run starts with a fresh attempt count; success.
 * - No connection, or the server unwell: retry, with the exponential backoff of the request.
 * - Credentials refused: failure, nothing retried, so a bad login never hammers the server.
 * - Held off ([ScraperWork.holdOff]: a game is being played): stopped between games, success;
 *   nothing queued while the game goes on (the app asks when the person is back), a follow-up at
 *   once when they are back before the run ended.
 * - Stopped by WorkManager (Wi-Fi lost, cancelled): the games in flight are not delivered.
 */
public class ScrapeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val work = (applicationContext as? ScraperHost)?.scraperWork()
        if (work == null) {
            Log.w(ScraperConfig.LOG_TAG, "scrape worker: no ScraperHost or scraping not set up; nothing done")
            return Result.success()
        }
        if (!work.scraper.available) {
            work.log("scrape worker: this build has no scraper (no developer credentials); nothing done")
            return Result.success()
        }
        val lowRam = work.lowRamDevice ?: MediaSaver.isLowRamDevice(applicationContext)
        val end = try {
            work.runLock.withLock { ScrapeRun(work, MediaSaver(work.mediaDir, lowRam, work.log)).run() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The library's source failed (database, disk): not something a retry fixes by itself.
            work.log("scrape worker: run failed (${e.javaClass.simpleName}); not retried")
            ended(work, ScrapeRunEnd.Stopped)
            return Result.failure()
        }
        ended(
            work,
            when (end) {
                ScrapeRun.End.Done -> ScrapeRunEnd.Done
                is ScrapeRun.End.Paused -> ScrapeRunEnd.Paused
                ScrapeRun.End.Continue -> ScrapeRunEnd.Continue
                ScrapeRun.End.HeldOff -> ScrapeRunEnd.HeldOff
                ScrapeRun.End.Retry -> ScrapeRunEnd.Retry
                ScrapeRun.End.Stopped -> ScrapeRunEnd.Stopped
            },
        )
        return when (end) {
            ScrapeRun.End.Done -> Result.success()
            is ScrapeRun.End.Paused -> followUp(work, (end.resumeAtMillis - work.clock()).coerceAtLeast(0))
            ScrapeRun.End.Continue -> followUp(work, delayMillis = 0)
            // Still playing: nothing queued (the app asks on the return). Back already: the return's request
            // (KEEP) was dropped while this run ended, so the follow-up comes from here.
            ScrapeRun.End.HeldOff -> if (work.holdOff()) Result.success() else followUp(work, delayMillis = 0)
            ScrapeRun.End.Retry -> Result.retry()
            ScrapeRun.End.Stopped -> Result.failure()
        }
    }

    private suspend fun ended(work: ScraperWork, end: ScrapeRunEnd) {
        try {
            work.onEnded(end, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            work.log("scrape worker: the host's end hook failed (${e.javaClass.simpleName})")
        }
    }

    /** Success, with the same unique work again after [delayMillis] (a fresh attempt count). */
    private suspend fun followUp(work: ScraperWork, delayMillis: Long): Result {
        val wifiOnly = inputData.getBoolean(ScrapeScheduler.KEY_WIFI_ONLY, true)
        val added = ScrapeScheduler.enqueueFollowUp(applicationContext, id, delayMillis, wifiOnly)
        work.log(if (added) "scrape worker: next run in $delayMillis ms" else "scrape worker: a next run already waits")
        return Result.success()
    }
}
