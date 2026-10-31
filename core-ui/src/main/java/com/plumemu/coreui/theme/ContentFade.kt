package com.plumemu.coreui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * New content fades in where other content stood (design/rules.md › Shapes and motion): alpha
 * 0 → 1 over [PlumMotion.FADE_IN_MS] ease-out, `graphicsLayer` only. A change while a fade is
 * still running lets it run on (a held key does not strobe); the first content shown does not fade.
 * With motion off nothing fades.
 */
@Stable
class ContentFade internal constructor(
    private val settle: Settle,
    private var shown: Any?,
) {
    /** The alpha to draw the content with. */
    val alpha: Float
        get() = 1f - settle.value

    /**
     * Alpha only, applied to each drawing rather than through an offscreen buffer: cheaper on
     * weak GPUs, and nothing in the faded content overlaps in a way the difference would show.
     */
    internal val layer: Modifier =
        Modifier.graphicsLayer {
            alpha = 1f - settle.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }

    internal fun show(
        key: Any?,
        isOn: Boolean,
        fadeIn: () -> Boolean,
    ) {
        if (key == shown) return
        shown = key
        when {
            !isOn -> settle.stop()
            // A swap that should not fade cuts in, and ends a fade still running.
            !fadeIn() -> settle.stop()
            !settle.isSettling -> settle.start(1f, PlumMotion.FADE_IN_MS * NANOS_PER_MS, PlumMotion.EaseOut)
        }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
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
    fade.show(key, PlumTheme.motion.isOn, fadeIn)
    return fade
}

/** Draws this content with [fade]'s alpha. */
fun Modifier.fadingIn(fade: ContentFade): Modifier = then(fade.layer)
