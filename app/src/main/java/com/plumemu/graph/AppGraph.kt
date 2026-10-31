package com.plumemu.graph

import com.plumemu.usage.backupEventOf
import com.plumemu.usage.restoreEventOf
import com.plumemu.usage.VersionSeen
import com.plumemu.R
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.PauseStore
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.SharedPreferencesPauseStore
import com.plumemu.scraper.account.KeystorePasswordCipher
import com.plumemu.scraper.account.PasswordCipher
import com.plumemu.scraper.work.ScrapeScheduler
import com.plumemu.library.scrape.LibraryScrapeSource
import android.net.Uri
import com.plumemu.BuildConfig
import java.io.File
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsHost
import kotlinx.coroutines.Job
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import com.plumemu.home.localeOf
import android.util.Log
import com.plumemu.content.CoilCoverImages
import com.plumemu.content.HomeState
import com.plumemu.content.LibraryHomeArt
import com.plumemu.content.LibraryHomeContent
import com.plumemu.content.SystemArrangement
import com.plumemu.home.HomeArtSource
import com.plumemu.coredata.db.BoxFrontShapes
import com.plumemu.coredata.backup.RestoredDatabase
import com.plumemu.coredata.db.DatabaseGate
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.shortcuts.PinnedShortcuts
import com.plumemu.shortcuts.ShortcutFiling
import com.plumemu.shortcuts.shortcutHostFor
import com.plumemu.launcher.Launching
import com.plumemu.launcher.apps.AppIcons
import com.plumemu.secondscreen.GameStartRelay
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.launcher.play.battery.AndroidBatteryReader
import com.plumemu.launcher.play.battery.PlayBatteryMeter
import com.plumemu.launcher.play.battery.UsualPace
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.scan.GameNaming
import com.plumemu.library.scan.LibraryScanner
import com.plumemu.library.scan.ScanEvent
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.settings.AppearanceCache
import com.plumemu.settings.settleButtonSet
import com.plumemu.secondscreen.AndroidDisplaySource
import com.plumemu.secondscreen.SecondScreenDisplays
import com.plumemu.secondscreen.SecondScreenSwitchCache
import com.plumemu.setup.SetupModel
import com.plumemu.telemetry.ProblemReports
import com.plumemu.telemetry.TelemetrySink
import com.plumemu.usage.UsageSession
import com.plumemu.update.Updates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's objects, wired by hand (no DI library): one per process, made by [PlumApplication].
 * Constructing it reads nothing; each part is built the first time it is asked for, off the main
 * thread where it reads files (the platform data, the database). Nothing here may crash the
 * process: Plumemu can be the home screen, and a crash there loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraph(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.e(TAG, it) },
    /** Usage statistics and problem reports (PlumApplication makes them); null in tests, which send nothing. */
    val telemetry: TelemetrySink? = null,
    private val problemReports: ProblemReports? = null,
) {
    private val app = context.applicationContext

    /**
     * App opens, time in front, screens and input for the statistics (graph/GraphUsage.kt). Made with
     * the graph, in `PlumApplication.onCreate`, so on Android 6 (no process start time) a cold start
     * is counted from here.
     */
    internal val versionSeen: VersionSeen by lazy { VersionSeen(app.getSharedPreferences(VersionSeen.FILE_NAME, Context.MODE_PRIVATE)) }

    internal val usage: UsageSession = UsageSession(telemetry, SystemClock::elapsedRealtime, processStartElapsedMs(), isProcessInForeground())

    /** Lives as long as the process: scans and detection run here. A stray failure is logged, never thrown. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingHandler(log))

    /**
     * The configuration's first language, followed for the life of the process: the main screen's
     * A–Z sorts by it (the content outlives a recreated Activity), as Settings does through
     * `currentLocale()` ([localeOf] for both).
     */
    val locale: StateFlow<Locale> by lazy { followedLocale(app) }

    /** The one DataStore of the process; screens read [settings] instead. */
    val settingsStore: SettingsRepository by lazy { SettingsRepository.create(app) }

    /**
     * The settings, decoded once per change for every reader; null until the first read. Starts
     * with the graph, so the file is being read while the Activity is created. A failure other than
     * a read error (which the store retries itself) is logged and gives the defaults.
     */
    val settings: StateFlow<PlumSettings?> by lazy {
        settingsStore.settings
            .catch { e ->
                log("Settings failed (${e.javaClass.simpleName}); using defaults")
                emit(PlumSettings.Defaults)
            }.stateIn(appScope, SharingStarted.Eagerly, null).also { appScope.launch(io) { settingsStore.settleButtonSet(log) } }
    }

    /** What the window paints before the settings arrive; kept equal to the stored appearance. */
    val appearanceCache: AppearanceCache by lazy {
        AppearanceCache(app).also { cache ->
            appScope.launch(io) { settings.filterNotNull().map { it.appearance }.distinctUntilChanged().collect(cache::write) }
        }
    }

    /** The second-screen switch for `MainActivity.onCreate` (layout C's first frame); kept equal to the stored one. */
    internal val secondScreenSwitchCache: SecondScreenSwitchCache by lazy {
        SecondScreenSwitchCache(app).also { cache ->
            appScope.launch(io) { settings.filterNotNull().map { it.secondScreen.enabled }.distinctUntilChanged().collect(cache::write) }
        }
    }

    /** Tells the second screen of the Activity that is up when an emulator is started (`MainActivity` sets the target). */
    internal val gameStarts = GameStartRelay()

    /**
     * The battery measurement of the play in progress: read by the second screen's
     * in-game card while it is up, its figures stored with the play's session by the launcher.
     */
    internal val playBattery: PlayBatteryMeter by lazy { PlayBatteryMeter(batteryReader) }

    private val batteryReader: AndroidBatteryReader by lazy { AndroidBatteryReader(app) }

    /** A game's usual draw as a share of a full battery an hour, for the picking line. */
    internal val usualPace: UsualPace by lazy { UsualPace(batteryReader) }

    /**
     * Room opens the file on its own threads at the first query, never here. Through the gate:
     * a restore that a process death stopped puts the files back before the first open, and a
     * restore shuts the file while it swaps it.
     */
    val database: PlumDatabase by lazy { PlumDatabase.open(app, databaseGate) }

    private val databaseGate = DatabaseGate { restoreFiles.recoverMovingFiles() }

    // Not cached when they fail: Try again (and the next caller) builds them anew.
    private val catalogLoad = RetryingLazy { withContext(io) { guarded("platform data") { loadShippedCatalog(app) } } }
    private val launchingLoad = RetryingLazy {
        val catalog = catalog() ?: return@RetryingLazy null
        withContext(io) { guarded("launching") { Launching.create(app, database, catalog, gameStarts, playBattery) } }
    }
    private val scannerLoad = RetryingLazy {
        val catalog = catalog() ?: return@RetryingLazy null
        withContext(io) {
            guarded("library scanner") { LibraryScanner.forDevice(app, database, systemRulesOf(catalog), naming = naming) }
        }
    }

    /** The name packs and the identifier: one of each per process, shared by scans and Fix name. */
    internal val naming: GameNaming by lazy { gameNamingFor(app) }

    /** Name fixes: Fix name…'s lists, Info's "Name from", the writes, naming now, box art after a rename. */
    internal val nameFixes: GraphNameFixes by lazy { GraphNameFixes(this, io, renameBoxArt = renameBoxArtFor(app, this, { libraryScrapeSource }, { scraperPauseStore }, { File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER) }, io, log)) }

    /** The platform data, read once it works; null while it cannot be read (logged). */
    suspend fun catalog(): PlatformCatalog? = catalogLoad.get()

    /** Null when the platform data or the launcher could not be set up (logged). */
    suspend fun launching(): Launching? = launchingLoad.get()

    suspend fun scanner(): LibraryScanner? = scannerLoad.get()

    /** Package changes, kept for the process so a change while no Activity lived is still seen. */
    internal val packageChanges: PackageChanges by lazy {
        PackageChanges(app) { isTemporaryRemoval ->
            appIcons.invalidate().also { launchingLoad.peek()?.detector?.packagesChanged() }
            startup.markPackagesChanged().also { appScope.launch { refreshAppsLogged() } }
            appGames.onPackageChange(isTemporaryRemoval)
        }
    }

    /**
     * App icons for the Apps entry's grid, cached in memory. `PackageChanges` does not say which
     * package changed, so any change drops every cached icon (an update may change a launcher icon).
     */
    val appIcons: AppIcons by lazy { AppIcons.create(app) }

    private val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())

    /** The Apps entry's list as last read (what its rows were drawn from); opening a row looks it up here. */
    internal val listedApps: StateFlow<List<LaunchableApp>> = apps.asStateFlow()

    private val appReads = AppListReads(apps) { launching()?.apps?.list() }

    suspend fun refreshApps() = appReads.refresh()

    /** Installed apps as games: [GraphAppGames.marked], `setChoice` / `clearChoice`. */
    internal val appGames: GraphAppGames by lazy {
        GraphAppGames(dao = { database.appGameDao() }, catalog = ::catalog, readApps = { launching()?.apps?.list() }, presence = { launching()?.apps?.presenceOf(it) }, ownPackage = app.packageName, scope = appScope, io = io, log = { Log.i(APP_GAMES_TAG, it) })
    }

    /**
     * Pinned shortcuts as games: the confirm screen's offer and add, and the
     * availability that follows the apps (checked after each start and package change, then
     * followed live while Plumemu is the home app). Below Android 8.0 the host answers nothing.
     */
    val pinnedShortcuts: PinnedShortcuts by lazy {
        val name = app.getString(R.string.shortcuts_system_name)
        PinnedShortcuts(dao = { database.pinnedShortcutDao() }, catalog = ::catalog, host = shortcutHostFor(app), shortcutsName = name, scope = appScope, io = io, log = { Log.i(SHORTCUTS_TAG, it) })
    }

    /**
     * What the main screen shows; null until the first answer, [HomeState.Failed] when the library
     * cannot be read. Shared, and kept five seconds after the screen stops collecting, so a
     * rotation does not query again while a game that runs for hours keeps no query alive.
     */
    val homeContent: StateFlow<HomeState?> by lazy {
        val source = LibraryHomeContent(
            games = database.gameDao(),
            catalog = ::catalog,
            apps = apps,
            showApps = settings.filterNotNull().map { it.homeScreen.showApps }.distinctUntilChanged(),
            appGames = appGames.marked, log = log,
            sort = settings.filterNotNull().map { it.library.sortGames }.distinctUntilChanged(),
            recentSize = settings.filterNotNull().map { it.library.recentSize }.distinctUntilChanged(),
            leftEdge = settings.filterNotNull().map { it.appearance.leftEdge }.distinctUntilChanged(),
            // Settings › Library › System order and Systems: the column follows at once.
            arrangement = settings.filterNotNull().map { SystemArrangement.of(it.library) }.distinctUntilChanged(),
            locale = locale,
            // grid, details and years only with box art set up (credentials and an
            // account). The account file alone (not the pause read of `status`), read from the
            // moment the content is first asked for, alongside the library's queries.
            hasBoxArt = scraperAccounts.isSignedIn.stateIn(appScope, SharingStarted.Eagerly, null).filterNotNull(),
            // Each system's shape from the person's saved large box fronts, once
            // enough are stored (built on collection, like every Room read here).
            coverRatios = flow { emitAll(BoxFrontShapes.observe(database, ScrapeMediaSizes.Large.name)) },
            extraAbbrs = mapOf(ShortcutFiling.SHORTCUTS to app.getString(R.string.shortcuts_system_abbr)),
        )
        attempts.flatMapLatest { source.states }.stateIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    }

    private val attempts = MutableStateFlow(0)

    /**
     * The home screen's ScreenScraper results and their pictures: Room's
     * `scraped_game` for the rows shown, decoded by one Coil loader for the process (built when
     * the first cover is drawn).
     */
    internal val homeArt: HomeArtSource by lazy {
        LibraryHomeArt(
            scraped = { database.scrapedGameDao() },
            images = CoilCoverImages(app, { CoilCoverImages.imageLoader(app, isLowRam = CoilCoverImages.isLowRamDevice(app)) }),
            log = log,
        )
    }

    /** The person's Try again: reads the library (and the platform data, if it failed) once more. */
    fun retryHomeContent() {
        attempts.value++
    }

    val libraryNotices = LibraryNotices() // what change checks found, for the home (memory only)
    val startup: StartupWork by lazy { StartupWork(appScope, settings, GraphStartupTasks(), onScanEnded = ::trackScan, onChangesFound = libraryNotices::checked) }

    /** In-app updates: checked from [startup]'s front hook and Settings › About. */
    internal val updates: Updates by lazy { graphUpdates(app, settings, appScope, io, telemetry) }

    /** Settings' writes, in order (see [SettingsWriter]). */
    internal val settingsWriter: SettingsWriter by lazy { SettingsWriter(appScope, settingsStore::update, log) }

    /**
     * Settings › Advanced › Reset library: the database forgets the games and what the person
     * attached to them, keeps the picked folders (and so the grants Android holds for them; nothing
     * here releases a grant), and every folder is scanned again ([StartupWork.resetLibrary]).
     */
    fun resetLibrary(): Job = startup.resetLibrary { withContext(io) { database.scanDao().forgetLibrary() }.also { appGames.requestSync() } }

    /**
     * Settings › Advanced › Back up…: one backup at a
     * time in [appScope], so it goes on when Settings closes. Built on first use; reads nothing until then.
     */
    internal val backup: GraphBackup by lazy {
        GraphBackup(
            // One read that throws on a read error: a backup never carries defaults or "signed out" unseen.
            settings = { settingsStore.readOnce() },
            account = { scraperAccounts.forBackup() },
            database = { database },
            mediaDir = { File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER) },
            workDir = { File(app.cacheDir, BACKUP_WORK_FOLDER) },
            markers = FileBackupMarkers(File(app.noBackupFilesDir, BACKUP_MARKER_FILE), log),
            outputFor = { id -> BackupDocument(app.contentResolver, Uri.parse(id), log) },
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            scope = appScope,
            io = io,
            log = log,
            mayStart = { !restore.isReplacing },
            onEnded = { kind, end -> backupEventOf(kind, end)?.let { telemetry?.track(it) } },
        )
    }

    private val restoreFiles: RestoreFiles by lazy {
        RestoreFiles(RestorePlaces(app.getDatabasePath(PlumDatabase.FILE_NAME), File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER), app.noBackupFilesDir), log)
    }

    /**
     * Settings › Advanced › Restore… and Undo last restore: one at a time in [appScope]. After a swap of the library the app restarts
     * (relaunchApp, in RestoreWiring.kt): every part of the graph that holds the database then opens the new file.
     */
    internal val restore: GraphRestore by lazy {
        GraphRestore(
            files = restoreFiles,
            appSchemaVersion = PlumDatabase.SCHEMA_VERSION,
            readSettings = { settingsStore.readOnce() },
            writeSettings = { transform -> settingsStore.update(transform) },
            accounts = scraperAccounts,
            prepareDatabase = { file, fromRoot, toRoot -> RestoredDatabase.prepare(app, file, fromRoot, toRoot) },
            closeDatabase = {
                withContext(io) { waitForBoxArtToStop(app, QUIET_TIMEOUT_MS) && databaseGate.closeWhenQuiet(database, QUIET_TIMEOUT_MS) }
            },
            isBackupRunning = { backup.state.value is BackupState.Running },
            isScanning = { startup.scanState.value.isBusy },
            holdScans = { startup.holdScansForRestore() },
            releaseScans = { startup.releaseScansAfterRestore() },
            stopBoxArt = { scrapes.cancelRuns() },
            relaunch = { relaunchApp(app, log) },
            onResult = { result -> restoreEventOf(result)?.let { telemetry?.track(it) } },
            scope = appScope,
            io = io,
            log = log,
        )
    }

    /** The document the save picker made for a backup. */
    internal fun backupDocument(uri: Uri): BackupOutput = BackupDocument(app.contentResolver, uri, log)

    /** The file the open picker gave for a restore; read through the resolver (no permission: the picker grants it). */
    internal fun restoreInput(uri: Uri): RestoreInput = RestoreInput { app.contentResolver.openInputStream(uri) ?: throw java.io.IOException("the picked file gave no stream") }

    /** Settings › Library and Emulators: folders, scan, emulator choices (one per process). */
    internal val settingsLibrary: GraphSettingsLibrary by lazy { GraphSettingsLibrary(this, app, io) }

    /** What Settings needs from the graph; [activity] adds the picker and the home-app screens. */
    internal fun settingsHost(
        versionName: String,
        activity: SettingsActivityActions,
    ): SettingsHost =
        SettingsHost(
            versionName = versionName,
            // What a Settings write changed is counted once it has landed (setting_changed, GraphUsage.kt).
            update = { transform -> settingsWriter.submitWatched(transform, ::trackSettingChanges) },
            resetAllSettings = { settingsWriter.resetAll(onPauseCleared = { scrapes.requestRun() }) },
            resetLibrary = { resetLibrary() },
            libraryReset = startup.libraryReset,
            saveFailed = settingsWriter.failed,
            content =
                combine(settingsLibrary.content, secondScreens.current, settings, scraperAccounts.status, scrapes.statusWithLimits) { content, screen, stored, account, boxArt ->
                    content.withSecondScreen(screen, stored?.secondScreen ?: SecondScreenSettings())
                        .copy(account = account, boxArt = boxArt.first, accountLimits = boxArt.second)
                }.combine(backup.state) { content, backupState -> content.copy(backup = backupState) }
                    .combine(combine(restore.state, restore.wayBack, ::Pair)) { content, (restoreState, wayBack) -> content.copy(restore = restoreState, wayBack = wayBack) }
                    .combine(updates.view) { content, update -> content.copy(update = update) }
                    .combine(boxArtFiles.facts) { content, facts -> content.copy(boxArtBytes = facts.bytes, boxArtGivenUp = facts.givenUp) },
            folderAdd = settingsLibrary.folderAdd,
            library = settingsLibrary,
            names = settingsLibrary.names,
            accounts = scraperAccounts,
            boxArt = scrapes.settingsActions,
            recent = settingsRecentActions(),
            activity = activity,
            statistics =
                GraphStatistics(
                    plays = { database.playDao() },
                    scraped = { database.scrapedGameDao() },
                    catalog = ::catalog,
                    hasBoxArt = { scraperAccounts.isSignedIn.first() },
                    art = homeArt,
                    io = io,
                    log = log,
                    arcadeGames = arcadeGamesOf { naming },
                ),
            backupWork = backupWorkOf(backup),
            restoreWork = restoreWorkOf(restore),
            updates = settingsUpdateActions(updates),
            problemReports = problemReportActionsOf(problemReports, appScope, Dispatchers.Main.immediate),
        )

    /**
     * The device's second screen, if any; `MainActivity` starts it in `onCreate` and
     * stops it in `onDestroy`. Built on the first ask: nothing is read until then.
     */
    internal val secondScreens: SecondScreenDisplays by lazy { SecondScreenDisplays(AndroidDisplaySource(app)) }

    /** First-run setup's state, kept for the process so leaving the app keeps the step. */
    internal val setup: SetupModel by lazy { SetupModel(appScope, GraphSetupSource(this, app, io), accounts = scraperAccounts, telemetry = telemetry) }

    /**
     * The person's ScreenScraper account: setup's and Settings' sign-in, the stored
     * account, and the [com.plumemu.scraper.Scraper] made for it. Each part is built on first use,
     * off the main thread (the account file, the Keystore, the pause file).
     */
    internal val scraperAccounts: GraphScraperAccounts by lazy {
        GraphScraperAccounts(
            isAvailable = DeveloperCredentials.buildHasCredentials,
            store = { scraperAccountStore },
            cipher = { passwordCipher },
            makeScraper = { user -> Scraper.create(ScraperConfig(cacheDir = app.cacheDir, user = user, pauseStore = scraperPauseStore)) },
            pauseStore = { scraperPauseStore },
            scope = appScope,
            io = io,
            main = Dispatchers.Main,
            log = log,
            onSignedIn = { scrapes.requestRun() },
            onSignedOut = { scrapes.cancelRuns() },
        )
    }

    /**
     * The ScreenScraper run: what `PlumApplication` gives the worker, the requests
     * for a run (after a scan, after a sign-in) and the run's state. Built on first use; reads
     * nothing until a run or Settings asks.
     */
    internal val scrapes: GraphScrapes by lazy {
        GraphScrapes(
            accounts = scraperAccounts,
            librarySource = { libraryScrapeSource },
            // Not backed up: pictures are large and the next run downloads them again.
            mediaDir = { File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER) },
            enqueue = { ScrapeScheduler.enqueue(app, wifiOnly = true) },
            cancel = { ScrapeScheduler.cancel(app) },
            inputs = deviceScrapeStatusInputs(app, { database }, { scraperPauseStore }),
            retryMark = { scrapeRetryMark },
            scope = appScope,
            io = io,
            log = log,
            // Read from the store itself: a worker started right after Resume must see the write.
            paused = settingsStore.settings.map { it.library.boxArtPaused }.distinctUntilChanged(),
            writePaused = { isPaused -> settingsStore.update { it.copy(library = it.library.copy(boxArtPaused = isPaused)) } },
            clearBoxArt = { onDone -> boxArtFiles.clear(onDone) },
            holdForPlay = { content.held.holds(HeldWork.BoxArt) },
        )
    }

    /** The saved box-art pictures: their size, Clear box art and the daily sweep ([GraphBoxArt]). */
    internal val boxArtFiles: GraphBoxArt by lazy { graphBoxArt(app, this, { File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER) }, io, log) }

    /** The background content pass (whole-file CRC of loose cartridges); see [GraphContent]. */
    internal val content: GraphContent by lazy { graphContentFor(app, { scanner() }, { scrapes.requestRun() }, log, { Log.i(TAG, it) }, playingAway({ startup.isInFront }) { launching()?.playTime?.isPending() == true }) }

    /**
     * Fix box art… in the game menu, wired by [boxArtFixesFor]. Thumbnails go to the
     * cache: Android may clear it, a later search downloads them again.
     */
    internal val boxArtFixes: GraphBoxArtFixes by lazy {
        boxArtFixesFor(app, this, { libraryScrapeSource }, { File(app.noBackupFilesDir, SCRAPE_MEDIA_FOLDER) }, { File(app.cacheDir, BOX_ART_THUMBNAIL_FOLDER) }, io, log)
    }

    /** The box-art run as Settings › Library shows it; see [ScrapeStatus]. */
    internal val scrapeStatus: Flow<ScrapeStatus> get() = scrapes.status
    /** The system the person is viewing; the home screen sets it, the run asks its games early. */
    internal val viewedSystem: MutableStateFlow<String?> get() = scrapes.viewedSystem

    // One per process: it remembers the games it could not store.
    private val libraryScrapeSource: LibraryScrapeSource by lazy {
        LibraryScrapeSource.forDevice(
            context = app,
            database = database,
            currentSystemId = { scrapes.viewedSystem.value },
            preferredRegion = { settings.filterNotNull().first().library.boxArtRegion.code },
        )
    }

    private val scrapeRetryMark: RetryMark by lazy { SharedPreferencesRetryMark(app, log) }
    private val scraperAccountStore: ScraperAccountStore by lazy { ScraperAccountStore.create(app) }
    private val passwordCipher: PasswordCipher by lazy { KeystorePasswordCipher() }
    private val scraperPauseStore: PauseStore by lazy { SharedPreferencesPauseStore(app) }

    private inner class GraphStartupTasks : StartupTasks {
        override suspend fun refreshApps() = this@AppGraph.refreshApps()
        override suspend fun syncAppGames() = appGames.syncNow().let {}

        override suspend fun detectEmulators() = catalog()?.let { launching()?.detector?.detect(it) }.let {}

        override suspend fun refreshDefaults() {
            val launching = launching() ?: return
            val installed = launching.detector.installed.value ?: return
            val systems = database.gameDao().observeSystemsWithGames().first().map { it.id }
            launching.choices.refreshDefaults(systems, installed)
        }

        override suspend fun setRescanAfterReset(pending: Boolean) =
            settingsStore.update { it.copy(flags = it.flags.copy(rescanAfterReset = pending)) }

        override suspend fun scanEnded() {
            scrapes.requestRun()
            content.scanEnded()
            appScope.launch(io) { boxArtFiles.sweepIfDue() }
        }

        override suspend fun checkPinnedShortcuts() = pinnedShortcuts.checkAvailability()

        override fun frontReturned() = updates.onFront()

        override fun frontLeft() = updates.onStop()

        override suspend fun recoverInterruptedBackup() = withContext(io) { backup.recoverInterrupted() }

        override suspend fun finishRestore(): Boolean = withContext(io) { restore.onStartup() }

        override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
            val scanner = scanner() ?: return false
            markRowsForNewRules(scanner)
            // Folders matched by older rules are unchanged on disk and would be skipped: list them all once.
            val stored = settings.value?.flags?.folderMatchVersion ?: FOLDER_MATCH_VERSION
            val rescannedAll = scanForFolderRules(stored, scanner::scan, scanner::rescanEverything, onEvent)
            // Its own step: a failed settings write must not turn a finished scan into a failed one.
            if (rescannedAll) {
                guarded("folder match version") {
                    settingsStore.update { it.copy(flags = it.flags.copy(folderMatchVersion = FOLDER_MATCH_VERSION)) }
                }
            }
            return true
        }

        override suspend fun changedFolders(onlyFolderIds: Set<Long>?) = scanner()?.changedFolders(onlyFolderIds) ?: super.changedFolders(onlyFolderIds)

        override suspend fun scanFolders(folderIds: Set<Long>, onEvent: (ScanEvent) -> Unit): Boolean =
            scanner()?.let { it.scan(folderIds).collect(onEvent); true } ?: false
    }

    internal suspend fun <T> guarded(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Could not set up the $what (${e.javaClass.simpleName})")
            null
        }

    private companion object {
        const val TAG = "PlumGraph"
        const val SHORTCUTS_TAG = "PlumShortcuts"
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long a restore waits for the box-art run and a write in flight before it refuses. */
        const val QUIET_TIMEOUT_MS = 10_000L

        /** Under `noBackupFilesDir`: `<ScreenScraper game id>/box-front-<size>-<w>x<h>.webp`, `support-...`. */
        const val SCRAPE_MEDIA_FOLDER = "screenscraper-media"

        /** Under `cacheDir`: the database copy a backup is taken from, deleted when the backup ends. */
        const val BACKUP_WORK_FOLDER = "backup"

        /** Under `noBackupFilesDir`: the running backup's document, removed at its end ([BackupMarker]). */
        const val BACKUP_MARKER_FILE = "backup-in-progress.json"

        /** Under `cacheDir`: Fix box art's small box fronts, one file per game and picture. */
        const val BOX_ART_THUMBNAIL_FOLDER = "box-art-thumbnails"
    }
}

/** Logs an uncaught failure of the app's scope by class instead of crashing. */
internal fun loggingHandler(log: (String) -> Unit): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, e -> log("Background work failed (${e.javaClass.simpleName})") }
