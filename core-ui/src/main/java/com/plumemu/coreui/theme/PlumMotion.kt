package com.plumemu.coreui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/** Motion tokens (design/rules.md › Shapes and motion). */
object PlumMotion {
    /** The strip, a game panel and the keyboard tray sliding, the segmented control's cap, a switch's knob ([PlumMotionState.slideSpec]). */
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

    /** The focused cover growing to its larger size and its rim fading in on arrival (on [Decelerate]). */
    const val FOCUS_GROW_MS: Int = 150

    /** The cover the focus left shrinking back and its rim fading out (on [Decelerate]); the rims cross-fade, none travels. */
    const val FOCUS_LEAVE_MS: Int = 150

    /** New content fading in where old content stood (a list swap, a panel's sub-screen, search). */
    const val FADE_IN_MS: Int = 110

    /**
     * A whole screen arriving (Settings opening or closing, the home coming back from a game): the
     * incoming screen settles from [SCREEN_DEPTH] nearer or further to its place while it fades in,
     * on [Decelerate]. Only the incoming screen moves; the one it replaces is already gone.
     */
    const val SCREEN_MS: Int = 240

    /** How far a screen starts from its place, as a share of its size: deeper (smaller) going in, nearer (larger) coming back. */
    const val SCREEN_DEPTH: Float = 0.04f

    /** The home growing into the focused cover or row as a game or app starts, while it fades into the background. */
    const val LAUNCH_DEPTH: Float = 0.08f

    /**
     * How long the home moves into the item before the game's window is asked for: only until its motion is visibly under
     * way (a quarter of [SCREEN_MS]); the rest runs while the window opens, and where Android honours it the window's own
     * growth from the item carries the motion. Every ms here is a ms later to the game.
     */
    const val LAUNCH_LEAD_MS: Long = 60

    /** How far the home fades as a game starts: to a dim, never to black, so the hand-over to the game's window never flashes. */
    const val LAUNCH_DIM: Float = 0.45f

    /**
     * Another system's games swapping in (L2/R2): they fade in while sliding [SWAP_SHIFT_DP] from the side
     * the step came from, over [GLIDE_MS] on [Decelerate], so the swap reads as one more step of the column.
     */
    const val SWAP_SHIFT_DP: Float = 16f

    /** A press shorter than this still shows the whole dip (never with motion off: the release is then at once). */
    const val PRESS_MIN_VISIBLE_MS: Long = 100

    /** A pressed row, cell or hint goes down over [PRESS_IN_MS] and comes back up over [PRESS_OUT_MS], ease-out. */
    const val PRESS_IN_MS: Int = 60
    const val PRESS_OUT_MS: Int = 100

    /** A flat-face key, switch knob or slider knob comes back up over this long (slower: the edge has to grow back). */
    const val KEY_PRESS_OUT_MS: Int = 140

    /** A focused or hovered key rises (and falls back) over this long, ease-out. */
    const val RAISE_MS: Int = 100

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

    /** CSS `ease-out`: presses and raises only (a key's own dip, not something travelling across the screen). */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /**
     * Ease-out cubic (CSS `cubic-bezier(.33, 1, .68, 1)`): fast start, soft landing. The one curve of everything that moves or
     * appears: glides, slides, swaps, screens, fades and the focus growing, so two things moving together never part ways.
     */
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
    /** The strip's, a panel's and the tray's slide: 180 ms on [PlumMotion.Decelerate], or an instant jump when motion is off. */
    fun <T> slideSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.DURATION_MS, easing = PlumMotion.Decelerate) else snap()
}
