package com.plumemu.content

import android.util.Log
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.GameListRow
import com.plumemu.coredata.db.SystemGameCount
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.graph.GraphCollections
import com.plumemu.home.FolderRows
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.LetterKey
import com.plumemu.home.SystemGames
import com.plumemu.home.systemInfoOf
import com.plumemu.home.withVariantTags
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.PlatformCatalog
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
    /** The stored sort (games list menu, R3): every system's list and Favorites; Recent stays newest first. */
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
    /**
     * Installed apps listed as games, package to system (`AppGraph.appGames.marked`): the Apps entry
     * leaves them out. Null (before the first sync) leaves nothing out.
     */
    private val appGames: Flow<Map<String, String>?> = flowOf(null),
    /** Game id to package of the app games (`AppGameDao.observePackages`): their rows carry the package (`GameRowModel.appPackage`). */
    private val appPackages: Flow<Map<Long, String>> = flowOf(emptyMap()),
    /**
     * The database the Collections folders read (Most played, Recently added, the own collections). Null: Most played and Recently added are
     * still listed, with no games, and there are no own collections. Built into [GraphCollections] here, so the graph passes one value.
     */
    private val database: PlumDatabase? = null,
    /** Epoch milliseconds now; the Recently added window and the day change follow it (a fake in tests). */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Emits when the date changes (and once at the start), so Recently added's window is asked again; default: local midnights by [clock]. */
    private val dayChanges: Flow<Long> = dayStarts(clock),
) {
    private val collections: GraphCollections? = database?.let { GraphCollections(it, clock) }

    val content: Flow<HomeContent> =
        flow { emit(SystemNames(catalog(), extraAbbrs)) }
            .flatMapLatest { names ->
                sort.distinctUntilChanged().flatMapLatest { order ->
                    combine(
                        arranged(names, order),
                        folders(names, order),
                        favorites(names, order),
                        appRows(),
                        combine(showApps.distinctUntilChanged(), hasBoxArt.distinctUntilChanged(), appPackages.distinctUntilChanged()) { show, art, packages -> HomeFlags(show, art, packages) },
                    ) { (systems, hidden), folders, favorites, apps, flags -> assemble(systems, hidden, folders, favorites, apps, flags.showApps, flags.hasBoxArt, order, flags.packages) }
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
        folders: List<FolderRows>,
        favorites: List<GameRowModel>,
        apps: List<GameRowModel>,
        showApps: Boolean,
        hasBoxArt: Boolean,
        order: SortGamesSetting,
        packages: Map<Long, String>,
    ): HomeContent =
        HomeContent.of(
            recent = withPackages(folders.first().rows, packages),
            otherFolders = folders.drop(1).map { it.copy(rows = withPackages(it.rows, packages)) },
            favorites = withPackages(favorites, packages),
            apps = apps,
            systems = if (packages.isEmpty()) systems else systems.map { it.copy(games = withPackages(it.games, packages)) },
            hasBoxArt = hasBoxArt,
            showApps = showApps,
            hiddenSystems = hiddenSystems,
            sortedByName = order == SortGamesSetting.Name,
            otherSort = order.takeIf { it != SortGamesSetting.Name },
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
            val shaped = all.map { it.copy(coverShape = SystemCoverShapes.of(names.systemOf(it.id), measured[it.id]), info = names.systemOf(it.id)?.let(::systemInfoOf), code = names.systemOf(it.id)?.code.orEmpty(), shortName = names.systemOf(it.id)?.shortName.orEmpty()) }
            val arranged = SystemOrder.arrange(shaped, arrangement, language) { names.factsOf(it.id, it.name) }
            SystemOrder.split(arranged, arrangement) { it.id }
        }

    /**
     * [coverRatios] while box art is set up (without it nothing is measured and each system keeps its platform data's shape, [SystemCoverShapes]). A
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

    /**
     * Recent first, then the folders of [GraphCollections.folders] (Most played, Recently added, the own collections), each with its games.
     * The structure is asked again when the date changes ([dayChanges]); a folder's games are its own flow, so a play restarts none of the others.
     */
    private fun folders(
        names: SystemNames,
        order: SortGamesSetting,
    ): Flow<List<FolderRows>> {
        fun rowsOf(rows: List<GameListRow>) = withVariantTags(rows.map { toRow(it, names.abbrOf(it.systemId)) })
        val recent = games.observeRecent(RECENT_GAMES).map { FolderRows(Folder.BuiltIn(BuiltInFolder.Recent, it.size), rowsOf(it)) }
        val source = collections
        val db = database
        if (source == null || db == null) return recent.map { listOf(it) + HomeContent.DEFAULT_FOLDERS }
        return dayChanges.distinctUntilChanged().flatMapLatest { now ->
            source.folders(now)
                // Only which folders there are and their names: a count that moves is read from the games, so no query restarts for it.
                .map { list -> list.mapNotNull(::withoutCount).filterNot { it is Folder.BuiltIn && it.kind == BuiltInFolder.Recent } }
                .distinctUntilChanged()
                .flatMapLatest { structure ->
                    val others =
                        structure.map { folder ->
                            when (folder) {
                                is Folder.BuiltIn ->
                                    if (folder.kind == BuiltInFolder.MostPlayed) {
                                        db.smartListDao().observeMostPlayed().map { FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, it.size), rowsOf(it)) }
                                    } else {
                                        db.smartListDao().observeRecentlyAdded(now).map { FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, it.size), rowsOf(it)) }
                                    }
                                is Folder.Own -> db.collectionDao().observeCollectionGames(folder.id, order).map { FolderRows(folder.copy(count = it.size), rowsOf(it)) }
                                Folder.NewCollection -> error("New collection is not a folder with games")
                            }
                        }
                    combine(listOf(recent) + others) { it.toList() }
                }
        }
    }

    /** [folder] with its count zeroed (its identity and name only); null for New collection…, which has no games. */
    private fun withoutCount(folder: Folder): Folder? =
        when (folder) {
            is Folder.BuiltIn -> folder.copy(count = 0)
            is Folder.Own -> folder.copy(count = 0)
            Folder.NewCollection -> null
        }

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

    /** What the content flow reads besides the lists. */
    private data class HomeFlags(val showApps: Boolean, val hasBoxArt: Boolean, val packages: Map<Long, String>)

    /** The rows of app games carry their package, so the screens draw their icon; the rest are as they were. */
    private fun withPackages(rows: List<GameRowModel>, packages: Map<Long, String>): List<GameRowModel> =
        if (packages.isEmpty()) rows else rows.map { row -> row.id.toLongOrNull()?.let(packages::get)?.let { row.copy(appPackage = it) } ?: row }

    companion object {
        private const val TAG = "PlumHome"

        /**
         * [clock] now, then again whenever the local date has changed. The date is checked at each midnight and at least every
         * [MAX_DAY_WAIT_MS]: a wait does not run while the device sleeps and a clock or time-zone change moves midnight, so the
         * wake-up looks at the clock again. A new collection (the screen started again) starts with a fresh reading.
         */
        fun dayStarts(
            clock: () -> Long,
            zone: () -> TimeZone = { TimeZone.getDefault() },
        ): Flow<Long> =
            flow {
                var lastDay = Long.MIN_VALUE
                while (true) {
                    val now = clock()
                    val calendar = Calendar.getInstance(zone()).apply { timeInMillis = now }
                    val day = calendar.get(Calendar.YEAR) * DAY_KEY + calendar.get(Calendar.DAY_OF_YEAR).toLong()
                    if (day != lastDay) {
                        lastDay = day
                        emit(now)
                    }
                    calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                    delay((calendar.timeInMillis - now).coerceIn(MIN_DAY_WAIT_MS, MAX_DAY_WAIT_MS))
                }
            }

        private const val MIN_DAY_WAIT_MS = 1_000L
        private const val MAX_DAY_WAIT_MS = 3_600_000L
        private const val DAY_KEY = 1_000L
        private const val MAX_CAUSES = 4

        /** How many games Recent shows, newest first; the play history itself is never trimmed. */
        const val RECENT_GAMES = 50

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
                artAspect = row.artAspect,
            )

        /** An app row: package and activity make the key (one package may have several launchers). */
        internal fun toRow(app: LaunchableApp): GameRowModel =
            GameRowModel(id = appRowId(app), title = app.label, region = "", sortName = LetterKey.foldedLabel(app.label))

        /** The id of [app]'s row; opening a row looks the app up by it (`GraphAppActions`). */
        internal fun appRowId(app: LaunchableApp): String = "${app.packageName}/${app.activityName}"

    }
}
