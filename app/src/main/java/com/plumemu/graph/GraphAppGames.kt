package com.plumemu.graph

import com.plumemu.coredata.db.AppGameDao
import com.plumemu.coredata.db.AppGameSync
import com.plumemu.coredata.db.AppKind
import com.plumemu.coredata.db.NewAppGame
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.launcher.apps.PackagePresence
import com.plumemu.platforms.PlatformCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The log tag of [GraphAppGames]' lines. */
internal const val APP_GAMES_TAG = "PlumAppGames"

/** How one [GraphAppGames.syncNow] ended. */
internal sealed interface AppGamesSyncOutcome {
    data class Synced(val sync: AppGameSync, val marked: Map<String, String>) : AppGamesSyncOutcome

    /** Nothing was written: the list, the choices or the platform data could not be read whole (logged). */
    data class Skipped(val why: String) : AppGamesSyncOutcome
}

/**
 * Installed apps as games: reads the launchable apps, decides
 * which are Android games or ports ([appGameSystems]) and makes the library's app games match
 * ([AppGameDao.sync]). One per process (`AppGraph.appGames`).
 *
 * [AppGameDao.sync] deletes the rows (with their play time) of packages not in the list, so a sync
 * runs only from a whole read: a list that could not be read, or reads empty (a device always lists
 * Android's Settings), skips it; so do unreadable choices or platform data. The list omits disabled
 * apps and apps on storage that is not mounted: a listed package missing from it keeps its row unless
 * Android says it is not installed at all ([PackagePresence.NotInstalled]; a failed question keeps it).
 * A package removal that is an update (`EXTRA_REPLACING`) or storage going away asks for nothing
 * ([onPackageChange]): the app is back a moment later.
 * Requests are debounced ([debounceMs]) so a burst of package broadcasts makes one sync; syncs run one
 * at a time.
 */
internal class GraphAppGames(
    private val dao: () -> AppGameDao,
    private val catalog: suspend () -> PlatformCatalog?,
    /** The launchable apps without Plumemu (launcher's `InstalledApps.list`); null when the launcher is not set up. */
    private val readApps: suspend () -> List<LaunchableApp>?,
    /** Whether Android still has a package in any state (launcher's `InstalledApps.presenceOf`); null is unknown. */
    private val presence: suspend (packageName: String) -> PackagePresence?,
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private val mutableMarked = MutableStateFlow<Map<String, String>?>(null)

    /**
     * Package name to the system (`android` or `ports`) of every installed app listed as a game, as of
     * the last sync; null before the first one. The Apps entry leaves these packages out.
     */
    val marked: StateFlow<Map<String, String>?> = mutableMarked.asStateFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val one = Mutex()
    private var isListening = false

    /** Asks for a sync after [debounceMs] without another request; returns at once. */
    fun requestSync() {
        startListening()
        requests.trySend(Unit)
    }

    /**
     * A package broadcast or a change seen at start ([isTemporaryRemoval]: `ACTION_PACKAGE_REMOVED`
     * with `EXTRA_REPLACING`, an update whose `ADDED` follows, or `ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE`).
     * Only another change asks for a sync.
     */
    fun onPackageChange(isTemporaryRemoval: Boolean) {
        if (isTemporaryRemoval) {
            log("App games: an app is being updated or its storage went away; no sync until it is back")
            return
        }
        requestSync()
    }

    /**
     * The person's answer for [packageName] ("Show as game" = [AppKind.GAME], "Show as port" =
     * [AppKind.PORT], "Not a game" = [AppKind.APP]); stored, then the library follows at once.
     * Throws when the answer could not be stored.
     */
    suspend fun setChoice(packageName: String, kind: AppKind): AppGamesSyncOutcome {
        withContext(io) { dao().setChoice(packageName, kind, clock()) }
        return syncNow()
    }

    /** Forgets the person's answer: Plumemu decides for [packageName] again. Throws when it could not be stored. */
    suspend fun clearChoice(packageName: String): AppGamesSyncOutcome {
        withContext(io) { dao().clearChoice(packageName) }
        return syncNow()
    }

    /** One sync now (after any running one); a failed read writes nothing. */
    suspend fun syncNow(): AppGamesSyncOutcome = one.withLock { withContext(io) { syncOnce() } }

    private suspend fun syncOnce(): AppGamesSyncOutcome {
        val catalog = guarded("platform data") { catalog() } ?: return skipped("the platform data could not be read")
        val systems = AppGameSystems.all.associateWith { catalog.system(it) }
        val missing = systems.filterValues { it == null }.keys
        // A system the data lost would drop every app game of it with its play time: write nothing.
        if (missing.isNotEmpty()) return skipped("the platform data has no system ${missing.sorted()}")
        val apps = guarded("installed apps") { readApps() } ?: return skipped("the installed apps could not be read")
        if (apps.isEmpty()) return skipped("the installed apps read empty")
        val choices = guarded("app kind choices") { dao().choices() } ?: return skipped("the person's choices could not be read")
        val emulatorPackages = catalog.emulators.flatMapTo(HashSet()) { emulator -> emulator.allPackages.map { it.packageName } }
        val marked =
            appGameSystems(
                apps = apps.map { InstalledApp(it.packageName, it.label, it.isGame) },
                knownApps = catalog.knownApps,
                choices = choices,
                emulatorPackages = emulatorPackages,
                ownPackage = ownPackage,
            )
        val labels = apps.associate { it.packageName to it.label }
        val rows =
            marked.map { (packageName, systemId) ->
                val label = labels.getValue(packageName)
                NewAppGame(packageName, label, TitleCleaner.sortName(label), systemId, checkNotNull(systems[systemId]).name)
            }
        val stored = guarded("app game records") { dao().records() } ?: return skipped("the app games could not be read")
        val read = apps.mapTo(HashSet()) { it.packageName }
        val keep =
            stored.map { it.packageName }
                .filter { it !in read && stillMarked(it, choices, emulatorPackages) && stillInstalled(it) }
                .toSet()
        val sync = guarded("app games sync") { dao().sync(rows, clock(), keep) } ?: return skipped("the library could not be written")
        mutableMarked.value = marked
        if (sync.added + sync.updated + sync.removed > 0) {
            log("App games: ${marked.size} listed (${sync.added} added, ${sync.updated} updated, ${sync.removed} removed)")
        }
        return AppGamesSyncOutcome.Synced(sync, marked)
    }

    /**
     * Whether a stored app game missing from the read would still be listed: Android's game mark cannot be read
     * while it is missing, so it counts as last known (the row exists, so something marked it); the person's
     * "not a game" ([AppKind.APP]) and the emulator rule still drop it, as [appGameSystems] would.
     */
    private fun stillMarked(packageName: String, choices: Map<String, AppKind>, emulatorPackages: Set<String>): Boolean =
        when (choices[packageName]) {
            AppKind.GAME, AppKind.PORT -> true
            AppKind.APP -> false
            null -> packageName != ownPackage && packageName !in emulatorPackages
        }

    /** False only when Android says [packageName] is not installed at all; a failed question keeps the row. */
    private suspend fun stillInstalled(packageName: String): Boolean =
        when (guarded("presence of an app") { presence(packageName) } ?: PackagePresence.Unknown) {
            PackagePresence.Installed -> true
            PackagePresence.NotInstalled -> false
            PackagePresence.Unknown -> true.also { log("App games: whether $packageName is installed is unknown; its game is kept") }
        }

    private fun skipped(why: String): AppGamesSyncOutcome {
        log("App games: not synced, $why")
        return AppGamesSyncOutcome.Skipped(why)
    }

    /** [block]'s answer, or null when it threw (logged by class); the caller's own cancellation still ends it. */
    private suspend fun <T> guarded(what: String, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: Exception) {
            // Not `if (e is CancellationException) throw e`: Room throws one from its own scope once the
            // database is closed; only our own cancellation ends the sync.
            currentCoroutineContext().ensureActive()
            log("App games: $what failed (${e.javaClass.simpleName})")
            null
        }

    @Synchronized
    private fun startListening() {
        if (isListening) return
        isListening = true
        scope.launch {
            while (true) {
                requests.receive()
                // Debounce: wait until no request came for debounceMs.
                while (withTimeoutOrNull(debounceMs) { requests.receive() } != null) Unit
                syncNow()
            }
        }
    }

    private companion object {
        /** Long enough for an update's REMOVED, ADDED and REPLACED broadcasts to land as one. */
        const val DEBOUNCE_MS = 1_500L
    }
}
