package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * How far the app's depth goes, the one place to turn it (docs/design/rules.md > Depth): the boards' lips, highlights, insets and
 * thicknesses are drawn at [RAISE], their soft shadows at [SHADOW] and glows at [GLOW] of their strength and [SHADOW_REACH] or [GLOW_REACH] of their reach,
 * a collar's lip at [LIP] of its width.
 */
object Depth {
    const val RAISE = 0.6f

    /** A cap's solid thickness and how far it sinks: glass is thin. */
    const val THICKNESS = 0.25f

    /** How wide the ring around a part is, its lip: a wide one reads as a thick dark frame on a dark theme. */
    const val LIP = 0.7f
    const val SHADOW = 0.4f

    /** A glow marks the focus: it keeps more strength than a shadow. */
    const val GLOW = 0.6f
    const val SHADOW_REACH = 0.25f
    const val GLOW_REACH = 0.6f

    /** A box cover's frame as glass: its lightness above the page, top to bottom (CIE L*), the fine edge that parts it from the page, dark and light themes, and its lit crest. */
    const val COVER_FRAME_TOP_L = 16.0
    const val COVER_FRAME_MID_L = 11.0
    const val COVER_FRAME_BOTTOM_L = 8.0
    const val COVER_FRAME_EDGE_DARK = 0.34f
    const val COVER_FRAME_EDGE_LIGHT = 0.30f
    const val COVER_FRAME_CREST = 0.45f
}

/**
 * The scale of a focused grid cover whose longer side is [longest] dp: a very large cover grows less, so its ring stays in the grid's
 * gaps and its neighbours whole.
 */
fun focusedCoverScale(longest: Float): Float {
    val room = PlumDimens.gridGapDown - PlumDimens.focusRingOutset - PlumDimens.focusRingKeyline
    return minOf(PlumDimens.FOCUSED_COVER_SCALE, 1f + 2f * room.value / longest.coerceAtLeast(1f))
}
