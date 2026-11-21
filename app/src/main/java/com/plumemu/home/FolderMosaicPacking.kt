package com.plumemu.home

import androidx.compose.runtime.Immutable

/** The folder card's row of box fronts, in dp: boxes left to right (collar outer edges) and the row's outer height. */
@Immutable
internal data class MosaicRow(
    val boxes: List<PackedBox>,
    val height: Float,
)

/**
 * The folder card's box fronts as ONE justified row, as the home grid justifies mixed shapes: the most covers (4, 3, then 2) whose
 * row, spanning [width] exactly at one picture height with one [gap] between covers (the grid's `justifiedPictureHeight`), has a
 * picture at least [minPicture] tall (64 dp for four, 60 for three, any for two) and an outer height within [height] (what the text
 * leaves). Only when two cannot fit, one cover at its own shape stands left-aligned, capped to [height]. A row is never scaled down to leave a gap on the right.
 * [chromeX] and [chromeY] are the collar's padding in total, as in [packRows]. Null: no cover or no room.
 */
internal fun mosaicRow(
    aspects: List<Float>,
    width: Float,
    height: Float,
    gap: Float,
    chromeX: Float,
    chromeY: Float,
): MosaicRow? {
    if (aspects.isEmpty() || width <= 0f || height <= chromeY) return null
    val shapes = aspects.map { raw -> raw.takeIf { it.isFinite() && it > 0f } ?: 1f }
    for (n in minOf(MOSAIC_MOST, shapes.size) downTo 2) {
        val picture = justifiedPictureHeight(shapes.take(n).sumOf { it.toDouble() }, n, gap, width, chromeX).toFloat()
        if (!picture.isFinite() || picture < minPicture(n) || picture + chromeY > height) continue
        var x = 0f
        val boxes =
            List(n) { i ->
                val whole = if (i == n - 1) width - x else picture * shapes[i] + chromeX
                PackedBox(x, whole).also { x += whole + gap }
            }
        return MosaicRow(boxes, picture + chromeY)
    }
    val picture = minOf((width - chromeX) / shapes[0], height - chromeY)
    if (picture <= 0f) return null
    return MosaicRow(listOf(PackedBox(0f, picture * shapes[0] + chromeX)), picture + chromeY)
}

/** The most covers in the row. */
private const val MOSAIC_MOST = 4

/** The shortest picture (dp) a row of [n] covers may have; below it fewer covers are shown, each larger. */
private fun minPicture(n: Int): Float =
    when (n) {
        4 -> 64f
        3 -> 60f
        else -> 0f
    }
