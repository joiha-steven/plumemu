package com.plumemu.graph

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.scan.ScanEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › ROM folders › Remove in the scan slot: never while a scan writes, a scan
 * of the folders that stay afterwards, then the default emulators. `runCurrent()` runs the work
 * (`advanceUntilIdle` skips `backgroundScope`).
 */
class StartupWorkRemovalTest {
    private val done = CopyOnWriteArrayList<String>()
    private val logLines = CopyOnWriteArrayList<String>()
    private var scanGate: CompletableDeferred<Unit>? = null
    private var failing: String? = null

    private val tasks =
        object : StartupTasks {
            override suspend fun refreshApps() = record("apps")

            override suspend fun detectEmulators() = record("detect")

            override suspend fun refreshDefaults() = record("defaults")

            override suspend fun setRescanAfterReset(pending: Boolean) = record(if (pending) "flag on" else "flag off")

            override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
                scanGate?.await()
                record("scan")
                return true
            }

            override suspend fun scanEnded() = Unit
        }

    private fun record(task: String) {
        if (task == failing) throw IllegalStateException("content://x/tree/primary%3AROMs/Secret Quest.sfc")
        done += task
    }

    private fun TestScope.work(): StartupWork {
        val settings = MutableStateFlow<PlumSettings?>(PlumSettings(flags = SetupFlags(setupCompleted = true)))
        return StartupWork(backgroundScope, settings, tasks, info = {}, clock = { 0L }) { logLines += it }
    }

    @Test
    fun aFolderRemovalWaitsForTheRunningScanThenScansAndPicksDefaults() = runTest {
        val work = work()
        val gate = CompletableDeferred<Unit>().also { scanGate = it }
        work.requestScan()
        runCurrent()
        val removed = work.removeFolder { record("remove") }
        runCurrent()
        assertEquals("nothing removed while a scan writes", emptyList<String>(), done)
        assertTrue(work.scanState.value.isBusy)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("scan", "remove", "scan", "defaults"), done)
        assertTrue(removed.await())
        assertFalse(work.scanState.value.isBusy)
    }

    @Test
    fun aFailedFolderRemovalIsReportedLoggedByClassAndStillScans() = runTest {
        val work = work()
        failing = "remove"
        val removed = work.removeFolder { record("remove") }
        assertTrue("busy from the request on", work.scanState.value.isBusy)
        runCurrent()
        assertFalse(removed.await())
        assertEquals(listOf("scan", "defaults"), done)
        assertTrue(logLines.any { it.contains("folder removal failed (IllegalStateException)") })
        assertFalse("no path in the log", logLines.any { it.contains("content://") })
    }

    @Test
    fun aScanRequestedDuringARemovalRunsAfterIt() = runTest {
        val work = work()
        val gate = CompletableDeferred<Unit>()
        work.removeFolder {
            gate.await()
            record("remove")
        }
        runCurrent()
        work.requestScan()
        runCurrent()
        assertEquals("no scan while the folder is being removed", emptyList<String>(), done)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("remove", "scan", "defaults"), done)
        assertFalse(work.scanState.value.isBusy)
    }

    @Test
    fun aRemovalAndAResetWaitForEachOther() = runTest {
        val work = work()
        val gate = CompletableDeferred<Unit>()
        work.removeFolder {
            gate.await()
            record("remove")
        }
        runCurrent()
        work.resetLibrary { record("forget") }
        runCurrent()
        assertEquals("the reset waits for the removal's slot", emptyList<String>(), done)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("remove", "scan", "defaults", "flag on", "forget", "scan", "defaults"), done)
        assertFalse(work.scanState.value.isBusy)
    }
}
