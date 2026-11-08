package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.LocalLaunchOrigin
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.CoverShape
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
    look.menuGameKey?.let { key -> systems.rowOf(key)?.let { return gameCard(it, systems, content.hasBoxArt) } }
    if (look.isSearchOpen) return look.searchHit?.let { gameCard(it.row, systems, content.hasBoxArt) } ?: SecondScreenContent.Calm
    val entry = content.entries.getOrNull(look.focus.systemIndex) ?: return SecondScreenContent.Calm
    if (look.focus.column == Column.Systems) return entryCard(entry, names, content.hasBoxArt)
    val row = entry.rows.getOrNull(look.focus.rowOf(entry.id)) ?: return entryCard(entry, names, content.hasBoxArt)
    return if (entry.kind == EntryKind.Apps) SecondScreenContent.App(row.title, row.iconKey) else gameCard(row, systems, content.hasBoxArt)
}

private fun gameCard(
    row: GameRowModel,
    systems: GameSystems,
    hasBoxArt: Boolean,
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
        systemShort = row.systemAbbr,
    )

/** A system, Recent, Favorites or Apps under the cursor in the system column. */
private fun entryCard(
    entry: HomeEntry,
    names: EntryNames,
    hasBoxArt: Boolean,
): SecondScreenContent =
    when (entry.kind) {
        EntryKind.Apps -> SecondScreenContent.Apps(entry.shownCount, entry.rows.take(MAX_APP_ICONS).map { AppIconRef(it.iconKey, it.title) })
        // Their figures are read after the settle (SecondScreenEntryReads); until then the card is its header.
        EntryKind.Recent -> RecentEntry(names.recent, entry.shownCount, systemLooksOf(entry), hasBoxArt)
        EntryKind.Favorites -> {
            val favorites = entry.rows.filter { it.isFavorite }
            FavoritesEntry(
                name = names.favorites,
                gameCount = entry.shownCount,
                systemCount = favorites.mapTo(HashSet()) { it.systemId }.size,
                games = favorites.take(FAVORITES_CARRIED).mapNotNull { row -> row.id.toLongOrNull()?.let { EntryGame(it, row.title, row.systemId, row.scrapeKey.takeIf { hasBoxArt }) } },
                systems = systemLooksOf(entry),
                hasBoxArt = hasBoxArt,
            )
        }
        else ->
            SystemEntry(
                systemId = entry.id,
                name = entry.name.orEmpty(),
                gameCount = entry.shownCount,
                info = entry.info,
                coverAspect = entry.coverShape.widthOverHeight,
                hasBoxArt = hasBoxArt,
            )
    }

/** The names and cover shapes of the systems of a mixed list's games (Recent, Favorites), by id. */
private fun systemLooksOf(entry: HomeEntry): Map<String, EntrySystemLook> {
    val shorts = HashMap<String, String>()
    for (row in entry.rows) if (row.systemId !in shorts) shorts[row.systemId] = row.systemAbbr
    return shorts.mapValues { (id, short) ->
        val name = entry.systemNames[id].orEmpty()
        EntrySystemLook(name, short, (entry.systemShapes[id] ?: CoverShape.Landscape).widthOverHeight, coverCaptionOf(name, entry.systemShortNames[id].orEmpty()))
    }
}

/** The board's longest label under a cover, "PlayStation": a display name up to it fits as it is. */
private const val CAPTION_NAME_CHARS = 11

/** The caption under a cover: the display name where it is short ("Game Boy", "PlayStation"), the short name for a long one ("Super Nintendo": "SNES"). */
internal fun coverCaptionOf(
    name: String,
    shortName: String,
): String = if (name.length <= CAPTION_NAME_CHARS || shortName.isEmpty()) name else shortName

/**
 * Follows the main screen's cursor, search and game menu while a window is up: one effect, reading
 * the state through snapshotFlow (as HomeStrip does), so nothing in the main screen's composition
 * reads it. [content] is what the main screen shows (with the favourite marks); [buildSystems]
 * makes the game index (tests count it).
 */
@Composable
internal fun FollowHomeOnSecondScreen(
    feed: SecondScreenFeed,
    content: HomeContent,
    state: StripState,
    search: SearchState,
    menu: GameMenuController,
    buildSystems: (HomeContent) -> GameSystems = GameSystems::of,
) {
    val names = EntryNames(stringResource(R.string.home_entry_recent), stringResource(R.string.home_entry_favorites))
    val origin = LocalLaunchOrigin.current
    LaunchedEffect(feed, content, state, search, menu, names) {
        feed.isFollowing.collectLatest { following ->
            if (!following) return@collectLatest
            val systems = withContext(Dispatchers.Default) { buildSystems(content) }
            snapshotFlow { lookOf(state, search, menu) }
                .map { look -> secondScreenContentOf(content, look, systems, names).also { SecondScreenMotion.noteOffered(it, look.focus, content.entries.size, origin) } }
                .distinctUntilChanged()
                .collect(feed::offer)
        }
    }
}

private fun lookOf(
    state: StripState,
    search: SearchState,
    menu: GameMenuController,
): HomeLook {
    val hit = if (search.isOpen && !search.inField) search.results.getOrNull(search.row) else null
    return HomeLook(state.focus, search.isOpen, hit, menu.panel?.game?.key)
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
