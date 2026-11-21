package com.plumemu.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The details column without box art: wide screens only, with no second-screen window, in the same 248 slot beside the
 * list. As with box art ([WideDetailsLayout]): the game's poster cover where the cover box stands, fitted to its system's
 * shape, then the title, "Super Nintendo · USA", then the facts table as with box art ([DetailsFacts]). No synopsis, year,
 * publisher or genre (none without ScreenScraper). With the systems focused ([textHidden]) the cover shows alone. The text comes first: at the larger text sizes the poster shrinks
 * to the height the text leaves and, under [PlumDimens.posterMinHeight], is left out ([WideDetailsLayout] `minCover`).
 */
@Composable
internal fun TextDetails(
    content: HomeContent,
    entry: HomeEntry,
    game: GameRowModel,
    textHidden: Boolean,
    modifier: Modifier,
) {
    val system = rememberSystemName(content, entry, game)
    val play = rememberShownPlay(game, content)
    val poster = entry.posterOf(game)
    // A tall poster (the system's shape) has the title and the system line beside it, as a tall box front does.
    val aspect = entry.shapeOf(game).widthOverHeight
    val sideTitle = aspect < TALL_BELOW && titleFitsBeside(game.title, aspect, PlumDimens.detailsColumnWidth, PlumTheme.type.meta, 1, PlumDimens.titleLineGap)
    WideDetailsLayout(
        cover = {
            // The same glass frame and slot as a real cover's: poster and box front read as one shelf.
            if (!textHidden || detailsCoverFitsWithSystemsOpen(PlumTheme.layout, content.hasBoxArt)) {
                if (sideTitle) SidePosterCover(poster, aspect, game) else CollaredDetailsCover(ArtSlot.Absent, DpSize(PlumDimens.detailsColumnWidth, PlumDimens.detailsCoverHeightWide), poster, aspect, game.appPackage)
            }
        },
        titleBlock = {
            if (!textHidden) {
                TitleBlock(PlumDimens.titleLineGap) {
                    DetailsTitle(game.title, maxLines = if (sideTitle) 3 else 2, subtitleLines = if (sideTitle) TITLE_SUBTITLE_LINES else 1)
                    SystemLine(system?.let { systemAndRegion(it, game.region) })
                }
            }
        },
        rest = { if (!textHidden) DetailsFacts(play, PlumTheme.type.smallFact) },
        modifier = modifier,
        minCover = PlumDimens.posterMinHeight,
        sideTitle = sideTitle,
    )
}

/** [SideTitleCover] for a poster: as wide as its shape needs at the band's height. */
@Composable
private fun SidePosterCover(
    poster: PosterSpec,
    aspect: Float,
    game: GameRowModel,
) {
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        val boxH = maxHeight - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding
        CollaredDetailsCover(ArtSlot.Absent, DpSize(boxH * aspect + CollarSize.Cover.sidePadding * 2, maxHeight), poster, aspect, game.appPackage)
    }
}

/**
 * "Super Nintendo · USA"; while [text] is null (a mixed list's game whose system is being found) an
 * empty line of the same style, so the facts under it never move when it arrives.
 */
@Composable
private fun SystemLine(text: String?) {
    if (text == null) {
        BasicText(text = "", style = PlumTheme.type.meta, maxLines = 1, modifier = Modifier.testTag(HomeTags.DETAILS_SYSTEM_PENDING))
    } else {
        FactLine(text, Modifier.testTag(HomeTags.DETAILS_SYSTEM), maxLines = 1)
    }
}

/** The system's name and the region with the details' separator; either alone when the other is empty. */
@Composable
private fun systemAndRegion(
    system: String,
    region: String,
): String =
    when {
        region.isEmpty() -> system
        system.isEmpty() -> region
        else -> stringResource(R.string.home_details_pair, system, region)
    }

/**
 * The name of [game]'s system: in a system's own list that system's (at once), in Recent and
 * Favorites the system that lists the game ([systemNameOf]), found once the focus has rested on it
 * (one walk over the library's rows per rested game, never per move); null until then. Kept by game:
 * new content for the same game (a favorite marked, back from a game) looks again but keeps the name
 * shown meanwhile, so the line never empties.
 */
@Composable
private fun rememberSystemName(
    content: HomeContent,
    entry: HomeEntry,
    game: GameRowModel,
): String? {
    if (entry.kind == EntryKind.System) return entry.name.orEmpty()
    val name = remember(game.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(content, game.id) {
        awaitDetailsSettle()
        name.value = content.systemNameOf(game.id).orEmpty()
    }
    return name.value
}

/** The name of the first system (shown or hidden) whose list holds game [gameId]; null when none does. */
internal fun HomeContent.systemNameOf(gameId: String): String? =
    (entries + hiddenEntries)
        .firstOrNull { it.kind == EntryKind.System && it.rows.any { row -> row.id == gameId } }
        ?.name
