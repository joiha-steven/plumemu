package com.plumemu.graph

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Starts the background content pass. Like
 * the scrape: nothing periodic; a run is asked for after a scan that leaves files to read (no setting). It reads local files, so it needs no network, only a battery that is not low.
 */
internal object ContentReadScheduler {
    const val UNIQUE_WORK_NAME: String = "plumemu.content"

    /** A run already waiting or going is kept: two scans in a row do not start two. */
    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request())
    }

    /**
     * Called by the running worker [runningId] when its time budget ended with files left: one
     * more run after this one. False when a follow-up already waits (a worker killed after
     * enqueueing one and run again). APPEND_OR_REPLACE for the reason in
     * [com.plumemu.scraper.work.ScrapeScheduler.enqueueFollowUp]: REPLACE would cancel the caller.
     */
    suspend fun enqueueFollowUp(context: Context, runningId: UUID): Boolean {
        val workManager = WorkManager.getInstance(context)
        val waiting = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).first()
            .any { it.id != runningId && !it.state.isFinished }
        if (waiting) return false
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request()).await()
        return true
    }

    fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ContentReadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).setRequiresBatteryNotLow(true).build())
        .build()
}
