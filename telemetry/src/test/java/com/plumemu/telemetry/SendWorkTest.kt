package com.plumemu.telemetry

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Application of these tests: hands the worker the telemetry a test made. */
class TelemetryTestHostApp : Application(), TelemetryHost {
    override var telemetry: Telemetry? = null
}

/** The WorkManager side: what is asked for, and what the worker answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TelemetryTestHostApp::class)
class SendWorkTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var h: TelemetryHarness

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        server.start()
        h = TelemetryHarness(folder.newFolder("telemetry"), server)
    }

    @After
    fun tearDown() {
        (context as TelemetryTestHostApp).telemetry = null
        h.stop()
        server.close()
    }

    private fun work(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun worker() = TestListenableWorkerBuilder<TelemetrySendWorker>(context).build()

    @Test
    fun sendNowWaitsForANetworkWithNoDelay() {
        TelemetryScheduler(context) { Log.w("test", it) }.sendNow()

        val info = work(TelemetryScheduler.NOW).single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(0L, info.initialDelayMillis)
    }

    @Test
    fun sendSoonWaitsFiveMinutesAndAskingAgainKeepsTheFirst() {
        val scheduler = TelemetryScheduler(context) { Log.w("test", it) }
        scheduler.sendSoon()
        val first = work(TelemetryScheduler.LATER).single()
        scheduler.sendSoon()

        val info = work(TelemetryScheduler.LATER).single()
        assertEquals(first.id, info.id)
        assertEquals(5 * 60 * 1000L, info.initialDelayMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun theWorkerSendsTheQueue() {
        (context as TelemetryTestHostApp).telemetry = h.telemetry
        h.answer(204)

        val result = runBlocking { worker().doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("/api/v1/events", h.take().url.encodedPath)
    }

    @Test
    fun theWorkerRetriesWhenTheServerIsUnwell() {
        (context as TelemetryTestHostApp).telemetry = h.telemetry
        h.answer(503)

        assertEquals(ListenableWorker.Result.retry(), runBlocking { worker().doWork() })
        assertEquals(1, h.telemetry.queue.size())
    }

    @Test
    fun withoutATelemetryTheWorkerDoesNothing() {
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker().doWork() })
        assertEquals(0, server.requestCount)
    }
}
