package com.plumemu.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.components.coverBoxShadow
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.home.CoverImages
import com.plumemu.home.PosterCover

/** A cover's width in its collar: the box at [height] in the picture's shape (else the system's), and the collar's sides. */
internal fun entryCoverWidth(
    game: EntryGame,
    systems: Map<String, EntrySystemLook>,
    height: Dp,
    fallbackAspect: Float,
): Dp = height * entryCoverAspect(game, systems, fallbackAspect) + CollarSize.Cover.sidePadding * 2

/** A cover's collar height: the box and the collar above and below it. */
internal fun entryCoverCell(height: Dp): Dp = height + CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding

internal fun entryCoverAspect(
    game: EntryGame,
    systems: Map<String, EntrySystemLook>,
    fallbackAspect: Float,
): Float = game.art?.aspect ?: systems[game.systemId]?.aspect ?: fallbackAspect

/**
 * One cover: the whole box front in a thin collar, at [height] inside the collar and its own shape, never cropped; the poster in
 * the system's shape where there is no picture (box art off, none for the game, or its file cannot be read). The picture is decoded at the
 * size drawn by the home screen's loader, off the main thread; the poster is drawn on the fly.
 */
@Composable
internal fun EntryCover(
    game: EntryGame,
    systems: Map<String, EntrySystemLook>,
    fallbackAspect: Float,
    height: Dp,
    images: CoverImages,
    modifier: Modifier = Modifier,
) {
    val look = systems[game.systemId]
    var unreadable by remember(game.art) { mutableStateOf(false) }
    val picture = game.art?.takeIf { !unreadable }
    val aspect = if (picture != null) picture.aspect else look?.aspect ?: fallbackAspect
    val size = DpSize(height * aspect, height)
    val corner = CollarDimens.coverBoxCorner
    Box(modifier.collar(CollarSize.Cover, corner, halo = false) { 0f }.testTag(EntryTags.COVER).semantics { contentDescription = game.title }) {
        Box(Modifier.size(size).coverBoxShadow(corner).clip(RoundedCornerShape(corner))) {
            if (picture != null) {
                val density = LocalDensity.current
                val widthPx = with(density) { size.width.roundToPx() }.coerceAtLeast(1)
                val heightPx = with(density) { size.height.roundToPx() }.coerceAtLeast(1)
                val cached = remember(images, picture, widthPx, heightPx) { images.cached(picture, widthPx, heightPx) }
                var bitmap by remember(images, picture, widthPx, heightPx) { mutableStateOf(cached) }
                if (cached == null) {
                    LaunchedEffect(images, picture, widthPx, heightPx) {
                        val loaded = images.load(picture, widthPx, heightPx)
                        if (loaded == null) unreadable = true else bitmap = loaded
                    }
                }
                bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize()) }
            } else {
                val full = look?.name.orEmpty()
                PosterCover(game.title, full.ifEmpty { look?.short.orEmpty() }, look?.short.orEmpty().ifEmpty { full }, Modifier.fillMaxSize())
            }
        }
    }
}

/** A cover with the time it was played under it and, on Recent, its system: the cell is as wide as the widest of them. */
@Composable
internal fun CoverWithCaption(
    game: EntryGame,
    systems: Map<String, EntrySystemLook>,
    fallbackAspect: Float,
    height: Dp,
    images: CoverImages,
    now: Long,
    isShort: Boolean,
    showsSystem: Boolean,
    styles: EntryStyles,
) {
    Column {
        EntryCover(game, systems, fallbackAspect, height, images)
        Spacer(Modifier.height(EntryCardDimens.captionGap))
        game.playedAt?.let { at ->
            BasicText(
                agoText(agoOf(at, now), isShort),
                style = styles.captionWhen,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = EntryCardDimens.captionInset).boardLine(styles.captionWhen, styles).testTag(EntryTags.WHEN),
            )
        }
        if (showsSystem) {
            systems[game.systemId]?.let { it.caption.ifEmpty { it.name } }?.takeIf { it.isNotEmpty() }?.let { name ->
                if (game.playedAt != null) Spacer(Modifier.height(EntryCardDimens.captionGap))
                BasicText(
                    name,
                    style = styles.captionSystem,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = EntryCardDimens.captionInset).boardLine(styles.captionSystem, styles).testTag(EntryTags.CAPTION_SYSTEM),
                )
            }
        }
    }
}

/**
 * Cells left to right with [gap] between, as many as fit the width: one that does not fit is left out whole, with the ones after it. Every cell is placed on the top edge.
 */
@Composable
internal fun CoverRow(
    count: Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (Int) -> Unit,
) {
    SubcomposeLayout(modifier.testTag(EntryTags.COVERS)) { constraints ->
        val gapPx = gap.roundToPx()
        var x = 0
        var tallest = 0
        val placed = mutableListOf<Pair<androidx.compose.ui.layout.Placeable, Int>>()
        for (at in 0 until count) {
            val measured = subcompose(at) { cell(at) }.map { it.measure(Constraints()) }
            val width = measured.maxOfOrNull { it.width } ?: 0
            val start = if (at == 0) 0 else x + gapPx
            if (at > 0 && start + width > constraints.maxWidth) {
                // Left out whole, with the ones after it: nothing is composed for them (this one goes at the next pass).
                break
            }
            measured.forEach { placed += it to start }
            x = start + width
            tallest = maxOf(tallest, measured.maxOfOrNull { it.height } ?: 0)
        }
        layout(x.coerceAtMost(constraints.maxWidth), tallest) { placed.forEach { (placeable, left) -> placeable.placeRelative(left, 0) } }
    }
}

/** The shape a poster takes where the game's system is not known: the usual landscape box. */
internal val DEFAULT_COVER_ASPECT: Float = CoverShape.Landscape.widthOverHeight

/**
 * Greedy rows: each cover goes on the row it fits, a new row starting when the next does not (at most [maxRows]); the indices of the covers
 * per row. A cover wider than a row stands alone on it. [widths] are px, [gap] too.
 */
internal fun packRows(
    widths: List<Int>,
    gap: Int,
    maxWidth: Int,
    maxRows: Int,
): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = 0
    for (at in widths.indices) {
        val current = rows.lastOrNull()
        if (current != null && used + gap + widths[at] <= maxWidth) {
            current += at
            used += gap + widths[at]
        } else {
            if (rows.size == maxRows) break
            rows += mutableListOf(at)
            used = widths[at]
        }
    }
    return rows
}
