package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.GameMenuController
import com.plumemu.home.EntryKind
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeEntry
import com.plumemu.home.search.SearchHit
import com.plumemu.home.search.SearchState
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.setup.SetupModel
import com.plumemu.setup.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/*
 * What the second screen shows, derived from the app's state. The derivation is a pure function over [HomeLook]; the composables
 * below read the state in one effect each (snapshotFlow), never in the top screen's composition, so
 * the top screen never recomposes because of the bottom one. They run only while a window is up
 * ([SecondScreenFeed.isFollowing]).
 */

/** What the main screen shows that matters to the second screen: the strip's focus, search, the game menu. */
@Immutable
internal data class HomeLook(
    val focus: StripFocus,
    val isSearchOpen: Boolean = false,
    /** The focused search result; null while the cursor is in the field or there are none. */
    val searchHit: SearchHit? = null,
    /** The game whose menu (START) is open. */
    val menuGameKey: String? = null,
    /**
     * The top screen shows the focused game's cover: its system is shown as a grid (also at the
     * narrow details stop beside it, whose column shows the cover too). A list never does while the
     * second screen's window is up (layout C: no details column).
     */
    val isCoverOnTopScreen: Boolean = false,
)

/** The names of the fixed entries, from strings.xml (the derivation runs outside composition). */
@Immutable
internal data class EntryNames(
    val recent: String,
    val favorites: String,
)

/**
 * Each game of [content]'s systems with its system, built once per content off the main thread:
 * Recent, Favorites, search results and the game menu name a game without its system.
 */
internal class GameSystems private constructor(
    private val byId: Map<String, Located>,
) {
    private class Located(val row: GameRowModel, val system: HomeEntry)

    /** The system entry [gameId] belongs to; null when no system lists it. */
    fun systemOf(gameId: String): HomeEntry? = byId[gameId]?.system

    /** The row of [gameId] as its system lists it. */
    fun rowOf(gameId: String): GameRowModel? = byId[gameId]?.row

    companion object {
        fun of(content: HomeContent): GameSystems {
            val map = HashMap<String, Located>(content.gameCount * 2)
            for (entry in content.entries) {
                if (entry.kind != EntryKind.System) continue
                // The first system that lists a game keeps it (HashMap.putIfAbsent needs API 24).
                for (row in entry.rows) if (row.id !in map) map[row.id] = Located(row, entry)
            }
            return GameSystems(map)
        }
    }
}

/** The card for [look] on [content]. */
internal fun secondScreenContentOf(
    content: HomeContent,
    look: HomeLook,
    systems: GameSystems,
    names: EntryNames,
): SecondScreenContent {
    if (content.isLibraryEmpty) return SecondScreenContent.Calm
    // The menu is about one game, wherever it was opened from (the strip or a search result).
    // Over the grid (the menu opened from it, or its focused game) the top screen shows the cover.
    val isCoverOnTop = look.isCoverOnTopScreen && !look.isSearchOpen
    look.menuGameKey?.let { key -> systems.rowOf(key)?.let { return gameCard(it, systems, content.hasBoxArt, isCoverOnTop) } }
    if (look.isSearchOpen) return look.searchHit?.let { gameCard(it.row, systems, content.hasBoxArt) } ?: SecondScreenContent.Calm
    val entry = content.entries.getOrNull(look.focus.systemIndex) ?: return SecondScreenContent.Calm
    if (look.focus.column == Column.Systems) return entryCard(entry, names)
    val row = entry.rows.getOrNull(look.focus.rowOf(entry.id)) ?: return entryCard(entry, names)
    return if (entry.kind == EntryKind.Apps) SecondScreenContent.App(row.title) else gameCard(row, systems, content.hasBoxArt, isCoverOnTop)
}

private fun gameCard(
    row: GameRowModel,
    systems: GameSystems,
    hasBoxArt: Boolean,
    isCoverOnTop: Boolean = false,
): SecondScreenContent.Game =
    SecondScreenContent.Game(
        title = row.title,
        systemName = systems.systemOf(row.id)?.name.orEmpty(),
        region = row.region.ifEmpty { null },
        isFavorite = row.isFavorite,
        // Play facts are read after the settle (SecondScreenPlayFacts): rows without box art carry none.
        gameId = row.id.toLongOrNull(),
        isPlayKnown = false,
        scrapeKey = if (hasBoxArt) row.scrapeKey else null,
        // As the top screen: its details column exists only with box art (nothing is only on the second screen).
        details = if (hasBoxArt) row.details else null,
        isCoverOnTopScreen = hasBoxArt && isCoverOnTop,
    )

/** A system, Recent, Favorites or Apps under the cursor in the system column. */
private fun entryCard(
    entry: HomeEntry,
    names: EntryNames,
): SecondScreenContent {
    if (entry.kind == EntryKind.Apps) return SecondScreenContent.Apps(entry.shownCount)
    // The last game is read after the settle (SecondScreenPlayFacts); an empty list has none to read.
    val lastPlayFrom =
        when {
            entry.rows.isEmpty() -> null
            entry.kind == EntryKind.Recent -> entry.rows.first().let { first -> first.id.toLongOrNull()?.let { LastPlayLookup.FirstGame(it, first.title) } }
            entry.kind == EntryKind.Favorites -> LastPlayLookup.AmongFavorites
            else -> LastPlayLookup.InSystem(entry.id)
        }
    val name =
        when (entry.kind) {
            EntryKind.Recent -> names.recent
            EntryKind.Favorites -> names.favorites
            else -> entry.name.orEmpty()
        }
    return SecondScreenContent.System(name, entry.shownCount, lastPlayFrom = lastPlayFrom, info = if (entry.kind == EntryKind.System) entry.info else null)
}

/**
 * Follows the main screen's cursor, search and game menu while a window is up: one effect, reading
 * the state through snapshotFlow (as HomeStrip does), so nothing in the main screen's composition
 * reads it. [content] is what the main screen shows (with the favourite marks); [gridIds] the
 * systems shown as grids (a game there gets its card without the cover the grid already shows),
 * read inside the flow so a switch does not restart it; [buildSystems] makes the game index (tests count it).
 */
@Composable
internal fun FollowHomeOnSecondScreen(
    feed: SecondScreenFeed,
    content: HomeContent,
    state: StripState,
    search: SearchState,
    menu: GameMenuController,
    gridIds: Set<String> = emptySet(),
    buildSystems: (HomeContent) -> GameSystems = GameSystems::of,
) {
    val names = EntryNames(stringResource(R.string.home_entry_recent), stringResource(R.string.home_entry_favorites))
    // Read as state inside the flow: SELECT must not restart the effect (it would rebuild the
    // game index, one entry per game, on every list / grid switch).
    val latestGridIds = rememberUpdatedState(gridIds)
    LaunchedEffect(feed, content, state, search, menu, names) {
        feed.isFollowing.collectLatest { following ->
            if (!following) return@collectLatest
            val systems = withContext(Dispatchers.Default) { buildSystems(content) }
            snapshotFlow { lookOf(state, search, menu, content, latestGridIds.value) }
                .map { secondScreenContentOf(content, it, systems, names) }
                .distinctUntilChanged()
                .collect(feed::offer)
        }
    }
}

private fun lookOf(
    state: StripState,
    search: SearchState,
    menu: GameMenuController,
    content: HomeContent,
    gridIds: Set<String>,
): HomeLook {
    val hit = if (search.isOpen && !search.inField) search.results.getOrNull(search.row) else null
    val focus = state.focus
    // The grid, or the narrow details stop beside it: both show the cover (moving Left / Right between them never flips the card).
    val isCoverOnTop = content.showsGrid(content.entries.getOrNull(focus.systemIndex), gridIds)
    return HomeLook(focus, search.isOpen, hit, menu.panel?.game?.key, isCoverOnTop)
}

/** Shows [content] on the second screen while this is composed and a window is up (a failed or loading library). */
@Composable
internal fun ShowOnSecondScreen(
    feed: SecondScreenFeed?,
    content: SecondScreenContent,
) {
    if (feed == null) return
    LaunchedEffect(feed, content) { feed.isFollowing.collect { if (it) feed.offer(content) } }
}

/**
 * The bottom card while Settings is open: its Statistics figures once read, from the read
 * Settings makes for its page ([com.plumemu.settings.screen.rememberStatistics]); Calm until then,
 * and for a failed read or an empty library.
 */
internal fun secondScreenContentOf(statistics: StatisticsState): SecondScreenContent =
    if (statistics is StatisticsState.Read && statistics.statistics.libraryGames > 0) {
        SecondScreenContent.Statistics(statistics.statistics)
    } else {
        SecondScreenContent.Calm
    }

/**
 * Settings: shows its [statistics] (Settings' own state, handed over through a flow) while a window
 * is up. One effect, reading the flow and never Settings' composition, so nothing on the top screen
 * recomposes for it; no read of its own.
 */
@Composable
internal fun ShowStatisticsOnSecondScreen(
    feed: SecondScreenFeed?,
    statistics: StateFlow<StatisticsState>,
) {
    if (feed == null) return
    LaunchedEffect(feed, statistics) {
        feed.isFollowing.collectLatest { following ->
            if (!following) return@collectLatest
            statistics
                .map(::secondScreenContentOf)
                .distinctUntilChanged()
                .collect(feed::offer)
        }
    }
}

/** Setup: the second screen's question step shows its line; every other step the calm mark. */
@Composable
internal fun FollowSetupOnSecondScreen(
    feed: SecondScreenFeed?,
    model: SetupModel,
) {
    if (feed == null) return
    LaunchedEffect(feed, model) {
        feed.isFollowing.collectLatest { following ->
            if (!following) return@collectLatest
            model.state
                .map { if (it.step == SetupStep.SecondScreen) SecondScreenContent.SetupQuestion else SecondScreenContent.Calm }
                .distinctUntilChanged()
                .collect(feed::offer)
        }
    }
}
