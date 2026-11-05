package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** The Apps grid's fixed sizes (one size: Settings › Grid size and L1 / R1 never change it). */
object AppsGridDimens {
    /**
     * An app's icon in the Apps grid: 56, the size Android's own launchers draw on a phone, small enough
     * that 7 tiles fit across the RP5's 664 dp of grid at Medium text ([appsGridTileMinWidth]).
     */
    val appsGridIcon = 56.dp

    /** An app game's icon in the details column and on the second screen's card (it has no cover; Apps has no details column). */
    val appGameDetailsIcon = 96.dp

    /**
     * The narrowest Apps tile at Medium text: the icon and 12 on each side, wide enough for a 9-letter name
     * ("Calculator") on one line. It grows with the text size (`PlumLayout.textScale`), so a larger text
     * gets fewer, wider tiles: 7 / 5 / 8 columns at 768 / 576 / 837 dp at Medium (7 on the Thor at its
     * default Large), 5 / 3 / 5 at Largest.
     */
    val appsGridTileMinWidth = 80.dp

    /** Inside an Apps tile, above the icon and below the name: the focus ring stands this far clear of both. */
    val appsGridTilePadding = 6.dp

    /** Between an app's icon and its name. */
    val appsGridNameGap = 4.dp
}
