package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
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
 */
@Composable
fun ListEdgeFade(
    atBottom: Boolean,
    modifier: Modifier = Modifier,
    reach: () -> Float = { 1f },
) {
    val colors = PlumTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlumDimens.listEdgeFade)
                .drawBehind {
                    val band = size.height * reach().coerceIn(0f, 1f)
                    if (band <= 0f) return@drawBehind
                    val clear = colors.bg.copy(alpha = 0f)
                    val from = if (atBottom) size.height - band else 0f
                    drawRect(
                        Brush.verticalGradient(if (atBottom) listOf(clear, colors.bg) else listOf(colors.bg, clear), startY = from, endY = from + band),
                        topLeft = Offset(0f, from),
                        size = Size(size.width, band),
                    )
                },
    )
}

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
}

/** The [EdgeFades] of a node drawn with [listEdgeFadeMask], for tests. */
val EdgeFadesKey = SemanticsPropertyKey<EdgeFades>("EdgeFades")

/**
 * Fades the content itself to transparent over [PlumDimens.listEdgeFade] at the top and bottom edges [fades] asks
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
            val fadePx = PlumDimens.listEdgeFade.toPx()
            val top = fades.top(fadePx, size.height).coerceIn(0f, size.height)
            val bottom = fades.bottom(fadePx, size.height).coerceIn(0f, size.height)
            if (top > 0f) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, MaskOpaque), startY = 0f, endY = top), size = Size(size.width, top), blendMode = BlendMode.DstIn)
            }
            if (bottom > 0f) {
                val from = size.height - bottom
                drawRect(
                    Brush.verticalGradient(listOf(MaskOpaque, Color.Transparent), startY = from, endY = size.height),
                    topLeft = Offset(0f, from),
                    size = Size(size.width, bottom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
