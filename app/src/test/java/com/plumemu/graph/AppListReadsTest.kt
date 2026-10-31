package com.plumemu.graph

import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reads of the Apps list never overlap, so an old slow read cannot bring back an app a newer read no longer lists. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppListReadsTest {
    private fun app(name: String) = LaunchableApp(name, "pkg.$name", "Main")

    @Test
    fun anOldSlowReadCannotOverwriteANewerOne() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MutableStateFlow<List<LaunchableApp>>(emptyList())
            val slow = CompletableDeferred<List<LaunchableApp>>()
            val answers = ArrayDeque<suspend () -> List<LaunchableApp>?>()
            answers += { slow.await() }
            answers += { listOf(app("kept")) }
            val reads = AppListReads(list) { answers.removeFirst()() }
            launch { reads.refresh() } // older: still reading
            launch { reads.refresh() } // newer: waits its turn
            slow.complete(listOf(app("kept"), app("uninstalled")))
            assertEquals(listOf(app("kept")), list.value)
        }

    @Test
    fun aReadWithNoLauncherKeepsTheList() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MutableStateFlow(listOf(app("kept")))
            AppListReads(list) { null }.refresh()
            assertEquals(listOf(app("kept")), list.value)
        }
}
