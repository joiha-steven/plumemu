package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** The empty state (text and buttons, no mark): the gaps between its parts and the body's width. */
object EmptyStateDimens {
    /** Between the title, the body and the buttons, and between the two buttons. */
    val gap = 11.dp

    /**
     * The body's widest line: the board's 420 px (280 dp) for its 19 px (12.67 dp) text, grown with the ramp's 13 so the board's
     * line breaks stay (280 x 13 / 12.67).
     */
    val bodyWidth = 287.4.dp
}
