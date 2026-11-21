package com.plumemu.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.EllipsisText
import com.plumemu.coreui.theme.PlumTheme

/**
 * The details title: the main title (at most [maxLines]) and, when the title has one ([splitTitle]), its subtitle on a line of its own in
 * `gameTitle` (16) and `text2`, at most [subtitleLines], cut at the end. Two nodes, so [TitleBlock] can leave the subtitle out before it cuts the main title.
 */
@Composable
internal fun DetailsTitle(
    title: String,
    maxLines: Int = TITLE_LINES,
    subtitleLines: Int = TITLE_SUBTITLE_LINES,
) {
    val layout = PlumTheme.layout
    val colors = PlumTheme.colors
    val split = remember(title) { splitTitle(title) }
    BasicText(
        text = split.main,
        style = PlumTheme.type.detailsTitle(layout.widthClass).copy(color = colors.text, lineBreak = LineBreak.Heading),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(HomeTags.DETAILS_TITLE),
    )
    if (split.subtitle.isNotEmpty()) {
        EllipsisText(
            text = split.subtitle,
            style = PlumTheme.type.gameTitle.copy(lineBreak = LineBreak.Heading),
            color = { colors.text2 },
            maxLines = subtitleLines,
            wholeWords = true,
            modifier = Modifier.testTag(HomeTags.DETAILS_TITLE_SUBTITLE),
        )
    }
}

/**
 * The narrow stop's title: the main title and a one-line subtitle, but the subtitle leaves when the main title already takes a second line.
 * The stop has no room for both (at the largest sizes they pushed the facts past the screen); measured, never by size.
 */
@Composable
internal fun NarrowTitle(
    title: String,
    gap: Dp,
) {
    val measurer = rememberTextMeasurer()
    val style = PlumTheme.type.detailsTitle(PlumTheme.layout.widthClass)
    val oneLine = remember(measurer, style) { measurer.measure(HELD_LINE, style, maxLines = 1).size.height }
    Layout(content = { DetailsTitle(title, subtitleLines = NARROW_SUBTITLE_LINES) }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val main = measurables.first().measure(loose)
        val subtitle = measurables.getOrNull(1)?.takeIf { main.height <= oneLine + 1 }?.measure(loose)
        val g = gap.roundToPx()
        layout(maxOf(main.width, subtitle?.width ?: 0), main.height + (subtitle?.let { it.height + g } ?: 0)) {
            main.place(0, 0)
            subtitle?.place(0, main.height + g)
        }
    }
}

private const val NARROW_SUBTITLE_LINES = 1
