package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.MaskOpaque
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The 32 dp fade at the right edge over content that runs past it (docs/design/rules.md › The
 * strip): from clear to the background. Draws only; touches pass through to the rows below.
 */
@Composable
fun EdgeFade(modifier: Modifier = Modifier) {
    val colors = PlumTheme.colors
    Box(
        modifier =
            modifier
                .width(PlumDimens.edgeFade)
                .fillMaxHeight()
                // Read while drawing: the background may be moving under a colour slider.
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(colors.bg.copy(alpha = 0f), colors.bg))) },
    )
}

/**
 * The fade at the bottom ([atBottom]) or top edge of a list that scrolls, drawn only while rows
 * continue past that edge: a row cut there fades out instead of ending in a hard line, so it is
 * plain the list scrolls. Place it over the list (Box, aligned to that edge); touches pass through.
 * [reach] (0 to 1, read while drawing) shortens it towards its edge, so it can stop short of a focused row.
 * [erases] is for a list on a card, whose face is not the background: the rows are erased instead of painted over, so the
 * parent must draw offscreen (`CompositingStrategy.Offscreen`).
 */
@Composable
fun ListEdgeFade(
    atBottom: Boolean,
    modifier: Modifier = Modifier,
    reach: () -> Float = { 1f },
    erases: Boolean = false,
) {
    val colors = PlumTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlumDimens.listFadeBand)
                .drawBehind {
                    val band = size.height * reach().coerceIn(0f, 1f)
                    if (band <= 0f) return@drawBehind
                    val solid = if (erases) MaskOpaque else colors.bg
                    val clear = solid.copy(alpha = 0f)
                    val from = if (atBottom) size.height - band else 0f
                    drawRect(
                        Brush.verticalGradient(if (atBottom) listOf(clear, solid) else listOf(solid, clear), startY = from, endY = from + band),
                        topLeft = Offset(0f, from),
                        size = Size(size.width, band),
                        blendMode = if (erases) BlendMode.DstOut else DrawScope.DefaultBlendMode,
                    )
                },
    )
}

/**
 * The fade across the whole screen where content runs under a bar (design/rules.md › Lists that scroll): one [ListEdgeFade] tight under the top bar, one tight on the hint bar, the
 * same height and curve. Put it over the content, in a box whose edges are [topGap] below the top bar and [bottomGap] above the hint bar. [topShows] is read while drawing.
 */
@Composable
fun BoxScope.BarFades(
    topGap: Dp,
    bottomGap: Dp,
    topShows: () -> Boolean,
    /** Share of the band the bottom fade takes: less over a lifted cover's ring standing in it. */
    bottomReach: () -> Float = { 1f },
) {
    if (!PlumTheme.edgeFadeOn) return
    val reach = remember(topShows) { { if (topShows()) 1f else 0f } }
    ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).offset(y = -topGap).testTag(BAR_FADE_TOP), reach)
    ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).offset(y = bottomGap).testTag(BAR_FADE_BOTTOM), bottomReach)
}

const val BAR_FADE_TOP = "bar_fade_top"
const val BAR_FADE_BOTTOM = "bar_fade_bottom"

/**
 * Where a scrolling list fades at its edges right now (design/rules.md › Lists that scroll), read while
 * drawing: a scroll redraws the mask, nothing recomposes.
 */
@Stable
interface EdgeFades {
    /** Whether the list can scroll at all: the mask and the pixel snap apply only while it can. */
    val canScroll: Boolean

    /** How far the fade reaches down from the top edge of a list [height] px tall, in px, at most [fadePx]; 0 for none. */
    fun top(
        fadePx: Float,
        height: Float,
    ): Float

    /** How far the fade reaches up from the bottom edge of a list [height] px tall, in px, at most [fadePx]; 0 for none. */
    fun bottom(
        fadePx: Float,
        height: Float,
    ): Float

    /** How much of the top edge is hidden outright, in px: the visible part of a row cut there, in a list that shows whole rows only. */
    fun clearTop(height: Float): Float = 0f

    /** How much of the bottom edge is hidden outright, in px, as [clearTop]. */
    fun clearBottom(height: Float): Float = 0f

}

/** The [EdgeFades] of a node drawn with [listEdgeFadeMask], for tests. */
val EdgeFadesKey = SemanticsPropertyKey<EdgeFades>("EdgeFades")

/** The [EdgeFades] of a node that clears the room past its window or hides cut rows, with or without a ramp (set whenever [listEdgeFadeMaskInRoom] draws), for tests. */
val EdgeClearsKey = SemanticsPropertyKey<EdgeFades>("EdgeClears")

/**
 * Fades the content itself to transparent over [PlumDimens.listFadeBand] at the top and bottom edges [fades] asks
 * for: an alpha mask (`DstIn` gradient), so it is right on any background and a cover's
 * shadow fades with it, where [ListEdgeFade] paints the background over the rows. There is no layer over the whole list (it redrew every row each frame on a handheld): rows the mask leaves alone are drawn straight and only the bands it touches go through a band-sized layer ([drawMasked]); [snapToPixels] keeps the content on whole pixels as that layer did. Put it on the list's visible
 * bounds (before a glide's taller layout). Draws only; touches pass through. With [isOn] false (the person turned
 * the fade off, [com.plumemu.coreui.theme.PlumTheme.edgeFadeOn]) it adds nothing: the edges stay flat.
 */
fun Modifier.listEdgeFadeMask(
    fades: EdgeFades,
    isOn: Boolean,
): Modifier =
    if (!isOn) this else semantics { this[EdgeFadesKey] = fades }
        .snapToPixels { fades.canScroll }
        .drawWithContent {
            if (!fades.canScroll) {
                drawContent()
                return@drawWithContent
            }
            val fadePx = PlumDimens.listFadeBand.toPx()
            val h = size.height
            val top = fades.top(fadePx, h).coerceIn(0f, h)
            val bottom = fades.bottom(fadePx, h).coerceIn(0f, h)
            val clearTop = fades.clearTop(h).coerceIn(0f, h)
            val clearBottom = fades.clearBottom(h).coerceIn(0f, h)
            val ops = ArrayList<MaskOp>(4)
            if (clearTop > 0f) ops.add(MaskOp(0f, clearTop, null))
            if (clearBottom > 0f) ops.add(MaskOp(h - clearBottom, h, null))
            if (top > 0f) ops.add(MaskOp(0f, top, maskBrush(atBottom = false, from = 0f, length = top)))
            if (bottom > 0f) ops.add(MaskOp(h - bottom, h, maskBrush(atBottom = true, from = h - bottom, length = bottom)))
            drawMasked(ops)
        }

/**
 * [listEdgeFadeMask] for a list that keeps [room] px past each end of its visible window (a [com.plumemu.coreui.theme.ListGlide]'s taller layout, see
 * [com.plumemu.coreui.theme.ListGlide.withFadeMask]): the mask then covers the room too (same banding as [listEdgeFadeMask]), so the slab of a first or last row is not cut at the window.
 * With [rampsOn] false only the cut rows are hidden, no ramp is drawn (the fade setting is off). The fade is measured on the window. Where the list continues past an edge (it fades there) the room is cleared, so a row cut by the window shows no more of itself; where it ends the room stays open for the slab.
 */
internal fun Modifier.listEdgeFadeMaskInRoom(
    fades: EdgeFades,
    rampsOn: Boolean = true,
    room: () -> Float,
): Modifier =
    // EdgeFadesKey says a fade is drawn: absent when only cut rows are hidden.
    this.semantics {
        this[EdgeClearsKey] = fades
        if (rampsOn) this[EdgeFadesKey] = fades
    }
        .snapToPixels { fades.canScroll }
        .drawWithContent {
            if (!fades.canScroll) {
                drawContent()
                return@drawWithContent
            }
            val pad = room()
            val height = size.height - 2f * pad
            if (height <= 0f) {
                drawContent()
                return@drawWithContent
            }
            val fadePx = PlumDimens.listFadeBand.toPx()
            val top = if (rampsOn) fades.top(fadePx, height).coerceIn(0f, height) else 0f
            val bottom = if (rampsOn) fades.bottom(fadePx, height).coerceIn(0f, height) else 0f
            val clearTop = fades.clearTop(height).coerceIn(0f, height)
            val clearBottom = fades.clearBottom(height).coerceIn(0f, height)
            val ops = ArrayList<MaskOp>(6)
            if (clearTop > 0f) ops.add(MaskOp(0f, pad + clearTop, null))
            if (clearBottom > 0f) ops.add(MaskOp(pad + height - clearBottom, size.height, null))
            if (top > 0f) {
                ops.add(MaskOp(0f, pad, null))
                ops.add(MaskOp(pad, pad + top, maskBrush(atBottom = false, from = pad, length = top)))
            }
            if (bottom > 0f) {
                val from = pad + height - bottom
                ops.add(MaskOp(pad + height, size.height, null))
                ops.add(MaskOp(from, pad + height, maskBrush(atBottom = true, from = from, length = bottom)))
            }
            drawMasked(ops)
        }

/** One vertical mask rectangle over the node's full width: [brush] null clears it, else a `DstIn` ramp. */
private class MaskOp(val top: Float, val bottom: Float, val brush: Brush?)

/**
 * Draws the content masked by [ops] (multiplied, as consecutive `DstIn` rects were) without an offscreen layer over the whole node: the
 * rows the mask leaves alone are drawn straight, a row fully cleared is skipped, and only the rows an op touches (its edges snapped out to
 * whole px, so a fractional edge keeps its partly covered row) go through a layer of just that strip. Every strip is clipped to the
 * node's width like the old node-wide layer was.
 */
private fun ContentDrawScope.drawMasked(ops: List<MaskOp>) {
    val w = size.width
    val h = size.height
    val cuts = ArrayList<Float>(2 + ops.size * 4)
    cuts.add(0f)
    cuts.add(h)
    for (op in ops) {
        for (e in floatArrayOf(op.top, op.bottom)) {
            cuts.add(floor(e).coerceIn(0f, h))
            cuts.add(ceil(e).coerceIn(0f, h))
        }
    }
    cuts.sort()
    var layerFrom = -1f
    var layerTo = -1f
    var prev = cuts[0]
    for (i in 1 until cuts.size) {
        val a = prev
        val b = cuts[i]
        if (b <= a) continue
        prev = b
        var cleared = false
        var touched = false
        for (op in ops) {
            if (op.top < b && op.bottom > a) {
                touched = true
                if (op.brush == null && op.top <= a && op.bottom >= b) cleared = true
            }
        }
        if (touched && !cleared) {
            if (layerFrom < 0f) layerFrom = a
            layerTo = b
            continue
        }
        if (layerFrom >= 0f) {
            drawMaskedStrip(layerFrom, layerTo, w, ops)
            layerFrom = -1f
        }
        if (!touched) clipRect(0f, a, w, b) { this@drawMasked.drawContent() }
    }
    if (layerFrom >= 0f) drawMaskedStrip(layerFrom, layerTo, w, ops)
}

private fun ContentDrawScope.drawMaskedStrip(
    from: Float,
    to: Float,
    w: Float,
    ops: List<MaskOp>,
) {
    clipRect(0f, from, w, to) {
        drawIntoCanvas { canvas ->
            val layer = Paint()
            canvas.saveLayer(Rect(0f, from, w, to), layer)
            this@drawMaskedStrip.drawContent()
            for (op in ops) {
                if (op.top >= to || op.bottom <= from) continue
                if (op.brush == null) {
                    drawRect(Color.Transparent, Offset(0f, op.top), Size(w, op.bottom - op.top), blendMode = BlendMode.DstIn)
                } else {
                    drawRect(op.brush, Offset(0f, op.top), Size(w, op.bottom - op.top), blendMode = BlendMode.DstIn)
                }
            }
            canvas.restore()
        }
    }
}

/** The mask ramp over [length] px from [from], the same curve as [ListEdgeFade]: at the bottom opaque to clear, at the top clear to opaque. */
internal fun maskBrush(
    atBottom: Boolean,
    from: Float,
    length: Float,
): Brush =
    if (atBottom) {
        Brush.verticalGradient(0f to MaskOpaque, 1f to Color.Transparent, startY = from, endY = from + length)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 1f to MaskOpaque, startY = from, endY = from + length)
    }
