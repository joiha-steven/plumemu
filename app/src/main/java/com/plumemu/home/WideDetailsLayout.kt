package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens

/**
 * The wide details column: [cover], then [titleBlock] (the title and its fact lines), then [rest] (the
 * facts and the synopsis) in the height left, [PlumDimens.detailsBlockGapWide] apart, like a Column.
 * The cover is [PlumDimens.detailsCoverHeightWide] high unless the title block would then not fit whole:
 * then it gives up the few dp the block needs (Largest in Serif on 432 high, since the type ramp
 * puts the title at 20), so a fact line is never cut to make room for a taller cover.
 *
 * With [minCover] set (the text-only column of a game without box art, whose poster is decoration) the text comes first: the
 * title block and the rest are measured whole, and the cover takes what height is left, up to the full height; under
 * [minCover] it is left out (zero high, no gap above the title) so no line of the text is ever cut for it.
 *
 * One plain layout pass, nothing written to state: the title block is measured first, then the cover at
 * exactly the height left ([cover] fills the height it is given), so the cover and the title move in the
 * same frame and a title that takes another line recomposes nothing.
 */
@Composable
internal fun WideDetailsLayout(
    cover: @Composable () -> Unit,
    titleBlock: @Composable () -> Unit,
    rest: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    minCover: Dp? = null,
) {
    // Kept as the same object while [rest] is: a new lambda would compose its slot again.
    val restContent: @Composable () -> Unit =
        remember(rest) { { Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsBlockGapWide), content = rest) } }
    Layout(contents = listOf(cover, titleBlock, restContent), modifier = modifier) { (coverParts, titleParts, restParts), constraints ->
        val gap = PlumDimens.detailsBlockGapWide.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedHeight
        val title = titleParts.map { it.measure(loose.copy(maxHeight = Constraints.Infinity)) }
        val titleHeight = title.sumOf { it.height }
        val full = PlumDimens.detailsCoverHeightWide.roundToPx()
        // Text first: the whole rest is measured before the cover is sized.
        val wholeRest = if (minCover != null) restParts.map { it.measure(loose.copy(maxHeight = if (bounded) constraints.maxHeight else Constraints.Infinity)) } else null
        val wholeRestHeight = wholeRest?.sumOf { it.height } ?: 0
        val coverPx =
            if (minCover != null) {
                val room = if (bounded) constraints.maxHeight - gap - titleHeight - (if (wholeRestHeight > 0) gap + wholeRestHeight else 0) else full
                minOf(full, room).let { if (it < minCover.roundToPx()) 0 else it }
            } else if (bounded) {
                minOf(full, constraints.maxHeight - gap - titleHeight).coerceAtLeast(0)
            } else {
                full
            }
        val cover = coverParts.map { it.measure(loose.copy(minHeight = coverPx, maxHeight = coverPx)) }
        // No cover: the title starts at the top, without the gap that would stand above it.
        val coverBlock = if (minCover != null && coverPx == 0) 0 else coverPx + gap
        val used = coverBlock + titleHeight
        val restMax = if (bounded) (constraints.maxHeight - used - gap).coerceAtLeast(0) else Constraints.Infinity
        val rest = wholeRest ?: restParts.map { it.measure(loose.copy(maxHeight = restMax)) }
        val restHeight = rest.sumOf { it.height }
        // Without a title block (the systems focused) the column is the cover alone, as before.
        val height = if (titleHeight == 0) coverPx else used + if (restHeight > 0) gap + restHeight else 0
        val width = (cover + title + rest).maxOfOrNull { it.width } ?: 0
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            cover.forEach { it.place(0, 0) }
            var y = coverBlock
            title.forEach {
                it.place(0, y)
                y += it.height
            }
            y += gap
            rest.forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}
