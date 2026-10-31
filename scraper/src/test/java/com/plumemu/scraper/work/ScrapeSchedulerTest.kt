package com.plumemu.scraper.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeSchedulerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun work(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(ScrapeScheduler.UNIQUE_WORK_NAME).get()

    @Test fun `a run waits for Wi-Fi and a battery that is not low, with no delay`() {
        ScrapeScheduler.enqueue(context)

        val info = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        assertTrue(info.constraints.requiresBatteryNotLow())
        assertEquals(0L, info.initialDelayMillis)
        assertEquals(PERIODIC_NONE, info.periodicityInfo)
    }

    @Test fun `asking twice keeps the first run`() {
        ScrapeScheduler.enqueue(context)
        val first = work().single().id

        ScrapeScheduler.enqueue(context, wifiOnly = false)

        val info = work().single()
        assertEquals(first, info.id)
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
    }

    @Test fun `wifiOnly false takes any connection`() {
        ScrapeScheduler.enqueue(context, wifiOnly = false)

        val info = work().single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertTrue(info.constraints.requiresBatteryNotLow())
    }

    @Test fun `cancel stops the run`() {
        ScrapeScheduler.enqueue(context)

        ScrapeScheduler.cancel(context)

        assertEquals(WorkInfo.State.CANCELLED, work().single().state)
    }

    @Test fun `a re-run after a pause keeps the network choice and waits its delay`() {
        val request = ScrapeScheduler.request(wifiOnly = false, delayMillis = 5_000)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(5_000L, request.workSpec.initialDelay)
        assertEquals(androidx.work.BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(ScrapeScheduler.BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)
        assertEquals(false, request.workSpec.input.getBoolean(ScrapeScheduler.KEY_WIFI_ONLY, true))
    }

    private companion object {
        val PERIODIC_NONE: WorkInfo.PeriodicityInfo? = null
    }
}
