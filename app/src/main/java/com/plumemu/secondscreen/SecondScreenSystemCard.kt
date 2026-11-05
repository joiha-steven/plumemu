package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.plumemu.R
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.CoverImages
import com.plumemu.home.firstThatFits
import com.plumemu.home.rememberCountFormat
import com.plumemu.home.systemFactsOf
import com.plumemu.settings.screen.statisticTime

/**
 * The system card (board `SecondSystem`): the name and game count, the last played games in their collars with when, three tiles (play time,
 * played, achievements), one status line, and the maker, kind and year with the about text at the bottom. Until its figures are read only the
 * header shows. When the height runs out parts go from the bottom, whole ([EntryColumn]).
 */
@Composable
internal fun BoxScope.SystemCard(
    content: SystemEntry,
    images: CoverImages,
) {
    val styles = rememberEntryStyles()
    val figures = content.figures
    val systems = remember(content) { mapOf(content.systemId to EntrySystemLook(content.name, content.info?.abbr.orEmpty(), content.coverAspect)) }
    EntryColumn(Modifier.align(Alignment.TopStart).entryCardPlace()) {
        EntryPartBox(EntryPart(priority = 0)) { EntryHeader(content.name, content.gameCount, styles) }
        if (figures != null) {
            if (figures.lastPlayed.isNotEmpty()) {
                EntryPartBox(EntryPart(priority = 1, gapAbove = EntryCardDimens.headerBottom)) { LastPlayed(content, figures, systems, images, styles) }
            }
            EntryPartBox(EntryPart(priority = 2, gapAbove = EntryCardDimens.tilesTop)) { Tiles(content, figures, styles) }
            val segments = statusSegments(figures)
            if (segments.isNotEmpty()) {
                EntryPartBox(EntryPart(priority = 3, gapAbove = EntryCardDimens.statusTop)) { StatusLine(segments, styles, EntryTags.STATUS) }
            }
            if (content.info != null) EntryPartBox(EntryPart(priority = 4, atBottom = true)) { Foot(content, styles) }
        }
    }
}

@Composable
private fun LastPlayed(
    content: SystemEntry,
    figures: SystemFigures,
    systems: Map<String, EntrySystemLook>,
    images: CoverImages,
    styles: EntryStyles,
) {
    Column {
        EntryLabel(stringResource(R.string.second_screen_last_played), styles)
        Spacer(Modifier.height(EntryCardDimens.labelToRow))
        CoverRow(figures.lastPlayed.size, EntryCardDimens.systemCoverGap) { at ->
            val game = figures.lastPlayed.getOrNull(at) ?: return@CoverRow
            CoverWithCaption(
                game, systems, content.coverAspect, EntryCardDimens.systemCoverHeight, images, figures.now,
                isShort = true, showsSystem = false, styles = styles,
            )
        }
    }
}

@Composable
private fun Tiles(
    content: SystemEntry,
    figures: SystemFigures,
    styles: EntryStyles,
) {
    val numbers = rememberCountFormat()
    EntryTileRow {
        EntryTile(EntryTags.TILE_PLAY, Modifier.weight(1f).fillMaxHeight()) {
            EntryLabel(stringResource(R.string.second_screen_play_time), styles)
            BigFigure(statisticTimeOrZero(figures.playSeconds), styles)
            SmallLine(stringResource(R.string.second_screen_on_system, content.name), styles)
        }
        EntryTile(EntryTags.TILE_PLAYED, Modifier.weight(1f).fillMaxHeight()) {
            EntryLabel(stringResource(R.string.second_screen_played), styles)
            BigFigure(numbers.format(figures.playedGames), styles, of = numbers.format(content.gameCount))
            EntryMeter(if (content.gameCount > 0) figures.playedGames.toFloat() / content.gameCount else 0f)
            SmallLine(stringResource(R.string.second_screen_games_played), styles)
        }
        figures.achievements?.let { ra ->
            EntryTile(EntryTags.TILE_ACHIEVEMENTS, Modifier.weight(1f).fillMaxHeight()) {
                EntryLabel(stringResource(R.string.second_screen_achievements), styles)
                BigFigure(numbers.format(ra.mastered), styles, unit = stringResource(R.string.second_screen_mastered))
                SmallLine(
                    if (ra.points != null) {
                        pluralStringResource(R.plurals.second_screen_points, ra.points.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), numbers.format(ra.points))
                    } else {
                        pluralStringResource(R.plurals.second_screen_games_with_progress, ra.gamesWithProgress, numbers.format(ra.gamesWithProgress))
                    },
                    styles,
                )
            }
        }
    }
}

/** A play time in the Info panel's words; none at all is "0 min", not "Less than a minute". */
@Composable
private fun statisticTimeOrZero(seconds: Long): String = if (seconds <= 0) com.plumemu.home.playTime(0) else statisticTime(seconds)

/** "3 new this week", "Opens in RetroArch", "Snes9x", "4 files not added": each only when it applies. */
@Composable
internal fun statusSegments(figures: SystemFigures): List<Segment> {
    val numbers = rememberCountFormat()
    val emulator = figures.emulator?.takeIf { it.isInstalled && !it.isNotChosenYet && it.name.isNotBlank() }
    return buildList {
        if (figures.newThisWeek > 0) add(Segment(pluralStringResource(R.plurals.second_screen_new_this_week, figures.newThisWeek, numbers.format(figures.newThisWeek))))
        if (emulator != null) {
            add(Segment(stringResource(R.string.second_screen_opens_in, emulator.name)))
            emulator.coreName?.takeIf { it.isNotBlank() }?.let { add(Segment(it)) }
        }
        if (figures.leftOutFiles > 0) add(Segment(pluralStringResource(R.plurals.second_screen_files_not_added, figures.leftOutFiles, numbers.format(figures.leftOutFiles)), isStrong = true))
    }
}

/** The foot: a rule, the facts line (the widest of its forms that fits one line), and the about text when it fits its two lines whole. */
@Composable
private fun Foot(
    content: SystemEntry,
    styles: EntryStyles,
) {
    val info = requireNotNull(content.info)
    val colors = PlumTheme.colors
    val lines = systemFactsOf(info).candidates(stringResource(R.string.system_facts_separator))
    val about = info.about?.let { stringResource(it) }
    val measurer = rememberTextMeasurer()
    val factsStyle = styles.status.copy(fontSize = styles.foot.fontSize, lineHeight = styles.foot.lineHeight, color = colors.text2)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(EntryTags.FOOT)) {
        val widthPx = constraints.maxWidth
        val facts = if (lines.isEmpty()) null else firstThatFits(lines) { measurer.measure(it, factsStyle, maxLines = 1, softWrap = false).size.width <= widthPx }
        val showsAbout = about != null && measurer.measure(about, styles.foot, constraints = Constraints(maxWidth = widthPx)).lineCount <= EntryCardDimens.ABOUT_MAX_LINES
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.fillMaxWidth().height(EntryCardDimens.aboutRule).background(colors.line))
            Spacer(Modifier.height(EntryCardDimens.aboutPadTop))
            if (facts != null) BasicText(facts, style = factsStyle, maxLines = 1, softWrap = false, modifier = Modifier.boardLine(factsStyle, styles).testTag(SecondScreenTags.SYSTEM_FACTS))
            if (facts != null && showsAbout) Spacer(Modifier.height(EntryCardDimens.aboutGap))
            if (showsAbout && about != null) BasicText(about, style = styles.foot, maxLines = EntryCardDimens.ABOUT_MAX_LINES, modifier = Modifier.boardLine(styles.foot, styles).testTag(SecondScreenTags.SYSTEM_ABOUT))
        }
    }
}
