package com.plumemu.graph

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.library.scrape.AskAgain
import com.plumemu.library.scrape.LibraryScrapeSource
import com.plumemu.scraper.AccountCheck
import com.plumemu.scraper.AccountLimits
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.Scraper
import com.plumemu.settings.screen.SettingsBoxArtActions
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.UnavailableReason
import com.plumemu.account.AccountStatus
import com.plumemu.scraper.work.ScrapeRunEnd
import com.plumemu.scraper.work.ScrapeScheduler
import com.plumemu.scraper.work.forgetNetworkFailures
import android.content.SharedPreferences
import java.util.UUID
import com.plumemu.scraper.work.ScraperWork
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * The app's side of the ScreenScraper run: what a background run gets ([scraperWork], for `PlumApplication` as the
 * [com.plumemu.scraper.work.ScraperHost]), when one is asked for ([requestRun]: after a scan, after
 * a sign-in), and the run's state for Settings ([status]).
 *
 * - Nothing runs without a usable account: signed in, not refused, a build with developer
 *   credentials ([GraphScraperAccounts.scraper] is null otherwise), so no request goes out.
 * - A run that meets a refused password marks the account ([GraphScraperAccounts.markRefused]),
 *   so Settings says "sign in again" and no later run asks with it.
 * - Signing out stops a run going or waiting ([cancelRuns]): nothing more is asked with the
 *   credentials the person removed.
 * - [viewedSystem] is the system the person is looking at (the home screen sets it, step 4); its
 *   games are asked right after Recent and Favorites.
 * - Settings' controls: [askAgain] marks games to ask again and starts a fresh run;
 *   [setPaused] stores the person's pause ([paused], `library.box_art_paused`): while it is set
 *   no run is asked for, a worker gets no work, and a run going or waiting is cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GraphScrapes(
    private val accounts: GraphScraperAccounts,
    private val librarySource: () -> LibraryScrapeSource,
    private val mediaDir: () -> File,
    private val enqueue: () -> Unit,
    private val cancel: () -> Unit,
    private val inputs: ScrapeStatusInputs,
    private val retryMark: () -> RetryMark,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
    /** The person's pause, as stored (`library.box_art_paused`). */
    private val paused: Flow<Boolean> = flowOf(false),
    /** Stores the person's pause; throws when it could not be written. */
    private val writePaused: suspend (Boolean) -> Unit = {},
    /** Where [askAgain]'s and [setPaused]'s answers are given (Settings reads them on the main thread). */
    private val main: CoroutineDispatcher = Dispatchers.Main,
    /** Settings' Clear box art (`GraphBoxArt.clear`); [onDone] gets, on the main thread, whether it worked. */
    private val clearBoxArt: (onDone: (BoxArtClearResult) -> Unit) -> Unit = { onDone -> onDone(BoxArtClearResult.Failed) },
    /** True while a game is played: a run stops between games and goes on at the return (`HeldForPlay`). */
    private val holdForPlay: suspend () -> Boolean = { false },
) {
    /** The system the home screen shows, or null; read at every page of a run. */
    val viewedSystem: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Deliveries in this process: one more after each stored game, so [status] reads the pace again. */
    private val deliveries = MutableStateFlow(0)

    /** One more after each stored game: Settings' box-art size is measured again (not more than every few seconds). */
    val deliveryCount: Flow<Int> get() = deliveries
    private val pace = ScrapePace(clock)

    /** Held by every run while it works; Clear box art and the sweep take it to delete pictures ([ScraperWork.runLock]). */
    val runLock = Mutex()

    /**
     * What one run needs, or null when scraping is not set up. One [com.plumemu.scraper.Scraper]
     * per account for the process ([GraphScraperAccounts.scraper]); the library's source is one per
     * process too (it remembers games it could not store), the run's source one per run (it
     * remembers which games its disc pass tried).
     */
    suspend fun scraperWork(): ScraperWork? {
        if (isPaused()) {
            log("Box art is paused by the person: no work")
            return null
        }
        val scraper = accounts.scraper() ?: return null
        val library = guarded("library scrape source") { librarySource() } ?: return null
        val folder = guarded("media folder") { mediaDir() } ?: return null
        // A run starts: an earlier retry is over (a stop for a lost constraint later is no retry).
        guarded("box art retry mark") { withContext(io) { retryMark().set(null) } }
        return ScraperWork(
            scraper = scraper,
            // The pace counts box fronts (the main pass) only: the time left is for covers.
            source = ScrapeRunSource(library, keyOf = library::scrapeKeyOf) {
                pace.recordDelivery()
                deliveries.update { it + 1 }
            },
            mediaDir = folder,
            runLock = runLock,
            holdOff = holdForPlay,
            mediaSizes = ScrapeMediaSizes.all,
            // Null asks the device (ActivityManager.isLowRamDevice): RGB_565 there.
            lowRamDevice = null,
            clock = clock,
            onUnavailable = { reason ->
                if (reason == UnavailableReason.BadUserCredentials) accounts.markRefused(scraper)
            },
            onEnded = { end, workId ->
                if (end == ScrapeRunEnd.Retry) guarded("box art retry mark") { withContext(io) { retryMark().set(workId) } }
            },
        )
    }

    /**
     * Asks for one run (Wi-Fi, battery not low) when scraping is set up; a run already waiting or
     * going is kept ([ScrapeScheduler.enqueue]). Called after a scan ends and after a sign-in,
     * never at app start before the first frame.
     */
    fun requestRun(): Job =
        scope.launch(io) {
            if (isPaused() || !accounts.hasUsableAccount()) return@launch
            guarded("box art run") { enqueue() }
        }

    /** Signed out: a run going or waiting stops (the current game is not delivered). */
    fun cancelRuns() = cancel()

    /**
     * Settings' Download missing and Re-download ([AskAgain.Missing], [AskAgain.All]) for
     * [systemIds] (null: every system): the library marks the games, then a fresh run is asked for
     * (one going is cancelled first: it would stop on a game it delivered coming back). While the
     * person's pause is set the games are marked and wait for Resume. [onDone] gets, on the main
     * thread, whether the games were marked.
     */
    fun askAgain(
        which: AskAgain,
        systemIds: Set<String>?,
        onDone: (Boolean) -> Unit,
    ): Job =
        scope.launch(io) {
            val marked = guarded("box art ask again") { librarySource().askAgain(which, systemIds) } != null
            if (marked && !isPaused() && accounts.hasUsableAccount()) {
                // Games dropped after network errors in three runs are asked again too.
                guarded("box art network failures") { accounts.scraper()?.forgetNetworkFailures() }
                guarded("box art run") {
                    cancel()
                    enqueue()
                }
            }
            withContext(main) { onDone(marked) }
        }

    /**
     * Settings' Pause and Resume: stores the pause, then cancels the run (Pause; the current game
     * is not delivered, so it is asked again) or asks for one (Resume). [onDone] gets, on the main
     * thread, whether the pause was stored.
     */
    fun setPaused(
        isPaused: Boolean,
        onDone: (Boolean) -> Unit,
    ): Job =
        scope.launch(io) {
            val stored = guarded("box art pause") { writePaused(isPaused) } != null
            if (stored) {
                if (isPaused) {
                    guarded("box art run") { cancel() }
                } else if (accounts.hasUsableAccount()) {
                    guarded("box art run") { enqueue() }
                }
            }
            withContext(main) { onDone(stored) }
        }

    /** What Settings › Library › ScreenScraper calls: [askAgain], [setPaused] and Clear box art. */
    val settingsActions: SettingsBoxArtActions =
        object : SettingsBoxArtActions {
            override fun askAgain(
                which: AskAgain,
                systemIds: Set<String>?,
                onDone: (Boolean) -> Unit,
            ) {
                this@GraphScrapes.askAgain(which, systemIds, onDone)
            }

            override fun setPaused(
                isPaused: Boolean,
                onDone: (Boolean) -> Unit,
            ) {
                this@GraphScrapes.setPaused(isPaused, onDone)
            }

            override fun clearBoxArt(onDone: (BoxArtClearResult) -> Unit) = clearBoxArt.invoke(onDone)
        }

    /** The stored pause; a store that cannot be read counts as not paused (logged). */
    private suspend fun isPaused(): Boolean = guarded("box art pause") { paused.first() } == true

    /**
     * The run's state ([scrapeStatusOf]). The account decides first ([accountOnlyStatus]): signed
     * out, refused or a build without credentials reads nothing else and watches nothing else.
     * With a usable account it is read again when the WorkManager run, the library's tables or a
     * delivery change. Built on collection: nothing is read (WorkManager is not even started)
     * until Settings looks.
     */
    val status: Flow<ScrapeStatus> =
        accounts.status.distinctUntilChanged().flatMapLatest { account ->
            accountOnlyStatus(account)?.let { flowOf(it) } ?: runStatus(account)
        }

    /** The scraper [askLimitsOnce] last asked; one ask per scraper, so a second Settings visit asks nothing. */
    private var limitsAskedOf: Scraper? = null

    /** The run's state and the account's limits, which the ScreenScraper panel reads together. */
    val statusWithLimits: Flow<Pair<ScrapeStatus, AccountLimits?>> get() = combine(status, accountLimits) { status, limits -> status to limits }

    /**
     * The account's limits for Settings › Library › ScreenScraper: empty while
     * signed out or refused, else the scraper's latest ([Scraper.accountLimits]), kept in memory
     * only. Built on collection: when Settings looks and no answer has given limits yet in this
     * process, [askLimitsOnce] asks `ssuserInfos.php` once.
     */
    val accountLimits: Flow<AccountLimits?> =
        accounts.status.distinctUntilChanged().flatMapLatest { account ->
            if (account !is AccountStatus.SignedIn || account.isRefused) {
                flowOf(null)
            } else {
                flow {
                    // Unknown at once: Settings must not wait for the Keystore (the scraper's password) to draw.
                    emit(null)
                    val scraper = guarded("account limits") { accounts.scraper() }
                    if (scraper != null) {
                        askLimitsOnce(scraper, account)
                        emitAll(scraper.accountLimits)
                    }
                }
            }
        }

    /**
     * One `ssuserInfos.php` for [scraper] when no limits are known yet, never twice for the same
     * scraper, and not while the daily quota holds requests back (the account's saved pause or
     * the scraper's own). A check that found no connection is forgotten, so the next visit tries
     * again; a refused password marks the account like a run's refusal does. The ask runs in the
     * graph's scope: leaving Settings does not cut it short.
     */
    private suspend fun askLimitsOnce(
        scraper: Scraper,
        account: AccountStatus.SignedIn,
    ) {
        if (scraper.accountLimits.value != null) return
        // Pause means no request, not even this one.
        if (isPaused()) return
        val now = clock()
        if (account.dailyLimitUntilMillis?.let { it > now } == true) return
        val pause = guarded("scraper pause") { withContext(io) { inputs.pause() } }
        if (pause != null && pause.resumeAtMillis > now) return
        synchronized(this) {
            if (limitsAskedOf === scraper) return
            limitsAskedOf = scraper
        }
        scope.launch(io) {
            val check = guarded("account limits") { scraper.checkAccount() }
            when {
                check == AccountCheck.Offline ->
                    synchronized(this@GraphScrapes) { if (limitsAskedOf === scraper) limitsAskedOf = null }
                check is AccountCheck.Refused && check.reason == UnavailableReason.BadUserCredentials -> accounts.markRefused(scraper)
            }
        }
    }

    private fun runStatus(account: AccountStatus): Flow<ScrapeStatus> =
        combine(
            flow { emitAll(inputs.runs) }.catch { e ->
                log("Box art run state failed (${e.javaClass.simpleName})")
                emit(emptyList())
            },
            flow { emitAll(inputs.libraryChanges) }.onStart { emit(Unit) }.catch { e ->
                log("Library changes for box art failed (${e.javaClass.simpleName})")
                emit(Unit)
            },
            deliveries,
            flow { emitAll(paused) }.catch { e ->
                log("Box art pause failed (${e.javaClass.simpleName})")
                emit(false)
            },
        ) { infos, _, _, isPaused -> infos to isPaused }
            .mapLatest { (infos, isPaused) ->
                withContext(io) {
                    val retried = guarded("box art retry mark") { retryMark().get() }
                    scrapeStatusOf(
                        account = account,
                        run = runSnapshotOf(infos, retried),
                        pause = guarded("scraper pause") { inputs.pause() },
                        counts = guarded("box art counts") { inputs.counts() },
                        conditions = guarded("device conditions") { inputs.conditions() } ?: DeviceConditions(unmeteredNetwork = true, batteryLow = false),
                        gamesPerMinute = pace.gamesPerMinute(),
                        now = clock(),
                        pausedByPerson = isPaused,
                    )
                }
            }

    private suspend fun <T> guarded(
        what: String,
        block: suspend () -> T,
    ): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Could not read or start the $what (${e.javaClass.simpleName})")
            null
        }
}

/** Where [GraphScrapes.status] reads from; the device's in [deviceScrapeStatusInputs], fakes in tests. */
internal interface ScrapeStatusInputs {
    /** The unique run's WorkInfos ([ScrapeScheduler.UNIQUE_WORK_NAME]). */
    val runs: Flow<List<WorkInfo>>

    /** Emits when the library's games or stored results change. */
    val libraryChanges: Flow<Unit>

    suspend fun counts(): ScrapeCounts

    /** The saved pause, or null. */
    fun pause(): ScrapeResult.Paused?

    fun conditions(): DeviceConditions
}

/** [ScrapeStatusInputs] of the device: WorkManager, Room, the pause file, the network and the battery. */
internal fun deviceScrapeStatusInputs(
    context: Context,
    database: () -> PlumDatabase,
    pauseStore: () -> PauseStore,
    clock: () -> Long = System::currentTimeMillis,
): ScrapeStatusInputs {
    val app = context.applicationContext
    return object : ScrapeStatusInputs {
        override val runs: Flow<List<WorkInfo>>
            get() = WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(ScrapeScheduler.UNIQUE_WORK_NAME)

        override val libraryChanges: Flow<Unit>
            get() = database().invalidationTracker.createFlow(GAME_TABLE, SCRAPED_GAME_TABLE, emitInitialState = false).map { }

        override suspend fun counts(): ScrapeCounts {
            val dao = database().scrapedGameDao()
            return ScrapeCounts(
                withBoxFront = dao.countWithBoxFront(),
                listed = dao.countListedForBoxArt(),
                pending = dao.countPending(clock() - ScrapedGameDao.NO_MATCH_RETRY_MILLIS),
            )
        }

        override fun pause(): ScrapeResult.Paused? = pauseStore().load()

        override fun conditions(): DeviceConditions = deviceConditionsOf(app)
    }
}

/**
 * The network and battery as WorkManager's constraints see them: an unmetered network
 * (`NetworkType.UNMETERED`), and "battery low" as Android's own low-battery state, 15% or less
 * and not charging.
 */
internal fun deviceConditionsOf(context: Context): DeviceConditions {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
    val unmetered =
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val battery = context.getSystemService(BatteryManager::class.java)
    val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val low = level in 0..LOW_BATTERY_PERCENT && battery?.isCharging != true
    return DeviceConditions(unmeteredNetwork = unmetered, batteryLow = low)
}

/** [RetryMark] in a small SharedPreferences file; read and written on the IO dispatcher. */
internal class SharedPreferencesRetryMark(
    context: Context,
    private val log: (String) -> Unit,
) : RetryMark {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun get(): UUID? = prefs.getString(KEY, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    override fun set(workId: UUID?) {
        if (get() == workId) return
        // commit, not apply: the worker returns right after and the process may end.
        val written = prefs.edit().apply { if (workId == null) remove(KEY) else putString(KEY, workId.toString()) }.commit()
        if (!written) log("Could not save the box art retry mark")
    }

    companion object {
        const val FILE_NAME = "plumemu.scrape.run"
        private const val KEY = "retry_work_id"
    }
}

private const val GAME_TABLE = "game"
private const val SCRAPED_GAME_TABLE = "scraped_game"
private const val LOW_BATTERY_PERCENT = 15
