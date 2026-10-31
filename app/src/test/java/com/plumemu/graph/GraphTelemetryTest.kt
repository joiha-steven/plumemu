package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.telemetry.SwitchPlace
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.TelemetrySink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * The two statistics switches reach the telemetry module with their place (brief A1): setup's
 * Ready step says `setup`; Settings, which only writes the store, says `settings` through the
 * follower. The module itself (telemetry tests) ignores a switch to the value it already has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphTelemetryTest {
    private class FakeSink : TelemetrySink {
        val switches: MutableList<Pair<Boolean, SwitchPlace>> = Collections.synchronizedList(ArrayList())

        override fun track(event: TelemetryEvent) = Unit

        override fun setEnabled(
            on: Boolean,
            where: SwitchPlace,
        ) {
            switches += on to where
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sink = FakeSink()
    private val graph = AppGraph(context, log = {}, telemetry = sink)

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun awaitSwitches(count: Int) =
        runBlocking {
            withTimeout(TIMEOUT_MS) { while (sink.switches.size < count) delay(10) }
        }

    private fun storedWith(statistics: Boolean, setupCompleted: Boolean): Boolean? {
        runBlocking {
            graph.settingsStore.update {
                it.copy(advanced = it.advanced.copy(usageStatistics = statistics), flags = it.flags.copy(setupCompleted = setupCompleted))
            }
        }
        return runBlocking { graph.storedUsageStatistics() }
    }

    @Test
    fun theStoredSwitchIsNothingChosenOnlyWhenOffWithSetupUnfinished() {
        assertEquals("off, setup unfinished: nothing chosen", null, storedWith(statistics = false, setupCompleted = false))
        assertEquals("off, setup finished: a choice", false, storedWith(statistics = false, setupCompleted = true))
        assertEquals("on, setup unfinished: a choice", true, storedWith(statistics = true, setupCompleted = false))
    }

    @Test
    fun settingsMovesArriveFromTheStoreWithThePlaceSettings() {
        graph.followUsageStatistics(sink)
        awaitSwitches(1)
        assertEquals("the stored value at start (off by default)", false to SwitchPlace.Settings, sink.switches[0])

        runBlocking { graph.settingsStore.update { it.copy(advanced = it.advanced.copy(usageStatistics = true)) } }
        awaitSwitches(2)
        assertEquals(true to SwitchPlace.Settings, sink.switches[1])

        runBlocking { graph.settingsStore.update { it.copy(advanced = it.advanced.copy(usageStatistics = false)) } }
        awaitSwitches(3)
        assertEquals(false to SwitchPlace.Settings, sink.switches[2])
    }

    @Test
    fun setupTellsTheModuleWithThePlaceSetupBeforeTheStoreChanges() {
        graph.followUsageStatistics(sink)
        awaitSwitches(1)
        val setup = GraphSetupSource(graph, context, Dispatchers.IO)

        runBlocking { setup.setUsageStatistics(true) }

        assertEquals("setup's call lands first", true to SwitchPlace.Setup, sink.switches[1])
        // The follower may then repeat the same value (the module ignores it); never the other one.
        runBlocking { delay(200) }
        assertTrue(sink.switches.drop(1).all { it.first })
        assertEquals(true, runBlocking { graph.settingsStore.settings.first() }.advanced.usageStatistics)
    }

    @Test
    fun aFailingStoreNeverSwitchesStatisticsOn() {
        val failing = flow<Boolean> { throw java.io.IOException("unreadable") }
        val job = graph.followUsageStatistics(sink, failing)
        runBlocking { withTimeout(TIMEOUT_MS) { job.join() } }

        assertEquals(emptyList<Pair<Boolean, SwitchPlace>>(), sink.switches.toList())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
