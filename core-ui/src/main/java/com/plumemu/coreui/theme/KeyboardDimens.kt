package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** Fixed sizes of the on-screen keyboard, in dp (design/rules.md > Keyboard). Width-dependent ones are in [PlumLayout]. */
object KeyboardDimens {
    // 10 columns of 44, centred, 4 rows of 36.
    /** A key's height, the same at every text size (labels grow, keys do not); [PlumLayout.keyboardKeyHeight] holds the half-screen cap. */
    val keyboardKeyHeight = 36.dp

    /** A key's width on a screen wide enough; narrower screens shrink the block to `width − 2 × 24`. */
    val keyboardColumnWidth = 44.dp

    /** Between keys, across and down; half of it on each side belongs to a key's touch area. */
    val keyboardKeyGap = 4.dp

    /** The tray above the first row of keys. */
    val keyboardTopPadding = 8.dp

    /** Between the last row of keys and the hint bar the caller draws on the tray. */
    val keyboardHintGap = 6.dp

    /** The accent ring drawn just outside the focused key (no gap, no glow: the 4 dp gaps have no room for more). */
    val keyboardFocusRing = 2.dp

    /** The focused action key's second ring (`onAccent`, as wide as the focus ring): this far inside its accent fill. */
    val keyboardActionRingInset = 3.dp

    /** The tray's top hairline. */
    val keyboardEdge = 1.dp

    /** The tray's upward shadow, the board's `box-shadow: 0 -6px 12px -4px`: offset, blur, spread (inwards). */
    val keyboardShadowOffset = 6.dp
    val keyboardShadowBlur = 12.dp
    val keyboardShadowSpread = 4.dp

    /** Shift, delete and hide icons at Medium ([PlumLayout.keyboardIconSize] grows them with the text). */
    val keyboardIconSize = 18.dp

    /** Locked shift: the bar under the arrow (thickness; the arrow's stroke is the same). */
    val keyboardIconStroke = 1.8.dp

    const val KEYBOARD_COLUMNS = 10
    const val KEYBOARD_ROWS = 4
}
