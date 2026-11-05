package com.plumemu.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.CoverImages
import com.plumemu.home.currentLocale
import com.plumemu.home.playTime
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.screen.minutesOf
import com.plumemu.settings.screen.statisticTime
import java.text.DateFormatSymbols

/**
 * The Recent card (board `SecondRecent`): the name and game count, the last games played with when and their systems, and under them,
 * on the bottom edge, this week's play time with a bar per day and last week's. Until its figures are read only the header shows;
 * when the height runs out the week goes first, whole.
 */
@Composable
internal fun BoxScope.RecentCard(
    content: RecentEntry,
    images: CoverImages,
) {
    val styles = rememberEntryStyles()
    val figures = content.figures
    EntryColumn(Modifier.align(Alignment.TopStart).entryCardPlace()) {
        EntryPartBox(EntryPart(priority = 0)) { EntryHeader(content.name, content.gameCount, styles) }
        if (figures != null && figures.games.isNotEmpty()) {
            EntryPartBox(EntryPart(priority = 1, gapAbove = EntryCardDimens.headerBottom)) {
                // A slot of the row before this one may be recomposed once with the new list: no game at its index.
                CoverRow(figures.games.size, EntryCardDimens.recentCoverGap) { at ->
                    val game = figures.games.getOrNull(at) ?: return@CoverRow
                    CoverWithCaption(
                        game, content.systems, DEFAULT_COVER_ASPECT, EntryCardDimens.recentCoverHeight, images, figures.now,
                        isShort = false, showsSystem = true, styles = styles,
                    )
                }
            }
            EntryPartBox(EntryPart(priority = 2, atBottom = true)) { WeekTile(figures.week, styles) }
        }
    }
}

/** This week: the play time and its games, a bar per day from the locale's first, and last week's time. */
@Composable
private fun WeekTile(
    week: WeekFigures,
    styles: EntryStyles,
) {
    val numbers = rememberCountFormat()
    val colors = PlumTheme.colors
    val locale = currentLocale()
    val names = remember(locale, week.firstDayOfWeek) { dayNames(locale, week.firstDayOfWeek) }
    val longest = week.daySeconds.maxOrNull()?.takeIf { it > 0 } ?: 1L
    EntryTile(EntryTags.WEEK, Modifier.fillMaxWidth(), verticalPad = true) {
        EntryLabel(stringResource(R.string.settings_statistics_this_week), styles)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BigFigure(if (week.totalSeconds <= 0) playTime(0) else statisticTime(week.totalSeconds), styles, modifier = Modifier.alignByBaseline().testTag(EntryTags.WEEK_TOTAL))
            if (week.games > 0) {
                SmallLine(
                    pluralStringResource(R.plurals.home_games_count, week.games, numbers.format(week.games)),
                    styles,
                    dim = false,
                    modifier = Modifier.alignByBaseline().testTag(EntryTags.WEEK_GAMES),
                )
            }
        }
        Spacer(Modifier.height(EntryCardDimens.weekChartTop))
        EvenColumns(EntryCardDimens.weekBarGap) {
            week.daySeconds.forEachIndexed { day, seconds ->
                val isToday = day == week.todayIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(EntryCardDimens.weekDayGap)) {
                    Box(Modifier.fillMaxWidth().height(EntryCardDimens.weekChartHeight), contentAlignment = Alignment.BottomCenter) {
                        val bar =
                            if (seconds > 0) maxOf(EntryCardDimens.weekBarMin, EntryCardDimens.weekChartHeight * (seconds.toFloat() / longest)) else EntryCardDimens.weekBarEmpty
                        Box(Modifier.weekBar(seconds, isToday, bar).testTag(EntryTags.weekBar(day)))
                    }
                    BasicText(names[day], style = styles.weekDay.copy(color = if (isToday) colors.text else colors.meta), maxLines = 1, softWrap = false)
                }
            }
        }
        if (week.lastWeekSeconds > 0) {
            val last = if (minutesOf(week.lastWeekSeconds) == 0) stringResource(R.string.settings_statistics_under_a_minute) else statisticTime(week.lastWeekSeconds)
            SmallLine(stringResource(R.string.second_screen_last_week, last), styles, modifier = Modifier.testTag(EntryTags.WEEK_LAST))
        }
    }
}

/** The short names of the seven days from [firstDay] (`Calendar.DAY_OF_WEEK`, 1 = Sunday): "Mon", "Tue", … in the locale. */
internal fun dayNames(
    locale: java.util.Locale,
    firstDay: Int,
): List<String> {
    val names = DateFormatSymbols(locale).shortWeekdays
    return List(DAYS_IN_WEEK) { names[(firstDay - 1 + it) % DAYS_IN_WEEK + 1] }
}

private const val DAYS_IN_WEEK = 7
