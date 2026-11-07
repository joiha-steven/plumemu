package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** The Apps grid's fixed sizes (one size: L1 / R1 never change it). */
object AppsGridDimens {
    /**
     * An app's icon in the Apps grid: 56, the size Android's own launchers draw on a phone, small enough
     * that 7 tiles fit across the RP5's 664 dp of grid at Medium text ([appsGridTileMinWidth]).
     */
    val appsGridIcon = 56.dp

    /**
     * The icon of a tile in the Apps grid (the second screen's cards keep [appsGridIcon]). A tile is never narrower
     * than 72 at any column count, because the columns are counted at 80 + 12 and drawn with a gap of 20.
     */
    val appsGridTileIcon = 72.dp

    /** Between two rows of tiles; the gap across stays the cover grid's. */
    val appsGridRowGap = 2.dp

    /** The focus mark's corner over the tile icon's side: most launcher masks are close to this. */
    const val APPS_ICON_CORNER_SHARE = 0.23f

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

    /** Between an app's icon and its name: the focus ring (5.5 outside the icon) stands clear of the name. */
    val appsGridNameGap = 8.dp
}
