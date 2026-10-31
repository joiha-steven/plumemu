package com.plumemu.graph

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plumemu.library.scan.ContentReadBudget
import com.plumemu.library.scan.ContentReadResult
import kotlinx.coroutines.CancellationException

/** What a content pass run needs from the app; the Application ([ContentReadHost]) hands it out. */
class ContentReadWork(
    val readContent: suspend (ContentReadBudget) -> ContentReadResult,
    /** Asks for a box art run (harmless without a usable account: it does nothing then). */
    val requestBoxArt: () -> Unit,
    val log: (String) -> Unit,
    /** Normal lines (a follow-up queued); [log] is for errors. */
    val info: (String) -> Unit = log,
    val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
    /** True while a game is played (marking the pass to go on at the return, `HeldForPlay`): the run stops between files. */
    val holdForPlay: suspend () -> Boolean = { false },
) {
    companion object {
        /**
         * WorkManager stops a job after about 10 minutes. 8 minutes leaves room to finish the file
         * being read (up to 64 MiB, slow on an SD card) and to enqueue the follow-up before then.
         */
        const val DEFAULT_BUDGET_MILLIS: Long = 8 * 60_000L
    }
}

/** Implemented by the Application, as `ScraperHost` is for the scrape worker. */
interface ContentReadHost {
    fun contentReadWork(): ContentReadWork?
}

/**
 * The background content pass: `LibraryScanner.readContent` with a time budget.
 *
 * - No host or graph: success, nothing done.
 * - Budget spent with files left and progress made (a row written or a failure counted; a read that was skipped is no progress, it would come again forever): the same
 *   unique work again; success. No progress (folder or card unavailable, everything skipped):
 *   nothing is rescheduled, the next scan enqueues again.
 * - Rows written: a box art run is requested, since their keys (and maybe names) changed.
 * - A game being played ([ContentReadWork.holdForPlay]): not begun, or stopped before the next file
 *   (what was read is written); no follow-up, the return asks for a run again (`HeldForPlay`); success.
 * - An error: logged with its class, failure, not retried. Stopped by the system: no log.
 */
internal class ContentReadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val work = (applicationContext as? ContentReadHost)?.contentReadWork() ?: return Result.success()
        var heldForPlay = work.holdForPlay()
        if (heldForPlay) return heldEnd(work, "not begun")
        val budget = ContentReadBudget(maxMillis = work.budgetMillis, stopBeforeNextFile = { work.holdForPlay().also { heldForPlay = it } })
        val result = try {
            work.readContent(budget)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            work.log("content worker: run failed (${e.javaClass.simpleName}); not retried")
            return Result.failure()
        }
        if (result.written > 0) work.requestBoxArt()
        if (heldForPlay) return heldEnd(work, "stopped between files")
        if (result.moreLeft && result.written + result.failed > 0) {
            val added = ContentReadScheduler.enqueueFollowUp(applicationContext, id)
            work.info(if (added) "content worker: next run queued" else "content worker: a next run already waits")
        }
        return Result.success()
    }

    /**
     * Held for a game. The return's request (`resumeHeldWork`, KEEP) is dropped while this run still
     * ends, so a person back by now gets the follow-up from here; one still playing is marked again
     * (`holdForPlay`) and the return asks.
     */
    private suspend fun heldEnd(work: ContentReadWork, what: String): Result {
        if (work.holdForPlay()) {
            work.info("content worker: $what while a game is played; goes on when Plumemu is back")
            return Result.success()
        }
        val added = ContentReadScheduler.enqueueFollowUp(applicationContext, id)
        work.info("content worker: $what for a game, and the person is back: " + if (added) "next run queued" else "a next run already waits")
        return Result.success()
    }
}
