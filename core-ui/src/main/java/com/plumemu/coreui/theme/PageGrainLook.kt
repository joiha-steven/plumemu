package com.plumemu.coreui.theme

import androidx.compose.runtime.compositionLocalOf
import com.plumemu.coreui.components.PageGrain

/** Numbers of the page grain (the same noise recipe as the missing-art card, `PosterPalette.BOX_GRAIN`), drawn once into a small repeating tile. */
object PageGrainLook {
    /** Side of the tile in pixels: one tile per page colour and strength, repeated over the page. */
    const val TILE_SIDE = 256

    /** Peak offset (each side, per colour channel) of the Light and Strong grain on a dark page. */
    const val LIGHT_AMPLITUDE = 4
    const val STRONG_AMPLITUDE = 8

    /** A light page shows the same noise only with more of it. */
    const val LIGHT_PAGE_SCALE = 1.5f

    /** The tile's noise is the same every run. */
    const val SEED = 20261114

    /** Relative luminance above which a page counts as light. */
    const val LIGHT_PAGE_LUMINANCE = 0.5f
}

/** The grain strength the screens below paint their page with; Off unless a host provides one. */
val LocalPageGrain = compositionLocalOf { PageGrain.Off }
