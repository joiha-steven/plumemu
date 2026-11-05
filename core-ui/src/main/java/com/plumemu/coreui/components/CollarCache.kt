package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.theme.SeatLook
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A collar at rest (no focus, no error) as one cached image: its lip, groove and shades are the same for every tile of a size and theme,
 * so an unfocused tile draws one image instead of a gradient, six shade layers, a fill and an inset shade. Only a tile that is lit or
 * animating goes through [drawSeatCollarLayers].
 */
internal object CollarImages {
    private class Family(
        val seat: SeatLook,
        val corner: Float,
        val lip: Float,
        val lipBottom: Float,
        val groove: Float,
        val density: Float,
    ) {
        override fun equals(other: Any?) =
            other is Family && seat === other.seat && corner == other.corner && lip == other.lip && lipBottom == other.lipBottom && groove == other.groove && density == other.density

        override fun hashCode() = (((corner.hashCode() * 31 + lip.hashCode()) * 31 + groove.hashCode()) * 31 + lipBottom.hashCode()) * 31 + System.identityHashCode(seat)
    }

    private class Key(
        val family: Family,
        val w: Float,
        val h: Float,
    ) {
        override fun equals(other: Any?) = other is Key && family == other.family && w == other.w && h == other.h

        override fun hashCode() = (family.hashCode() * 31 + w.hashCode()) * 31 + h.hashCode()
    }

    /** An image built for a [w] x [h] box. */
    class Resting(
        val placed: SoftImages.Placed,
        val w: Float,
        val h: Float,
    ) {
        val bytes: Long get() = placed.image.width.toLong() * placed.image.height * BYTES_PER_PIXEL

        /** When it was last drawn ([System.nanoTime]): an image drawn within [IN_USE_NS] is never dropped for another. */
        @Volatile var drawnAt = System.nanoTime()
    }

    // Bounded by bytes, not count: a collar image is of the box's size, and on older Android a bitmap lives in the app's small heap.
    private val cache = LinkedHashMap<Key, Resting>(16, 0.75f, true)
    private var cachedBytes = 0L

    /** Adds [built] under [key] and drops the least recently used until [share] holds (always keeps the newest). Call with the cache locked. */
    private fun keep(
        key: Key,
        built: Resting,
    ) {
        cache.put(key, built)?.let { cachedBytes -= it.bytes }
        cachedBytes += built.bytes
        val oldest = cache.entries.iterator()
        while (cachedBytes > share && cache.size > 1 && oldest.hasNext()) {
            val eldest = oldest.next()
            if (eldest.value === built) break
            cachedBytes -= eldest.value.bytes
            oldest.remove()
            // A stand-in is only ever an image the cache holds, so the budget counts it.
            latest.entries.removeAll { it.value === eldest.value }
        }
    }

    /** What the collar images may hold: two thirds of [ImageBuilding.budgetBytes] (the shadow caches, whose images are small, share the rest). */
    private val share: Long get() = ImageBuilding.budgetBytes * 2 / 3

    /**
     * Whether an image of [bytes] fits beside the ones being built without dropping one drawn within [IN_USE_NS]. A grid of boxes each
     * of its own size can need more than the budget: those past it are drawn layer by layer, never built, dropped and built again.
     * Call with the cache locked.
     */
    private fun hasRoom(bytes: Long): Boolean {
        val now = System.nanoTime()
        val inUse = cache.values.sumOf { if (now - it.drawnAt < IN_USE_NS) it.bytes else 0L }
        return inUse + reserved + bytes <= share
    }

    /** The bytes of the images being built, by key. */
    private val pending = HashMap<Key, Long>()
    private var reserved = 0L

    /** The last image built for each look, drawn stretched while the exact one is built in the background. */
    private val latest = object : LinkedHashMap<Family, Resting>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Family, Resting>?) = size > MAX_FAMILIES
    }

    /**
     * The image of the collar for the box now drawn: the exact one; else, with [ImageBuilding.inBackground], the last one of its look
     * (stretched) or none (the caller draws the layers) while the exact one is built off the main thread, or none at all when the
     * images in use fill the budget. Without [ImageBuilding.inBackground] (screenshot tests) it is built here.
     */
    fun DrawScope.resting(
        seat: SeatLook,
        collarSize: CollarSize,
        cornerRadius: Float,
    ): Resting? {
        val family = Family(seat, cornerRadius, collarSize.lip.toPx(), collarSize.lipBottom.toPx(), collarSize.groove.toPx(), density)
        val key = Key(family, size.width, size.height)
        val density = Density(density, fontScale)
        val layoutDirection = layoutDirection
        val box = Size(size.width, size.height)
        if (!ImageBuilding.inBackground) {
            synchronized(cache) { cache[key]?.let { return it } }
            val built = build(density, layoutDirection, box, seat, collarSize, cornerRadius)
            synchronized(cache) {
                keep(key, built)
                latest[family] = built
            }
            return built
        }
        synchronized(cache) {
            cache[key]?.let {
                it.drawnAt = System.nanoTime()
                return it
            }
            if (key !in pending) {
                val bytes = bytesFor(density, seat, box)
                if (!hasRoom(bytes)) {
                    // Redrawn (and asked again) when any build lands, which is when images in use change; no timer, no cost per frame.
                    BackgroundBuilds.watchLanding()
                    return null
                }
                pending[key] = bytes
                reserved += bytes
                val queued =
                    BackgroundBuilds.submit {
                        try {
                            val built = build(density, layoutDirection, box, seat, collarSize, cornerRadius)
                            synchronized(cache) {
                                keep(key, built)
                                latest[family] = built
                            }
                            built.placed.image
                        } finally {
                            synchronized(cache) { pending.remove(key)?.let { reserved -= it } }
                        }
                    }
                if (!queued) {
                    pending.remove(key)
                    reserved -= bytes
                    return null
                }
            }
            BackgroundBuilds.watchLanding()
            return latest[family]
        }
    }

    private fun bytesFor(
        density: Density,
        seat: SeatLook,
        box: Size,
    ): Long {
        val m = margin(density, seat)
        return (ceil(box.width).toLong() + 2 * m) * (ceil(box.height).toLong() + 2 * m) * BYTES_PER_PIXEL
    }

    private fun build(
        density: Density,
        layoutDirection: LayoutDirection,
        box: Size,
        seat: SeatLook,
        collarSize: CollarSize,
        cornerRadius: Float,
    ): Resting =
        traceMiss("Plum:collarImage") {
            val m = margin(density, seat)
            val bw = ceil(box.width).toInt() + 2 * m
            val bh = ceil(box.height).toInt() + 2 * m
            val image = ImageBitmap(bw, bh)
            val canvas = Canvas(image)
            canvas.translate(m.toFloat(), m.toFloat())
            CanvasDrawScope().draw(density, layoutDirection, canvas, box) {
                drawSeatCollarLayers(seat, collarSize, cornerRadius, 0f, null, androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.unit.Dp.Hairline)
            }
            Resting(SoftImages.Placed(image, -m, -m), box.width, box.height)
        }

    /** The room round the box its lip's outer shadows need (the shadows' own reach, as `CssImages` sizes it, plus a pixel). */
    private fun margin(
        density: Density,
        seat: SeatLook,
    ): Int =
        with(density) {
            ceil(seat.lipShadows.filter { !it.inset }.maxOfOrNull { max(0f, it.spread.toPx()) + 3f * (it.blur.toPx() / 2f) + abs(it.y.toPx()) } ?: 0f).toInt() + 2
        }

    /** Empties the cache (tests). */
    fun clear() =
        synchronized(cache) {
            cache.clear()
            latest.clear()
            cachedBytes = 0L
            pending.clear()
            reserved = 0L
        }

    /** The bytes of the distinct images this cache holds (tests). */
    val heldBytes: Long get() = synchronized(cache) { distinctBytes(cache.values.map { it.placed.image } + latest.values.map { it.placed.image }) }

    private const val BYTES_PER_PIXEL = 4
    private const val MAX_FAMILIES = 8
    private const val IN_USE_NS = 2_000_000_000L
}

/**
 * Draws the cached resting collar in the whole bounds of the scope (stretched to them while the exact image is still being built);
 * false when there is no image to draw (the caller draws the layers).
 */
internal fun DrawScope.drawRestingCollar(
    seat: SeatLook,
    collarSize: CollarSize,
    cornerRadius: Float,
): Boolean {
    val resting = with(CollarImages) { resting(seat, collarSize, cornerRadius) } ?: return false
    val placed = resting.placed
    val image = placed.image
    val sx = if (resting.w > 0f) size.width / resting.w else 1f
    val sy = if (resting.h > 0f) size.height / resting.h else 1f
    val isExact = sx == 1f && sy == 1f
    val dstOffset = if (isExact) IntOffset(placed.dx, placed.dy) else IntOffset((placed.dx * sx).roundToInt(), (placed.dy * sy).roundToInt())
    val dstSize = if (isExact) IntSize(image.width, image.height) else IntSize((image.width * sx).roundToInt(), (image.height * sy).roundToInt())
    drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), dstOffset, dstSize, filterQuality = FilterQuality.Low)
    return true
}
