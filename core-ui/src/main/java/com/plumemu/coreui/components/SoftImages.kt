package com.plumemu.coreui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.plumemu.coreui.theme.FocusHalo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Soft shadows and glows as small bitmaps: a Gaussian-blurred rounded rectangle (a shadow) or a 2D Gaussian (the light slab's blob), computed once per
 * size and colour and drawn scaled with a bilinear filter. One draw per frame instead of a stack of shapes, and smooth: the alpha has 255 levels
 * (a strong shadow changes the page by under one level per alpha step). The bitmap is coarser than the screen where the blur is wide; the filter hides it.
 */
internal object SoftImages {
    /** Page pixels per bitmap pixel for a blur of [sigma] px: a thirtieth of it (the screen itself below 60 px), because a coarser grid shows in the tail as uneven steps. */
    fun step(sigma: Float): Int = max(1, (sigma / 30f).toInt())

    fun blobScale(sigmaY: Float): Int = max(1, (sigmaY / BLOB_GRID).toInt())

    private class Key(val kind: Int, val w: Int, val h: Int, val a: Int, val b: Int, val rgb: Int, val alpha: Int) {
        override fun equals(other: Any?) = other is Key && kind == other.kind && w == other.w && h == other.h && a == other.a && b == other.b && rgb == other.rgb && alpha == other.alpha

        override fun hashCode() = ((((kind * 31 + w) * 31 + h) * 31 + a) * 31 + b) * 31 + rgb + alpha
    }

    private val cache = object : LinkedHashMap<Key, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?) = size > MAX_ENTRIES
    }

    /** The shadows, bounded by bytes: on older Android a bitmap lives in the app's small heap. */
    private val shades = LinkedHashMap<Key, Shade>(16, 0.75f, true)

    private fun alphaLevel(color: Color) = (color.alpha * 255f).roundToInt().coerceIn(0, 255)

    private fun rgbOf(color: Color) = color.copy(alpha = 1f).toArgb() and 0xFFFFFF

    /**
     * The shadow of a [w] x [h] px rounded rectangle of corner radius [corner] px blurred by [sigma] px: [color]'s alpha deep inside, half of it at a
     * straight edge, and the bitmap reaches [REACH_SIGMAS] sigmas out. The exact Gaussian of the rounded shape (not of its bounding rectangle), so the
     * shade follows the corners: each row of the shape is blurred along x as an interval, then the rows are blurred along y (the straight middle
     * band in closed form, the two corner bands row by row).
     */
    fun shadow(
        w: Int,
        h: Int,
        sigma: Float,
        color: Color,
        corner: Float = 0f,
    ): Shade {
        val r = corner.coerceIn(0f, min(w, h) / 2f)
        val look = Key(0, 0, 0, (sigma * 100).roundToInt(), (r * 100).roundToInt(), rgbOf(color), alphaLevel(color))
        val step = step(sigma)
        val keep = keepOf(sigma, r)
        // A side long enough to be cut shares one image with every such side of the same remainder: the cost does not grow with the box.
        val kw = if (cut(w, sigma, keep) > 0) SLICED - w % step else w
        val kh = if (cut(h, sigma, keep) > 0) SLICED - h % step else h
        val key = Key(0, kw, kh, look.a, look.b, look.rgb, look.alpha)
        var stale: Shade? = null
        synchronized(shades) {
            shades[key]?.let { return it }
            if (ImageBuilding.inBackground && !BackgroundBuilds.onBuildThread) stale = latest[look]?.takeIf { standsIn(it.w, it.h, w, h) }
            if (stale != null && key !in pending) {
                pending.add(key)
                val queued =
                    BackgroundBuilds.submit {
                        try {
                            val built = traceMiss("Plum:softShadow") { buildShadow(w, h, sigma, color, r, keep) }
                            synchronized(shades) {
                                keep(key, built)
                                latest[look] = built
                            }
                            built.image
                        } finally {
                            synchronized(shades) { pending.remove(key) }
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
        val built = traceMiss("Plum:softShadow") { buildShadow(w, h, sigma, color, r, keep) }
        synchronized(shades) {
            keep(key, built)
            latest[look] = built
        }
        return built
    }

    /**
     * A shadow image for a [w] x [h] px box. Along a side it was [slicedX] / [slicedY] on, it is cut to [keep] cells at each end and
     * one between, which [draw] repeats to the side's length (as [StretchedPixels.expand] would); else it is the side's whole image.
     */
    class Shade(
        val image: ImageBitmap,
        val w: Int,
        val h: Int,
        val keep: Int,
        val slicedX: Boolean,
        val slicedY: Boolean,
    ) {
        val width: Int get() = image.width
        val height: Int get() = image.height

        /** Draws it into [size] page px at [at], [step] page px a cell: sliced along a cut side, stretched along a side built for another length. */
        fun DrawScope.draw(
            at: IntOffset,
            size: IntSize,
            step: Int,
            blendMode: BlendMode,
        ) {
            val kx = if (slicedX && size.width >= (2 * keep + 1) * step) keep else -1
            val ky = if (slicedY && size.height >= (2 * keep + 1) * step) keep else -1
            drawSliced(image, kx, ky, step, at, size, blendMode = blendMode)
        }
    }

    private fun keepOf(
        sigma: Float,
        r: Float,
    ): Int = ceil((2 * (REACH_SIGMAS * sigma).toInt() + r) / step(sigma)).toInt() + 3

    /** How many cells of a side of [length] px are left out of its image (0: not cut). */
    private fun cut(
        length: Int,
        sigma: Float,
        keep: Int,
    ): Int {
        val cells = cells(length, sigma)
        return if (cells > 2 * keep + 2) cells - (2 * keep + 2) else 0
    }

    private var cachedBytes = 0L

    /** Adds [built] under [key] and drops the least recently used until a sixth of [ImageBuilding.budgetBytes] or [MAX_ENTRIES] images hold (the newest stays). Call with [shades] locked. */
    private fun keep(
        key: Key,
        built: Shade,
    ) {
        shades.put(key, built)?.let { cachedBytes -= bytesOf(it.image) }
        cachedBytes += bytesOf(built.image)
        val oldest = shades.entries.iterator()
        while ((cachedBytes > ImageBuilding.budgetBytes / BUDGET_SHARE || shades.size > MAX_ENTRIES) && shades.size > 1 && oldest.hasNext()) {
            val eldest = oldest.next()
            if (eldest.value === built) break
            cachedBytes -= bytesOf(eldest.value.image)
            oldest.remove()
            // A stand-in is only ever an image the cache holds, so the budget counts it.
            latest.entries.removeAll { it.value === eldest.value }
        }
    }

    private fun bytesOf(image: ImageBitmap) = image.width.toLong() * image.height * BYTES_PER_PIXEL

    /** The cells (bitmap pixels) across a shadow of a [length] px side blurred by [sigma]: what [shadow]'s image holds for it, a stand-in or not. */
    fun cells(
        length: Int,
        sigma: Float,
    ): Int = (length + 2 * (REACH_SIGMAS * sigma).toInt() + step(sigma) - 1) / step(sigma)

    /** Empties the caches (tests). */
    fun clear() {
        synchronized(cache) { cache.clear() }
        synchronized(shades) {
            shades.clear()
            latest.clear()
            cachedBytes = 0L
        }
        synchronized(dropCache) { dropCache.clear() }
        synchronized(insetCache) { insetCache.clear() }
    }

    /** The bytes of the distinct images these caches hold (tests). */
    val heldBytes: Long
        get() =
            synchronized(cache) { distinctBytes(cache.values.toList()) } +
                synchronized(shades) { distinctBytes(shades.values.map { it.image } + latest.values.map { it.image }) } +
                synchronized(dropCache) { distinctBytes(dropCache.values.map { it.image }) } +
                synchronized(insetCache) { distinctBytes(insetCache.values.toList()) }

    /** How many shadows are kept (tests). */
    val keptCount: Int get() = synchronized(shades) { shades.size }

    /** The last shadow built for each look (sigma, corner, colour), whatever its size: stretched while the exact one is built in the background. */
    private val latest = object : LinkedHashMap<Key, Shade>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Shade>?) = size > MAX_LOOKS
    }
    private val pending = HashSet<Key>()

    private fun buildShadow(
        w: Int,
        h: Int,
        sigma: Float,
        color: Color,
        r: Float,
        keep: Int,
    ): Shade {
        run {
            val step = step(sigma)
            // Past the corners and the blur the shade does not change along an edge: a big one is drawn from a small one's, its middle repeated.
            val cutX = cut(w, sigma, keep)
            val cutY = cut(h, sigma, keep)
            val smallW = w - cutX * step
            val smallH = h - cutY * step
            val sbw = cells(w, sigma) - cutX
            val sbh = cells(h, sigma) - cutY
            val extraX = sbw * step - smallW
            val extraY = sbh * step - smallH
            val peak = alphaLevel(color) / 255f
            val rgb = rgbOf(color)
            val xs = FloatArray(sbw) { i -> (i + 0.5f) * step - extraX / 2f }
            val ys = FloatArray(sbh) { j -> (j + 0.5f) * step - extraY / 2f }
            val v = blurred(smallW.toFloat(), smallH.toFloat(), r, sigma, xs, ys, 1f)
            val small = IntArray(sbw * sbh) { p -> ((255f * peak * v[p]).roundToInt().coerceIn(0, 255) shl 24) or rgb }
            return Shade(Bitmap.createBitmap(small, sbw, sbh, Bitmap.Config.ARGB_8888).asImageBitmap(), w, h, keep, cutX > 0, cutY > 0)
        }
    }

    /**
     * The Gaussian blur (sigma [sigma]) of a [w] x [h] rounded rectangle of radius [r] with its top-left at 0, sampled at [xs] x [ys]: 1 deep inside, 0.5 at a
     * straight edge. Each row of the shape is blurred along x as an interval, then the rows along y (the straight middle band in closed form, the two corner bands
     * row by row, rows at most [rowStep] sigmas apart so a narrow blur is not aliased).
     */
    internal fun blurred(
        w: Float,
        h: Float,
        r: Float,
        sigma: Float,
        xs: FloatArray,
        ys: FloatArray,
        rowStep: Float,
    ): FloatArray {
        val bw = xs.size
        val bh = ys.size
        fun cdf(d: Float) = FocusHalo.softEdge(-d, sigma)
        val fx = FloatArray(bw) { i -> FocusHalo.softEdge(xs[i] - w, sigma) - FocusHalo.softEdge(xs[i], sigma) }
        val fy = FloatArray(bh) { j -> cdf(ys[j] - r) - cdf(ys[j] - (h - r)) }
        val n = if (r > 0f) max(ceil(r).toInt(), ceil(r / (sigma * rowStep)).toInt()) else 0
        val dy = if (n > 0) r / n else 0f
        val rowX = Array(n) { k ->
            val up = (k + 0.5f) * dy
            val inset = r - sqrt(max(0f, r * r - (r - up) * (r - up)))
            FloatArray(bw) { i -> FocusHalo.softEdge(xs[i] - (w - inset), sigma) - FocusHalo.softEdge(xs[i] - inset, sigma) }
        }
        val norm = dy / (sigma * sqrt(2f * PI.toFloat()))
        val out = FloatArray(bw * bh)
        val wRow = FloatArray(n)
        for (j in 0 until bh) {
            val y = ys[j]
            for (k in 0 until n) {
                val dTop = (y - (k + 0.5f) * dy) / sigma
                val dBottom = (y - (h - (k + 0.5f) * dy)) / sigma
                wRow[k] = norm * (exp(-0.5f * dTop * dTop) + exp(-0.5f * dBottom * dBottom))
            }
            // Row by row over x, each corner row added in the same order as pixel by pixel (same sums, bit for bit), but through contiguous arrays.
            val base = j * bw
            val fyj = fy[j]
            for (i in 0 until bw) out[base + i] = fx[i] * fyj
            for (k in 0 until n) {
                val weight = wRow[k]
                if (weight == 0f) continue // adding 0 changes nothing
                val row = rowX[k]
                for (i in 0 until bw) out[base + i] += row[i] * weight
            }
        }
        return out
    }

    private fun storedChannel(exact: Float, alpha: Int): Int {
        val premultiplied = (exact * 255f).roundToInt().coerceIn(0, alpha)
        return (premultiplied * 255f / alpha).roundToInt().coerceIn(0, 255)
    }

    /** Where a [drops] image's top-left sits against the shape's own top-left, in px (negative: above and left of it). */
    class Placed(
        val image: ImageBitmap,
        val dx: Int,
        val dy: Int,
    )

    /**
     * The outer soft layers under a [w] x [h] px rounded rectangle (corner [corner]) composited into one bitmap, page pixel for page pixel: [near] over
     * [far] (one tint) over [glow] (none if null), each moved its dy down and blurred by its sigma (CSS `0 dy blur`). One image keeps the rounding of a
     * single composite, so the shade has no steps where quantised layers meet. [under] (opaque, or transparent for none) is the colour it will mostly be
     * drawn over: each pixel's stored colour then absorbs its alpha's rounding, so over that colour the result is the exact composite rounded once.
     */
    fun drops(
        w: Int,
        h: Int,
        corner: Float,
        near: Inset,
        far: Inset,
        glow: Inset?,
        under: Color = Color.Transparent,
    ): Placed? {
        if (w <= 0 || h <= 0) return null
        val key = DropKey(w, h, (corner * 100).roundToInt(), layerKey(near), layerKey(far), glow?.let { layerKey(it) }, under.toArgb())
        synchronized(dropCache) { dropCache[key] }?.let { return it }
        // Built outside the lock: a background warm-up must not make a draw on the main thread wait for a cached image.
        val built = traceMiss("Plum:drops") { dropsBuild(w, h, corner, near, far, glow, under) } ?: return null
        return synchronized(dropCache) { dropCache.getOrPut(key) { built } }
    }

    private fun dropsBuild(w: Int, h: Int, corner: Float, near: Inset, far: Inset, glow: Inset?, under: Color): Placed? {
        run {
            val r = corner.coerceIn(0f, min(w, h) / 2f)
            val layers = listOfNotNull(near, far, glow)
            fun reach(l: Inset) = ceil(REACH_SIGMAS * l.sigma)
            val left = -ceil(layers.maxOf { reach(it) }).toInt()
            val top = kotlin.math.floor(layers.minOf { it.dy - reach(it) }).toInt()
            val bottom = ceil(layers.maxOf { h + it.dy + reach(it) }).toInt()
            val bw = w - 2 * left
            val bh = bottom - top
            val xs = FloatArray(bw) { i -> i + left + 0.5f }
            fun layer(l: Inset) = blurred(w.toFloat(), h.toFloat(), r, max(l.sigma, MIN_SIGMA), xs, FloatArray(bh) { j -> j + top + 0.5f - l.dy }, 1f)
            val vn = layer(near)
            val vf = layer(far)
            val vg = glow?.let { layer(it) }
            val shade = near.color
            val nearA = near.color.alpha
            val farA = far.color.alpha
            val glowA = glow?.color?.alpha ?: 0f
            val withUnder = under.alpha > 0f
            val shadeR = shade.red
            val shadeG = shade.green
            val shadeB = shade.blue
            val glowR = glow?.color?.red ?: 0f
            val glowG = glow?.color?.green ?: 0f
            val glowB = glow?.color?.blue ?: 0f
            val underR = under.red
            val underG = under.green
            val underB = under.blue
            val pixels = IntArray(bw * bh) { p ->
                val an = nearA * vn[p]
                val shadeA = an + farA * vf[p] * (1f - an)
                val ag = if (glow != null && vg != null) glowA * vg[p] else 0f
                val a = shadeA + ag * (1f - shadeA)
                val alpha = (255f * a).roundToInt().coerceIn(0, 255)
                if (alpha == 0) {
                    0
                } else {
                    // The bitmap stores colour premultiplied by alpha: pick the stored colour so that the premultiplied value is the exact one rounded once,
                    // plus (over [under]) what the rounded alpha lets through of it too much or too little.
                    val lost = if (withUnder) a - alpha / 255f else 0f
                    val r = storedChannel((shadeR * shadeA + glowR * ag * (1f - shadeA)) - underR * lost, alpha)
                    val g = storedChannel((shadeG * shadeA + glowG * ag * (1f - shadeA)) - underG * lost, alpha)
                    val bl = storedChannel((shadeB * shadeA + glowB * ag * (1f - shadeA)) - underB * lost, alpha)
                    (alpha shl 24) or (r shl 16) or (g shl 8) or bl
                }
            }
            return Placed(Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888).asImageBitmap(), left, top)
        }
    }

    private data class DropKey(val w: Int, val h: Int, val corner: Int, val near: List<Int>, val far: List<Int>, val glow: List<Int>?, val under: Int)

    private val dropCache = object : LinkedHashMap<DropKey, Placed>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DropKey, Placed>?) = size > MAX_INSETS
    }

    /** One inset layer in px: [color] (alpha = strength), its hole moved [dy] down, blurred by [sigma]. */
    class Inset(
        val color: Color,
        val dy: Float,
        val sigma: Float,
    )

    /**
     * The CSS inset shadows of a [w] x [h] px rounded box (corner [corner]), drawn inside its [border]: each layer is its colour wherever the box's padding area,
     * moved by its dy and blurred, does not cover, clipped to the padding area; [over] is painted over [under] (the CSS's first shadow is on top). One bitmap
     * of the box's own size, page pixel for page pixel.
     */
    fun inset(
        w: Int,
        h: Int,
        corner: Float,
        border: Float,
        over: Inset?,
        under: Inset?,
    ): ImageBitmap? {
        if (w <= 0 || h <= 0 || (over == null && under == null)) return null
        val key = InsetKey(w, h, (corner * 100).roundToInt(), (border * 100).roundToInt(), over?.let { layerKey(it) }, under?.let { layerKey(it) })
        synchronized(insetCache) { insetCache[key] }?.let { return it }
        val built = traceMiss("Plum:inset") { insetBuild(w, h, corner, border, over, under) } ?: return null
        return synchronized(insetCache) { insetCache.getOrPut(key) { built } }
    }

    private fun insetBuild(w: Int, h: Int, corner: Float, border: Float, over: Inset?, under: Inset?): ImageBitmap? {
        run {
            val pw = w - 2 * border
            val ph = h - 2 * border
            val pr = max(0f, min(corner - border, min(pw, ph) / 2f))
            val xs = FloatArray(w) { i -> i + 0.5f - border }
            fun layer(l: Inset?) = l?.let { blurred(pw, ph, pr, max(l.sigma, MIN_SIGMA), xs, FloatArray(h) { j -> j + 0.5f - border - l.dy }, 1f) }
            val vo = layer(over)
            val vu = layer(under)
            val pixels = IntArray(w * h)
            for (j in 0 until h) {
                for (i in 0 until w) {
                    val p = j * w + i
                    val cov = coverage(xs[i], j + 0.5f - border, pw, ph, pr)
                    if (cov <= 0f) continue
                    val au = if (under != null && vu != null) under.color.alpha * (1f - vu[p]).coerceIn(0f, 1f) else 0f
                    val ao = if (over != null && vo != null) over.color.alpha * (1f - vo[p]).coerceIn(0f, 1f) else 0f
                    val a = ao + au * (1f - ao)
                    if (a <= 0f) continue
                    fun ch(get: (Color) -> Float) = ((over?.let { get(it.color) * ao } ?: 0f) + (under?.let { get(it.color) * au * (1f - ao) } ?: 0f)) / a
                    val alpha = (255f * a * cov).roundToInt().coerceIn(0, 255)
                    val red = (255f * ch { it.red }).roundToInt().coerceIn(0, 255)
                    val green = (255f * ch { it.green }).roundToInt().coerceIn(0, 255)
                    val blue = (255f * ch { it.blue }).roundToInt().coerceIn(0, 255)
                    pixels[p] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    /** How much of the pixel at ([x], [y]) the [w] x [h] rounded rectangle of radius [r] at 0 covers (a 1 px ramp across its edge). */
    private fun coverage(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
    ): Float {
        val qx = max(abs(x - w / 2f) - (w / 2f - r), 0f)
        val qy = max(abs(y - h / 2f) - (h / 2f - r), 0f)
        val outside = max(abs(x - w / 2f) - w / 2f, abs(y - h / 2f) - h / 2f)
        val d = if (qx > 0f && qy > 0f) sqrt(qx * qx + qy * qy) - r else outside
        return (0.5f - d).coerceIn(0f, 1f)
    }

    private fun layerKey(l: Inset) = listOf(l.color.toArgb(), (l.dy * 100).roundToInt(), (l.sigma * 100).roundToInt())

    private data class InsetKey(val w: Int, val h: Int, val corner: Int, val border: Int, val over: List<Int>?, val under: List<Int>?)

    private val insetCache = object : LinkedHashMap<InsetKey, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InsetKey, ImageBitmap>?) = size > MAX_INSETS
    }

    /** A 2D Gaussian of [sigmaX] by [sigmaY] px in [color] (its alpha is the peak), out to [BLOB_SIGMAS] sigmas. */
    fun blob(
        sigmaX: Float,
        sigmaY: Float,
        color: Color,
    ): ImageBitmap {
        val key = Key(1, 0, 0, (sigmaX * 10).roundToInt(), (sigmaY * 10).roundToInt(), rgbOf(color), alphaLevel(color))
        synchronized(cache) {
            cache[key]?.let { return it }
            val scale = blobScale(sigmaY)
            val bw = ((2 * BLOB_SIGMAS * sigmaX) / scale).toInt() + 1
            val bh = ((2 * BLOB_SIGMAS * sigmaY) / scale).toInt() + 1
            val peak = alphaLevel(color) / 255f
            val rgb = rgbOf(color)
            val pixels = IntArray(bw * bh) { p ->
                val dx = ((p % bw) + 0.5f - bw / 2f) * scale / sigmaX
                val dy = ((p / bw) + 0.5f - bh / 2f) * scale / sigmaY
                ((255f * peak * exp(-0.5f * (dx * dx + dy * dy))).roundToInt().coerceIn(0, 255) shl 24) or rgb
            }
            return Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888).asImageBitmap().also { cache[key] = it }
        }
    }

    private const val MAX_ENTRIES = 24
    private const val SLICED = -1
    private const val BUDGET_SHARE = 6
    private const val BYTES_PER_PIXEL = 4
    private const val MAX_LOOKS = 16
    private const val MAX_INSETS = 8

    /** A blur narrower than this (px) is drawn as this: the sampled corner rows stay smooth. */
    private const val MIN_SIGMA = 0.35f
    private const val REACH_SIGMAS = 3f
    private const val BLOB_SIGMAS = 2.8f

    /** Blob bitmap pixels per sigma: fine enough that the bilinear filter shows no kinks under the slab's drops (`SoftShadowStepTest`). */
    private const val BLOB_GRID = 20f
}
