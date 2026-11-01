package com.plumemu.settings.screen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coreui.components.drawSunken
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.StatisticsDimens
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.home.currentLocale
import com.plumemu.home.playTime
import com.plumemu.home.rememberCountFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Statistics' figure tiles (SettingsStatisticsPage.kt): the tiles, the week's bars and day letters, the thin bars the lists share too.
 */

/** The figure tiles, [perRow] to a row, equal widths and, in a row, equal heights; a row with fewer keeps the tile width. */
@Composable
internal fun Tiles(
    figures: PlayStatistics,
    perRow: Int,
    showsDays: Boolean = true,
) {
    val tiles = tilesOf(figures)
    Column(Modifier.fillMaxWidth().testTag(StatisticsTags.TILES), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
        for (row in tiles.chunked(perRow)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
                for (tile in row) Tile(tile, figures, Modifier.weight(1f).fillMaxHeight(), showsDays)
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Which tiles the figures have: the week once a session is recorded, the box art once it is set up for some game. */
internal fun tilesOf(figures: PlayStatistics): List<StatisticTile> =
    buildList {
        if (figures.hasPlayed) {
            add(StatisticTile.Total)
            if (figures.hasSessions) add(StatisticTile.Week)
            if (figures.libraryGames > 0) add(StatisticTile.Played)
        }
        if (figures.hasBoxArt && figures.boxArtGames > 0) add(StatisticTile.BoxArt)
    }

internal enum class StatisticTile { Total, Week, Played, BoxArt }

@Composable
internal fun Tile(
    tile: StatisticTile,
    figures: PlayStatistics,
    modifier: Modifier,
    showsDays: Boolean = true,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val (label, figure) =
        when (tile) {
            StatisticTile.Total -> stringResource(R.string.settings_statistics_total) to statisticTime(figures.totalSeconds)
            // Nothing this week yet reads "0 min", a figure like the others.
            StatisticTile.Week -> stringResource(R.string.settings_statistics_this_week) to if (figures.thisWeekSeconds <= 0) playTime(0) else statisticTime(figures.thisWeekSeconds)
            StatisticTile.Played -> stringResource(R.string.settings_statistics_played) to countOf(figures.playedGames, figures.libraryGames)
            StatisticTile.BoxArt -> stringResource(R.string.settings_statistics_with_box_art) to percentOf(figures.gamesWithBoxArt, figures.boxArtGames)
        }
    Column(
        modifier
            // Tagged outside the padding: the tag's bounds are the tile's, which the layout tests measure.
            .testTag(StatisticsTags.tile(tile))
            .drawBehind { drawRoundRect(colors.sel2, cornerRadius = CornerRadius(PlumDimens.cornerRow.toPx())) }
            .padding(StatisticsDimens.tilePadding),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap),
    ) {
        BasicText(label, style = type.meta, color = { colors.meta }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(figure, style = type[TypeRole.DetailsTitleWide], color = { colors.text }, maxLines = 2, modifier = Modifier.testTag(StatisticsTags.figure(tile)))
        // Every tile ends in something at its foot, so tiles of one height have no empty half.
        Spacer(Modifier.weight(1f))
        when (tile) {
            StatisticTile.Total -> {
                val numbers = rememberCountFormat()
                BasicText(
                    pluralStringResource(R.plurals.settings_statistics_across_games, figures.playedGames, numbers.format(figures.playedGames)),
                    style = type.smallFact,
                    color = { colors.meta },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(StatisticsTags.foot(tile)),
                )
            }
            StatisticTile.Week -> WeekBars(figures, showsDays)
            StatisticTile.Played -> Bar(shareOf(figures.playedGames, figures.libraryGames), { colors.accent }, { colors.bg }, Modifier.testTag(StatisticsTags.foot(tile)))
            StatisticTile.BoxArt -> Bar(shareOf(figures.gamesWithBoxArt, figures.boxArtGames), { colors.accent }, { colors.bg }, Modifier.testTag(StatisticsTags.foot(tile)))
        }
    }
}

private fun shareOf(
    count: Int,
    of: Int,
): Float = if (of > 0) count.toFloat() / of else 0f

/** "73 %" in the locale's way, rounded down so a library one cover short never reads 100. */
@Composable
private fun percentOf(
    count: Int,
    of: Int,
): String {
    val locale = currentLocale()
    val format = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val share = if (of <= 0) 0 else (count.toLong() * PERCENT / of).toInt()
    return format.format(share / PERCENT.toDouble())
}

/** A system's share of all play time ("85 %"), rounded; a system played at all never reads 0. */
@Composable
internal fun sharePercentOf(
    seconds: Long,
    of: Long,
): String {
    val locale = currentLocale()
    val format = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val share = if (of <= 0) 0L else ((seconds * PERCENT + of / 2) / of).coerceAtLeast(if (seconds > 0) 1L else 0L)
    return format.format(share / PERCENT.toDouble())
}

/**
 * The week's seven days, from the week's first, as bars against the longest day: today in the accent, the others muted; a day
 * without play a stub. Under each its narrow name in the locale ("M", "T"), today's in the accent.
 */
@Composable
private fun WeekBars(
    figures: PlayStatistics,
    showsDays: Boolean,
) = Column(verticalArrangement = Arrangement.spacedBy(StatisticsDimens.barGap)) {
    val colors = PlumTheme.colors
    val days = figures.weekDaySeconds
    val longest = days.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val locale = currentLocale()
    val letters = remember(locale) { narrowWeekdays(locale) }
    Row(
        Modifier.fillMaxWidth().height(StatisticsDimens.weekChartHeight).testTag(StatisticsTags.WEEK),
        horizontalArrangement = Arrangement.spacedBy(StatisticsDimens.weekBarGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { day, seconds ->
            val isToday = day == figures.todayIndex
            val share = seconds.toFloat() / longest
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(share.coerceAtLeast(MIN_DAY_SHARE))
                    .heightIn(min = StatisticsDimens.barHeight)
                    .drawBehind {
                        val fill = if (seconds <= 0) colors.bg else if (isToday) colors.accent else colors.muted
                        drawSunken(fill, colors.inset, radius = StatisticsDimens.barHeight.toPx() / 2)
                    }
                    .testTag(StatisticsTags.day(day)),
            )
        }
    }
    if (showsDays) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StatisticsDimens.weekBarGap)) {
        letters.forEachIndexed { day, letter ->
            val isToday = day == figures.todayIndex
            // Centred on its bar and free to be wider than it: a "W" at Largest is wider than a bar of a four-tile row, never its neighbours' room.
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                BasicText(
                    letter,
                    style = PlumTheme.type.smallFact,
                    color = { if (isToday) colors.accent else colors.muted },
                    maxLines = 1,
                    modifier = Modifier.wrapContentWidth(unbounded = true).testTag(StatisticsTags.dayLetter(day)),
                )
            }
        }
    }
}

/**
 * The seven days' narrow names ("M", "T", ... in English), from the [locale]'s first day of
 * the week, as `weekStartOf` counts it. Android's own date format knows the narrow form ("EEEEE"); before Android 7
 * that is `java.text`'s, from Android 7 on ICU's (the same data).
 */
internal fun narrowWeekdays(locale: Locale): List<String> {
    val calendar = Calendar.getInstance(locale)
    val first = calendar.firstDayOfWeek
    val format: (Date) -> String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.icu.text.SimpleDateFormat(NARROW_WEEKDAY, locale)::format
        } else {
            java.text.SimpleDateFormat(NARROW_WEEKDAY, locale)::format
        }
    return List(DAYS_IN_WEEK) { day ->
        calendar.set(Calendar.DAY_OF_WEEK, (first - 1 + day) % DAYS_IN_WEEK + 1)
        format(calendar.time)
    }
}

/** A thin bar: [share] (0..1) of its width in [fill] over a sunken [track]. */
@Composable
internal fun Bar(
    share: Float,
    fill: () -> Color,
    track: () -> Color,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    Box(modifier.fillMaxWidth().height(StatisticsDimens.barHeight).drawBehind { drawSunken(track(), colors.inset) }) {
        if (share > 0f) Box(Modifier.fillMaxWidth(share.coerceIn(MIN_BAR_SHARE, 1f)).height(StatisticsDimens.barHeight).drawBehind { drawSunken(fill(), colors.inset) })
    }
}

/** "EEEEE": a day's narrow name in Android's date formats. */
private const val NARROW_WEEKDAY = "EEEEE"

private const val DAYS_IN_WEEK = 7

/** A day played for a minute still shows a bar above the stub of a day not played. */
private const val MIN_DAY_SHARE = 0.15f

/** A game played far less than the first still shows a sliver. */
private const val MIN_BAR_SHARE = 0.02f

private const val PERCENT = 100
