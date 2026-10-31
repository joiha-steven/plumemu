package com.plumemu.scraper.media

import com.plumemu.scraper.MediaKind

/**
 * How a box front is asked for.
 *
 * Pictures arrive at about 130 KB/s on a typical account, so bytes are time. Measured live on
 * US box fronts: a PNG at height 1080 is 169-1053 KB, at 520 it is 133-583 KB, a JPEG at 520 is
 * 35-62 KB. No transparent pixel was found in 25 box fronts over 18 systems, so JPEG is safe for
 * them. The largest draw is about 506 px tall (the second-screen card), so 720 serves the grid,
 * the details column and the bottom screen from one download. The discs and cartridges
 * ([MediaKind.Support]) are round or transparent and stay PNG at the saved sizes.
 */
internal object BoxFrontRequest {
    /** The tallest box front asked for; [com.plumemu.scraper.work.RunPictures] caps the saved sizes' height here. */
    const val MAX_HEIGHT = 720

    /** The `outputformat` value that turns a scaled picture into a JPEG. */
    const val JPEG_FORMAT = "jpg"

    /** A scaled box front (the run, a pick, a thumbnail) comes as a JPEG; a full-size one is left as stored. */
    fun asksJpeg(kind: MediaKind, scaled: Boolean): Boolean = scaled && kind == MediaKind.BoxFront
}
