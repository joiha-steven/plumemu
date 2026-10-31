package com.plumemu.graph

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.scan.ScanEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The front hook the update check hangs on: told once the
 * startup work is done, then on every return to the front, and when Plumemu leaves it; never before
 * the first frame, and a failure in it never breaks the Activity's start or stop.
 */
class StartupWorkFrontTest {
    private val calls = CopyOnWriteArrayList<String>()
    private val logLines = CopyOnWriteArrayList<String>()
    private var failFront = false

    /** Runs inside frontReturned, after "front" is recorded (the race test holds the call open here). */
    @Volatile private var duringFront: () -> Unit = {}

    private val tasks =
        object : StartupTasks {
            override suspend fun refreshApps() = Unit

            override suspend fun detectEmulators() = Unit

            override suspend fun refreshDefaults() = Unit

            override suspend fun setRescanAfterReset(pending: Boolean) = Unit

            override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean = true

            override suspend fun scanEnded() = Unit

            override fun frontReturned(): Job? {
                calls += "front"
                duringFront()
                if (failFront) throw IllegalStateException("broken")
                return null
            }

            override fun frontLeft() {
                calls += "left"
            }
        }

    private fun TestScope.work(): StartupWork {
        val settings = MutableStateFlow<PlumSettings?>(PlumSettings(flags = SetupFlags(setupCompleted = true)))
        return StartupWork(backgroundScope, settings, tasks, clock = { testScheduler.currentTime }) { logLines += it }
    }

    @Test
    fun toldAfterTheStartupWorkThenOnEveryReturnAndLeave() =
        runTest {
            val work = work()
            // MainActivity's onStart comes before the first frame: nothing yet.
            work.onStart()
            runCurrent()
            assertEquals(emptyList<String>(), calls)
            work.onContentShown()
            runCurrent()
            assertEquals(listOf("front"), calls)
            work.onStop()
            work.onStart()
            runCurrent()
            assertEquals(listOf("front", "left", "front"), calls)
        }

    /** a game started during the startup scan; its end must not report the front. */
    @Test
    fun leavingBeforeTheStartupWorkEndsWaitsForTheNextRealReturn() =
        runTest {
            val work = work()
            work.onStart()
            work.onStop()
            work.onContentShown()
            runCurrent()
            assertEquals(listOf("left"), calls)
            work.onStart()
            runCurrent()
            assertEquals(listOf("left", "front"), calls)
        }

    /**
     * the startup work's "still in front? then tell the tasks" must be one
     * step. While its frontReturned runs (held open here), an onStop from the main thread waits for it,
     * so "left" always comes last and nothing believes Plumemu is in front during a game.
     */
    @Test
    fun anOnStopDuringTheStartupFrontCallComesAfterIt() =
        runBlocking {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            duringFront = {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                calls += "front-end"
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val settings = MutableStateFlow<PlumSettings?>(PlumSettings(flags = SetupFlags(setupCompleted = true)))
                val work = StartupWork(scope, settings, tasks, clock = { System.currentTimeMillis() }) { logLines += it }
                work.onStart()
                work.onContentShown()
                assertTrue("the startup work reached frontReturned", entered.await(5, TimeUnit.SECONDS))
                duringFront = {}
                val stop = thread { work.onStop() }
                Thread.sleep(HOLD_MS)
                release.countDown()
                stop.join(5_000)
                assertEquals(listOf("front", "front-end", "left"), calls.toList())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun theNetworkKindFollowsTransportAndMetering() {
        assertEquals(com.plumemu.update.NetworkKind.Wifi, networkKindOf(isMetered = false, isWifiOrEthernet = true))
        assertEquals(com.plumemu.update.NetworkKind.Unmetered, networkKindOf(isMetered = false, isWifiOrEthernet = false))
        assertEquals(com.plumemu.update.NetworkKind.Metered, networkKindOf(isMetered = true, isWifiOrEthernet = true))
    }

    @Test
    fun aFailingHookIsLoggedAndTheStartGoesOn() =
        runTest {
            val work = work()
            work.onContentShown()
            runCurrent()
            failFront = true
            work.onStart()
            assertTrue(logLines.any { it == "Startup front failed (IllegalStateException)" })
        }

    private companion object {
        const val HOLD_MS = 300L
    }
}
