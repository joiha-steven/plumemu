package com.plumemu.coreui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/** Motion tokens (design/rules.md › Shapes and motion). */
object PlumMotion {
    const val DURATION_MS: Int = 180

    /**
     * A list catching up with one row's jump (design/rules.md › Shapes and motion;
     * replaces 110 ms ease-out), on [Decelerate]: most of the way early, a soft landing. A held
     * key's next step instead glides linearly at the speed of the repeats (see [GLIDE_HELD_MIN_MS]),
     * so the rows move evenly and never stop between repeats.
     */
    const val GLIDE_MS: Int = 200

    /** The shortest a held step's glide gets: the fastest repeat is 32 ms (2 frames at 60 Hz), and a glide longer than the gap would leave rows undrawn and be cut by the one-row cap. */
    const val GLIDE_HELD_MIN_MS: Int = 32

    /** The focused cover growing to its larger size on arrival, and back on leave (ease-out, scale only). */
    const val FOCUS_GROW_MS: Int = 150

    /** The light band crossing the focused cover once, and how long focus must rest on the cover before it starts. */
    const val FOCUS_SWEEP_MS: Int = 400
    const val FOCUS_SWEEP_REST_MS: Int = 120

    /** New content fading in where old content stood (a list swap, a panel's sub-screen, search). */
    const val FADE_IN_MS: Int = 110

    /** A press shorter than this still shows the whole dip (never with motion off: the release is then at once). */
    const val PRESS_MIN_VISIBLE_MS: Long = 100

    /** A pressed key cap or hint shrinks to [HINT_PRESS_SCALE]; its dip goes in over [PRESS_IN_MS] and out over [PRESS_OUT_MS], ease-out. */
    const val HINT_PRESS_SCALE: Float = 0.92f
    const val PRESS_IN_MS: Int = 60
    const val PRESS_OUT_MS: Int = 100

    /** A pressed item shrinks to this (design/rules.md > Shapes and motion > Pressed). */
    const val PRESS_SCALE: Float = 0.97f

    /** A pressed focus fill moves this far toward the text colour (lighter on Graphite, darker on Paper). */
    const val PRESS_FILL_MIX: Float = 0.14f

    /** The favourite star's pop starts at this scale and grows to 1. */
    const val STAR_START_SCALE: Float = 0.6f

    /** The scan indicator's breathing: one full cycle (dim, bright, dim), alpha only. */
    const val SCAN_PULSE_CYCLE_MS: Int = 1600

    /** The scan indicator at its dimmest; its brightest is 1. */
    const val SCAN_PULSE_MIN_ALPHA: Float = 0.35f

    /** CSS `ease-in-out`, for the slow breathing of the scan indicator. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** CSS `ease-out`, the curve the board animates with. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** Ease-out cubic (CSS `cubic-bezier(.33, 1, .68, 1)`): fast start, soft landing; the list glide's curve. */
    val Decelerate: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    /**
     * The one place that decides whether anything moves: off when the system animator duration
     * scale is 0 (Developer options or accessibility "Remove animations") or the app's
     * Animations setting is off.
     */
    fun isMotionOn(
        systemAnimatorScale: Float,
        animationsSetting: Boolean,
    ): Boolean = animationsSetting && systemAnimatorScale > 0f
}

/** Whether motion is on for this composition; read via `PlumTheme.motion`. */
@Immutable
data class PlumMotionState(
    val isOn: Boolean,
) {
    /** The strip's slide: 180 ms ease-out, or an instant jump when motion is off. */
    fun <T> slideSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.DURATION_MS, easing = PlumMotion.EaseOut) else snap()
}
