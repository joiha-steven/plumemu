package com.plumemu.content

import android.util.Log
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.GameListRow
import com.plumemu.coredata.db.SystemGameCount
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.LetterKey
import com.plumemu.home.SystemGames
import com.plumemu.home.systemInfoOf
import com.plumemu.home.withVariantTags
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.PlatformCatalog
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * The main screen's content from the library (Room) and the app list. Production source at the
 * D-stage wiring point; `FakeHomeContent` stays for tests, previews and the benchmark.
 *
 * Each list is its own Flow that skips equal answers (GameDao), so a scan batch that touched one
 * system re-maps only that system; the others keep their row lists (the same instances), and
 * Compose skips their rows. The set of systems is watched apart from their counts, so new games in
 * an existing system do not restart every list. All mapping runs on [mapping], off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryHomeContent(
    private val games: GameDao,
    private val catalog: suspend () -> PlatformCatalog?,
    private val apps: Flow<List<LaunchableApp>>,
    private val showApps: Flow<Boolean>,
    private val mapping: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = { Log.e(TAG, it) },
    /** Settings › Library › Sort games: every system's list and Favorites; Recent stays newest first. */
    private val sort: Flow<SortGamesSetting> = flowOf(SortGamesSetting.Name),
    /**
     * Whether box art is set up (this build has developer credentials and an account is
     * signed in): the grid and the details column show, the list shows years.
     */
    private val hasBoxArt: Flow<Boolean> = flowOf(false),
    /** Settings › Library › System order and Systems: how the system column is arranged and which systems it leaves out. */
    private val arrangement: Flow<SystemArrangement> = flowOf(SystemArrangement()),
    /**
     * The language whose alphabet A to Z follows: the configuration's first one, as Settings reads
     * it (`AppGraph.locale`); a change re-arranges the column.
     */
    private val locale: Flow<Locale> = flowOf(Locale.getDefault()),
    /**
     * Each system's median box-front width over height from the person's own covers (core-data's
     * `BoxFrontShapes.observe`: read once, then after changes settle), read only with box art.
     * Systems it leaves out take the platform data's ratio ([SystemCoverShapes]).
     */
    private val coverRatios: Flow<Map<String, Float>> = flowOf(emptyMap()),
    /** After [coverRatios] fails, how long until it is observed again. */
    private val coverRetryMillis: Long = COVER_RETRY_MILLIS,
    /**
     * Short names of systems the platform data does not have, from the app's strings: the pinned
     * shortcuts' list (`shortcuts`, "Link"), so its games have a right-hand column in Recent,
     * Favorites and Search like every other.
     */
    private val extraAbbrs: Map<String, String> = emptyMap(),
    /** Settings › Appearance › Left edge: what the home screen's peek shows; null (not picked) is Game counts. */
    private val leftEdge: Flow<LeftEdgeSetting?> = flowOf(null),
    /**
     * Installed apps listed as games, package to system (`AppGraph.appGames.marked`): the Apps entry
     * leaves them out. Null (before the first sync) leaves nothing out.
     */
    private val appGames: Flow<Map<String, String>?> = flowOf(null),
    /** Settings' Recent size: how many games Recent keeps; a change queries Recent again. */
    private val recentSize: Flow<RecentSizeSetting> = flowOf(RecentSizeSetting.Twenty),
) {
    val content: Flow<HomeContent> =
        flow { emit(SystemNames(catalog(), extraAbbrs)) }
            .flatMapLatest { names ->
                sort.distinctUntilChanged().flatMapLatest { order ->
                    combine(
                        arranged(names, order),
                        recent(names),
                        favorites(names, order),
                        appRows(),
                        combine(showApps.distinctUntilChanged(), hasBoxArt.distinctUntilChanged(), leftEdge.map { it ?: LeftEdgeSetting.Default }.distinctUntilChanged(), ::Triple),
                    ) { (systems, hidden), recent, favorites, apps, (show, art, edge) -> assemble(systems, hidden, recent, favorites, apps, show, art, order, edge) }
                }
            }
            .flowOn(mapping)

    /**
     * [content] as screen states: any failure upstream (the database cannot open, a query throws)
     * ends in [HomeState.Failed] with one log line (class only), never in a crash.
     */
    val states: Flow<HomeState> =
        content
            .map<HomeContent, HomeState> { HomeState.Ready(it) }
            .catch { e ->
                if (e is CancellationException) throw e
                // Classes only, cause chain included (a Room migration error, e.g. a v1 build over
                // v2 data, is an IllegalStateException).
                val classes = generateSequence(e) { it.cause }.take(MAX_CAUSES).joinToString(" <- ") { it.javaClass.simpleName }
                log("Main screen content failed ($classes)")
                emit(HomeState.Failed)
            }

    private fun assemble(
        systems: List<SystemGames>,
        hiddenSystems: List<SystemGames>,
        recent: List<GameRowModel>,
        favorites: List<GameRowModel>,
        apps: List<GameRowModel>,
        showApps: Boolean,
        hasBoxArt: Boolean,
        order: SortGamesSetting,
        edge: LeftEdgeSetting,
    ): HomeContent =
        HomeContent.of(
            recent = recent,
            favorites = favorites,
            apps = apps,
            systems = systems,
            hasBoxArt = hasBoxArt,
            showApps = showApps,
            hiddenSystems = hiddenSystems,
            sortedByName = order == SortGamesSetting.Name,
            leftEdge = edge,
        )

    /**
     * Every system's games in the column's order, split into the shown and the hidden ones
     * ([SystemOrder]), each with its cover shape. A change of order, of the hidden set, of the
     * language or of a shape only re-arranges the lists already mapped: no query restarts.
     */
    private fun arranged(
        names: SystemNames,
        order: SortGamesSetting,
    ): Flow<Pair<List<SystemGames>, List<SystemGames>>> =
        combine(systems(names, order), arrangement.distinctUntilChanged(), locale.distinctUntilChanged(), measuredShapes()) { all, arrangement, language, measured ->
            val shaped = all.map { it.copy(coverShape = SystemCoverShapes.of(names.systemOf(it.id), measured[it.id]), info = names.systemOf(it.id)?.let(::systemInfoOf), code = names.systemOf(it.id)?.code.orEmpty()) }
            val arranged = SystemOrder.arrange(shaped, arrangement, language) { names.factsOf(it.id, it.name) }
            SystemOrder.split(arranged, arrangement) { it.id }
        }

    /**
     * [coverRatios] while box art is set up (without it there is no grid, so nothing is read). A
     * failure logs its class and puts the platform data's shapes back, and the covers are observed
     * again [coverRetryMillis] later for as long as the content is collected: the screen never
     * fails for it, and one bad read does not lose the person's shapes for the session.
     */
    private fun measuredShapes(): Flow<Map<String, Float>> =
        hasBoxArt.distinctUntilChanged().flatMapLatest { art ->
            if (!art) {
                flowOf(emptyMap())
            } else {
                coverRatios.retryWhen { e, _ ->
                    if (e is CancellationException) throw e
                    log("Cover shapes could not be read (${e.javaClass.simpleName})")
                    emit(emptyMap())
                    delay(coverRetryMillis)
                    true
                }
            }
        }

    private fun systems(
        names: SystemNames,
        order: SortGamesSetting,
    ): Flow<List<SystemGames>> =
        games.observeSystemsWithGames()
            .map { counts -> names.ordered(counts) }
            .distinctUntilChanged()
            .flatMapLatest { named ->
                if (named.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(named.map { (id, name) -> systemGames(names, id, name, order) }) { it.toList() }
                }
            }

    private fun systemGames(
        names: SystemNames,
        id: String,
        name: String,
        order: SortGamesSetting,
    ): Flow<SystemGames> = games.observeSystemGames(id, order).map { rows -> SystemGames(id, name, withVariantTags(rows.map { toRow(it, names.abbrOf(it.systemId)) })) }

    private fun recent(names: SystemNames): Flow<List<GameRowModel>> =
        recentSize
            .distinctUntilChanged()
            .flatMapLatest { size -> games.observeRecent(size.games) }
            .map { rows -> withVariantTags(rows.map { toRow(it, names.abbrOf(it.systemId)) }) }

    private fun favorites(
        names: SystemNames,
        order: SortGamesSetting,
    ): Flow<List<GameRowModel>> = games.observeFavorites(order).map { rows -> withVariantTags(rows.map { toRow(it, names.abbrOf(it.systemId)) }) }

    private fun appRows(): Flow<List<GameRowModel>> =
        combine(apps, showApps, appGames) { list, show, listed ->
            if (show) list.filter { listed == null || it.packageName !in listed }.map(::toRow) else emptyList()
        }.distinctUntilChanged()

    /**
     * Names and facts from the platform data (`displayName`, `maker`, `year`); a system the data
     * does not know shows the name the database holds and has no maker. The column's order is
     * [SystemOrder]'s, applied after the lists are mapped; [ordered] only keeps the lists' set stable.
     */
    private class SystemNames(
        catalog: PlatformCatalog?,
        private val extraAbbrs: Map<String, String>,
    ) {
        private val systems = catalog?.systems.orEmpty().associateBy { it.id }

        fun ordered(counts: List<SystemGameCount>): List<Pair<String, String>> =
            counts
                .sortedBy { it.id }
                .map { it.id to (systems[it.id]?.displayName ?: it.name) }

        fun factsOf(
            id: String,
            name: String,
        ): OrderFacts = systems[id].let { OrderFacts(id, name, it?.maker, it?.year) }

        fun systemOf(id: String): GameSystem? = systems[id]

        /** The short name of [id] ("SNES"); empty when neither the platform data nor [extraAbbrs] knows the system. */
        fun abbrOf(id: String): String = systems[id]?.abbr ?: extraAbbrs[id].orEmpty()
    }

    companion object {
        private const val TAG = "PlumHome"
        private const val MAX_CAUSES = 4

        /** A failed read of the covers' shapes is tried again after this long. */
        const val COVER_RETRY_MILLIS = 30_000L

        /** A game row: its id as the key, the clean title, the first region (shown without a year), its scrape key, its system's id and short name. */
        internal fun toRow(
            row: GameListRow,
            systemAbbr: String = "",
        ): GameRowModel =
            GameRowModel(
                id = row.id.toString(),
                title = row.title,
                region = row.regions.substringBefore(','),
                isFavorite = row.isFavorite,
                scrapeKey = row.scrapeKey,
                systemAbbr = systemAbbr,
                systemId = row.systemId,
                sortName = row.sortName,
                fileName = row.fileName,
            )

        /** An app row: package and activity make the key (one package may have several launchers). */
        internal fun toRow(app: LaunchableApp): GameRowModel =
            GameRowModel(id = appRowId(app), title = app.label, region = "", sortName = LetterKey.foldedLabel(app.label))

        /** The id of [app]'s row; opening a row looks the app up by it (`GraphAppActions`). */
        internal fun appRowId(app: LaunchableApp): String = "${app.packageName}/${app.activityName}"

    }
}
