package com.plumemu.graph

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.scan.ScanEvent
import com.plumemu.telemetry.ScanTrigger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each scan pass tells why it ran and how it ended (`library_scanned`), from every way a scan starts. */
class StartupWorkScanTriggerTest {
    private val ended = CopyOnWriteArrayList<ScanEnded>()
    private var scanOk = true
    private var now = 0L

    private val tasks =
        object : StartupTasks {
            override suspend fun refreshApps() = Unit

            override suspend fun detectEmulators() = Unit

            override suspend fun refreshDefaults() = Unit

            override suspend fun setRescanAfterReset(pending: Boolean) = Unit

            override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
                now += 3_000
                return scanOk
            }

            override suspend fun scanEnded() = Unit
        }

    private fun TestScope.work(): StartupWork {
        val settings =
            MutableStateFlow<PlumSettings?>(
                PlumSettings(flags = SetupFlags(setupCompleted = true, folderMatchVersion = FOLDER_MATCH_VERSION, namingRulesVersion = NAMING_RULES_VERSION)),
            )
        return StartupWork(backgroundScope, settings, tasks, info = {}, clock = { now }, onScanEnded = { ended += it }) { }
    }

    @Test
    fun theStartupScanSaysStartupWithItsTime() = runTest {
        work().onContentShown()
        runCurrent()
        assertEquals(listOf(ScanEnded(ScanTrigger.Startup, isOk = true, hasFolderLost = false, millis = 3_000)), ended.toList())
    }

    @Test
    fun setupAndAFolderAddedSayWhyAndAFailureSaysSo() = runTest {
        val work = work()
        work.requestScan(ScanTrigger.Setup)
        runCurrent()
        scanOk = false
        work.requestScan(ScanTrigger.FolderAdded)
        runCurrent()
        assertEquals(listOf(ScanTrigger.Setup to true, ScanTrigger.FolderAdded to false), ended.map { it.trigger to it.isOk })
    }

    @Test
    fun aRescanFromTheGameMenuIsManual() = runTest {
        val work = work()
        work.scanIfIdle()
        assertEquals(listOf(ScanTrigger.Manual), ended.map { it.trigger })
    }

    @Test
    fun aResetIsManual() = runTest {
        work().resetLibrary { }
        runCurrent()
        assertEquals(listOf(ScanTrigger.Manual), ended.map { it.trigger })
    }
}
