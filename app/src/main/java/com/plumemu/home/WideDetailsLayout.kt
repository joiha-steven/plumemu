package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumDimens

/**
 * The wide details column: [cover], then [titleBlock] (the title and its fact lines), then [rest] (the
 * facts and the synopsis) in the height left, [PlumDimens.detailsBlockGapWide] apart, like a Column.
 * The title block gives way when the cover at its floor and the PLAYED / LAST PLAYED row do not fit with it: the line under the title leaves first, then the
 * title's second line ([TitleBlock]; the full name is in the focused row). The cover is [PlumDimens.detailsCoverHeightWide] high unless the title block and the facts would then not fit whole: it gives up the
 * room they need, but never goes under [PlumDimens.detailsCoverMinWide] (then the facts' cells leave, HomeDetailsBlock). It gives up the few dp the title block needs (Largest in Serif on 432 high, since the type ramp
 * puts the title at 20), so a fact line is never cut to make room for a taller cover. Under a title of two lines it may go [SQUEEZED_COVER] of that floor, so the three rows of facts stay.
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
    /** What follows [rest] in the height left, cut or left out before the cover gives anything (the battery line, the synopsis). */
    tail: @Composable ColumnScope.() -> Unit = {},
    /**
     * A tall box ([TALL_BELOW]): [titleBlock] stands beside [cover] (which has the width it needs), top-aligned with the box, not under it,
     * and does not count in the cover's room. The cover is the band's whole height. If no cover is left (the text-first floor) the title goes under, as always.
     */
    sideTitle: Boolean = false,
) {
    // Kept as the same object while [rest] is: a new lambda would compose its slot again.
    val restContent: @Composable () -> Unit =
        remember(rest) { { Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsBlockGapWide), content = rest) } }
    val tailContent: @Composable () -> Unit =
        remember(tail) { { Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsBlockGapWide), content = tail) } }
    Layout(contents = listOf(cover, titleBlock, restContent, tailContent), modifier = modifier) { (coverParts, titleParts, restParts, tailParts), constraints ->
        val gap = PlumDimens.detailsBlockGapWide.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedHeight
        val full = PlumDimens.detailsCoverHeightWide.roundToPx()
        // The facts block read whole (the cover gives up height for it, after the title block) and at its least (PLAYED and LAST PLAYED, tightest gaps).
        val factsHeight = if (minCover == null && bounded) restParts.sumOf { it.maxIntrinsicHeight(constraints.maxWidth) } else 0
        val timeRowHeight = if (minCover == null && bounded) restParts.sumOf { it.minIntrinsicHeight(constraints.maxWidth) } else 0
        // What the title block may take: the cover at its floor and the time row both stay, so a title keeps its second line only when they still fit.
        val titleBudget =
            if (minCover == null && bounded) {
                val coverFloor = minOf(full, PlumDimens.detailsCoverMinWide.roundToPx())
                val restRoom = if (timeRowHeight > 0) PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(gap) + timeRowHeight else 0
                (constraints.maxHeight - coverFloor - gap - restRoom).takeIf { it > 0 } ?: Constraints.Infinity
            } else {
                Constraints.Infinity
            }
        // Beside the cover the title is measured after it (it needs the cover's width); until then it takes no room.
        val titleStacked = if (sideTitle) emptyList() else titleParts.map { it.measure(loose.copy(maxHeight = titleBudget)) }
        val titleHeightForCover = titleStacked.sumOf { it.height }
        // Text first: the whole rest is measured before the cover is sized.
        val wholeRest = if (minCover != null) restParts.map { it.measure(loose.copy(maxHeight = if (bounded) constraints.maxHeight else Constraints.Infinity)) } else null
        val wholeRestHeight = wholeRest?.sumOf { it.height } ?: 0
        val coverPx = coverHeight(constraints, minCover, gap, full, titleHeightForCover, factsHeight, wholeRestHeight)
        val cover = coverParts.map { it.measure(loose.copy(minHeight = coverPx, maxHeight = coverPx)) }
        val besideTitle = sideTitle && coverPx > 0
        val coverWidth = cover.maxOfOrNull { it.width } ?: 0
        val topPad = CollarSize.Cover.topPadding.roundToPx()
        val title =
            when {
                besideTitle -> titleParts.map { it.measure(loose.copy(maxWidth = (constraints.maxWidth - coverWidth - gap).coerceAtLeast(0), maxHeight = (coverPx - topPad).coerceAtLeast(0))) }
                sideTitle -> titleParts.map { it.measure(loose.copy(maxHeight = titleBudget)) }
                else -> titleStacked
            }
        val titleHeight = if (besideTitle) 0 else title.sumOf { it.height }
        // No cover: the title starts at the top, without the gap that would stand above it. Beside the cover, the band is the cover's height.
        val coverBlock = if (coverPx == 0) 0 else if (besideTitle) coverPx else coverPx + gap
        val used = coverBlock + titleHeight
        // With no cover left to give up (at its floor, or none), the gap under the title gives next (down to the facts' own tight gap), before the facts do.
        val restGap =
            if (minCover == null && bounded && factsHeight > 0) {
                (constraints.maxHeight - used - factsHeight).coerceIn(PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(gap), gap)
            } else {
                gap
            }
        val restMax = if (bounded) (constraints.maxHeight - used - restGap).coerceAtLeast(0) else Constraints.Infinity
        val rest = wholeRest ?: restParts.map { it.measure(loose.copy(maxHeight = restMax)) }
        val tailMax = (restMax - rest.sumOf { it.height } - gap).coerceAtLeast(0)
        val tailPlaced = tailParts.map { it.measure(loose.copy(maxHeight = if (bounded) tailMax else Constraints.Infinity)) }
        val restHeight = rest.sumOf { it.height } + tailPlaced.sumOf { it.height }.let { if (it > 0 && rest.sumOf { r -> r.height } > 0) it + gap else it }
        // Without a title block (the systems focused) the column is the cover alone, as before.
        val height = if (titleHeight == 0 && !besideTitle) coverPx else used + if (restHeight > 0) restGap + restHeight else 0
        val width = (cover + title + rest).maxOfOrNull { it.width } ?: 0
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            cover.forEach { it.place(0, 0) }
            var y = coverBlock
            if (besideTitle) {
                title.forEach {
                    it.place(coverWidth + gap, topPad)
                }
            } else {
                title.forEach {
                    it.place(0, y)
                    y += it.height
                }
            }
            y += restGap
            rest.forEach {
                it.place(0, y)
                y += it.height
            }
            if (rest.sumOf { it.height } > 0 && tailPlaced.sumOf { it.height } > 0) y += gap
            tailPlaced.forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}

/**
 * The cover's height in px: with [minCover] what the text leaves (left out under it); otherwise the full height unless the title and the facts need room, never under
 * the cover's floor (the facts give way) except by [SQUEEZED_COVER] of it when that keeps all their rows. [titleHeightForCover] is the title's stacked height, 0 when it stands beside the cover.
 */
private fun MeasureScope.coverHeight(
    constraints: Constraints,
    minCover: Dp?,
    gap: Int,
    full: Int,
    titleHeightForCover: Int,
    factsHeight: Int,
    wholeRestHeight: Int,
): Int {
    val bounded = constraints.hasBoundedHeight
    return if (minCover != null) {
        val room = if (bounded) constraints.maxHeight - gap - titleHeightForCover - (if (wholeRestHeight > 0) gap + wholeRestHeight else 0) else full
        minOf(full, room).let { if (it < minCover.roundToPx()) 0 else it }
    } else if (bounded) {
        // The cover comes first: it keeps [PlumDimens.detailsCoverMinWide] and the facts give way (their cells leave whole, HomeDetailsBlock).
        // But the title is known in the first frame: when the cover need only go as far as [SQUEEZED_COVER] of its floor for the facts to keep all their
        // rows (a title of two lines), it does, and nothing leaves.
        val factsGap = PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(gap)
        val roomFull = constraints.maxHeight - gap - titleHeightForCover - (if (factsHeight > 0) gap + factsHeight else 0)
        // The gap under the title may shrink to the facts' own tight gap (restGap below), so that is the least the facts need.
        val roomTight = constraints.maxHeight - gap - titleHeightForCover - (if (factsHeight > 0) factsGap + factsHeight else 0)
        val floor = minOf(full, PlumDimens.detailsCoverMinWide.roundToPx(), (constraints.maxHeight - gap - titleHeightForCover).coerceAtLeast(0))
        when {
            roomFull >= floor -> minOf(full, roomFull)
            roomTight >= (floor * SQUEEZED_COVER).toInt() -> minOf(floor, roomTight)
            else -> floor
        }
    } else {
        full
    }
}

/**
 * The title and the lines under it, [gap] apart, as a Column while they fit the height given. When they do not, the lines under the title leave
 * first (left out, not measured), then the title takes fewer lines (an ellipsis ends the last): measured, never by font or size.
 */
@Composable
internal fun TitleBlock(
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val g = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        // Read from the intrinsics first: a measurable is measured once.
        val wholeHeight = measurables.sumOf { it.maxIntrinsicHeight(loose.maxWidth) } + g * (measurables.size - 1).coerceAtLeast(0)
        val fits = !constraints.hasBoundedHeight || wholeHeight <= constraints.maxHeight
        // When they do not fit, the title alone, in the height given: it keeps its second line only if that fits.
        val all = (if (fits) measurables else measurables.take(1)).map { it.measure(if (fits) loose else loose.copy(maxHeight = constraints.maxHeight)) }
        layout(all.maxOfOrNull { it.width } ?: 0, all.sumOf { it.height } + g * (all.size - 1).coerceAtLeast(0)) {
            var y = 0
            all.forEach {
                it.place(0, y)
                y += it.height + g
            }
        }
    }
}

/** A box narrower than this (width over height) is Tall: the wide column puts the title beside it. */
internal const val TALL_BELOW = 0.85f

/** The least share of [PlumDimens.detailsCoverMinWide] the cover keeps when the title and the facts need the room. */
private const val SQUEEZED_COVER = 0.77f
