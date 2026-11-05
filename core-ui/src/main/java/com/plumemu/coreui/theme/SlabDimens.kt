package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** Sizes of the focused row's slab ([FocusSlab]); the board's px / 1.5 unless said. */
object SlabDimens {
    /** The accent bar: 26 px tall at most (the share of a shorter row's height below), 6 px wide; 13 px (8.67 dp) in from the slab's left edge; the row's text starts 23.33 dp in (`PlumDimens.gameRowPadding`). */
    val barHeight = 17.33.dp

    /** The board's distance from a card's edge to the focused slab's right and bottom edges (22 px padding plus the 1 px border). */
    val cardGap = 15.33.dp
    const val BAR_HEIGHT_SHARE = 0.46f
    val barWidth = 4.dp
    val barInset = 8.67.dp

    /** The slab's drawn height (the board's 70 px); a row shorter than this is overhung by half the difference above and below, so no row's layout moves. */
    val height = 46.67.dp

    /** The most a slab is drawn past a row, above and below: over the shortest row (`PlumDimens.gameRowHeight`, which grows with the text size). A list keeps this much room at its ends so it never cuts the slab. */
    val maxOverhang = (height - PlumDimens.gameRowHeight) / 2

    /** The bar's glow, the boards' `0 0 12px glowColor`: a Gaussian of sigma 12 / 2 px. */
    val barGlowSigma = 4.dp

    /** The border, 1 board px, drawn inside the outline. */
    val edge = 0.667.dp

    /**
     * The slab's glow `0 0 glowBlur glowColor`, glowBlur 22 px at the boards' glow 0.3: sigma 7 dp, Chrome's render of the board (the CSS Gaussian is
     * 22 / 2 px = 7.33 dp; fitted with the dark far drop, `FocusSlab.FAR_DARK_RENDERED`). The drops are [SlabLook]'s.
     */
    val glowSigma = 7.dp

    /** How far a list's side clip lets the glow reach past the slab: where it is under 2 of 255. */
    val glowReach = 13.dp

    /** What a list keeps past its first and last row: the overhang and the glow around it, so a focused end row is never cut flat by the list's clip. */
    val listEndRoom = maxOverhang + glowReach

    /** The last stretch of the shadow and glow before their clip fades to the page colour, so the clip leaves no line. */
    val tailFade = 12.dp

    /**
     * The light board's blue glow under the focused slab, a 2D Gaussian blob (fitted to the Paper board's 768 list: peak 0.5, sigma 120 dp wide on the 421 dp slab,
     * kept as a share of the slab width, 60 dp tall, centred 18.67 dp below the slab's centre).
     */
    val blobDrop = 18.667.dp
    val blobSigmaY = 60.dp
    const val BLOB_SIGMA_X_SHARE = 0.285f

    /** How far the blob shows past the slab's sides (the clip leaves this room): about 2.3 sigmas of a 421 dp slab's, where it is under 2 of 255. */
    val blobReach = 80.dp
}
