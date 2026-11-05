package com.plumemu.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import com.plumemu.home.CoverImages
import com.plumemu.home.LocalBatteryReading
import com.plumemu.home.NoBatteryReading
import com.plumemu.home.firstThatFits
import com.plumemu.home.rememberBatteryReading
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.components.SystemBadge
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideSecondScreenText
import com.plumemu.coreui.theme.SecondScreenDimens

/** Test tags of the second screen's cards. */
internal object SecondScreenTags {
    const val CARD = "second_screen_card"
    const val BLOCK = "second_screen_block"
    const val TITLE = "second_screen_title"
    const val MARK = "second_screen_mark"

    /** The system card's facts line ("Nintendo · Home console · 1990–2003"); its badge has core-ui's `SYSTEM_BADGE_TAG`. */
    const val SYSTEM_FACTS = "second_screen_system_facts"
    const val SYSTEM_ABOUT = "second_screen_system_about"
    const val COVER = "second_screen_cover"
    const val TOP = "second_screen_top"

    /** The App and Apps cards: the whole card inside its margins; the Apps card's icon grid and each icon in it. */
    const val DETAILS = "second_screen_details"
    const val APPS_GRID = "second_screen_apps_grid"
    const val APPS_ICON = "second_screen_apps_icon"

    /** The statistics card: the whole block, its parts, a most-played game's row and its parts. */
    const val STATISTICS = "second_screen_statistics"
    const val STATISTICS_TOTAL = "second_screen_statistics_total"
    const val STATISTICS_TOTAL_LABEL = "second_screen_statistics_total_label"
    const val STATISTICS_NOTHING_PLAYED = "second_screen_statistics_nothing_played"
    const val STATISTICS_GAME_TITLE = "second_screen_statistics_game_title"
    const val STATISTICS_COVER = "second_screen_statistics_cover"
    const val STATISTICS_FIGURE = "second_screen_statistics_figure"
    const val STATISTICS_SYSTEM = "second_screen_statistics_system"
    const val STATISTICS_PLAYED = "second_screen_statistics_played"
    const val STATISTICS_BOX_ART = "second_screen_statistics_box_art"

    /** A most-played game's row, [rank] from 1. */
    fun statisticsGame(rank: Int) = "second_screen_statistics_game_$rank"
}

/**
 * The second screen while the person is picking:
 * fills the size it is given with the theme's background and draws [content] as one block resting
 * on the bottom margin, left-aligned, the calm mark in the middle, or while Settings is open its statistics ([StatisticsCard]).
 * A game, a system, Recent and Favorites have their own cards; an app and the Apps entry go through [PickingCard]. A change of [content]
 * replaces the block at once: no fade, so a change costs one frame (the window shares the main thread with the top screen).
 */
@Composable
internal fun SecondScreenCard(
    content: SecondScreenContent,
    modifier: Modifier = Modifier,
    /** Decodes the box art a [SecondScreenContent.Game] carries; none by default (the card without art). */
    images: CoverImages = CoverImages.None,
    /** Settings › Show battery: off, no battery line and no battery receiver (as the top screen's bar and the in-game card). */
    showBattery: Boolean = true,
) {
    val colors = PlumTheme.colors
    // One battery receiver for the picking window, kept across moves: the picking line reads it, never its own.
    val battery = if (showBattery) rememberBatteryReading() else NoBatteryReading
    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg).then(if (hasBackdropGlow(content)) Modifier.backdropGlow() else Modifier).testTag(SecondScreenTags.CARD)) {
        val dimens = SecondScreenDimens.forShorterSide(minOf(maxWidth, maxHeight))
        ProvideSecondScreenText {
            CompositionLocalProvider(LocalBatteryReading provides battery, LocalCardLine provides true) { PickingWindowCard(content, dimens, images) }
        }
    }
}

/** The accent glow in the corner belongs to the game, system, Recent and Favorites cards (the boards); the other cards keep the plain page. */
private fun hasBackdropGlow(content: SecondScreenContent) = content is SecondScreenContent.Game || content is SystemEntry || content is RecentEntry || content is FavoritesEntry

/** What [SecondScreenCard] draws for [content] at [dimens]. */
@Composable
private fun BoxScope.PickingWindowCard(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
    images: CoverImages,
) {
    val colors = PlumTheme.colors
    cardTrace("SS:picking") { when (content) {
        SecondScreenContent.Calm -> CalmMark(Modifier.align(Alignment.Center))
        SecondScreenContent.SetupQuestion ->
            BasicText(
                text = stringResource(R.string.second_screen_setup_question),
                style = PlumTheme.type.card(dimens.factsRole).copy(color = colors.text2),
                maxLines = SecondScreenDimens.QUESTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = blockModifier(dimens),
            )
        is SecondScreenContent.Statistics -> StatisticsCard(content.statistics, dimens, images)
        is SecondScreenContent.Game -> GameCard(content)
        is SystemEntry -> SystemCard(content, images)
        is RecentEntry -> RecentCard(content, images)
        is FavoritesEntry -> FavoritesCard(content, images)
        else -> PickingCard(content, dimens)
    } }
}

private fun BoxScope.blockModifier(dimens: SecondScreenDimens): Modifier =
    Modifier.align(Alignment.BottomStart).padding(start = dimens.margin, end = dimens.margin, bottom = dimens.margin).testTag(SecondScreenTags.BLOCK)

/** A picking card with no icon to show: one block on the bottom margin, left-aligned, the top line and the title. */
@Composable
internal fun BoxScope.PickingBlock(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val texts = cardTexts(content) ?: return
    Column(blockModifier(dimens)) {
        TopLine(texts.top, type.card(dimens.topRole).copy(color = colors.meta))
        Spacer(Modifier.height(SecondScreenDimens.topToTitleGap))
        SecondScreenTitle(
            title = texts.title,
            isFavorite = texts.isFavorite,
            titleStyle = type.card(dimens.titleRole).copy(color = colors.text),
            starStyle = type.card(dimens.starRole),
            starColor = colors.meta,
            maxLines = dimens.titleMaxLines,
            modifier = Modifier.testTag(SecondScreenTags.TITLE),
        )
    }
}

/** The top line: the accent dot (the top screen's focus dot: "this is where the cursor is"), then [text] on one line. */
@Composable
internal fun TopLine(
    text: String,
    style: TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(SecondScreenDimens.topDot).background(PlumTheme.colors.accent, CircleShape))
        Spacer(Modifier.width(SecondScreenDimens.topDotGap))
        BasicText(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(SecondScreenTags.TOP),
        )
    }
}

/**
 * The Plumemu mark alone: the launcher icon's monochrome shapes, cropped to the mark (the icon
 * sits in a 108 dp canvas with room around it), [SecondScreenDimens.calmMarkHeight] high, `chip`.
 */
@Composable
private fun CalmMark(modifier: Modifier) {
    val unit = SecondScreenDimens.calmMarkHeight.value / (SecondScreenDimens.MARK_BOTTOM - SecondScreenDimens.MARK_TOP)
    val canvas = (SecondScreenDimens.ICON_CANVAS * unit).dp
    val centreY = (SecondScreenDimens.MARK_TOP + SecondScreenDimens.MARK_BOTTOM) / 2
    Box(modifier.size(SecondScreenDimens.calmMarkHeight).testTag(SecondScreenTags.MARK), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(PlumTheme.colors.chip),
            modifier = Modifier.requiredSize(canvas).offset(y = ((SecondScreenDimens.ICON_CANVAS / 2 - centreY) * unit).dp),
        )
    }
}
