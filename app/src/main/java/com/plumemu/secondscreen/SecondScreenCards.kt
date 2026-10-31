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
import com.plumemu.coreui.theme.SecondScreenDimens

/** Test tags of the second screen's cards. */
internal object SecondScreenTags {
    const val CARD = "second_screen_card"
    const val BLOCK = "second_screen_block"
    const val TITLE = "second_screen_title"
    const val MARK = "second_screen_mark"
    const val FACTS = "second_screen_facts"

    /** The system card's facts line ("Nintendo · Home console · 1990–2003"); its badge has core-ui's `SYSTEM_BADGE_TAG`. */
    const val SYSTEM_FACTS = "second_screen_system_facts"
    const val SYSTEM_ABOUT = "second_screen_system_about"
    const val COVER = "second_screen_cover"
    const val TOP = "second_screen_top"

    /** The details card (layout C): the whole card inside its margins, its two fact lines, its description. */
    const val DETAILS = "second_screen_details"
    const val FACT_LINE = "second_screen_fact_line"
    const val DESCRIPTION = "second_screen_description"

    /** The details card's file line: the file Play launches, without its extension, above the played line. */
    const val FILE_NAME = "second_screen_file_name"

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
 * on the bottom margin, left-aligned, the calm mark in the middle, for a game with details the
 * details card ([PickingCard], layout C), or while Settings is open its statistics ([StatisticsCard]). A change of [content]
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
    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg).testTag(SecondScreenTags.CARD)) {
        val dimens = SecondScreenDimens.forShorterSide(minOf(maxWidth, maxHeight))
        CompositionLocalProvider(LocalBatteryReading provides battery) { PickingWindowCard(content, dimens, images) }
    }
}

/** What [SecondScreenCard] draws for [content] at [dimens]. */
@Composable
private fun BoxScope.PickingWindowCard(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
    images: CoverImages,
) {
    val colors = PlumTheme.colors
    when (content) {
        SecondScreenContent.Calm -> CalmMark(Modifier.align(Alignment.Center))
        SecondScreenContent.SetupQuestion ->
            BasicText(
                text = stringResource(R.string.second_screen_setup_question),
                style = PlumTheme.type[dimens.factsRole].copy(color = colors.text2),
                maxLines = SecondScreenDimens.QUESTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = blockModifier(dimens),
            )
        is SecondScreenContent.Statistics -> StatisticsCard(content.statistics, dimens, images)
        else -> PickingCard(content, dimens, images)
    }
}

private fun BoxScope.blockModifier(dimens: SecondScreenDimens): Modifier =
    Modifier.align(Alignment.BottomStart).padding(start = dimens.margin, end = dimens.margin, bottom = dimens.margin).testTag(SecondScreenTags.BLOCK)

/**
 * A picking card: one block on the bottom margin, left-aligned. A game with details gets the details card instead ([PickingCard]).
 */
@Composable
internal fun BoxScope.PickingBlock(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
) {
    val colors = PlumTheme.colors
    val texts = cardTexts(content, separatorColor = colors.muted) ?: return
    val margin = dimens.margin
    val place = Modifier.align(Alignment.BottomStart).padding(start = margin, end = margin, bottom = margin).testTag(SecondScreenTags.BLOCK)
    val oldGap = if (texts.systemFacts.isEmpty()) SecondScreenDimens.titleToFactsGap else SecondScreenDimens.systemFactsToLastGap
    if (texts.systemAbout == null) {
        Column(place) {
            PickingBlockTop(texts, dimens)
            if (texts.facts != null) Spacer(Modifier.height(oldGap))
            PickingBlockBottom(content, texts, dimens)
        }
    } else {
        BlockWithAbout(place, texts, dimens, oldGap, topMargin = margin, top = { PickingBlockTop(texts, dimens) }, bottom = { PickingBlockBottom(content, texts, dimens) })
    }
}

/** A picking block from the badge to the system's facts line. */
@Composable
private fun PickingBlockTop(
    texts: CardTexts,
    dimens: SecondScreenDimens,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column {
        texts.badge?.let { abbr ->
            SystemBadge(abbr, Modifier.defaultMinSize(dimens.badgeMinWidth, dimens.badgeMinHeight))
            Spacer(Modifier.height(SecondScreenDimens.badgeToTopGap))
        }
        TopLine(texts.top, type[dimens.topRole].copy(color = colors.meta))
        Spacer(Modifier.height(SecondScreenDimens.topToTitleGap))
        SecondScreenTitle(
            title = texts.title,
            isFavorite = texts.isFavorite,
            titleStyle = type[dimens.titleRole].copy(color = colors.text),
            starStyle = type[dimens.starRole],
            starColor = colors.meta,
            // The badge takes the height of a title line and more; a long system name gives one line up so the block keeps inside its margins at the largest text.
            maxLines = if (texts.badge != null) maxOf(1, dimens.titleMaxLines - 1) else dimens.titleMaxLines,
            modifier = Modifier.testTag(SecondScreenTags.TITLE),
        )
        if (texts.systemFacts.isNotEmpty()) {
            Spacer(Modifier.height(SecondScreenDimens.titleToFactsGap))
            SystemFactsLine(texts.systemFacts, type[dimens.factsRole].copy(color = colors.meta))
        }
    }
}

/** A picking block's last lines: the play facts and the battery line. */
@Composable
private fun PickingBlockBottom(
    content: SecondScreenContent,
    texts: CardTexts,
    dimens: SecondScreenDimens,
) {
    Column {
        texts.facts?.let { facts -> FactsLine(facts, PlumTheme.type[dimens.factsRole].copy(color = PlumTheme.colors.text2)) }
        BatteryLine((content as? SecondScreenContent.Game)?.batteryPace, texts.facts, dimens)
    }
}

/**
 * A system block with about text: [top], the about text under it, then [bottom]. The about text is
 * composed only when the whole block, measured, stays inside the card's margins; otherwise it is left out
 * entirely (the title is never given a line less to make room), and the gap above [bottom] is [plainGap],
 * as in a block with no about text. Its height is measured with the text measurer, at the width and
 * lines the text is drawn with.
 */
@Composable
private fun BlockWithAbout(
    modifier: Modifier,
    texts: CardTexts,
    dimens: SecondScreenDimens,
    plainGap: Dp,
    topMargin: Dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val about = texts.systemAbout ?: return
    val style = PlumTheme.type[dimens.factsRole].copy(color = PlumTheme.colors.meta)
    val measurer = rememberTextMeasurer()
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val topPlaceables = subcompose(SLOT_TOP, top).map { it.measure(loose) }
        val bottomPlaceables = subcompose(SLOT_BOTTOM, bottom).map { it.measure(loose) }
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0
        val hasBottom = bottomHeight > 0
        val gapBefore = (if (texts.systemFacts.isEmpty()) SecondScreenDimens.titleToFactsGap else SecondScreenDimens.systemFactsToAboutGap).roundToPx()
        val gapAfter = if (hasBottom) SecondScreenDimens.systemAboutToLastGap.roundToPx() else 0
        val aboutLayout = measurer.measure(about, style, constraints = Constraints(maxWidth = constraints.maxWidth))
        // Whole or not at all: more lines than the card allows, or a block that would not fit, leaves the text out.
        val withAbout =
            aboutLayout.lineCount <= dimens.aboutMaxLines &&
                topHeight + gapBefore + aboutLayout.size.height + gapAfter + bottomHeight <= constraints.maxHeight - topMargin.roundToPx()
        val aboutPlaceables =
            if (withAbout) {
                subcompose(SLOT_ABOUT) {
                    BasicText(
                        text = about,
                        style = style,
                        maxLines = dimens.aboutMaxLines,
                        modifier = Modifier.testTag(SecondScreenTags.SYSTEM_ABOUT),
                    )
                }.map { it.measure(loose) }
            } else {
                emptyList()
            }
        val aboutRealHeight = aboutPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomGap = if (!hasBottom) 0 else if (withAbout) gapAfter else plainGap.roundToPx()
        val aboutBlock = if (withAbout) gapBefore + aboutRealHeight else 0
        val width = maxOf(topPlaceables.maxOfOrNull { it.width } ?: 0, aboutPlaceables.maxOfOrNull { it.width } ?: 0, bottomPlaceables.maxOfOrNull { it.width } ?: 0)
        val height = topHeight + aboutBlock + bottomGap + bottomHeight
        layout(width, height) {
            topPlaceables.forEach { it.placeRelative(0, 0) }
            aboutPlaceables.forEach { it.placeRelative(0, topHeight + gapBefore) }
            bottomPlaceables.forEach { it.placeRelative(0, topHeight + aboutBlock + bottomGap) }
        }
    }
}

private const val SLOT_TOP = "top"
private const val SLOT_ABOUT = "about"
private const val SLOT_BOTTOM = "bottom"

/**
 * The system's facts line: the first of [lines] (widest first) that fits the width the layout gives
 * it, so the kind goes first and then the maker; one line, never wrapped or cut (the same rule as the top screen's band).
 */
@Composable
private fun SystemFactsLine(
    lines: List<String>,
    style: TextStyle,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val widthPx = constraints.maxWidth
        val shown = remember(lines, style, widthPx) { firstThatFits(lines) { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= widthPx } }
        BasicText(text = shown, style = style, maxLines = 1, softWrap = false, modifier = Modifier.testTag(SecondScreenTags.SYSTEM_FACTS))
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
