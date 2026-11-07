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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.MaskOpaque
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

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
    /** Whether the list can scroll at all: the mask's offscreen layer is kept while it can, not made per scroll. */
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
 * for: an alpha mask (offscreen layer, `DstIn` gradient), so it is right on any background and a cover's
 * shadow fades with it, where [ListEdgeFade] paints the background over the rows. Put it on the list's visible
 * bounds (before a glide's taller layout). Draws only; touches pass through. With [isOn] false (the person turned
 * the fade off, [com.plumemu.coreui.theme.PlumTheme.edgeFadeOn]) it adds nothing: the edges stay flat.
 */
fun Modifier.listEdgeFadeMask(
    fades: EdgeFades,
    isOn: Boolean,
): Modifier =
    if (!isOn) this else semantics { this[EdgeFadesKey] = fades }
        .graphicsLayer { compositingStrategy = if (fades.canScroll) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val fadePx = PlumDimens.listFadeBand.toPx()
            val top = fades.top(fadePx, size.height).coerceIn(0f, size.height)
            val bottom = fades.bottom(fadePx, size.height).coerceIn(0f, size.height)
            val clearTop = fades.clearTop(size.height).coerceIn(0f, size.height)
            val clearBottom = fades.clearBottom(size.height).coerceIn(0f, size.height)
            if (clearTop > 0f) drawRect(Color.Transparent, size = Size(size.width, clearTop), blendMode = BlendMode.DstIn)
            if (clearBottom > 0f) drawRect(Color.Transparent, topLeft = Offset(0f, size.height - clearBottom), size = Size(size.width, clearBottom), blendMode = BlendMode.DstIn)
            if (top > 0f) {
                drawRect(maskBrush(atBottom = false, from = 0f, length = top), size = Size(size.width, top), blendMode = BlendMode.DstIn)
            }
            if (bottom > 0f) {
                val from = size.height - bottom
                drawRect(
                    maskBrush(atBottom = true, from = from, length = bottom),
                    topLeft = Offset(0f, from),
                    size = Size(size.width, bottom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/**
 * [listEdgeFadeMask] for a list that keeps [room] px past each end of its visible window (a [com.plumemu.coreui.theme.ListGlide]'s taller layout, see
 * [com.plumemu.coreui.theme.ListGlide.withFadeMask]): the offscreen layer then holds the room too, so the slab of a first or last row is not cut at the window.
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
        .graphicsLayer { compositingStrategy = if (fades.canScroll) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val pad = room()
            val height = size.height - 2f * pad
            if (height <= 0f) return@drawWithContent
            val fadePx = PlumDimens.listFadeBand.toPx()
            val top = if (rampsOn) fades.top(fadePx, height).coerceIn(0f, height) else 0f
            val bottom = if (rampsOn) fades.bottom(fadePx, height).coerceIn(0f, height) else 0f
            val clearTop = fades.clearTop(height).coerceIn(0f, height)
            val clearBottom = fades.clearBottom(height).coerceIn(0f, height)
            if (clearTop > 0f) drawRect(Color.Transparent, size = Size(size.width, pad + clearTop), blendMode = BlendMode.DstIn)
            if (clearBottom > 0f) drawRect(Color.Transparent, topLeft = Offset(0f, pad + height - clearBottom), size = Size(size.width, clearBottom + pad), blendMode = BlendMode.DstIn)
            if (top > 0f) {
                drawRect(Color.Transparent, size = Size(size.width, pad), blendMode = BlendMode.DstIn)
                drawRect(maskBrush(atBottom = false, from = pad, length = top), topLeft = Offset(0f, pad), size = Size(size.width, top), blendMode = BlendMode.DstIn)
            }
            if (bottom > 0f) {
                val from = pad + height - bottom
                drawRect(Color.Transparent, topLeft = Offset(0f, pad + height), size = Size(size.width, pad), blendMode = BlendMode.DstIn)
                drawRect(
                    maskBrush(atBottom = true, from = from, length = bottom),
                    topLeft = Offset(0f, from),
                    size = Size(size.width, bottom),
                    blendMode = BlendMode.DstIn,
                )
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
