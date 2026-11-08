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
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.CoverImages
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.LocalButtonLetters

/**
 * The Favorites card (board `SecondFavorites`): the name and game count, then the favourites' covers (each whole in its collar, one
 * height, packed left to right in up to three rows), and "13 shown of 18 · on 6 systems". The covers are as many as fit the rows the height
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
        var rows: List<List<Int>> = emptyList()
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
            rows = packRows(widths, gap, width, rowsFit)
            shownCount = rows.sumOf { it.size }
            if (shownCount > 0) {
                val text = if (shownCount < content.gameCount) shownOf.replace("%1\$s", numbers.format(shownCount)) else allText
                rowPlaceables =
                    rows.mapIndexed { at, indices ->
                        subcompose("row$at") {
                            Row(Modifier.testTag(EntryTags.favoritesRow(at)), horizontalArrangement = Arrangement.spacedBy(EntryCardDimens.favoritesCoverGap)) {
                                for (index in indices) EntryCover(content.games[index], content.systems, DEFAULT_COVER_ASPECT, EntryCardDimens.favoritesCoverHeight, images)
                            }
                        }.map { it.measure(Constraints(maxWidth = width)) }.first()
                    }
                status = subcompose("status") { StatusText(statusWords(text, systemsText, separator, styles, colors.muted, colors.text2), styles) }.map { it.measure(Constraints(maxWidth = width)) }
                used = top + rows.size * cell + (rows.size - 1) * rowGap + statusGap + (status.maxOfOrNull { it.height } ?: 0)
            }
        }
        val isEmpty = content.isArtRead && content.games.isEmpty()
        val emptyLine = if (isEmpty) subcompose("empty") { EntryEmptyLine(emptyText, styles) }.map { it.measure(Constraints(maxWidth = width)) } else emptyList()
        layout(width, height) {
            header.forEach { it.place(0, 0) }
            emptyLine.forEach { it.place(0, headerHeight + EntryCardDimens.headerBottom.roundToPx()) }
            if (shownCount > 0) {
                val cell = entryCoverCell(EntryCardDimens.favoritesCoverHeight).roundToPx()
                val rowGap = EntryCardDimens.favoritesRowGap.roundToPx()
                var y = headerHeight + EntryCardDimens.headerBottom.roundToPx()
                rowPlaceables.forEach {
                    it.place(0, y)
                    y += cell + rowGap
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
