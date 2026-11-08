package com.plumemu

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GpuWarmth] over frames on the compose test clock: after an input the ticker draws every [GpuWarmth.TICK_MS] for [GpuWarmth.WARM_MS] and
 * stops by 100 ms after it, with no gap the GPU would sleep through; its pixel is laid out and drawn; another input inside the window extends it; with no input nothing is written, so no frame is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GpuWarmthTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val warmth = GpuWarmth()

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { GpuWarmthTicker(warmth) }
        compose.mainClock.advanceTimeBy(REST_MS)
    }

    /** Advances [ms] frame by frame from now and returns the times (ms from now) of the frames in which the ticker drew again. */
    private fun warmFramesOver(ms: Long): List<Long> {
        val out = mutableListOf<Long>()
        val start = compose.mainClock.currentTime
        while (compose.mainClock.currentTime - start < ms) {
            val before = warmth.tick
            compose.mainClock.advanceTimeByFrame()
            if (warmth.tick != before) out += compose.mainClock.currentTime - start
        }
        return out
    }

    private fun poke() = compose.runOnUiThread { warmth.poke() }

    @Test
    fun anInputKeepsFramesComingForTheWindowThenNone() {
        show()
        poke()
        val frames = warmFramesOver(LOOK_MS)
        assertTrue("warm frames start at once: $frames", frames.isNotEmpty() && frames.first() <= 2 * FRAME_MS)
        assertTrue("a frame every ${GpuWarmth.TICK_MS} ms while warm: $frames", frames.size >= GpuWarmth.WARM_MS / GpuWarmth.TICK_MS - 2)
        assertTrue("no gap the GPU would sleep through: $frames", frames.zipWithNext().all { (a, b) -> b - a <= GPU_SLEEPS_AFTER_MS })
        assertTrue("the last warm frame comes by $STOP_BY_MS ms: ${frames.last()}", frames.last() <= STOP_BY_MS)
    }

    @Test
    fun thePixelIsLaidOutAndDrawn() {
        show()
        // Robolectric draws only when captured: a pixel left unplaced is never drawn, and the GPU gets no frame.
        compose.onRoot().captureToImage()
        assertTrue("the ticker's pixel was drawn", warmth.draws > 0)
    }

    @Test
    fun anotherInputInsideTheWindowExtendsIt() {
        show()
        poke()
        warmFramesOver(GpuWarmth.WARM_MS / 2)
        poke()
        val frames = warmFramesOver(LOOK_MS)
        assertTrue("warm for about another window: ${frames.last()}", frames.last() in GpuWarmth.WARM_MS - 2 * FRAME_MS..STOP_BY_MS)
    }

    @Test
    fun atRestNothingIsWrittenSoNoFrameIsDrawn() {
        show()
        poke()
        warmFramesOver(LOOK_MS)
        // The window's own writes are announced lazily: flush them so only what happens at rest is counted.
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        val writes = mutableListOf<Any>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes.addAll(changed) }
        try {
            val tick = warmth.tick
            assertEquals("no warm frame at rest", emptyList<Long>(), warmFramesOver(REST_MS))
            assertEquals(tick, warmth.tick)
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            assertTrue("no state written at rest: $writes", writes.isEmpty())
        } finally {
            observer.dispose()
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val LOOK_MS = 2_000L
        const val STOP_BY_MS = GpuWarmth.WARM_MS + 100L

        /** The handhelds' GPUs powered down after gaps of 92-100 ms between frames (Nova traces). */
        const val GPU_SLEEPS_AFTER_MS = 64L
        const val REST_MS = 5_000L
    }
}
