package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import java.text.NumberFormat
import kotlin.math.floor

/**
 * The details column for the focused game of the focused system. With
 * box art, as below; without it (wide screens only) the text-only
 * column, [TextDetails]. Wide (RP5-2-games): 248 beside the list, the large box front fitted
 * in 248×178 (bottom left, so the title below never moves), title, year and publisher, genre and
 * players, the facts table when known, the synopsis in what height is left. Narrow
 * (Nova-3-details): the third strip stop, `width − 104`; title,
 * year and publisher, genre and players and the facts on the left, the cover fitted in 228×164 on the right, the
 * synopsis underneath. On the narrow games stop the same layout peeks in from the right, cut by the screen's edge.
 */
@Composable
internal fun DetailsColumn(
    content: HomeContent,
    state: StripState,
    onAction: (Action) -> Unit,
    modifier: Modifier,
) {
    val entryIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    val entry = content.entries.getOrNull(entryIndex) ?: return
    val row by remember(state, entry.id) { derivedStateOf { state.focus.rowOf(entry.id) } }
    val game = entry.rows.getOrNull(row) ?: return
    // With the systems focused the strip rests at its start and the column sits under the screen's right
    // edge: its text would be cut mid-word, so none is composed (wide: only the cover shows; narrow: the layout keeps its places).
    val textHidden by remember(state) { derivedStateOf { state.focus.column == Column.Systems } }
    if (!content.hasBoxArt) {
        // Wide only (HomeStrip, listHasDetails): not a strip stop, a tap here has nothing to do.
        if (textHidden) Spacer(modifier.testTag(HomeTags.DETAILS)) else TextDetails(content, entry, game, modifier.testTag(HomeTags.DETAILS))
        return
    }
    val slot = LocalHomeArt.current.slot(game.scrapeKey)
    if (PlumTheme.layout.widthClass == WidthClass.Wide) {
        // Not a strip stop on wide screens: a tap here has nothing to do.
        WideDetails(content, game, slot, textHidden, modifier.testTag(HomeTags.DETAILS))
    } else {
        // The narrow details stop: a tap from the list opens it, a tap in it plays (core-input).
        val tap = remember(onAction) { Modifier.tapItem(Column.Details, 0, onAction).testTag(HomeTags.DETAILS) }
        // The peek shows the same details as the third stop, cut by the screen's edge, so the person sees there is more to the right
        // With the systems focused the column is off screen or a sliver of cover, so its text is not composed.
        NarrowDetails(content, game, slot, textHidden, modifier.then(tap))
    }
}

@Composable
private fun WideDetails(
    content: HomeContent,
    game: GameRowModel,
    slot: ArtSlot,
    textHidden: Boolean,
    modifier: Modifier,
) {
    val type = PlumTheme.type
    val extras = rememberExtras(game, slot)
    val play = rememberShownPlay(game, content)
    val details = detailsOf(game, slot)?.withRating(extras)
    val synopsis = extras.synopsis
    // One layout whether the text shows or not, so the cover is not composed again when the systems take focus.
    WideDetailsLayout(
        cover = { DetailsCover(slot, DpSize(PlumDimens.detailsColumnWidth, PlumDimens.detailsCoverHeightWide), Alignment.BottomStart, isBoxFitted = false, shrinksToFit = true) },
        titleBlock = {
            if (!textHidden) {
                Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
                    DetailsTitle(game.title)
                    if (details != null) FactLines(details)
                }
            }
        },
        rest = {
            if (!textHidden) {
                DetailsFacts(play, type.smallFact)
                Synopsis(synopsis)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun NarrowDetails(
    content: HomeContent,
    game: GameRowModel,
    slot: ArtSlot,
    textHidden: Boolean,
    modifier: Modifier,
) {
    val type = PlumTheme.type
    val extras = rememberExtras(game, slot)
    val play = rememberShownPlay(game, content)
    val details = detailsOf(game, slot)?.withRating(extras)
    val synopsis = extras.synopsis
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopBlockGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopCoverGap)) {
            // Text first, the cover on the right: the peek shows the text, cut by the screen's edge.
            if (!textHidden) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopLineGap)) {
                    DetailsTitle(game.title)
                    if (details != null) FactLines(details)
                    if (play != ShownPlay.Absent) {
                        Spacer(Modifier.height(PlumDimens.detailsStopFactsSpacer))
                        DetailsFacts(play, type.smallFact)
                    }
                }
            } else {
                // The cover keeps its place on the right while the text is not composed.
                Spacer(Modifier.weight(1f))
            }
            DetailsCover(slot, DpSize(PlumDimens.detailsStopCoverWidth, PlumDimens.detailsStopCoverHeight), Alignment.TopStart, isBoxFitted = true)
        }
        if (!textHidden) Synopsis(synopsis)
    }
}

/**
 * The synopsis in the height the column has left: as many whole lines as fit, the last one cut
 * with an ellipsis; nothing when not even one line fits (a long synopsis never pushes anything off
 * the screen, and a half line is never drawn).
 */
@Composable
private fun ColumnScope.Synopsis(text: String) {
    if (text.isEmpty()) return
    val style = PlumTheme.type.description.copy(color = PlumTheme.colors.text2)
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
        val lineHeight = with(density) { style.lineHeight.toPx() }
        val lines = if (lineHeight > 0f && constraints.hasBoundedHeight) floor(constraints.maxHeight / lineHeight).toInt() else Int.MAX_VALUE
        if (lines >= 1) {
            BasicText(
                text = text,
                style = style,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(HomeTags.DETAILS_SYNOPSIS),
            )
        }
    }
}

/** [this] with the rating read with the synopsis, when it has none of its own (the library's results). */
private fun GameDetails.withRating(extras: DetailsExtras): GameDetails = if (rating != null) this else copy(rating = extras.rating)

/**
 * [game]'s synopsis and rating: its own (the fakes), else its ScreenScraper result's, read once the
 * focus has rested on it ([awaitDetailsSettle]; a held key reads none) and kept for the last few
 * games. Until then the genre line shows the genre alone.
 */
@Composable
private fun rememberExtras(
    game: GameRowModel,
    slot: ArtSlot,
): DetailsExtras {
    game.details?.let { return DetailsExtras(it.description, it.rating) }
    val key = game.scrapeKey
    if (key == null || slot !is ArtSlot.Found) return DetailsExtras.None
    val art = LocalHomeArt.current
    val text = remember(art, key, slot) { mutableStateOf(art.keptExtras(key) ?: DetailsExtras.None) }
    LaunchedEffect(art, key, slot) {
        if (art.keptExtras(key) != null) return@LaunchedEffect
        awaitDetailsSettle()
        text.value = art.extras(key)
    }
    return text.value
}

/**
 * Returns once the focus has rested [DETAILS_SETTLE_MS] on the game of the effect that calls it (a
 * move cancels that effect first). Frame time, not a timer: it follows the screen's clock (and the
 * tests' clock). The details column's reads (synopsis, play facts) start after it.
 */
internal suspend fun awaitDetailsSettle() {
    val start = withFrameMillis { it }
    var now = start
    while (now - start < DETAILS_SETTLE_MS) now = withFrameMillis { it }
}

/**
 * The large box front in the details' cover slot of [size], at its own shape. With [isBoxFitted]
 * (narrow: the text is beside it) the box itself takes the picture's shape ([fitInside],
 * height-bound for a portrait box), so the text uses the width it leaves. Without (wide: the text
 * is below it) the box keeps the slot's size and the picture stands in it by [alignment], so the
 * title never moves from game to game. Without a picture, the tile fills the slot without a
 * title or year: the details title and facts right under it name the game
 * (not twice).
 */
@Composable
private fun DetailsCover(
    slot: ArtSlot,
    size: DpSize,
    alignment: Alignment,
    isBoxFitted: Boolean,
    shrinksToFit: Boolean = false,
) {
    val picture = (slot as? ArtSlot.Found)?.art?.large
    CoverArt(
        title = null,
        picture = picture,
        size = if (picture != null && isBoxFitted) fitInside(picture.aspect, size) else size,
        alignment = alignment,
        corner = pictureCorner(picture?.aspect ?: (size.width / size.height)),
        titlePadding = PlumDimens.coverPaddingDetails,
        modifier = Modifier.testTag(HomeTags.DETAILS_COVER),
        shrinksToFit = shrinksToFit,
    )
}

@Composable
internal fun DetailsTitle(title: String) {
    val layout = PlumTheme.layout
    BasicText(
        text = title,
        style = PlumTheme.type.detailsTitle(layout.widthClass).copy(color = PlumTheme.colors.text),
        maxLines = TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(HomeTags.DETAILS_TITLE),
    )
}

/** "1994 · Nintendo" and "Racing · 8/10", each left out when it has nothing to say. */
@Composable
private fun FactLines(details: GameDetails) {
    yearAndPublisher(details).takeIf { it.isNotEmpty() }?.let { FactLine(it) }
    genreAndRating(details).takeIf { it.isNotEmpty() }?.let { FactLine(it) }
}

/**
 * A 13 sp line in `meta`: "1994 · Nintendo", "Racing · 8/10". It wraps to a second
 * line at the large text sizes (between words) rather than cutting a long publisher or genre.
 */
@Composable
internal fun FactLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = PlumTheme.type.meta.copy(color = PlumTheme.colors.meta),
        maxLines = FACT_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Last played, play time, emulator: labels in `muted`, values in `text2`, 12 apart, lines 4 apart.
 * The emulator, the last row, may take a second line: the library's names run long ("RetroArch:
 * Genesis Plus GX Wide") and its wrap moves no other row.
 */
@Composable
internal fun Facts(
    play: PlayFacts,
    style: TextStyle,
) {
    val colors = PlumTheme.colors
    val labels =
        listOf(
            stringResource(R.string.home_details_last_played),
            stringResource(R.string.home_details_play_time),
            stringResource(R.string.home_details_emulator),
        )
    val values = listOf(lastPlayed(play.lastPlayedDaysAgo), playTime(play.playTimeMinutes), play.emulator)
    Row(modifier = Modifier.testTag(HomeTags.DETAILS_FACTS)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
            labels.forEach { BasicText(text = it, style = style.copy(color = colors.muted), maxLines = 1) }
        }
        Spacer(Modifier.width(PlumDimens.factsColumnGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
            values.forEachIndexed { i, value ->
                val lines = if (i == values.lastIndex) EMULATOR_LINES else 1
                BasicText(text = value, style = style.copy(color = colors.text2), maxLines = lines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "1994 · Nintendo"; the year alone when there is no publisher. Years are never grouped ("1,994"). */
@Composable
internal fun yearAndPublisher(details: GameDetails): String {
    val year = details.year?.toString().orEmpty()
    return when {
        year.isEmpty() -> details.publisher
        details.publisher.isEmpty() -> year
        else -> stringResource(R.string.home_details_pair, year, details.publisher)
    }
}

/**
 * "Racing · 8/10": the genre and ScreenScraper's rating out of 10 (the players
 * count is not shown, the data keeps it); either alone when the other is unknown, "" with neither.
 * Also the second screen's details card.
 */
@Composable
internal fun genreAndRating(details: GameDetails): String {
    val rating = ratingLabel(details)
    return when {
        details.genre.isEmpty() -> rating
        rating.isEmpty() -> details.genre
        else -> stringResource(R.string.home_details_pair, details.genre, rating)
    }
}

/**
 * "8/10", "7.5/10": [GameDetails.rating] out of 10; "" for none. 0 counts as none: ScreenScraper
 * gives no real 0/20 ratings.
 */
@Composable
internal fun ratingLabel(details: GameDetails): String =
    details.rating?.takeIf { it in 1..MAX_RATING }?.let { stringResource(R.string.home_details_rating, ratingOutOfTen(it)) }.orEmpty()

/** A rating out of 20 shown out of 10, in the locale's digits: "8", one decimal only when not whole ("7.5"). */
@Composable
private fun ratingOutOfTen(outOfTwenty: Int): String {
    val locale = currentLocale()
    val oneDecimal = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    val numbers = rememberCountFormat()
    return if (outOfTwenty % 2 == 0) numbers.format(outOfTwenty / 2) else oneDecimal.format(outOfTwenty / 2.0)
}

@Composable
internal fun lastPlayed(daysAgo: Int?): String {
    val numbers = rememberCountFormat()
    return when (daysAgo) {
        null -> stringResource(R.string.home_details_never)
        0 -> stringResource(R.string.home_details_today)
        1 -> stringResource(R.string.home_details_yesterday)
        else -> pluralStringResource(R.plurals.home_details_days_ago, daysAgo, numbers.format(daysAgo))
    }
}

@Composable
internal fun playTime(minutes: Int): String {
    val numbers = rememberCountFormat()
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(R.string.home_details_hours_minutes, numbers.format(hours), numbers.format(rest))
    } else {
        stringResource(R.string.home_details_minutes, numbers.format(rest))
    }
}

private const val MINUTES_PER_HOUR = 60

/** ScreenScraper's ratings are out of 20. */
private const val MAX_RATING = 20

/** How long the focus rests on a game before its synopsis and play facts are read: a held key (32-80 ms a row) reads none. */
private const val DETAILS_SETTLE_MS = 150L
private const val TITLE_LINES = 2
private const val FACT_LINES = 2
private const val EMULATOR_LINES = 2
