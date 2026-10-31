package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.lastPlayed
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.LocalButtonLetters

/*
 * Statistics' side panel. A most-played game's row shows
 * that game here: its cover, title, system, the figures stored for it and "A to play". Any other
 * row keeps the privacy explanation.
 */

/** The panel for the focused Statistics row: the game's, or the explanation when [game] is null. */
@Composable
internal fun StatisticsPanel(game: StatisticRow.Game?) {
    if (game == null) {
        ExplainPanel(R.string.settings_section_statistics, R.string.settings_statistics_body)
    } else {
        StatisticGamePanel(game.game)
    }
}

/**
 * The cover fitted inside the panel's width by [PlumDimens.settingsStatisticsCoverHeight] at most, then
 * the title (two lines), the system, the facts and the play line. The text below is measured first and
 * the cover takes the height that is left, so a large text size or a two-line title shrinks the cover,
 * never cuts a line. A figure that is not stored is left out: no "0 times", no invented date.
 */
@Composable
private fun StatisticGamePanel(game: TopGame) {
    val style = PlumTheme.type.panelBody
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val height = minOf(maxHeight, PlumDimens.settingsStatisticsCoverHeight)
            FittedCover(game.cover, DpSize(settingsPanelWidth(PlumTheme.layout), height), SettingsPanelTags.STATISTIC_COVER)
        }
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
            PanelTitle(game.title)
            PanelFact(game.systemName)
        }
        StatisticGameFacts(game, style)
        PanelFact(
            stringResource(R.string.settings_statistics_play_hint, stringResource(LocalButtonLetters.current.confirm)),
            Modifier.testTag(SettingsPanelTags.STATISTIC_PLAY_HINT),
        )
    }
}

/** Play time, times played and last played: a `muted` label and its value in `text2` on one line. */
@Composable
private fun StatisticGameFacts(
    game: TopGame,
    style: TextStyle,
) {
    val colors = PlumTheme.colors
    val numbers = rememberCountFormat()
    val facts =
        buildList {
            add(stringResource(R.string.home_details_play_time) to statisticTime(game.seconds))
            if (game.timesPlayed > 0) add(stringResource(R.string.settings_statistics_times_played) to numbers.format(game.timesPlayed))
            game.lastPlayedDaysAgo?.let { add(stringResource(R.string.home_details_last_played) to lastPlayed(it)) }
        }
    // One line per fact: label left, value right (it wraps, never the label's line, when a large text size needs it).
    Column(Modifier.testTag(SettingsPanelTags.STATISTIC_FACTS), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
        for ((label, value) in facts) {
            Row(Modifier.onRowTextLine(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.factsColumnGap)) {
                BasicText(label, style = style.copy(color = colors.muted))
                BasicText(value, style = style.copy(color = colors.text2, textAlign = TextAlign.End), modifier = Modifier.weight(1f))
            }
        }
    }
}
