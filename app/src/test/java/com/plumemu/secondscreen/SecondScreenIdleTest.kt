package com.plumemu.secondscreen

import android.app.Dialog
import android.view.ViewGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The second screen's details card at rest costs nothing: once the card is settled and drawn,
 * 2 s with the cursor still run no composable and apply no state change (an animation's value, a
 * polled field: what any redraw starts from). The control, a polling content in the same window,
 * shows both counters move when work is asked. Robolectric has no surface: Choreographer frames
 * are not looper messages and nothing draws by itself, so those two cannot be counted here (the
 * control showed zero for both); a frame needs a state change or invalidation, which are counted.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenIdleTest : SecondScreenHostTestBase() {
    private var compositions = 0
    private var stateChanges = 0
    private var changes: ObserverHandle? = null

    @Before
    fun startCounting() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    compositions++
                }

                override fun traceEventEnd() = Unit
            },
        )
        changes = Snapshot.registerApplyObserver { changed, _ -> stateChanges += changed.size }
    }

    @After
    fun stopCounting() {
        Composer.setTracer(null)
        changes?.dispose()
    }

    /** Settles [content] in the window, draws it once as a device would, then counts 2 s of rest. */
    private fun measureRest(content: SecondScreenContent): Dialog {
        val host = startHost()
        feed.offer(content)
        idle(SETTLED_MS)
        val window = host.shownWindow!!
        pixelsOf(window)
        idle()
        resetCounters()
        idle(REST_MS)
        return window
    }

    private fun resetCounters() {
        compositions = 0
        stateChanges = 0
    }

    private fun assertNothingAtRest(what: String) {
        assertEquals("$what: composable calls in 2 s at rest", 0, compositions)
        assertEquals("$what: state changes in 2 s at rest", 0, stateChanges)
    }

    @Test
    fun gameCardIsIdleAtRest() {
        val window = measureRest(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        assertTrue("the card is in the window: ${textsOf(window)}", "Super Metroid" in textsOf(window).joinToString())
        assertNothingAtRest("game card")
    }

    @Test
    fun statisticsCardIsIdleAtRest() {
        measureRest(SecondScreenContent.Statistics(SecondScreenStatisticsSamples.withCovers))
        assertNothingAtRest("statistics card")
    }

    @Test
    fun theCountersSeeAPollInTheSameWindow() {
        val window = measureRest(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        resetCounters()
        window.addContentView(
            ComposeView(window.context).apply {
                setContent {
                    var ticks by remember { mutableIntStateOf(0) }
                    // Bounded: on the paused looper an endless poll never lets a step end (frames run back to back).
                    LaunchedEffect(Unit) { repeat(POLL_FRAMES) { withFrameNanos { ticks++ } } }
                    BasicText("ticks $ticks")
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        idle(REST_MS)
        assertTrue(
            "the control: $compositions compositions, $stateChanges state changes: a counter is blind",
            compositions > 0 && stateChanges > 0,
        )
    }

    private companion object {
        const val REST_MS = 2000L
        const val POLL_FRAMES = 400
    }
}
