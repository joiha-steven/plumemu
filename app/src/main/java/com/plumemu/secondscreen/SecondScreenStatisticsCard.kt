package com.plumemu.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.fitInside
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.STAND_IN_ASPECT
import com.plumemu.settings.screen.StatisticRow
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.countOf
import com.plumemu.settings.screen.statisticRowsOf
import com.plumemu.settings.screen.statisticTime
import com.plumemu.settings.screen.minutesOf

/*
 * The statistics card: while Settings
 * is open the second screen shows Settings › Statistics' figures, larger. Its rows are the page's
 * own (`statisticRowsOf`, the same read) without Version, which is a Settings fact and not a
 * statistic (everything here is also on the one-screen page).
 */

/**
 * One block on the bottom margin, left-aligned: the top line "Statistics", the total play time
 * large with its "Total play time" line (or "Nothing played yet"), the most-played games (a cover
 * slot with box art set up, the title cut with "…", the time whole at the right), the most-played
 * system, Games played and Games with box art. When the card runs out of height a line that does not fit is
 * skipped ([PRIORITY], [keptParts]), and the lower lines are still tried, rather than overflow; no text other than a game's title is ever cut.
 */
@Composable
internal fun BoxScope.StatisticsCard(
    statistics: PlayStatistics,
    dimens: SecondScreenDimens,
    images: CoverImages,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val line = type[dimens.factsRole]
    // The Settings page's rows for the same figures; Version is not a statistic, and an empty library never reaches here.
    val rows = remember(statistics) { statisticRowsOf(StatisticsState.Read(statistics)).filter { it != StatisticRow.Version && it != StatisticRow.NoGames } }
    val separator = stringResource(R.string.second_screen_separator)
    DroppingColumn(
        dimens,
        Modifier.align(Alignment.BottomStart).padding(dimens.margin).testTag(SecondScreenTags.STATISTICS),
    ) {
        Part(StatisticsPart.Top) { TopLine(stringResource(R.string.settings_section_statistics), type[dimens.topRole].copy(color = colors.meta)) }
        for (row in rows) {
            when (row) {
                is StatisticRow.Total -> {
                    Part(StatisticsPart.Total) {
                        BasicText(statisticTime(row.seconds), style = type[dimens.titleRole].copy(color = colors.text), softWrap = false, modifier = Modifier.testTag(SecondScreenTags.STATISTICS_TOTAL))
                    }
                    Part(StatisticsPart.TotalLabel) {
                        val week = rows.firstOrNull { it is StatisticRow.ThisWeek } as StatisticRow.ThisWeek?
                        TotalLabel(
                            plain = stringResource(R.string.settings_statistics_total),
                            withWeek = week?.let { statisticTotalWithWeek(it.seconds) },
                            style = line.copy(color = colors.text2),
                        )
                    }
                }
                // The week rides on the total's label line (TotalLabel), not a line of its own.
                is StatisticRow.ThisWeek -> Unit
                StatisticRow.NothingPlayed ->
                    Part(StatisticsPart.NothingPlayed) {
                        BasicText(stringResource(R.string.settings_statistics_nothing_played), style = type[dimens.detailsTitleRole].copy(color = colors.text2), modifier = Modifier.testTag(SecondScreenTags.STATISTICS_NOTHING_PLAYED))
                    }
                is StatisticRow.Game ->
                    Part(StatisticsPart.Game(row.rank)) {
                        GameRow(row, dimens, images, line)
                    }
                is StatisticRow.System -> {
                    val words =
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.text)) { append(row.system.name) }
                            withStyle(SpanStyle(color = colors.muted)) { append(separator) }
                            withStyle(SpanStyle(color = colors.text2)) { append(stringResource(R.string.settings_statistics_top_system)) }
                        }
                    Part(StatisticsPart.System) { FigureRow(words, statisticTime(row.system.seconds), line, SecondScreenTags.STATISTICS_SYSTEM) }
                }
                is StatisticRow.Played ->
                    Part(StatisticsPart.Played) {
                        FigureRow(AnnotatedString(stringResource(R.string.settings_statistics_played)), countOf(row.played, row.of), line, SecondScreenTags.STATISTICS_PLAYED)
                    }
                is StatisticRow.BoxArt ->
                    Part(StatisticsPart.BoxArt) {
                        FigureRow(AnnotatedString(stringResource(R.string.settings_statistics_box_art)), countOf(row.withArt, row.of), line, SecondScreenTags.STATISTICS_BOX_ART)
                    }
                StatisticRow.Version, StatisticRow.NoGames, StatisticRow.Unreadable -> Unit
            }
        }
    }
}

/**
 * "Total play time · 2 h 10 min this week" on one line; when that does not fit the width it is
 * just [plain]. Both texts are composed, only the one that fits is placed (so only it is drawn).
 * The week variant is one line and never cut; [plain] may wrap, as before the week, so a longer
 * translation is never cut.
 */
@Composable
private fun TotalLabel(
    plain: String,
    withWeek: String?,
    style: TextStyle,
) {
    Layout(
        content = {
            BasicText(plain, style = style, modifier = Modifier.testTag(SecondScreenTags.STATISTICS_TOTAL_LABEL))
            if (withWeek != null) BasicText(withWeek, style = style, maxLines = 1, softWrap = false, modifier = Modifier.testTag(SecondScreenTags.STATISTICS_TOTAL_LABEL))
        },
    ) { measurables, constraints ->
        val own = measurables[0].measure(Constraints(maxWidth = constraints.maxWidth))
        val long = measurables.getOrNull(1)?.measure(Constraints())
        val shown = if (long != null && long.width <= constraints.maxWidth) long else own
        layout(shown.width, shown.height) { shown.place(0, 0) }
    }
}

/** The total's label with the week: "Total play time · 2 h 10 min this week", "… · none this week", "… · less than a minute this week". */
@Composable
private fun statisticTotalWithWeek(seconds: Long): String =
    when {
        seconds <= 0 -> stringResource(R.string.second_screen_total_week_none)
        minutesOf(seconds) == 0 -> stringResource(R.string.second_screen_total_week_under_a_minute)
        else -> stringResource(R.string.second_screen_total_week, statisticTime(seconds))
    }

/** A most-played game: its cover slot (box art set up only), the title in at most its lines with "…", the time whole at the right. */
@Composable
private fun GameRow(
    row: StatisticRow.Game,
    dimens: SecondScreenDimens,
    images: CoverImages,
    line: TextStyle,
) {
    val colors = PlumTheme.colors
    Row(Modifier.fillMaxWidth().testTag(SecondScreenTags.statisticsGame(row.rank)), verticalAlignment = Alignment.CenterVertically) {
        if (row.showsCover) {
            StatisticsCover(row.game.cover, dimens.statisticsCover, images)
            Spacer(Modifier.width(SecondScreenDimens.statisticsCoverGap))
        }
        Row(Modifier.weight(1f)) {
            BasicText(
                text = row.game.title,
                style = line.copy(color = colors.text),
                maxLines = dimens.statisticsTitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline().testTag(SecondScreenTags.STATISTICS_GAME_TITLE),
            )
            Spacer(Modifier.width(SecondScreenDimens.statisticsFigureGap))
            Figure(statisticTime(row.game.seconds), line, Modifier.alignByBaseline())
        }
    }
}

/** Words at the left, wrapping when they must (never cut), and the figure whole at the right on their first line. */
@Composable
private fun FigureRow(
    words: AnnotatedString,
    figure: String,
    line: TextStyle,
    tag: String,
) {
    val colors = PlumTheme.colors
    Row(Modifier.fillMaxWidth().testTag(tag)) {
        BasicText(words, style = line.copy(color = colors.text), modifier = Modifier.weight(1f).alignByBaseline())
        Spacer(Modifier.width(SecondScreenDimens.statisticsFigureGap))
        Figure(figure, line, Modifier.alignByBaseline())
    }
}

/** A time or a count: one line, measured before the words beside it, so it is never cut. */
@Composable
private fun Figure(
    text: String,
    line: TextStyle,
    modifier: Modifier,
) {
    BasicText(text, style = line.copy(color = PlumTheme.colors.text2), maxLines = 1, softWrap = false, modifier = modifier.testTag(SecondScreenTags.STATISTICS_FIGURE))
}

/**
 * A most-played game's cover, fitted in a [slot] square at its own shape, decoded at that size by the
 * home screen's loader (the window's [images]); until it is decoded, and for good without one or
 * when the file cannot be read, the stand-in tile Settings › Statistics shows, so nothing moves.
 */
@Composable
private fun StatisticsCover(
    picture: ArtPicture?,
    slot: Dp,
    images: CoverImages,
) {
    val density = LocalDensity.current
    val aspect = picture?.aspect ?: STAND_IN_ASPECT
    val drawn = fitInside(aspect, DpSize(slot, slot))
    val widthPx = with(density) { drawn.width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { drawn.height.roundToPx() }.coerceAtLeast(1)
    val cached = remember(images, picture, widthPx, heightPx) { picture?.let { images.cached(it, widthPx, heightPx) } }
    var bitmap by remember(images, picture, widthPx, heightPx) { mutableStateOf(cached) }
    if (picture != null && cached == null) {
        // Null: the file is gone or damaged (the loader logs why); the stand-in stays.
        LaunchedEffect(images, picture, widthPx, heightPx) { images.load(picture, widthPx, heightPx)?.let { bitmap = it } }
    }
    val corner = if (aspect >= 1f) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait
    Box(Modifier.size(slot).testTag(SecondScreenTags.STATISTICS_COVER), contentAlignment = Alignment.Center) {
        val tile = Modifier.size(drawn).clip(RoundedCornerShape(corner))
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low, modifier = tile)
        } else {
            Box(tile.background(PlumTheme.colors.sel))
        }
    }
}

/** A part of the card: its group (the gap above it) and whether it may go when the card runs out of height. */
@Immutable
internal sealed class StatisticsPart(
    /** Parts of one group sit [gapInGroup] apart; a part after another group sits the group gap under it. */
    val group: Int,
    val gapInGroup: (SecondScreenDimens) -> Dp,
) {
    data object Top : StatisticsPart(0, { 0.dp })

    data object Total : StatisticsPart(0, { SecondScreenDimens.topToTitleGap })

    data object TotalLabel : StatisticsPart(0, { SecondScreenDimens.statisticsLabelGap })

    data object NothingPlayed : StatisticsPart(0, { SecondScreenDimens.topToTitleGap })

    data class Game(val rank: Int) : StatisticsPart(1, { it.statisticsRowGap })

    data object System : StatisticsPart(2, { it.statisticsRowGap })

    data object Played : StatisticsPart(2, { it.statisticsRowGap })

    data object BoxArt : StatisticsPart(2, { it.statisticsRowGap })
}

/**
 * The order lines claim the height, highest
 * first: the most-played games 1, 2, 3, the system, Games played, Games with box art. The top line
 * and the total (or "Nothing played yet") always stay. A line that does not fit what is left is
 * skipped and the lower ones are still tried ([keptParts]).
 */
internal val PRIORITY: List<StatisticsPart> =
    listOf(StatisticsPart.Game(1), StatisticsPart.Game(2), StatisticsPart.Game(3), StatisticsPart.System, StatisticsPart.Played, StatisticsPart.BoxArt)

/**
 * Which of [parts] (top to bottom, with their heights in px) the card shows in [maxHeight] px
 * (null: unbounded). Parts outside [PRIORITY] always stay; the others are tried in [PRIORITY] order
 * and kept when the card, with the gaps its neighbours give, still fits; one that does not fit is
 * skipped and the next is still tried. [gapAbove] is the gap above a part that follows [before].
 */
internal fun keptParts(
    parts: List<Pair<StatisticsPart, Int>>,
    maxHeight: Int?,
    gapAbove: (before: StatisticsPart, part: StatisticsPart) -> Int,
): List<StatisticsPart> {
    fun heightOf(kept: List<Pair<StatisticsPart, Int>>): Int =
        kept.mapIndexed { at, (part, height) -> height + if (at == 0) 0 else gapAbove(kept[at - 1].first, part) }.sum()

    if (maxHeight == null) return parts.map { it.first }
    val chosen = parts.filter { it.first !in PRIORITY }.map { it.first }.toMutableSet()
    for (part in PRIORITY) {
        if (parts.none { it.first == part }) continue
        val tried = parts.filter { it.first in chosen || it.first == part }
        if (heightOf(tried) <= maxHeight) chosen += part
    }
    return parts.map { it.first }.filter { it in chosen }
}

/**
 * One part of [DroppingColumn]: its content tagged with [part] for the layout. Emitted straight from
 * the card's composition: a scope that collected parts and emitted them later would miss the
 * changes Compose recomposes in the collecting lambda alone (the card kept its first figures).
 */
@Composable
private fun Part(
    part: StatisticsPart,
    content: @Composable () -> Unit,
) {
    Box(Modifier.layoutId(part)) { content() }
}

/**
 * The parts top to bottom with their gaps, as tall as the parts kept: every part is measured at the
 * full width, then the lines are chosen by [keptParts]. A part that is skipped is not placed (not
 * drawn) and takes its gap with it.
 */
@Composable
private fun DroppingColumn(
    dimens: SecondScreenDimens,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val measured: List<Pair<StatisticsPart, Placeable>> =
            measurables.map { (it.layoutId as StatisticsPart) to it.measure(Constraints(maxWidth = width)) }
        val groupGap = dimens.statisticsGroupGap.roundToPx()

        // The one gap rule, for choosing the lines and for placing them.
        fun gapBetween(
            before: StatisticsPart,
            part: StatisticsPart,
        ): Int = if (before.group == part.group) part.gapInGroup(dimens).roundToPx() else groupGap

        fun gapsOf(kept: List<Pair<StatisticsPart, Placeable>>): List<Int> = kept.mapIndexed { at, (part, _) -> if (at == 0) 0 else gapBetween(kept[at - 1].first, part) }

        fun heightOf(kept: List<Pair<StatisticsPart, Placeable>>): Int = kept.sumOf { it.second.height } + gapsOf(kept).sum()

        val chosen =
            keptParts(measured.map { it.first to it.second.height }, if (constraints.hasBoundedHeight) constraints.maxHeight else null, ::gapBetween)
        val kept = measured.filter { it.first in chosen }
        val gaps = gapsOf(kept)
        layout(width, heightOf(kept).coerceAtMost(constraints.maxHeight)) {
            var y = 0
            kept.forEachIndexed { at, (_, placeable) ->
                y += gaps[at]
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}
