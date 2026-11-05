package com.plumemu.coreui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.plumemu.coreui.theme.CssCap
import com.plumemu.coreui.theme.CssFace
import com.plumemu.coreui.theme.CssShadow
import com.plumemu.coreui.theme.SeatLook
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A CSS box drawn layer by layer (design/rules.md › Seated parts: dp = board px / 1.5): its fill, then one cached bitmap holding
 * every `box-shadow` layer composited back to front, outer layers outside the box only and inset ones inside it only, each the exact Gaussian
 * of the rounded shape (blur B = sigma B / 2), page pixel for page pixel. No blur filter, no layer, one image per box size and recipe.
 * [alpha] fades the whole box (a state cross-fade).
 */
internal fun DrawScope.drawCssFace(
    face: CssFace,
    topLeft0: Offset,
    size0: Size,
    corner: Float,
    alpha: Float = 1f,
    fill: Brush? = null,
) {
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    // The fill takes the whole-pixel box its shadow image is made for, so the two meet without a seam of half-covered pixels.
    val topLeft = Offset(topLeft0.x.roundToInt().toFloat(), topLeft0.y.roundToInt().toFloat())
    val size = Size(size0.width.roundToInt().toFloat(), size0.height.roundToInt().toFloat())
    val r = min(corner, min(size.width, size.height) / 2)
    val brush = fill ?: if (face.stops.size > 1) Brush.verticalGradient(face.stops, startY = topLeft.y, endY = topLeft.y + size.height) else null
    if (brush != null) {
        drawRoundRect(brush, topLeft, size, CornerRadius(r), alpha = alpha)
    } else if (face.stops.isNotEmpty()) {
        drawRoundRect(face.stops[0], topLeft, size, CornerRadius(r), alpha = alpha)
    }
    drawCssShadows(face.shadows, topLeft, size, r, alpha)
}

/** Only the shadow layers of a box (its fill drawn by the caller, or none: a glow alone). */
internal fun DrawScope.drawCssShadows(
    shadows: List<CssShadow>,
    topLeft: Offset,
    size: Size,
    corner: Float,
    alpha: Float = 1f,
) {
    if (shadows.isEmpty() || alpha <= 0f) return
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    if (w <= 0 || h <= 0) return
    val layers = shadows.map { CssImages.Layer(it.color, it.y.toPx(), it.blur.toPx() / 2f, it.spread.toPx(), it.inset) }
    val shown = CssImages.shown(w, h, min(corner, min(w, h) / 2f), layers) ?: return
    with(shown) { drawIn(topLeft, w, h, alpha) }
}

/**
 * A [CssCap] in the box at [topLeft] of [size]: [press] 0 is the rest face, 1 the down face moved [CssCap.travel] lower (pressed or off);
 * between, the two cross-fade as the face moves. [moveFace] false leaves the move to the caller (it translates the whole key).
 */
internal fun DrawScope.drawCssCap(
    cap: CssCap,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    corner: Float = min(size.width, size.height) / 2,
    press: Float = 0f,
    moveFace: Boolean = true,
    restFill: Brush? = null,
) {
    val y = if (moveFace) cap.travel.toPx() * press else 0f
    val at = Offset(topLeft.x, topLeft.y + y)
    if (press < 1f) drawCssFace(cap.rest, at, size, corner, 1f - press, restFill)
    if (press > 0f) drawCssFace(cap.down, at, size, corner, press)
}

/**
 * The LIP of [seat] in the box at [topLeft] of [size]: `radial-gradient(ellipse 120% H at 50% 0%, ...)` (H = [SeatLook.lipEllipseHeight]) and
 * its six (Paper five) layers, on the whole-pixel box its layers are made for.
 */
internal fun DrawScope.drawLip(
    seat: SeatLook,
    topLeft: Offset,
    size: Size,
    corner: Float,
) {
    val at = Offset(topLeft.x.roundToInt().toFloat(), topLeft.y.roundToInt().toFloat())
    val box = Size(size.width.roundToInt().toFloat(), size.height.roundToInt().toFloat())
    val brush = CssImages.lipBrush(seat, box)
    translate(at.x, at.y) { drawRoundRect(brush, Offset.Zero, box, CornerRadius(min(corner, min(box.width, box.height) / 2))) }
    drawCssShadows(seat.lipShadows, at, box, corner)
}

internal object CssImages {
    class Layer(
        val color: Color,
        val y: Float,
        val sigma: Float,
        val spread: Float,
        val inset: Boolean,
    ) {
        val key: List<Int> get() = listOf(color.toArgb(), (y * 100).roundToInt(), (sigma * 100).roundToInt(), (spread * 100).roundToInt(), if (inset) 1 else 0)
    }

    /**
     * An image with the box size it was built for. Along a side built [slicedX] / [slicedY] it is the small box's image, whose middle
     * pixel repeats to fit any longer side ([drawSliced], [keep] pixels kept at each end); a stand-in of another size is stretched.
     */
    class Shown(
        val placed: SoftImages.Placed,
        val w: Int,
        val h: Int,
        val keep: Int,
        val slicedX: Boolean,
        val slicedY: Boolean,
    ) {
        val bytes: Long get() = placed.image.width.toLong() * placed.image.height * 4

        /** Draws the layers of the [bw] x [bh] px box at [topLeft]. */
        fun DrawScope.drawIn(
            topLeft: Offset,
            bw: Int,
            bh: Int,
            alpha: Float,
        ) {
            val image = placed.image
            val x = axis(topLeft.x, placed.dx, image.width, w, bw, slicedX)
            val y = axis(topLeft.y, placed.dy, image.height, h, bh, slicedY)
            drawSliced(image, if (x[2] == 1) keep else -1, if (y[2] == 1) keep else -1, 1, IntOffset(x[0], y[0]), IntSize(x[1], y[1]), alpha)
        }

        /** Where one side is drawn: (start, length, 1 when sliced). A side built for another length and not sliced is stretched. */
        private fun axis(
            at: Float,
            d: Int,
            length: Int,
            built: Int,
            drawn: Int,
            sliced: Boolean,
        ): IntArray {
            val start = at.roundToInt()
            if (sliced && drawn - 2 * d >= 2 * keep + 1) return intArrayOf(start + d, drawn - 2 * d, 1)
            if (drawn == built) return intArrayOf(start + d, length, 0)
            val s = drawn.toFloat() / built
            return intArrayOf(start + (d * s).roundToInt(), (length * s).roundToInt(), 0)
        }
    }

    private data class Look(val corner: Int, val layers: List<List<Int>>)

    private data class Key(val w: Int, val h: Int, val look: Look)

    private val cache = LinkedHashMap<Key, Shown>(16, 0.75f, true)

    /** The last image of each look (any size), drawn stretched while the exact one is built in the background. */
    private val latest = object : LinkedHashMap<Look, Shown>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Look, Shown>?) = size > MAX_LOOKS
    }
    private val pending = HashSet<Key>()

    /**
     * All [layers] (CSS order, the first on top) of a [w] x [h] px box of corner [corner] composited into one image, placed against the box's
     * corner. The exact one; else, with [ImageBuilding.inBackground] and a not very different one of the same look, that one (to be stretched
     * by the caller) while the exact one is built off the main thread; else built here.
     */
    fun shown(
        w: Int,
        h: Int,
        corner: Float,
        layers: List<Layer>,
    ): Shown? {
        if (layers.isEmpty()) return null
        val look = Look((corner * 100).roundToInt(), layers.map { it.key })
        val reach = Reach.of(corner, layers)
        // Every side long enough to be sliced shares one image: what a box costs does not grow with it, nor with how many sizes are drawn.
        val key = Key(if (reach.slices(w)) SLICED else w, if (reach.slices(h)) SLICED else h, look)
        var stale: Shown? = null
        synchronized(cache) {
            cache[key]?.let { return it }
            if (ImageBuilding.inBackground && !BackgroundBuilds.onBuildThread) stale = latest[look]?.takeIf { standsIn(it.w, it.h, w, h) }
            if (stale != null && key !in pending) {
                pending.add(key)
                val queued =
                    BackgroundBuilds.submit {
                        try {
                            val built = traceMiss("Plum:cssImage") { build(w, h, corner, layers, reach) }
                            synchronized(cache) {
                                keep(key, built)
                                latest[look] = built
                            }
                            built.placed.image
                        } finally {
                            synchronized(cache) { pending.remove(key) }
                        }
                    }
                if (!queued) {
                    pending.remove(key)
                    stale = null
                }
            }
        }
        stale?.let {
            BackgroundBuilds.watchLanding()
            return it
        }
        // Built outside the lock: the background builder may be building one too, and a draw must not wait for it.
        val built = traceMiss("Plum:cssImage") { build(w, h, corner, layers, reach) }
        synchronized(cache) {
            keep(key, built)
            latest[look] = built
        }
        return built
    }

    private var cachedBytes = 0L

    /** Empties the cache (tests). */
    fun clear() =
        synchronized(cache) {
            cache.clear()
            latest.clear()
            cachedBytes = 0L
        }

    /** The bytes of the distinct images this cache holds (tests). */
    val heldBytes: Long get() = synchronized(cache) { distinctBytes(cache.values.map { it.placed.image } + latest.values.map { it.placed.image }) }

    /** Adds [built] under [key] and drops the least recently used until a sixth of [ImageBuilding.budgetBytes] hold (the newest stays). Call with the cache locked. */
    private fun keep(
        key: Key,
        built: Shown,
    ) {
        cache.put(key, built)?.let { cachedBytes -= it.bytes }
        cachedBytes += built.bytes
        val oldest = cache.entries.iterator()
        while (cachedBytes > ImageBuilding.budgetBytes / BUDGET_SHARE && cache.size > 1 && oldest.hasNext()) {
            val eldest = oldest.next()
            if (eldest.value === built) break
            cachedBytes -= eldest.value.bytes
            oldest.remove()
            // A stand-in is only ever an image the cache holds, so the budget counts it.
            latest.entries.removeAll { it.value === eldest.value }
        }
    }

    /** How far a look's layers reach past its box ([m]) and how many pixels from the image's edge it keeps before it is the same along a side ([keep]). */
    private class Reach(
        val m: Int,
        val keep: Int,
    ) {
        /** Whether a box side of [length] px is drawn from the small image's sliced side. */
        fun slices(length: Int) = length + 2 * m > 2 * keep + 2

        companion object {
            fun of(
                corner: Float,
                layers: List<Layer>,
            ): Reach {
                val m = ceil(layers.filter { !it.inset }.maxOfOrNull { max(0f, it.spread) + REACH * it.sigma + abs(it.y) } ?: 0f).toInt() + 1
                // Past the corners and the blur the image does not change along an edge.
                return Reach(m, m + ceil(corner).toInt() + ceil(layers.maxOf { abs(it.spread) + REACH * it.sigma + abs(it.y) }).toInt() + STRETCH_MARGIN)
            }
        }
    }

    /** The image of a [w] x [h] box, or of the small box whose middle stands for any longer side (drawn sliced: [Shown.drawIn]). */
    private fun build(
        w: Int,
        h: Int,
        corner: Float,
        layers: List<Layer>,
        reach: Reach,
    ): Shown {
        val m = reach.m
        val smallW = if (reach.slices(w)) 2 * reach.keep + 2 - 2 * m else w
        val smallH = if (reach.slices(h)) 2 * reach.keep + 2 - 2 * m else h
        val pixels = pixelsOf(smallW, smallH, corner, layers, m)
        val image: ImageBitmap = Bitmap.createBitmap(pixels, smallW + 2 * m, smallH + 2 * m, Bitmap.Config.ARGB_8888).asImageBitmap()
        return Shown(SoftImages.Placed(image, -m, -m), smallW, smallH, reach.keep, smallW != w, smallH != h)
    }

    /** The pixels of the [w] x [h] box's layers on a bitmap with [m] px round it. */
    private fun pixelsOf(
        w: Int,
        h: Int,
        corner: Float,
        layers: List<Layer>,
        m: Int,
    ): IntArray {
        run {
            val bw = w + 2 * m
            val bh = h + 2 * m
            val xs = FloatArray(bw) { i -> i - m + 0.5f }
            val ys = FloatArray(bh) { j -> j - m + 0.5f }
            val box = FloatArray(bw * bh) { p -> coverage(xs[p % bw], ys[p / bw], w.toFloat(), h.toFloat(), corner) }
            val acc = FloatArray(bw * bh * 4)
            for (l in layers.asReversed()) {
                val s = if (l.inset) -l.spread else l.spread
                val sw = w + 2 * s
                val sh = h + 2 * s
                if (sw <= 0f || sh <= 0f) continue
                val sr = max(0f, corner + s)
                val v = shape(sw, sh, sr, l.sigma, xs, ys, -s, l.y - s)
                val ca = l.color.alpha
                for (p in 0 until bw * bh) {
                    val a = ca * if (l.inset) (1f - v[p]) * box[p] else v[p] * (1f - box[p])
                    if (a <= 0f) continue
                    val k = 1f - a
                    acc[p * 4] = l.color.red * a + acc[p * 4] * k
                    acc[p * 4 + 1] = l.color.green * a + acc[p * 4 + 1] * k
                    acc[p * 4 + 2] = l.color.blue * a + acc[p * 4 + 2] * k
                    acc[p * 4 + 3] = a + acc[p * 4 + 3] * k
                }
            }
            val pixels = IntArray(bw * bh) { p ->
                val a = acc[p * 4 + 3]
                val alpha = (a * 255f).roundToInt().coerceIn(0, 255)
                if (alpha == 0) {
                    0
                } else {
                    fun ch(i: Int) = (acc[p * 4 + i] / a * 255f).roundToInt().coerceIn(0, 255)
                    (alpha shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
                }
            }
            return pixels
        }
    }

    /** The coverage (0..1) of a [w] x [h] rounded rect of radius [r] at ([x0], [y0]), blurred by [sigma] (sharp below [SHARP]), sampled at [xs] x [ys]. */
    private fun shape(
        w: Float,
        h: Float,
        r: Float,
        sigma: Float,
        xs: FloatArray,
        ys: FloatArray,
        x0: Float,
        y0: Float,
    ): FloatArray {
        val rx = FloatArray(xs.size) { xs[it] - x0 }
        val ry = FloatArray(ys.size) { ys[it] - y0 }
        if (sigma >= SHARP) return SoftImages.blurred(w, h, min(r, min(w, h) / 2f), sigma, rx, ry, 1f)
        return FloatArray(xs.size * ys.size) { p -> coverage(rx[p % xs.size], ry[p / xs.size], w, h, r) }
    }

    /** How much of the pixel at ([x], [y]) a [w] x [h] rounded rect of radius [r] at 0 covers (a 1 px ramp across its edge). */
    fun coverage(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r0: Float,
    ): Float {
        val r = min(r0, min(w, h) / 2f)
        val qx = max(abs(x - w / 2f) - (w / 2f - r), 0f)
        val qy = max(abs(y - h / 2f) - (h / 2f - r), 0f)
        val outside = max(abs(x - w / 2f) - w / 2f, abs(y - h / 2f) - h / 2f)
        val d = if (qx > 0f && qy > 0f) sqrt(qx * qx + qy * qy) - r else outside
        return (0.5f - d).coerceIn(0f, 1f)
    }

    private data class BrushKey(val w: Int, val h: Int, val stops: List<Int>, val ellipse: Int)

    private val brushes = object : LinkedHashMap<BrushKey, ShaderBrush>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BrushKey, ShaderBrush>?) = size > MAX_IMAGES
    }

    /** The LIP's elliptical radial gradient for a box of [size] at 0: centre at the top middle, 120 % wide and [SeatLook.lipEllipseHeight] high. */
    fun lipBrush(
        seat: SeatLook,
        size: Size,
    ): ShaderBrush {
        val key = BrushKey(size.width.roundToInt(), size.height.roundToInt(), seat.lipStops.map { it.toArgb() }, (seat.lipEllipseHeight * 100).roundToInt())
        synchronized(brushes) {
            brushes[key]?.let { return it }
            val rx = max(1f, size.width * LIP_ELLIPSE_WIDTH)
            val ry = max(1f, size.height * seat.lipEllipseHeight)
            val shader = RadialGradient(size.width / 2f, 0f, rx, seat.lipStops.map { it.toArgb() }.toIntArray(), LIP_STOPS, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, size.width / 2f, 0f) })
            return ShaderBrush(shader).also { brushes[key] = it }
        }
    }

    /**
     * The RAISED chip's `radial-gradient(ellipse at 50% 18%, top 0%, bottom 85%)` for a box of [size] at 0: the default farthest-corner ellipse
     * (radii sqrt(2) times the distances to the far sides), clamped past 85 %.
     */
    fun chipBrush(
        top: Color,
        bottom: Color,
        size: Size,
    ): ShaderBrush {
        val key = BrushKey(size.width.roundToInt(), size.height.roundToInt(), listOf(top.toArgb(), bottom.toArgb()), CHIP_ELLIPSE)
        synchronized(brushes) {
            brushes[key]?.let { return it }
            val cx = size.width / 2f
            val cy = size.height * CHIP_CENTRE_Y
            val rx = max(1f, cx * SQRT2)
            val ry = max(1f, (size.height - cy) * SQRT2)
            val shader = RadialGradient(cx, cy, rx, intArrayOf(top.toArgb(), bottom.toArgb()), CHIP_STOPS, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
            return ShaderBrush(shader).also { brushes[key] = it }
        }
    }

    private const val CHIP_ELLIPSE = -1
    private const val CHIP_CENTRE_Y = 0.18f
    private const val SQRT2 = 1.4142135f
    private val CHIP_STOPS = floatArrayOf(0f, 0.85f)
    private val LIP_STOPS = floatArrayOf(0f, 0.45f, 1f)
    private const val LIP_ELLIPSE_WIDTH = 1.2f
    private const val MAX_IMAGES = 48
    private const val MAX_LOOKS = 8
    private const val REACH = 3f
    private const val STRETCH_MARGIN = 4
    private const val SLICED = -1
    private const val BUDGET_SHARE = 6

    /** A blur narrower than this (px of sigma) is drawn sharp, by coverage. */
    private const val SHARP = 0.35f
}
