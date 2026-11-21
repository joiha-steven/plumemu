package com.plumemu.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.floor

/** A tall box in the wide column: as wide as its shape needs at the band's height; [WideDetailsLayout] puts the title beside it. */
@Composable
internal fun SideTitleCover(
    slot: ArtSlot,
    poster: PosterSpec,
    boxAspect: Float,
    game: GameRowModel,
) {
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        val h = maxHeight
        val boxH = h - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding
        val w = boxH * boxAspect + CollarSize.Cover.sidePadding * 2
        CollaredDetailsCover(slot, DpSize(w, h), poster, boxAspect, game.appPackage, game.artAspect)
    }
}

/**
 * Whether the title stands whole beside a box of [aspect]: in the room the box leaves (at the band's full height, the widest the box gets) it takes at
 * most [SIDE_TITLE_LINES] lines (its subtitle part, [splitTitle], at most [TITLE_SUBTITLE_LINES] more), none ends in an ellipsis and none breaks inside a word. Measured with the title's own style, not guessed; when it does not
 * fit, the title stays under the box as for a wide one.
 */
@Composable
internal fun titleFitsBeside(
    title: String,
    aspect: Float,
    columnWidth: Dp,
    /** The line(s) under the title beside the box: its style, its most lines (held: counted whether or not it has text yet, so a late value flips nothing) and the gap above it. */
    subtitleStyle: TextStyle = PlumTheme.type.detailsSubtitle,
    subtitleLines: Int = SIDE_SUBTITLE_LINES,
    subtitleGap: Dp = PlumDimens.detailsSubtitleGap,
): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = PlumTheme.type.detailsTitle(PlumTheme.layout.widthClass).copy(lineBreak = LineBreak.Heading)
    val partStyle = PlumTheme.type.gameTitle.copy(lineBreak = LineBreak.Heading)
    return remember(title, aspect, columnWidth, style, partStyle, density, measurer, subtitleStyle, subtitleLines, subtitleGap) {
        val split = splitTitle(title)
        val main = split.main
        val boxHeight = PlumDimens.detailsCoverHeightWide - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding
        val room = columnWidth - (boxHeight * aspect + CollarSize.Cover.sidePadding * 2) - PlumDimens.detailsBlockGapWide
        val roomPx = with(density) { room.roundToPx() }
        if (roomPx <= 0) {
            false
        } else {
            val result = measurer.measure(AnnotatedString(main), style, overflow = TextOverflow.Ellipsis, maxLines = SIDE_TITLE_LINES, constraints = Constraints(maxWidth = roomPx))
            // The title's own subtitle line(s) ([DetailsTitle]) must stand whole too.
            val part = split.subtitle.takeIf { it.isNotEmpty() }?.let { measurer.measure(AnnotatedString(it), partStyle, overflow = TextOverflow.Ellipsis, maxLines = TITLE_SUBTITLE_LINES, constraints = Constraints(maxWidth = roomPx)) }
            val partPx = part?.let { it.size.height + with(density) { subtitleGap.roundToPx() } } ?: 0
            // The title and its line(s) under it must stand whole in the band at its least height (the cover's floor), else the block would lose a line.
            val subtitleHeight = measurer.measure(HELD_LINE, subtitleStyle, maxLines = 1).size.height * subtitleLines
            val bandPx = with(density) { (PlumDimens.detailsCoverMinWide - CollarSize.Cover.topPadding).roundToPx() }
            val blockPx = result.size.height + partPx + with(density) { subtitleGap.roundToPx() } + subtitleHeight
            !result.didOverflowHeight && !result.didOverflowWidth && part?.hasVisualOverflow != true && blockPx <= bandPx &&
                (0 until result.lineCount).all { line ->
                    val end = result.getLineEnd(line)
                    !result.isLineEllipsized(line) && (end >= main.length || main[end - 1].isWhitespace() || main[end - 1] == '-' || main[end].isWhitespace())
                }
        }
    }
}

internal const val SIDE_TITLE_LINES = 3
internal const val SIDE_SUBTITLE_LINES = 2
