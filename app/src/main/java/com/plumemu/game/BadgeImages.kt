package com.plumemu.game

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The achievement badges Info draws (`content/CoilBadgeImages`): [cached] answers at once from
 * memory, [load] downloads and decodes at [sizePx] (null when it cannot: the caller draws a
 * placeholder). [None] is for screens with no network and for tests.
 */
internal interface BadgeImages {
    fun cached(url: String, sizePx: Int): ImageBitmap?

    suspend fun load(url: String, sizePx: Int): ImageBitmap?

    /**
     * [load] from the disk cache only: never the network, so a card that follows the cursor may ask. Null when the picture was never
     * downloaded (the caller draws its empty badge).
     */
    suspend fun loadCached(url: String, sizePx: Int): ImageBitmap? = null

    data object None : BadgeImages {
        override fun cached(url: String, sizePx: Int): ImageBitmap? = null

        override suspend fun load(url: String, sizePx: Int): ImageBitmap? = null
    }
}

/** Provided by `MainScreen`; [BadgeImages.None] anywhere else, so a badge is a placeholder. */
internal val LocalBadgeImages = staticCompositionLocalOf<BadgeImages> { BadgeImages.None }
