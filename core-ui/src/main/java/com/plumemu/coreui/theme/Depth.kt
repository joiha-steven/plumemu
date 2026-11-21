package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * How far the app's depth goes, the one place to turn it (docs/design/rules.md > Depth): the boards' lips, highlights, insets and
 * thicknesses are drawn at [RAISE], their soft shadows at [SHADOW] and glows at [GLOW] of their strength and [SHADOW_REACH] or [GLOW_REACH] of their reach,
 * a collar's lip at [LIP] of its width.
 */
object Depth {
    const val RAISE = 0.6f

    /**
     * A cap's solid thickness and how far it sinks. Was 0.25 (glass is thin); the board's whole thickness since faces went flat: with no gradient left to shade a cap, its solid base is what tells it is a button, 2 dp under a button's cap, and a press
     * drops the face onto it. A cover's hard drop keeps [COVER_THICKNESS].
     */
    const val THICKNESS = 1f

    /** A box cover's hard drop: covers kept their thin glass look. */
    const val COVER_THICKNESS = 0.25f

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

    /**
     * A resting cover's drop: a tight contact layer and a soft one under its glass frame, in the page's own shadow colour
     * (`SeatLook.shade`), already at their final strength and reach (not scaled by [SHADOW]). Drawn in the collar's cached image. Dark, then light alpha.
     */
    const val COVER_DROP_NEAR_DY = 1.0f
    const val COVER_DROP_NEAR_BLUR = 2.0f
    const val COVER_DROP_NEAR_DARK = 0.60f
    const val COVER_DROP_NEAR_LIGHT = 0.24f
    const val COVER_DROP_FAR_DY = 3.0f
    const val COVER_DROP_FAR_BLUR = 6.0f
    const val COVER_DROP_FAR_DARK = 0.42f
    const val COVER_DROP_FAR_LIGHT = 0.16f
}
