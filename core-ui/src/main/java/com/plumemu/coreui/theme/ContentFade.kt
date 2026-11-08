package com.plumemu.coreui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.plainLayer

/**
 * New content fades in where other content stood (design/rules.md › Shapes and motion): alpha
 * 0 → 1 over [PlumMotion.FADE_IN_MS] on [PlumMotion.Decelerate], `graphicsLayer` only. A change while a fade is
 * still running lets it run on (a held key does not strobe); the first content shown does not fade.
 * With motion off nothing fades.
 */
@Stable
class ContentFade internal constructor(
    private val settle: Settle,
    private var shown: Any?,
    /** Px the new content starts from its place on the side its step came from; 0: a plain fade. */
    private val shiftPx: Float = 0f,
) {
    /** The step the content now shown came with. Plain state of the fade: read only while drawing. */
    private var step = SwapStep.None

    /** The alpha to draw the content with. */
    val alpha: Float
        get() = 1f - settle.value

    /**
     * Alpha only, applied to each drawing rather than through an offscreen buffer: cheaper on
     * weak GPUs, and nothing in the faded content overlaps in a way the difference would show.
     */
    internal val layer: Modifier =
        Modifier.plainLayer {
            val left = settle.value
            alpha = 1f - left
            if (left != 0f) {
                translationX = step.x * shiftPx * left
                translationY = step.y * shiftPx * left
                if (step.depth != 0) {
                    val scale = 1f + step.depth * PlumMotion.SCREEN_DEPTH * left
                    scaleX = scale
                    scaleY = scale
                }
            }
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }

    /** [key] is now shown; [next] says how it came (asked only when [key] changed). */
    internal fun show(
        key: Any?,
        isOn: Boolean,
        next: () -> SwapStep?,
        fadeIn: () -> Boolean,
    ) {
        if (key == shown) return
        shown = key
        val came = next()
        when {
            !isOn -> settle.stop()
            // A swap that should not fade cuts in, and ends a fade still running.
            !fadeIn() -> settle.stop()
            // A screen-like swap settles with depth over a screen's time.
            came != null && came.depth != 0 -> {
                step = came
                settle.start(1f, PlumMotion.SCREEN_MS * NANOS_PER_MS, PlumMotion.Decelerate)
            }
            // A sliding swap restarts on every step, so a held key keeps the content moving the way it goes.
            came != null -> {
                step = came
                settle.start(1f, PlumMotion.GLIDE_MS * NANOS_PER_MS, PlumMotion.Decelerate)
            }
            !settle.isSettling -> settle.start(1f, PlumMotion.FADE_IN_MS * NANOS_PER_MS, PlumMotion.Decelerate)
        }
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L

        /**
         * -1, 0 or 1: the side a step of [step] places came from. In a list of [wrapsAt] places that wraps around (the
         * system column), the shorter way round is the step taken: the last system's next one is the first, from below.
         */
        fun sideOf(
            step: Int,
            wrapsAt: Int,
        ): Float {
            var d = step
            if (wrapsAt > 1) {
                if (d > wrapsAt / 2) d -= wrapsAt
                if (d < -wrapsAt / 2) d += wrapsAt
            }
            return d.coerceIn(-1, 1).toFloat()
        }
    }
}

/**
 * A [ContentFade] that fades in whenever [key] changes after the first composition and [fadeIn]
 * says so; [fadeIn] is asked only when the key changes, while composing (read state in it without
 * observing, or the caller recomposes on every change of that state).
 */
@Composable
fun rememberContentFade(
    key: Any?,
    fadeIn: () -> Boolean = { true },
): ContentFade {
    val scope = rememberCoroutineScope()
    val fade = remember(scope) { ContentFade(Settle(scope), key) }
    fade.show(key, PlumTheme.motion.isOn, { null }, fadeIn)
    return fade
}

/**
 * A [ContentFade] whose new content also slides in by [PlumMotion.SWAP_SHIFT_DP] from the side it came from: from below
 * (or the right, [vertical] false) when [order] grew, from above when it shrank. [order] is read, without observing, only
 * when [key] changes. Over [PlumMotion.GLIDE_MS] on [PlumMotion.Decelerate]; with motion off nothing moves.
 */
@Composable
fun rememberSlidingSwap(
    key: Any?,
    order: () -> Int,
    vertical: Boolean = true,
    /** The number of places when the order wraps around (the last one's next is the first); 0 when it does not. */
    wrapsAt: Int = 0,
    fadeIn: () -> Boolean = { true },
): ContentFade {
    val shown = remember { IntArray(1).also { it[0] = Snapshot.withoutReadObservation(order) } }
    return rememberSteppedSwap(key, fadeIn = fadeIn) {
        val now = Snapshot.withoutReadObservation(order)
        val side = ContentFade.sideOf(now - shown[0], wrapsAt).toInt()
        shown[0] = now
        if (vertical) SwapStep(y = side) else SwapStep(x = side)
    }
}

/**
 * How new content comes in a [rememberSteppedSwap]: from the side [x] and [y] say (-1, 0 or 1 each: from the left or
 * above, none, from the right or below), sliding [PlumMotion.SWAP_SHIFT_DP] over [PlumMotion.GLIDE_MS]; or, with a
 * [depth], settling like a screen over [PlumMotion.SCREEN_MS] from [PlumMotion.SCREEN_DEPTH] smaller (-1, going in) or
 * larger (1, coming back). Always on [PlumMotion.Decelerate], fading in.
 */
@Immutable
data class SwapStep(
    val x: Int = 0,
    val y: Int = 0,
    val depth: Int = 0,
) {
    companion object {
        val None = SwapStep()
    }
}

/**
 * A [ContentFade] whose new content comes as [step] says each time [key] changes (asked then, while composing: read state
 * in it without observing). With motion off nothing moves.
 */
@Composable
fun rememberSteppedSwap(
    key: Any?,
    fadeIn: () -> Boolean = { true },
    step: () -> SwapStep,
): ContentFade {
    val scope = rememberCoroutineScope()
    val shift = with(LocalDensity.current) { PlumMotion.SWAP_SHIFT_DP.dp.toPx() }
    val fade = remember(scope, shift) { ContentFade(Settle(scope), key, shift) }
    fade.show(key, PlumTheme.motion.isOn, step, fadeIn)
    return fade
}

/** Draws this content with [fade]'s alpha. */
fun Modifier.fadingIn(fade: ContentFade): Modifier = then(fade.layer)
