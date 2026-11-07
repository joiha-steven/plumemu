package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Rescan library on the real graph also looks for emulators, and a detection that fails leaves the scan alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RescanDetectsEmulatorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val logged = CopyOnWriteArrayList<String>()
    private val graph = AppGraph(context, log = { logged += it })
    private val detections = AtomicInteger()
    private val detected = CompletableDeferred<Unit>()

    private fun library(detect: suspend () -> Unit) =
        GraphSettingsLibrary(graph, context, Dispatchers.IO, main = Dispatchers.Unconfined, log = { logged += it }, info = {}, findEmulators = detect)

    private suspend fun scanEnded() = withTimeout(TIMEOUT_MS) { graph.startup.scanState.first { !it.isBusy } }

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    @Test
    fun aRescanAlsoRunsDetection() = runBlocking {
        val library =
            library {
                detections.incrementAndGet()
                detected.complete(Unit)
            }
        assertTrue(library.rescanLibrary())
        withTimeout(TIMEOUT_MS) { detected.await() }
        scanEnded()
        assertEquals(1, detections.get())
    }

    @Test
    fun aFailedDetectionIsLoggedAndTheRescanStillRunsAndEnds() = runBlocking {
        val library =
            library {
                detected.complete(Unit)
                throw IOException("no packages")
            }
        assertTrue(library.rescanLibrary())
        withTimeout(TIMEOUT_MS) { detected.await() }
        scanEnded()
        assertTrue(logged.toString(), withTimeout(TIMEOUT_MS) { awaitLog() })
        assertTrue("the scan ended with its result", graph.startup.scanReport.value.result != null)
    }

    @Test
    fun aRescanRefusedBecauseOneRunsStartsNoDetection() = runBlocking {
        val library = library { detections.incrementAndGet() }
        val gate = CompletableDeferred<Unit>()
        graph.startup.removeFolder { gate.await() }
        withTimeout(TIMEOUT_MS) { graph.startup.scanState.first { it.isBusy } }
        assertFalse(library.rescanLibrary())
        gate.complete(Unit)
        scanEnded()
        assertEquals(0, detections.get())
    }

    private suspend fun awaitLog(): Boolean {
        while (logged.none { it.contains("emulator detection failed") }) kotlinx.coroutines.delay(LOG_POLL_MS)
        return true
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val LOG_POLL_MS = 5L
    }
}
