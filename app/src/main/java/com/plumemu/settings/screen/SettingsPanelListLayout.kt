package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens

/**
 * A panel of [header] parts (title, text, line: each a whole block), a list and a [footer] fact, [SlabDimens.listEndRoom] under the list: the list always
 * keeps room for two rows and for its tallest row with the peeks around it ([PanelListRoom]), so there is something to
 * pick at the largest text and a focused card is whole. When the header and footer leave
 * less, the footer goes first, then header parts leave from the last one; a part is shown whole or not at all
 * (never cut mid-line). The list takes what is left and scrolls, ending [SlabDimens.listEndRoom] above the panel's foot so the last
 * row's shadow is whole, and as far below the bar's gap when no part is left; with few rows the footer follows the last one.
 */
@Composable
internal fun PanelListLayout(
    modifier: Modifier,
    header: List<@Composable () -> Unit>,
    list: @Composable () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val keep = PlumTheme.layout.rowHeight * 2 + PlumDimens.listRowGap
    val gap = PlumDimens.settingsPanelListGap
    val room = remember { PanelListRoom() }
    val reporting: @Composable () -> Unit = remember(list) { { CompositionLocalProvider(LocalPanelListRoom provides room, content = list) } }
    Layout(contents = header + listOf(reporting, footer), modifier = modifier) { groups, constraints ->
        measureHeaderAndList(groups, header.size, keep, gap, room, constraints)
    }
}

/**
 * [PanelListLayout] for a panel written as a column: every child but the last is a part of the header, the last is the list. A list that keeps [SlabDimens.listEndRoom]
 * in its own box ([KeepFocusedColumn]) passes [listKeepsEnd].
 */
@Composable
internal fun PanelListColumn(
    modifier: Modifier,
    listKeepsEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    val keep = PlumTheme.layout.rowHeight * 2 + PlumDimens.listRowGap
    val room = remember { PanelListRoom() }
    Layout({ CompositionLocalProvider(LocalPanelListRoom provides room, content = content) }, modifier) { children, constraints ->
        val groups = children.dropLast(1).map { listOf(it) } + listOf(children.takeLast(1), emptyList())
        measureHeaderAndList(groups, children.size - 1, keep, PlumDimens.settingsPanelListGap, room, constraints, listKeepsEnd)
    }
}

private fun MeasureScope.measureHeaderAndList(
    groups: List<List<Measurable>>,
    headerCount: Int,
    keep: Dp,
    gap: Dp,
    room: PanelListRoom,
    constraints: Constraints,
    listKeepsEnd: Boolean = false,
): MeasureResult {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val loose = Constraints(minWidth = width, maxWidth = width)
    val gapPx = gap.roundToPx()
    val parts = groups.take(headerCount).map { group -> group.map { it.measure(loose) } }
    val partHeights = parts.map { group -> group.maxOfOrNull { it.height } ?: 0 }.filter { it > 0 }
    val bottom = groups.last().map { it.measure(loose) }
    val footerHeight = bottom.maxOfOrNull { it.height } ?: 0
    val endRoom = SlabDimens.listEndRoom.roundToPx()
    val spare = (height - maxOf(keep.roundToPx(), roomForRow(room.tallestRow)) - endRoom).coerceAtLeast(0)
    val footerGap = maxOf(gapPx, endRoom)
    val footerRoom = if (footerHeight > 0) footerHeight + footerGap else 0
    val fullHeader = partHeights.sum() + gapPx * (partHeights.size - 1).coerceAtLeast(0)
    val showsFooter = footerRoom > 0 && fullHeader + gapPx + footerRoom <= spare
    val footerTaken = if (showsFooter) footerRoom else 0
    // Parts shown from the first while each fits whole; the first that does not ends the header.
    var used = 0
    var shownParts = 0
    for (h in partHeights) {
        val next = used + (if (shownParts > 0) gapPx else 0) + h
        if (next > spare - footerTaken) break
        used = next
        shownParts++
    }
    val listTop = if (used > 0) used + gapPx else (SlabDimens.listEndRoom - PlumDimens.barGap).roundToPx()
    val keptEnd = if (showsFooter) footerTaken else if (listKeepsEnd) 0 else endRoom
    // With the header gone a card as tall as the window takes the end room, or the window's clip cuts its foot flat under its shadow.
    val cardOver = room.tallestRow - (height - listTop - keptEnd)
    val listEnd = if (used == 0 && !showsFooter && cardOver > 0) (keptEnd - cardOver).coerceAtLeast(0) else keptEnd
    val under = groups[headerCount].map { it.measure(loose.copy(maxHeight = (height - listTop - listEnd).coerceAtLeast(0))) }
    val underHeight = under.maxOfOrNull { it.height } ?: 0
    return layout(width, height) {
        var y = 0
        var index = 0
        for (group in parts) {
            val h = group.maxOfOrNull { it.height } ?: 0
            if (h > 0) {
                if (index < shownParts) group.forEach { it.place(0, y) }
                y += h + gapPx
                index++
            }
        }
        under.forEach { it.place(0, listTop) }
        if (showsFooter) bottom.forEach { it.place(0, listTop + underHeight + footerGap) }
    }
}

/**
 * A panel of [header] parts (title, text), the [main] block that is the page's point (swatches) and a [rest] that
 * takes what the whole header leaves. The main block always stays whole: when the header and it do not both fit, header parts
 * leave from the last one, each whole or not at all (never cut mid-line). [leading] (the look preview) sits before the header and is
 * shown only if it fits whole with the main block, the rest and the first header part (the title): the swatches and the title win
 * over it, the later header parts (the text) give way to it.
 */
@Composable
internal fun PanelHeaderYields(
    modifier: Modifier,
    gap: Dp,
    header: List<@Composable () -> Unit>,
    main: @Composable () -> Unit,
    rest: @Composable () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val lead = if (leading == null) 0 else 1
    Layout(contents = listOfNotNull(leading) + header + listOf(main, rest), modifier = modifier) { all, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(minWidth = width, maxWidth = width)
        val gapPx = gap.roundToPx()
        val groups = all.drop(lead)
        val leadPlaced = if (lead == 1) all[0].map { it.measure(loose) } else emptyList()
        val parts = groups.take(header.size).map { group -> group.map { it.measure(loose) } }
        val mainPlaced = groups[header.size].map { it.measure(loose) }
        val mainHeight = mainPlaced.maxOfOrNull { it.height } ?: 0
        val leadHeight = leadPlaced.maxOfOrNull { it.height } ?: 0
        // Parts from the first while each fits whole above the main block, starting `from` high; the first that does not ends the header.
        fun partsFrom(from: Int): Pair<Int, Int> {
            var used = from
            var shown = 0
            for (group in parts) {
                val h = group.maxOfOrNull { it.height } ?: 0
                if (h == 0) { shown++; continue }
                val next = used + h + gapPx
                if (next + mainHeight > height) break
                used = next
                shown++
            }
            return shown to used
        }
        // The rest gets only what the whole header leaves: room freed by a part that left is for the main block, not for it.
        val fullHeader = parts.sumOf { group -> (group.maxOfOrNull { it.height } ?: 0).let { if (it > 0) it + gapPx else 0 } }
        val restRoom = (height - fullHeader - mainHeight - gapPx).coerceAtLeast(0)
        val restPlaced = groups.last().map { it.measure(loose.copy(maxHeight = restRoom)) }
        val restHeight = restPlaced.maxOfOrNull { it.height } ?: 0
        val restBlock = if (restHeight > 0) restHeight + gapPx else 0
        // The preview needs the swatches whole, the rest and the first header part (the title) beside it; later parts (the text) give way to it.
        val withLead = partsFrom(if (leadHeight > 0) leadHeight + gapPx else 0)
        val leadShown = leadHeight > 0 && withLead.second + mainHeight + restBlock <= height && (withLead.first >= 1 || parts.isEmpty())
        val (shown, _) = if (leadShown) withLead else partsFrom(0)
        layout(width, height) {
            var y = 0
            if (leadShown) {
                leadPlaced.forEach { it.place(0, 0) }
                y = leadHeight + gapPx
            }
            parts.take(shown).forEach { group ->
                val h = group.maxOfOrNull { it.height } ?: 0
                group.forEach { it.place(0, y) }
                if (h > 0) y += h + gapPx
            }
            mainPlaced.forEach { it.place(0, y) }
            if (restHeight > 0) restPlaced.forEach { it.place(0, y + mainHeight + gapPx) }
        }
    }
}
