package com.plumemu.telemetry

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Implemented by the Application: how the send worker finds the process's [Telemetry]. An
 * Application that is not one, or answers null (the app's Robolectric tests), makes every run a
 * no-op.
 */
public interface TelemetryHost {
    public val telemetry: Telemetry?
}

/** When the queue goes to the server. */
internal interface SendScheduler {
    /** As soon as there is a network (the first launch, the app going to the background, a decline). */
    fun sendNow()

    /** Within [TelemetryScheduler.BATCH_DELAY_MILLIS]; a send already waiting is kept, so at most one batch per 5 minutes. */
    fun sendSoon()
}

/**
 * The sends as WorkManager work, so they wait for a network and survive the process. No periodic
 * work and no polling: a send is asked for only when something was queued.
 *
 * Two unique works, each KEEP: "now" (no delay) and "later" (5 minutes). They may run together;
 * [Telemetry.sendPending] lets one in at a time and the second finds the queue already sent.
 */
internal class TelemetryScheduler(
    context: Context,
    private val log: (String) -> Unit,
) : SendScheduler {
    private val app = context.applicationContext

    override fun sendNow() = enqueue(NOW, delayMillis = 0)

    override fun sendSoon() = enqueue(LATER, BATCH_DELAY_MILLIS)

    private fun enqueue(
        name: String,
        delayMillis: Long,
    ) {
        try {
            WorkManager.getInstance(app).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request(delayMillis))
        } catch (e: RuntimeException) {
            // WorkManager not set up (a host without Configuration.Provider): the queue waits for the next start.
            log("Statistics: send could not be scheduled (${e.javaClass.simpleName})")
        }
    }

    companion object {
        const val NOW = "plumemu.telemetry.now"
        const val LATER = "plumemu.telemetry.later"

        /** Batches go at most this often while the app is in use (docs/conventions/telemetry.md › Delivery). */
        const val BATCH_DELAY_MILLIS: Long = 5 * 60 * 1000

        /** WorkManager doubles it on every retry (no network, 429, 5xx), up to its 5 h cap. */
        const val BACKOFF_MILLIS: Long = 60_000

        fun request(delayMillis: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<TelemetrySendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .apply { if (delayMillis > 0) setInitialDelay(delayMillis, TimeUnit.MILLISECONDS) }
                .build()
    }
}

/** Sends what is queued ([Telemetry.sendPending]); retries later when the server or the network said so. */
public class TelemetrySendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val telemetry = (applicationContext as? TelemetryHost)?.telemetry
        if (telemetry == null) {
            Log.i(Telemetry.LOG_TAG, "send worker: no Telemetry in this process; nothing done")
            return Result.success()
        }
        return when (telemetry.sendPending()) {
            SendOutcome.Done -> Result.success()
            SendOutcome.Retry -> Result.retry()
        }
    }
}
