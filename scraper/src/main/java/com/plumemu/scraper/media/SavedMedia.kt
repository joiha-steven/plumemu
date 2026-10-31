package com.plumemu.scraper.media

import com.plumemu.scraper.MediaKind

/**
 * A named pixel size a picture is saved at, e.g. `MediaSize("grid", 240, 336)`. The picture is
 * scaled to fit inside it, keeping its aspect ratio, never enlarged. The host (the app) picks the
 * sizes from its layout; the engine has none of its own.
 *
 * @property name part of the file name: lowercase letters, digits and `_` only.
 */
public data class MediaSize(val name: String, val maxWidth: Int, val maxHeight: Int) {
    init {
        require(NAME.matches(name)) { "media size name must match ${NAME.pattern}: $name" }
        require(maxWidth > 0 && maxHeight > 0) { "media size must be positive: ${maxWidth}x$maxHeight" }
    }

    private companion object {
        val NAME = Regex("[a-z0-9_]+")
    }
}

/**
 * One saved picture: [path] is a WebP file of [width] x [height] pixels, at
 * `<mediaDir>/<ScreenScraper game id>/<kind>-<sizeName>-<maxWidth>x<maxHeight>.webp` (the size's
 * pixels in the name, so new pixels for the same name are saved again). Holds no URL and no
 * credential.
 */
public data class SavedMedia(
    val kind: MediaKind,
    val sizeName: String,
    val path: String,
    val width: Int,
    val height: Int,
)
