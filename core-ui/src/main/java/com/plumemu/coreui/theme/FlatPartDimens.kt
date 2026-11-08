package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * The sizes and strengths only the flat parts (a button, the switch) have (docs/design/rules.md > Flat parts). The focus rim they wear is
 * [FocusLift]'s: width, colour, alpha and glow sigma are not repeated here.
 */
object FlatPartDimens {
    /** The hairline of a flat part (one pixel at least). */
    val flatHairline = 0.5.dp

    /** How far a flat part's crisp drop shadow (a button on a light page, a thumb) sits under it. */
    val flatDrop = 1.dp

    /** The off switch's outline, drawn inside the track's bounds, and how much smaller the off thumb is than the on one (so it clears the outline). */
    val switchOffOutline = 1.5.dp
    val switchOffKnobShrink = 3.dp

    // Parts are small and sit close together, so their rim's light and shadow are shorter and weaker than a cover's ([FocusLift]).
    /** The depth shadow under a rimmed part: how far it drops and its blur (a cover's drops 4 dp and blurs 4.5 dp sigma). */
    val shadowDrop = 2.dp
    val shadowBlur = 4.dp

    /** The glow's alpha (a cover's is 0.30 / 0.22), and the depth shadow's (black on dark pages, brown on light ones). */
    const val GLOW_ALPHA_DARK = 0.22f
    const val GLOW_ALPHA_LIGHT = 0.16f
    const val SHADOW_ALPHA_DARK = 0.45f
    const val SHADOW_ALPHA_LIGHT = 0.24f

    /** Room a panel leaves above and below a button so the rim, its glow and its shadow are never cut by the panel's scroll clip (a Gaussian's tail is 3 sigma). */
    val focusRimRoom = 12.dp
}
