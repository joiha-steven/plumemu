package com.plumemu.scraper.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Starts and stops the background scrape. Nothing periodic, no polling: a run starts
 * only when the app asks (the person pressed scrape, or a schedule they set), waits for Wi-Fi
 * (or any connection) and a battery that is not low, and stops when it has nothing left to ask.
 */
public object ScrapeScheduler {
    public const val UNIQUE_WORK_NAME: String = "plumemu.scrape"
    internal const val KEY_WIFI_ONLY = "wifiOnly"

    /** WorkManager doubles it on every retry (network down, server unwell), up to its 5 h cap. */
    internal const val BACKOFF_MILLIS: Long = 60_000

    /**
     * Asks for a run. A run already waiting or going (or waiting out a pause) is kept: pressing
     * scrape twice does not start two, nor jump a quota pause.
     */
    public fun enqueue(context: Context, wifiOnly: Boolean = true) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request(wifiOnly, delayMillis = 0))
    }

    /** Stops the run (the current game is not delivered, so it is asked again next time). */
    public fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Called by the running worker [runningId] on a pause or at the end of its time budget: one
     * more run, [delayMillis] from when this one ends. Returns false when a follow-up already
     * waits (a worker killed after enqueueing one and run again): no second one is added.
     *
     * APPEND_OR_REPLACE, not REPLACE: REPLACE would cancel the work that is running (the caller
     * itself) and drop its result. APPEND_OR_REPLACE adds the run after this one, and starts a
     * fresh chain if the old one had failed or been cancelled.
     */
    internal suspend fun enqueueFollowUp(context: Context, runningId: UUID, delayMillis: Long, wifiOnly: Boolean): Boolean {
        val workManager = WorkManager.getInstance(context)
        val waiting = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).first()
            .any { it.id != runningId && !it.state.isFinished }
        if (waiting) return false
        workManager
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(wifiOnly, delayMillis))
            .await()
        return true
    }

    internal fun request(wifiOnly: Boolean, delayMillis: Long): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        return OneTimeWorkRequestBuilder<ScrapeWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_WIFI_ONLY to wifiOnly))
            .apply { if (delayMillis > 0) setInitialDelay(delayMillis, TimeUnit.MILLISECONDS) }
            .build()
    }
}
