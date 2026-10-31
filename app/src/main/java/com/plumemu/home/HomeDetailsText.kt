package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The details column without box art: wide screens only, with no
 * second-screen window, in the same 248 slot beside the list. Text only, top aligned where the
 * cover box starts with box art: the title, "Super Nintendo · USA", then the facts table as with box
 * art ([DetailsFacts]). No cover, no synopsis, no year, publisher or genre (none without ScreenScraper).
 */
@Composable
internal fun TextDetails(
    content: HomeContent,
    entry: HomeEntry,
    game: GameRowModel,
    modifier: Modifier,
) {
    val system = rememberSystemName(content, entry, game)
    val play = rememberShownPlay(game, content)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsBlockGapWide)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
            DetailsTitle(game.title)
            SystemLine(system?.let { systemAndRegion(it, game.region) })
        }
        DetailsFacts(play, PlumTheme.type.smallFact)
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
        FactLine(text, Modifier.testTag(HomeTags.DETAILS_SYSTEM))
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
