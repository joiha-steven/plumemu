package com.plumemu.secondscreen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.CoverImages
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.LocalButtonLetters

/**
 * The Favorites card (board `SecondFavorites`): the name and game count, then the favourites' covers (each whole in its collar, one
 * height, packed left to right in up to three rows, each row with games after it scaled to end at the right margin), and "13 shown of 18 · on 6 systems". The covers are as many as fit the rows the height
 * leaves, so the figure is the number drawn; a cover that does not fit a row starts the next, and the ones after the last row are left out.
 */
@Composable
internal fun BoxScope.FavoritesCard(
    content: FavoritesEntry,
    images: CoverImages,
) {
    val styles = rememberEntryStyles()
    val colors = PlumTheme.colors
    val numbers = rememberCountFormat()
    val measurer = rememberTextMeasurer()
    val separator = stringResource(R.string.second_screen_separator)
    val systemsText = pluralStringResource(R.plurals.second_screen_on_systems, content.systemCount, numbers.format(content.systemCount))
    val allText = pluralStringResource(R.plurals.home_games_count, content.gameCount, numbers.format(content.gameCount))
    val shownOf = stringResource(R.string.second_screen_shown_of, "%1\$s", numbers.format(content.gameCount))
    val emptyText = stringResource(R.string.home_favorites_empty, stringResource(LocalButtonLetters.current.favorite))
    val ready = content.isArtRead && content.games.isNotEmpty()
    SubcomposeLayout(Modifier.align(Alignment.TopStart).entryCardPlace()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val header = subcompose("header") { EntryHeader(content.name, content.gameCount, styles) }.map { it.measure(Constraints(maxWidth = width)) }
        val headerHeight = header.maxOfOrNull { it.height } ?: 0
        var used = headerHeight
        var shownCount = 0
        var rows: List<FavoritesRow> = emptyList()
        var rowHeights: List<Int> = emptyList()
        var status: List<androidx.compose.ui.layout.Placeable> = emptyList()
        var rowPlaceables: List<androidx.compose.ui.layout.Placeable> = emptyList()
        if (ready) {
            val statusWorst = statusWords(shownOf.replace("%1\$s", numbers.format(content.gameCount)), systemsText, separator, styles, colors.muted, colors.text2)
            val statusHeight = measurer.measure(statusWorst, styles.status, constraints = Constraints(maxWidth = width)).size.height
            val statusGap = EntryCardDimens.statusTopFavorites.roundToPx()
            val gap = EntryCardDimens.favoritesCoverGap.roundToPx()
            val rowGap = EntryCardDimens.favoritesRowGap.roundToPx()
            val cell = entryCoverCell(EntryCardDimens.favoritesCoverHeight).roundToPx()
            val top = headerHeight + EntryCardDimens.headerBottom.roundToPx()
            val room = height - top - statusGap - statusHeight
            val rowsFit = if (room < cell) 0 else minOf(EntryCardDimens.FAVORITES_ROWS, (room + rowGap) / (cell + rowGap))
            val widths = content.games.map { entryCoverWidth(it, content.systems, EntryCardDimens.favoritesCoverHeight, DEFAULT_COVER_ASPECT).roundToPx() }
            val aspects = content.games.map { entryCoverAspect(it, content.systems, DEFAULT_COVER_ASPECT) }
            val collarSides = CollarSize.Cover.sidePadding.roundToPx() * 2
            val collarVertical = CollarSize.Cover.topPadding.roundToPx() + CollarSize.Cover.bottomPadding.roundToPx()
            fun plain(packed: List<List<Int>>) = packed.map { FavoritesRow(it, null, null) }
            fun heightOf(row: FavoritesRow) = if (row.boxHeight == null) cell else row.boxHeight + collarVertical
            fun stack(planned: List<FavoritesRow>) = planned.sumOf { heightOf(it) } + (planned.size - 1) * rowGap
            // A row with games after it reaches the right margin, taking rows from the top while the taller, scaled ones still fit the
            // height; the rest stay as packed. Never fewer covers than the greedy packing shows.
            val packed = packRows(widths, gap, width, rowsFit)
            var planned: List<FavoritesRow> = plain(packed)
            for (at in packed.indices) {
                if (packed[at].last() >= content.games.lastIndex) continue
                val trial = planned.toMutableList().also { it[at] = justifiedRow(packed[at], aspects, gap, collarSides, width) }
                if (stack(trial) <= room) planned = trial
            }
            rows = planned
            shownCount = rows.sumOf { it.indices.size }
            if (shownCount > 0) {
                val text = if (shownCount < content.gameCount) shownOf.replace("%1\$s", numbers.format(shownCount)) else allText
                rowPlaceables =
                    rows.mapIndexed { at, row ->
                        subcompose("row$at") {
                            Row(Modifier.testTag(EntryTags.favoritesRow(at)), horizontalArrangement = Arrangement.spacedBy(EntryCardDimens.favoritesCoverGap)) {
                                row.indices.forEachIndexed { slot, index ->
                                    val game = content.games[index]
                                    if (row.boxHeight == null || row.boxWidths == null) {
                                        EntryCover(game, content.systems, DEFAULT_COVER_ASPECT, EntryCardDimens.favoritesCoverHeight, images)
                                    } else {
                                        EntryCover(game, content.systems, DEFAULT_COVER_ASPECT, row.boxHeight.toDp(), images, width = row.boxWidths[slot].toDp())
                                    }
                                }
                            }
                        }.map { it.measure(Constraints(maxWidth = width)) }.first()
                    }
                rowHeights = rows.map { heightOf(it) }
                status = subcompose("status") { StatusText(statusWords(text, systemsText, separator, styles, colors.muted, colors.text2), styles) }.map { it.measure(Constraints(maxWidth = width)) }
                used = top + stack(rows) + statusGap + (status.maxOfOrNull { it.height } ?: 0)
            }
        }
        val isEmpty = content.isArtRead && content.games.isEmpty()
        val emptyLine = if (isEmpty) subcompose("empty") { EntryEmptyLine(emptyText, styles) }.map { it.measure(Constraints(maxWidth = width)) } else emptyList()
        layout(width, height) {
            header.forEach { it.place(0, 0) }
            emptyLine.forEach { it.place(0, headerHeight + EntryCardDimens.headerBottom.roundToPx()) }
            if (shownCount > 0) {
                val rowGap = EntryCardDimens.favoritesRowGap.roundToPx()
                var y = headerHeight + EntryCardDimens.headerBottom.roundToPx()
                rowPlaceables.forEachIndexed { at, placeable ->
                    placeable.place(0, y)
                    y += rowHeights[at] + rowGap
                }
                status.forEach { it.place(0, y - rowGap + EntryCardDimens.statusTopFavorites.roundToPx()) }
            }
        }
    }
}

@Composable
private fun StatusText(
    words: AnnotatedString,
    styles: EntryStyles,
) {
    androidx.compose.foundation.text.BasicText(words, style = styles.status, modifier = Modifier.fillMaxWidth().boardLine(styles.status, styles).testTag(EntryTags.STATUS))
}

private fun statusWords(
    shown: String,
    systems: String,
    separator: String,
    styles: EntryStyles,
    muted: androidx.compose.ui.graphics.Color,
    text: androidx.compose.ui.graphics.Color,
): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = text)) { append(shown) }
        withStyle(SpanStyle(color = muted)) { append(separator) }
        withStyle(SpanStyle(color = text)) { append(systems) }
    }
