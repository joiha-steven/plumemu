package com.plumemu.coreui.components

import android.graphics.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * One paint per thread for every piece of every draw (a paint per call per frame was garbage on the UI thread): a draw sets its alpha and blend mode,
 * so the main thread and the image-build thread (which draws shadow layers into cached images) must not share one.
 */
private val slicePaints =
    object : ThreadLocal<Paint>() {
        // Not ThreadLocal.withInitial: it needs API 26 and the app runs on 23.
        override fun initialValue() =
            Paint().apply {
                isAntiAlias = false
                filterQuality = FilterQuality.Low
                // A scaled bitmap draw dithers by default; on a flat stretched piece that is row-to-row noise (the soft shadows are drawn without it).
                asFrameworkPaint().isDither = false
            }
    }

/**
 * Draws [image] into [dstSize] at [dst] as [StretchedPixels.expand] would have made it: along an axis whose keep ([keepX], [keepY]) is
 * 0 or more and whose drawn length is not the image's, the first and last keep pixels are drawn as they are (each [scale] page px)
 * and the pixel after the first ones fills the middle; along another axis the whole image is scaled. Up to nine draws of one small
 * image instead of one big image per box size.
 */
internal fun DrawScope.drawSliced(
    image: ImageBitmap,
    keepX: Int,
    keepY: Int,
    scale: Int,
    dst: IntOffset,
    dstSize: IntSize,
    alpha: Float = 1f,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    val xs = pieces(image.width, keepX, scale, dstSize.width)
    val ys = pieces(image.height, keepY, scale, dstSize.height)
    // Pieces without edge smoothing: on a canvas moved by part of a pixel each pixel then comes from one piece only, as from the whole image.
    val slicePaint = slicePaints.get()!!
    slicePaint.alpha = alpha
    slicePaint.blendMode = blendMode
    val bitmap = image.asAndroidBitmap()
    val src = Rect()
    val to = Rect()
    val canvas = drawContext.canvas.nativeCanvas
    for (y in ys.indices step 4) {
        for (x in xs.indices step 4) {
            src.set(xs[x], ys[y], xs[x] + xs[x + 1], ys[y] + ys[y + 1])
            to.set(dst.x + xs[x + 2], dst.y + ys[y + 2], dst.x + xs[x + 2] + xs[x + 3], dst.y + ys[y + 2] + ys[y + 3])
            canvas.drawBitmap(bitmap, src, to, slicePaint.asFrameworkPaint())
        }
    }
}

/** The pieces of one axis as (source start, source length, drawn start, drawn length) fours, empty ones left out. */
private fun pieces(
    length: Int,
    keep: Int,
    scale: Int,
    drawn: Int,
): IntArray {
    val corner = keep * scale
    if (keep < 0 || drawn == length * scale || length < 2 * keep + 1 || drawn < 2 * corner) return intArrayOf(0, length, 0, drawn)
    val middle = drawn - 2 * corner
    val out = ArrayList<Int>(12)
    if (keep > 0) out += listOf(0, keep, 0, corner)
    if (middle > 0) out += listOf(keep, 1, corner, middle)
    if (keep > 0) out += listOf(length - keep, keep, drawn - corner, corner)
    return out.toIntArray()
}
