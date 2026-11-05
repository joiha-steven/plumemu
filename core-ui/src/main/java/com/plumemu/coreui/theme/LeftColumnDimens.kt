package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** Where the left columns' (Home systems, Settings sections) slab, accent bar and labels stand. */
object LeftColumnDimens {
    /** The slab starts this far before the page margin ([PlumDimens.stripMarginSide]): 16 dp from the screen edge. */
    val systemPillOverhang = 8.dp
    val systemPillStart = PlumDimens.stripMarginSide - systemPillOverhang

    /** The systems label sits this far inside the slab (it starts at the margin plus 8). */
    val systemLabelInset = 16.dp
    val systemLabelStart = systemPillStart + systemLabelInset

    /** The systems accent bar stands in the overhang, 3 dp from the slab's edge and rounded ends, about 9 dp before the label. */
    val systemBarInset = 3.dp

    /** A Settings section's label starts after its bar as a game row's title does. */
    val sectionLabelStart = systemPillStart + PlumDimens.gameRowPadding

    /** The middle of a section's accent bar; the open unfocused section's dot is centred here. */
    val sectionBarCentre = systemPillStart + SlabDimens.barInset + SlabDimens.barWidth / 2

    /** How far a column's longest label stays clear of the column's right edge (the divider). */
    val systemLabelEndClear = 8.dp
}
