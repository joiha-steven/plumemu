package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rescan library's result lives in the graph and only a person's move clears it: Settings' content
 * flow ending (the screen going to sleep stops its collection) and starting again must not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ScanResultSurvivesSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val library = GraphSettingsLibrary(graph, context, Dispatchers.IO, main = Dispatchers.Unconfined, log = {}, info = {})

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    @Test
    fun theResultSurvivesTheContentFlowBeingCollectedCancelledAndCollectedAgain() = runBlocking {
        graph.startup.requestScan().join()
        assertNotNull("a pass ended with a result", graph.startup.scanReport.value.result)

        val firstLook = withTimeout(TIMEOUT_MS) { library.content.first() }
        assertNotNull(firstLook.scan.result)
        // first() cancelled the collection, as ON_STOP does; collecting again is ON_START.
        val again = withTimeout(TIMEOUT_MS) { library.content.first() }
        assertNotNull("still there after the flow ended and started again", again.scan.result)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
