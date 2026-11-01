package com.plumemu.settings.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.components.ListEdgeFade
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.StatisticsDimens
import com.plumemu.coreui.theme.WidthClass
import kotlin.math.roundToInt

/*
 * Settings › Statistics as one page (docs/design/rules.md › Statistics page): figure tiles across the top, then the
 * most-played games and the systems as two lists of bars (stacked on narrow screens), and one line saying the figures stay
 * on the handheld. Only the games take the cursor; A or a tap on one shows it on the main screen. The page scrolls as a
 * whole: past the first or last game, or on a page with no game, Up and Down scroll it ([StatisticsScroll]).
 */

/** Statistics' cursor stops: one per most-played game, else the page itself (one stop); none until the figures arrive. */
internal fun statisticStopsOf(state: StatisticsState): Int =
    when (state) {
        StatisticsState.NotRead -> 0
        StatisticsState.Failed -> 1
        is StatisticsState.Read -> shownGamesOf(state.statistics).size.coerceAtLeast(1)
    }

/** The most-played game at cursor stop [index]; null for the page itself. */
internal fun StatisticsState.gameAt(index: Int): TopGame? = (this as? StatisticsState.Read)?.let { shownGamesOf(it.statistics).getOrNull(index) }

/** The games the page lists: none until something was played. */
private fun shownGamesOf(figures: PlayStatistics): List<TopGame> = if (figures.hasPlayed) figures.topGames else emptyList()

/** The page's width once entered: the options column and, on wide screens, the side panel's room, which this page does not use. */
internal fun statisticsPageWidth(layout: PlumLayout): Dp = layout.width - PlumDimens.stripMarginSide * 2

/** A D-pad press that scrolls the page instead of moving the cursor; [count] tells two presses the same way apart. */
@Immutable
internal data class StatisticsStep(
    val count: Int,
    val down: Boolean,
)

/**
 * Up or Down past the first or last game (or on a page with none): the page scrolls a step that way, as far as it can.
 * Down past the last game reads on to the page's end (on narrow screens the stacked Systems list is taller than the
 * window), so the last game may leave the view; while it is out ([isPastFocus]) Up scrolls back instead of moving on.
 */
@Stable
internal class StatisticsScroll {
    var step by mutableStateOf(StatisticsStep(0, down = false))
        private set

    /** The page is scrolled below the focused game: set by the page while it is drawn. */
    var isPastFocus by mutableStateOf(false)

    fun scroll(down: Boolean) {
        step = StatisticsStep(step.count + 1, down)
    }
}

/**
 * The page. [isPreview]: the sections column has the focus and the page only previews: the top tiles, two to a row,
 * nothing to scroll. Entered, it shows everything.
 */
@Composable
internal fun StatisticsPage(
    model: SettingsModel,
    touch: SettingsTouch,
    isPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = model.content.statistics
    when {
        state == StatisticsState.NotRead -> Unit
        state == StatisticsState.Failed -> PageLines(modifier) { Unreadable() }
        // The preview's tiles are narrow: the week shows its bars without the day letters, and the tiles keep their height (clipped, never squeezed).
        isPreview ->
            Box(modifier.clipToBounds().testTag(StatisticsTags.PAGE)) {
                Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) { Tiles((state as StatisticsState.Read).statistics, StatisticsDimens.NARROW_TILES_PER_ROW, showsDays = false) }
            }
        else -> ScrollingPage((state as StatisticsState.Read).statistics, model, touch, modifier)
    }
}

/** A page of plain lines (a failed read): still a whole page, with the footer. */
@Composable
private fun PageLines(
    modifier: Modifier,
    lines: @Composable ColumnScope.() -> Unit,
) = Column(modifier.testTag(StatisticsTags.PAGE), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
    lines()
    Footer()
}

@Composable
private fun ScrollingPage(
    figures: PlayStatistics,
    model: SettingsModel,
    touch: SettingsTouch,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    val games = shownGamesOf(figures)
    // Each game row's place, measured against the page (px, unscrolled) when asked: a row is placed before the page is, so
    // a span worked out while placing the rows would be missing on the first frame.
    val rows = remember { HashMap<Int, LayoutCoordinates>() }
    val page = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val spanOf =
        remember {
            { index: Int ->
                val within = page[0]?.takeIf { it.isAttached }
                val row = rows[index]?.takeIf { it.isAttached }
                if (within == null || row == null) null else within.localBoundingBoxOf(row).let { it.top.roundToInt() until it.bottom.roundToInt() }
            }
        }
    val isWide = PlumTheme.layout.widthClass == WidthClass.Wide
    FollowStatisticsFocus(model, scroll, spanOf, games.size)
    val perRow = if (isWide) StatisticsDimens.WIDE_TILES_PER_ROW else StatisticsDimens.NARROW_TILES_PER_ROW
    BoxWithConstraints(modifier.testTag(StatisticsTags.PAGE)) {
        val tileWidth = (maxWidth - PlumDimens.settingsPanelGap * (perRow - 1)) / perRow
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .onPlaced { page[0] = it }
                // Nothing played: the invitation, the box art tile and the footer stand together in the middle of the page.
                .then(if (figures.hasPlayed) Modifier else Modifier.heightIn(min = maxHeight)),
            verticalArrangement = if (figures.hasPlayed) Arrangement.spacedBy(PlumDimens.settingsPanelGap) else Arrangement.spacedBy(PlumDimens.settingsPanelGap, Alignment.CenterVertically),
            horizontalAlignment = if (figures.hasPlayed) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            if (!figures.hasPlayed) {
                EmptyPage(figures, tileWidth)
            } else {
                Tiles(figures, perRow)
                val most: @Composable (Modifier) -> Unit = { place -> MostPlayed(games, figures.hasBoxArt, model, touch, rows, place) }
                val systems: @Composable (Modifier) -> Unit = { place -> Systems(figures.topSystems, figures.totalSeconds, place) }
                if (isWide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.columnGap)) {
                        most(Modifier.weight(1f))
                        systems(Modifier.weight(1f))
                    }
                } else {
                    most(Modifier.fillMaxWidth())
                    systems(Modifier.fillMaxWidth())
                }
            }
            Footer()
        }
        if (scroll.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(StatisticsTags.PAGE + FADE_BOTTOM_SUFFIX))
        if (scroll.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).testTag(StatisticsTags.PAGE + FADE_TOP_SUFFIX))
    }
}

/**
 * Keeps the focused game whole on screen when the cursor moves (the first game also brings the tiles above it back, the
 * last the lists' end, when they fit with it), and turns [StatisticsScroll]'s steps into scrolling: down to the page's
 * end, and up no further than the top. Collected in effects: a cursor move recomposes only the two rows whose look changes.
 */
@Composable
private fun FollowStatisticsFocus(
    model: SettingsModel,
    scroll: ScrollState,
    spanOf: (Int) -> IntRange?,
    gameCount: Int,
) {
    val motion = PlumTheme.motion
    val density = LocalDensity.current
    val step = with(density) { (PlumTheme.layout.rowHeight * STEP_ROWS).roundToPx() }
    // The focused game stays clear of the edge fades too, not only inside the window: a row under a fade reads as cut.
    val fade = with(density) { PlumDimens.listEdgeFade.roundToPx() }

    fun focused(): IntRange? = if (gameCount == 0) null else spanOf(model.optionIndex)?.let { (it.first - fade).coerceAtLeast(0)..(it.last + fade) }
    LaunchedEffect(model, scroll, gameCount) {
        // The page's length is a key too: a text size change moves the rows without changing the window.
        snapshotFlow { Triple(model.optionIndex, scroll.viewportSize, scroll.maxValue) }.collect { (index, viewport) ->
            val span = focused() ?: return@collect
            if (viewport == 0) return@collect
            val target =
                when {
                    index == 0 && span.last + 1 <= viewport -> 0
                    index == gameCount - 1 && span.first >= scroll.maxValue -> scroll.maxValue
                    else -> keepVisible(scroll.value, viewport, span).coerceIn(0, scroll.maxValue)
                }
            if (target != scroll.value) scroll.animateScrollBy((target - scroll.value).toFloat(), motion.glideSpec())
        }
    }
    LaunchedEffect(model, scroll) {
        var seen = model.statisticsScroll.step.count
        snapshotFlow { model.statisticsScroll.step }.collect { press ->
            if (press.count == seen) return@collect
            seen = press.count
            // Up back to the focused game stops where it is whole again; down reads on to the end.
            val span = focused()
            val low = if (!press.down && span != null && scroll.value > span.first) span.first else 0
            val target = (scroll.value + if (press.down) step else -step).coerceIn(low, scroll.maxValue)
            if (target != scroll.value) scroll.animateScrollBy((target - scroll.value).toFloat(), motion.glideSpec())
        }
    }
    LaunchedEffect(model, scroll) {
        snapshotFlow { focused()?.let { scroll.value > it.first } == true }.collect { model.statisticsScroll.isPastFocus = it }
    }
}

/** A small heading over a list ("Most played"): not a stop, on the rows' text line. */
@Composable
private fun Heading(text: String) {
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.meta, color = { colors.meta }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.onRowTextLine().testTag(StatisticsTags.HEADING))
}

@Composable
private fun MostPlayed(
    games: List<TopGame>,
    hasBoxArt: Boolean,
    model: SettingsModel,
    touch: SettingsTouch,
    rows: HashMap<Int, LayoutCoordinates>,
    modifier: Modifier,
) = Column(modifier.testTag(StatisticsTags.MOST_PLAYED), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
    Heading(stringResource(R.string.settings_statistics_most_played))
    val longest = games.firstOrNull()?.seconds?.takeIf { it > 0 } ?: 1L
    games.forEachIndexed { index, game ->
        GameRow(
            index, game, hasBoxArt, game.seconds.toFloat() / longest, model,
            Modifier
                .onPlaced { rows[index] = it }
                .tapTarget { touch.onOption(index) },
        )
    }
}

/** A most-played game: its cover while box art is set up, title and time on one line, the system under it, its share of the first game's time. */
@Composable
private fun GameRow(
    index: Int,
    game: TopGame,
    hasBoxArt: Boolean,
    share: Float,
    model: SettingsModel,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused by remember(model, index) { derivedStateOf { model.focus.column == SettingsColumn.Options && model.optionIndex == index } }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PlumTheme.layout.rowHeight)
            .focusPill(focused)
            .padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap)
            .testTag(StatisticsTags.game(index)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasBoxArt) {
            StatisticCover(game.cover, index, game.title, game.systemName)
            Spacer(Modifier.width(PlumDimens.focusDotGap))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                BasicText(
                    game.title,
                    style = if (focused) type.settingsOptionFocused else type.settingsOption,
                    color = { if (focused) colors.strong else colors.text2 },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag(StatisticsTags.gameTitle(index)),
                )
                Spacer(Modifier.width(PlumDimens.rowTrailingGap))
                BasicText(statisticTime(game.seconds), style = type.meta, color = { if (focused) colors.meta else colors.text }, maxLines = 1)
            }
            BasicText(game.systemName, style = type.smallFact, color = { if (focused) colors.meta else colors.muted }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(StatisticsDimens.barGap))
            Bar(share, { colors.muted }, { colors.sel })
        }
    }
}

/**
 * The systems played longest, each in the same box as a game row (a line of name and time, a smaller line, the bar), so the
 * two lists side by side keep one row height and their bars line up across.
 */
@Composable
private fun Systems(
    systems: List<TopSystem>,
    totalSeconds: Long,
    modifier: Modifier,
) = Column(modifier.testTag(StatisticsTags.SYSTEMS), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
    if (systems.isEmpty()) return@Column
    Heading(stringResource(R.string.settings_statistics_systems))
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val longest = systems.first().seconds.takeIf { it > 0 } ?: 1L
    systems.forEachIndexed { index, system ->
        // Information only: the same box as a game row, so the two lists' lines match, but no stop and no tap.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .testTag(StatisticsTags.system(index))
                .padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap),
            verticalArrangement = Arrangement.Center,
        ) {
            Row {
                BasicText(system.name, style = type.settingsOption, color = { colors.text2 }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(PlumDimens.rowTrailingGap))
                BasicText(statisticTime(system.seconds), style = type.meta, color = { colors.text }, maxLines = 1)
            }
            BasicText(
                stringResource(R.string.settings_statistics_share_of_play, sharePercentOf(system.seconds, totalSeconds)),
                style = type.smallFact,
                color = { colors.muted },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(StatisticsDimens.barGap))
            Bar(system.seconds.toFloat() / longest, { colors.muted }, { colors.sel })
        }
    }
}

/** Nothing played yet: one centred line asking for a first game; the box art tile still shows under it, at a tile's width. */
@Composable
private fun EmptyPage(
    figures: PlayStatistics,
    tileWidth: Dp,
) {
    val colors = PlumTheme.colors
    val line = if (figures.libraryGames == 0) R.string.settings_statistics_no_games else R.string.settings_statistics_play_invite
    BasicText(
        stringResource(line),
        style = PlumTheme.type.settingsOption.copy(textAlign = TextAlign.Center),
        color = { colors.text },
        modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(StatisticsTags.INVITE),
    )
    for (tile in tilesOf(figures)) Tile(tile, figures, Modifier.width(tileWidth))
}

@Composable
private fun Unreadable() {
    val colors = PlumTheme.colors
    Column(Modifier.onRowTextLine().testTag(StatisticsTags.UNREADABLE), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
        BasicText(stringResource(R.string.settings_statistics_unreadable), style = PlumTheme.type.settingsOption, color = { colors.text })
        BasicText(stringResource(R.string.settings_statistics_unreadable_detail), style = PlumTheme.type.smallFact, color = { colors.muted })
    }
}

/** Kept on the handheld: these figures are never the anonymous usage statistics (Advanced › Send usage statistics). */
@Composable
private fun Footer() {
    val colors = PlumTheme.colors
    BasicText(
        stringResource(R.string.settings_statistics_kept_here),
        style = PlumTheme.type.smallFact,
        color = { colors.muted },
        modifier = Modifier.onRowTextLine().testTag(StatisticsTags.FOOTER),
    )
}

/** A D-pad step of the page: three rows, as the licence texts scroll. */
private const val STEP_ROWS = 3

internal object StatisticsTags {
    const val PAGE = "settings_statistics_page"
    const val TILES = "settings_statistics_tiles"
    const val WEEK = "settings_statistics_week"
    const val MOST_PLAYED = "settings_statistics_most_played"
    const val SYSTEMS = "settings_statistics_systems"
    const val HEADING = "settings_statistics_heading"
    const val INVITE = "settings_statistics_invite"
    const val UNREADABLE = "settings_statistics_unreadable"
    const val FOOTER = "settings_statistics_footer"

    fun tile(tile: StatisticTile) = "settings_statistics_tile_${tile.name.lowercase()}"

    fun figure(tile: StatisticTile) = "settings_statistics_figure_${tile.name.lowercase()}"

    fun day(index: Int) = "settings_statistics_day_$index"

    fun dayLetter(index: Int) = "settings_statistics_day_letter_$index"

    fun foot(tile: StatisticTile) = "settings_statistics_foot_${tile.name.lowercase()}"

    fun game(index: Int) = "settings_statistics_game_$index"

    fun gameTitle(index: Int) = "settings_statistics_game_title_$index"

    fun system(index: Int) = "settings_statistics_system_$index"
}
