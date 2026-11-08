package com.plumemu.coreui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The focus of a grid cover or an Apps tile (docs/design/rules.md > Focus and current): the item grows a little about a pivot that keeps it
 * inside the grid, a 2 dp rim sits glued on its frame's edge, and a tight glow and a small depth shadow lie under it. No accent colour: the
 * rim is near-white on a dark page and the page's text ink on a light one. Every number of the look is here; the drawing is `FocusLiftDraw.kt`.
 */
object FocusLift {
    /** How much larger the focused item is drawn than at rest. */
    const val SCALE = 1.035f

    /** The rim: a stroke of this width outside the frame's edge, concentric with it. */
    val rimWidth = 2.dp

    /** The rim's alpha (its colour: [rimColor]). */
    const val RIM_ALPHA = 0.96f

    /** The tight glow round the rim: a Gaussian of this sigma, at [GLOW_ALPHA_DARK] / [GLOW_ALPHA_LIGHT]. */
    val glowSigma = 2.25.dp
    const val GLOW_ALPHA_DARK = 0.30f
    const val GLOW_ALPHA_LIGHT = 0.22f

    /** The small depth shadow under the lifted item: black, moved [shadowDrop] down, blurred by [shadowSigma]. */
    val shadowDrop = 4.dp
    val shadowSigma = 4.5.dp
    const val SHADOW_ALPHA_DARK = 0.45f
    const val SHADOW_ALPHA_LIGHT = 0.26f

    /** How far the rim and glow reach past the lifted frame (the glow counted to two sigmas): the room the scroll rules keep clear of the bars. */
    val reach = rimWidth + glowSigma * 2

    /** The share of an item's height by which a lift grows it past its centre pivot, up and down each. */
    const val GROWTH_SHARE = (SCALE - 1f) / 2f

    /** The item's scale at [amount] (0 at rest, 1 focused). */
    fun scaleOf(amount: Float): Float = 1f + (SCALE - 1f) * amount

    /** The rim's colour: near-white on a dark page, the page's text [ink] on a light one. */
    fun rimColor(
        dark: Boolean,
        ink: Color,
    ): Color = (if (dark) Color.White else ink).copy(alpha = RIM_ALPHA)

    /** The glow's colour (its alpha the peak). */
    fun glowColor(
        dark: Boolean,
        ink: Color,
    ): Color = (if (dark) Color.White else ink).copy(alpha = if (dark) GLOW_ALPHA_DARK else GLOW_ALPHA_LIGHT)

    /** The depth shadow's colour (its alpha the peak): black, lighter on a light page. */
    fun shadowColor(dark: Boolean): Color = Color.Black.copy(alpha = if (dark) SHADOW_ALPHA_DARK else SHADOW_ALPHA_LIGHT)

    /**
     * The point [frame] grows about so it never leaves the grid: its right edge in the last column ([atRight]), its left edge in the first
     * ([atLeft]), its top edge in the top row ([atTop]), else its centre. A lone column (both flags) keeps its centre.
     */
    fun pivot(
        frame: Rect,
        atLeft: Boolean,
        atRight: Boolean,
        atTop: Boolean,
    ): Offset {
        val x = if (atLeft == atRight) frame.center.x else if (atRight) frame.right else frame.left
        val y = if (atTop) frame.top else frame.center.y
        return Offset(x, y)
    }
}
