package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** Sizes of the segmented control, the swatch and the one-of-several choice (the V2 board's, at 1x). */
object ChoiceDimens {
    /** A segment: its height (grows with text size), and the space either side of its label. */
    val segmentHeight = 29.33.dp
    val segmentPaddingX = (52f / 3f).dp

    /** The sunken tray around the segments. */
    val segmentTrayPad = 2.dp
    val segmentGap = (8f / 3f).dp

    /** A swatch: its side and corner. */
    val swatchSize = 26.67.dp
    val swatchCorner = (20f / 3f).dp

    /** The mark of a choice: its diameter, the gap to the label, the row's side padding and the gap between its two lines. */
    val choiceMark = 16.dp
    val choiceGap = (32f / 3f).dp
    val choicePaddingX = 12.dp
    val choiceLineGap = 1.dp

    /** The check on a swatch or a mark: stroke as a share of the box it is drawn in. */
    const val CHECK_STROKE = 0.08f
    const val CHECK_SIZE = 0.56f
}
