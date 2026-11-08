package com.plumemu

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.plumemu.coreui.components.plainLayer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Keeps the GPU powered for [WARM_MS] after each input, so the next press's frame does not pay its wake-up.
 *
 * The handhelds' GPUs power down within about 100 ms of the last frame, and the first frame after that waits 7-12 ms in
 * `eglSwapBuffers` while the GPU's power controller wakes it. The screens draw nothing at rest, so a person stepping through a grid
 * or a list a few times a second paid that wake-up on every press, in the frame that also does the press's work. After a key, a
 * stick move or a touch, [GpuWarmthTicker] redraws one transparent pixel every [TICK_MS] until [WARM_MS] have passed with no input:
 * one tiny frame for the GPU, nothing that shows. Then nothing draws, as at rest.
 */
internal class GpuWarmth {
    private val pokes = Channel<Unit>(Channel.CONFLATED)

    /** Changes once per warm frame; read only by the ticker's draw. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** Inputs seen, for tests. */
    internal var inputs = 0
        private set

    /** Times the ticker's pixel was drawn, for tests: a frame the GPU does not get is no warm frame. */
    internal var draws = 0

    /** An input arrived (any key, stick or touch event). Call it on the main thread, from the Activity's dispatch. */
    fun poke() {
        inputs++
        pokes.trySend(Unit)
    }

    /**
     * Draws a frame every [TICK_MS] until [WARM_MS] have passed since the last [poke], then waits for the next one. Paced by
     * `delay`, not by the frame clock: a frame about every other vsync is enough to keep the GPU up (it powers down after about
     * 100 ms without one), and the window follows the time the caller runs on.
     */
    internal suspend fun run() {
        for (unused in pokes) {
            var left = WARM_MS
            while (left > 0) {
                tick++
                delay(TICK_MS)
                left = if (pokes.tryReceive().isSuccess) WARM_MS else left - TICK_MS
            }
        }
    }

    companion object {
        /**
         * How long the GPU is kept warm after the last input: longer than the gap between presses of someone stepping through a
         * grid (adb's walks press 590-730 ms apart, and half a second let the GPU sleep before each press), short enough not to
         * matter for battery: about 30 one-pixel frames.
         */
        const val WARM_MS = 1_000L

        /** Time between warm frames: well under the GPU's power-down delay, and half the frames of drawing at every vsync. */
        const val TICK_MS = 33L
    }
}

/**
 * One transparent pixel at the top left corner that is drawn again on every warm frame of [warmth]. A sibling of the screen at the
 * root of the content, so no screen changes. It has no semantics and takes no input.
 */
@Composable
internal fun GpuWarmthTicker(warmth: GpuWarmth) {
    LaunchedEffect(warmth) { warmth.run() }
    Box(
        // One device pixel, not a size from the theme: it only has to give the RenderThread something to draw.
        // Its own layer, so a warm frame records this pixel again and nothing else; a plain layer carries no semantics node.
        Modifier.layout { measurable, _ ->
            val pixel = measurable.measure(Constraints.fixed(1, 1))
            layout(1, 1) { pixel.place(0, 0) }
        }.plainLayer {}.drawBehind {
            // Read while drawing: a warm frame records this layer again, so the RenderThread has a frame to draw.
            warmth.tick
            warmth.draws++
            drawRect(Color.Transparent)
        },
    )
}
