package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes of the second screen's picking cards (design/rules.md › Second screen), in dp on that
 * screen's own density. The class follows the shorter side of the window: [Roomy] from
 * [ROOMY_MIN_SHORT_SIDE] (the Thor's 468), [Compact] below (a 4:3 bottom screen such as 480x360).
 */
@Immutable
enum class SecondScreenDimens(
    val margin: Dp,
    val titleMaxLines: Int,
    val topRole: TypeRole,
    val titleRole: TypeRole,
    val starRole: TypeRole,
    val factsRole: TypeRole,
    /** The details card (layout C): the side of the square the box front is fitted in. */
    val artSlot: Dp,
    /** The details card's title role and lines, and the description's lines. */
    val detailsTitleRole: TypeRole,
    val detailsTitleMaxLines: Int,
    val descriptionMaxLines: Int,
    /**
     * The description's lines while the top screen shows the game's grid: the card has no cover then,
     * so the description takes its room; fewer when they do not fit.
     */
    val descriptionMaxLinesBesideGrid: Int,
    /** The statistics card: the square a most-played game's cover is fitted in, and its title's lines. */
    val statisticsCover: Dp,
    val statisticsTitleMaxLines: Int,
    /** The statistics card: between its rows, and between its groups (the total, the games, the other lines). */
    val statisticsRowGap: Dp,
    val statisticsGroupGap: Dp,
    /** The system card's badge: its least width and height. */
    val badgeMinWidth: Dp,
    val badgeMinHeight: Dp,
    /** The system card's about text: its most lines. */
    val aboutMaxLines: Int,
) {
    Roomy(
        40.dp, 3, TypeRole.SecondTopRoomy, TypeRole.SecondTitleRoomy, TypeRole.SecondStarRoomy, TypeRole.SecondFactsRoomy,
        artSlot = 240.dp, detailsTitleRole = TypeRole.SecondDetailsTitleRoomy, detailsTitleMaxLines = 3, descriptionMaxLines = 3,
        descriptionMaxLinesBesideGrid = 8,
        statisticsCover = 48.dp, statisticsTitleMaxLines = 2, statisticsRowGap = 8.dp, statisticsGroupGap = 20.dp,
        badgeMinWidth = 96.dp, badgeMinHeight = 64.dp, aboutMaxLines = 3,
    ),
    Compact(
        28.dp, 2, TypeRole.Meta, TypeRole.SecondTitleCompact, TypeRole.SecondStarCompact, TypeRole.SecondFactsCompact,
        artSlot = 168.dp, detailsTitleRole = TypeRole.SecondDetailsTitleCompact, detailsTitleMaxLines = 2, descriptionMaxLines = 2,
        descriptionMaxLinesBesideGrid = 4,
        statisticsCover = 36.dp, statisticsTitleMaxLines = 1, statisticsRowGap = 6.dp, statisticsGroupGap = 12.dp,
        badgeMinWidth = 72.dp, badgeMinHeight = 48.dp, aboutMaxLines = 3,
    ),
    ;

    /**
     * Whether this size has room for box art; both have it (layout C). The feed reads no art for a size without (app `SecondScreenFeed.hasArtRoom`).
     */
    val showsArt: Boolean get() = artSlot > 0.dp

    companion object {
        val ROOMY_MIN_SHORT_SIDE: Dp = 400.dp

        fun forShorterSide(shorterSide: Dp): SecondScreenDimens = if (shorterSide >= ROOMY_MIN_SHORT_SIDE) Roomy else Compact

        /** Between the top line and the title. */
        val topToTitleGap: Dp = 12.dp

        /** Between the title and the facts line. */
        val titleToFactsGap: Dp = 16.dp

        /** The system card: between the badge and the top line (the dot and the count). */
        val badgeToTopGap: Dp = 18.dp

        /** The system card: between its facts line ("Nintendo · Home console · 1990–2003") and the last-played line. */
        val systemFactsToLastGap: Dp = PlumDimens.factsRowGap

        /** The system card: between its facts line and the about text under it. */
        val systemFactsToAboutGap: Dp = PlumDimens.factsRowGap

        /** The system card: between the about text and the last-played line. */
        val systemAboutToLastGap: Dp = topToTitleGap

        /** Between the end of the title and its star. */
        val starGap: Dp = 10.dp

        /** The accent dot before the top line, and the gap after it: the focus dot of the top screen. */
        val topDot: Dp = PlumDimens.focusDot
        val topDotGap: Dp = PlumDimens.focusDotGap

        /** The calm screen's Plumemu mark: its height, and the part of the launcher icon's 108 dp canvas it sits in. */
        val calmMarkHeight: Dp = 52.dp
        const val ICON_CANVAS = 108f
        const val MARK_TOP = 33.3f
        const val MARK_BOTTOM = 80.9f

        /** The details card: the top line's role (16 at both sizes; the fact lines' 15 is off the ramp, see [TypeRole.SecondDetailsFact]) and the fact lines' ("1994 · Nintendo"). */
        val detailsTopRole: TypeRole = TypeRole.SecondTopRoomy
        val detailsFactRole: TypeRole = TypeRole.SecondDetailsFact

        /** The details card: between the art and the text beside it. */
        val artToTextGap: Dp = 24.dp

        /** The details card: between the art (or the text beside it, if taller) and the description under it. */
        val artToDescriptionGap: Dp = 20.dp

        /** The details card: between the title and the "1994 · Nintendo" line. */
        val titleToFactLinesGap: Dp = 12.dp

        /** The details card: between "1994 · Nintendo" and "Action · 1 player". */
        val factLineGap: Dp = PlumDimens.factsRowGap

        /** The details card: the least room above the description (without art) and above the played line. */
        val descriptionGap: Dp = 16.dp

        /** The statistics card: between a most-played game's cover slot and its title. */
        val statisticsCoverGap: Dp = 16.dp

        /** The statistics card: between the total and its "Total play time" line. */
        val statisticsLabelGap: Dp = PlumDimens.factsRowGap

        /** The statistics card: the least room between a row's words and its figure at the right. */
        val statisticsFigureGap: Dp = 16.dp

        /** Lines the setup question may take at the largest text size. */
        const val QUESTION_MAX_LINES = 2
    }
}
