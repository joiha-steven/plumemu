package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
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
import androidx.compose.ui.unit.Dp
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
import java.util.Locale
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
 * Apps' list has [AppDetails] instead (icon, name, package), wide and narrow. Where a narrow screen leaves the column no room (576: under the cover's 140 dp) it is not composed, so a tap has nothing to hit there: the details stop is reached by the key or by dragging the strip.
 */
@Composable
internal fun DetailsColumn(
    content: HomeContent,
    state: StripState,
    onAction: (Action) -> Unit,
    modifier: Modifier,
    width: Dp = PlumDimens.detailsColumnWidth,
) {
    val entryIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    val entry = content.entries.getOrNull(entryIndex) ?: return
    val row by remember(state, entry.id) { derivedStateOf { state.focus.rowOf(entry.id) } }
    val game = entry.rows.getOrNull(row) ?: return
    // With the systems focused the strip rests at its start and the column sits under the screen's right
    // edge: its text would be cut mid-word, so none is composed (wide: only the cover shows; narrow: the layout keeps its places).
    val textHidden by remember(state) { derivedStateOf { state.focus.column == Column.Systems } }
    if (entry.kind == EntryKind.Apps) {
        // An app has no art: its icon and package, wide or narrow, with or without box art elsewhere.
        // Narrow: a tap at the games stop opens the details stop, a tap in it opens the app (core-input), as for a game.
        val tap = if (PlumTheme.layout.widthClass == WidthClass.Wide) Modifier else remember(onAction) { Modifier.tapItem(Column.Details, 0, onAction) }
        AppDetails(content, game, textHidden, modifier.then(tap).testTag(HomeTags.DETAILS))
        return
    }
    if (!content.hasBoxArt) {
        // Wide only (HomeStrip, listHasDetails): not a strip stop, a tap here has nothing to do.
        TextDetails(content, entry, game, textHidden, modifier.testTag(HomeTags.DETAILS))
        return
    }
    val slot = LocalHomeArt.current.slot(game.scrapeKey)
    if (PlumTheme.layout.widthClass == WidthClass.Wide) {
        // Not a strip stop on wide screens: a tap here has nothing to do.
        WideDetails(content, game, slot, entry.posterOf(game), entry.shapeOf(game).widthOverHeight, textHidden, modifier.testTag(HomeTags.DETAILS))
    } else {
        // The narrow details stop: a tap from the list opens it, a tap in it plays (core-input).
        val tap = remember(onAction) { Modifier.tapItem(Column.Details, 0, onAction).testTag(HomeTags.DETAILS) }
        val atDetailsStop by remember(state) { derivedStateOf { state.focus.column == Column.Details } }
        if (atDetailsStop) {
            NarrowDetails(content, game, slot, entry.posterOf(game), entry.shapeOf(game).widthOverHeight, textHidden, modifier.then(tap))
        } else {
            // The games stop: the wide layout in the room the margin leaves, so nothing is cut by the screen's edge.
            CompositionLocalProvider(LocalDetailsColumnWidth provides width) {
                WideDetails(content, game, slot, entry.posterOf(game), entry.shapeOf(game).widthOverHeight, textHidden, modifier.then(tap), width)
            }
        }
    }
}

@Composable
private fun WideDetails(
    content: HomeContent,
    game: GameRowModel,
    slot: ArtSlot,
    poster: PosterSpec,
    posterAspect: Float,
    textHidden: Boolean,
    modifier: Modifier,
    width: Dp = PlumDimens.detailsColumnWidth,
) {
    val extras = rememberExtras(game, slot)
    val play = rememberShownPlay(game, content)
    val details = detailsOf(game, slot)?.withRating(extras)
    val synopsis = extras.synopsis
    // The facts box shows nothing until every late value is in (or [REVEAL_LATE_MS] after the focus rested), then all of it at once.
    // Another play source (a new library) starts the reveal over, like another game.
    val revealKey = game.id to LocalHomePlayFacts.current
    val revealed = rememberRevealed(revealKey, play !== ShownPlay.Reading && extras !== PENDING_EXTRAS && slot !is ArtSlot.Unknown)
    // One layout whether the text shows or not, so the cover is not composed again when the systems take focus.
    WideDetailsLayout(
        // With the systems focused the column is mostly off screen (its text is not composed): its cover shows only if the whole column fits.
        cover = { if (!textHidden || detailsCoverFitsWithSystemsOpen(PlumTheme.layout, content.hasBoxArt)) CollaredDetailsCover(slot, DpSize(width, PlumDimens.detailsCoverHeightWide), poster, posterAspect, game.appPackage, game.artAspect) },
        titleBlock = {
            if (!textHidden) {
                TitleBlock(PlumDimens.detailsSubtitleGap) {
                    DetailsTitle(game.title)
                    // The line is held (empty until the result is read) so the cover below does not change height when it arrives.
                    DetailsSubtitle(details?.let { publisherAndYear(it) }.orEmpty())
                }
            }
        },
        rest = {
            if (!textHidden) {
                DetailsBlock(details, play, revealed)
            }
        },
        tail = {
            // With the facts, at the reveal; the room under the box is fixed, so it only fills.
            if (!textHidden && revealed) {
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
    poster: PosterSpec,
    posterAspect: Float,
    textHidden: Boolean,
    modifier: Modifier,
) {
    val type = PlumTheme.type
    val extras = rememberExtras(game, slot)
    val play = rememberShownPlay(game, content)
    val details = detailsOf(game, slot)?.withRating(extras)
    val synopsis = extras.synopsis
    // Laid out with the facts, as in the wide column: the text beside the cover grows by the battery line then, and the synopsis must
    // not follow it down.
    val revealed = rememberRevealed(game.id to LocalHomePlayFacts.current, play !== ShownPlay.Reading && extras !== PENDING_EXTRAS && slot !is ArtSlot.Unknown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopBlockGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopCoverGap)) {
            // Text first, the cover on the right: the peek shows the text, cut by the screen's edge.
            if (!textHidden) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsStopLineGap)) {
                    DetailsTitle(game.title)
                    // Both lines and the facts' room are held (blank until the data arrives), so nothing below or beside them moves.
                    FactLines(details)
                    Spacer(Modifier.height(PlumDimens.detailsStopFactsSpacer))
                    DetailsFacts(if (play == ShownPlay.Absent) ShownPlay.Reading else play, type.smallFact)
                }
            } else {
                // The cover keeps its place on the right while the text is not composed.
                Spacer(Modifier.weight(1f))
            }
            // The cover's slot has the width the layout fixes from what is known at once (the stored shape, else the system's) and the full
            // height: the text beside never rewraps and the synopsis below never moves when the result lands; the picture only fits inside the slot.
            val full = DpSize(PlumDimens.detailsStopCoverWidth, PlumDimens.detailsStopCoverHeight)
            // Latched per game: a shape that arrives while the focus rests on it (the shapes pass) changes nothing beside the cover.
            val slotAspect = remember(game.id) { game.artAspect ?: posterAspect }
            val slotSize = DpSize(fitInside(slotAspect, full).width, full.height)
            // The box hugs the right edge, as a stored one does, when it is narrower than the slot (first seen).
            Box(Modifier.size(slotSize).testTag(HomeTags.DETAILS_COVER_SLOT), contentAlignment = Alignment.TopEnd) {
                DetailsCover(slot, slotSize, Alignment.TopStart, isBoxFitted = true, poster = poster, posterAspect = posterAspect, appPackage = game.appPackage, storedAspect = game.artAspect)
            }
        }
        if (!textHidden && revealed) Synopsis(synopsis)
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
    // By identity: a read with nothing equals [PENDING_EXTRAS] by value, and the default policy would keep the marker for good.
    val text = remember(art, key, slot) { mutableStateOf(art.keptExtras(key) ?: PENDING_EXTRAS, referentialEqualityPolicy()) }
    LaunchedEffect(art, key, slot) {
        if (art.keptExtras(key) != null) return@LaunchedEffect
        awaitDetailsSettle()
        text.value = art.extras(key)
    }
    return text.value
}

/** The extras of a game whose result is still being read; compared by identity, never by value ([DetailsExtras.None] is a read with nothing). */
private val PENDING_EXTRAS = DetailsExtras("", null)

/**
 * True from the first frame [ready] holds (the late values are in), or [REVEAL_LATE_MS] after the focus rested on [key], whichever is first,
 * and then for good for that game: the facts box is laid out once, at that frame, and a value that comes later fills its cell in place.
 */
@Composable
private fun rememberRevealed(
    key: Any,
    ready: Boolean,
): Boolean {
    val late = remember(key) { mutableStateOf(false) }
    // No timer once the values are in (it is cancelled when [ready] turns true).
    LaunchedEffect(key, ready) {
        if (ready) return@LaunchedEffect
        awaitDetailsSettle()
        awaitFrameTime(REVEAL_LATE_MS)
        late.value = true
    }
    val latch = remember(key) { booleanArrayOf(false) }
    if (ready || late.value) latch[0] = true
    return latch[0]
}

/**
 * Returns once the focus has rested [DETAILS_SETTLE_MS] on the game of the effect that calls it (a
 * move cancels that effect first). Frame time, not a timer: it follows the screen's clock (and the
 * tests' clock). The details column's reads (synopsis, play facts) start after it.
 */
internal suspend fun awaitDetailsSettle() = awaitFrameTime(DETAILS_SETTLE_MS)

/** Returns once [ms] of frame time have passed. */
private suspend fun awaitFrameTime(ms: Long) {
    val start = withFrameMillis { it }
    var now = start
    while (now - start < ms) now = withFrameMillis { it }
}

/**
 * The large box front in the details' cover slot of [size], at its own shape. With [isBoxFitted]
 * (narrow: the text is beside it) the box itself takes the picture's shape ([fitInside],
 * height-bound for a portrait box), so the text uses the width it leaves; while the result is being read
 * it already has that shape ([coverBoxAspect]). Without (wide: the text
 * is below it) the box keeps the slot's size and the picture stands in it by [alignment], so the
 * title never moves from game to game. Without a picture, the tile fills the slot without a
 * title or year: the details title and facts right under it name the game
 * (not twice). With a [poster] (and the system's shape, [posterAspect]) a game with no picture for good wears it, fitted in the slot like a picture.
 * With an [appPackage] (an app game) the slot holds the app's icon instead ([AppGameIcon]).
 */
@Composable
internal fun DetailsCover(
    slot: ArtSlot,
    size: DpSize,
    alignment: Alignment,
    isBoxFitted: Boolean,
    shrinksToFit: Boolean = false,
    poster: PosterSpec? = null,
    posterAspect: Float? = null,
    appPackage: String? = null,
    storedAspect: Float? = null,
) {
    if (appPackage != null) {
        // An installed app listed as a game: its Android icon centred in the slot, never a poster.
        AppGameIcon(appPackage, poster?.title.orEmpty(), size, Modifier.testTag(HomeTags.DETAILS_COVER))
        return
    }
    val picture = (slot as? ArtSlot.Found)?.art?.large
    // The poster stands in for a game that has no picture for good (never while its result is still being read, [ArtSlot.Unknown]),
    // and for a picture whose file cannot be read; the plain tile shows meanwhile.
    val shownPoster = if (slot is ArtSlot.Unknown) null else poster
    val boxAspect = coverBoxAspect(slot, picture, storedAspect, posterAspect)
    CoverArt(
        title = null,
        picture = picture,
        size = if (boxAspect != null && isBoxFitted) fitInside(boxAspect, size) else size,
        alignment = alignment,
        corner = pictureCorner(boxAspect ?: posterAspect.takeIf { shownPoster != null } ?: (size.width / size.height)),
        titlePadding = PlumDimens.coverPaddingDetails,
        modifier = Modifier.testTag(HomeTags.DETAILS_COVER),
        shrinksToFit = shrinksToFit,
        poster = shownPoster,
        posterAspect = posterAspect,
        tileSystem = poster,
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

/** The line under the wide details title ("1994 · Nintendo"): small and dim, as the board's "Nintendo, 1994". */
@Composable
private fun DetailsSubtitle(text: String) {
    BasicText(
        // A no-break space holds the line's height while there is nothing to say (an empty text takes no height at all).
        text = text.ifEmpty { HELD_LINE },
        style = PlumTheme.type.detailsSubtitle.copy(color = PlumTheme.colors.muted),
        // Exactly one line, so a long publisher cannot make the line grow when it arrives.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(HomeTags.DETAILS_SUBTITLE),
    )
}

/** "1994 · Nintendo" and "Racing · 8/10", each left out when it has nothing to say. */
@Composable
private fun FactLines(details: GameDetails?) {
    FactLine(details?.let { publisherAndYear(it) }.orEmpty().ifEmpty { HELD_LINE }, Modifier.testTag(HomeTags.DETAILS_FACT_LINE), maxLines = 1)
    FactLine(details?.let { genreAndRating(it) }.orEmpty().ifEmpty { HELD_LINE }, Modifier.testTag(HomeTags.DETAILS_FACT_LINE), maxLines = 1)
}

/**
 * A 13 sp line in `meta`: "1994 · Nintendo", "Racing · 8/10". It wraps to a second
 * line at the large text sizes (between words) rather than cutting a long publisher or genre.
 */
@Composable
internal fun FactLine(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = FACT_LINES,
) {
    BasicText(
        text = text,
        style = PlumTheme.type.meta.copy(color = PlumTheme.colors.meta),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Last played, play time, emulator: labels in `muted`, values in `text2`, 12 apart, lines 4 apart.
 * Every value is one line with an ellipsis (the library's emulator names run long; the full name is in Info): the table is as high when the
 * values arrive as it was held, so nothing below or beside it moves, whatever a value says.
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
            // Last played and play time are phrases (Jost, tabular figures); the emulator's name is a word.
            values.forEachIndexed { index, value ->
                BasicText(text = value, style = (if (index < 2) PlumTheme.type.factNumber else style).copy(color = colors.text2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(HomeTags.DETAILS_FACT_VALUE))
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

/** "Nintendo, 1994", the details subtitle's order; either alone when the other is unknown. */
@Composable
internal fun publisherAndYear(details: GameDetails): String {
    val year = details.year?.toString().orEmpty()
    return when {
        year.isEmpty() -> details.publisher
        details.publisher.isEmpty() -> year
        else -> stringResource(R.string.home_details_publisher_year, details.publisher, year)
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
internal fun ratingLabel(
    details: GameDetails,
    locale: Locale = currentLocale(),
    numbers: NumberFormat = rememberCountFormat(),
): String =
    details.rating?.takeIf { it in 1..MAX_RATING }?.let { stringResource(R.string.home_details_rating, ratingOutOfTen(it, locale, numbers)) }.orEmpty()

/** [GameDetails.rating] as the number the details block shows beside the dots: "8", "7.5". */
@Composable
internal fun ratingNumberOf(
    outOfTwenty: Int,
    locale: Locale = currentLocale(),
    numbers: NumberFormat = rememberCountFormat(),
): String = ratingOutOfTen(outOfTwenty, locale, numbers)

/** A rating out of 20 shown out of 10, in the locale's digits: "8", one decimal only when not whole ("7.5"). */
@Composable
private fun ratingOutOfTen(
    outOfTwenty: Int,
    locale: Locale,
    numbers: NumberFormat,
): String {
    val oneDecimal = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    return if (outOfTwenty % 2 == 0) numbers.format(outOfTwenty / 2) else oneDecimal.format(outOfTwenty / 2.0)
}

@Composable
internal fun lastPlayed(
    daysAgo: Int?,
    numbers: NumberFormat = rememberCountFormat(),
): String {
    return when (daysAgo) {
        null -> stringResource(R.string.home_details_never)
        0 -> stringResource(R.string.home_details_today)
        1 -> stringResource(R.string.home_details_yesterday)
        else -> pluralStringResource(R.plurals.home_details_days_ago, daysAgo, numbers.format(daysAgo))
    }
}

@Composable
internal fun playTime(
    minutes: Int,
    numbers: NumberFormat = rememberCountFormat(),
): String {
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
internal const val MAX_RATING = 20

/** How long the focus rests on a game before its synopsis and play facts are read: a held key (32-80 ms a row) reads none. */
private const val DETAILS_SETTLE_MS = 150L

/** How long after the settle the facts box waits for its values before it shows what is known (a slow or hung read must not keep it blank). */
private const val REVEAL_LATE_MS = 400L
/** A no-break space: not shown, holds a line's height (an empty text takes none). */
internal const val HELD_LINE = "\u00A0"
private const val TITLE_LINES = 2
private const val FACT_LINES = 2
