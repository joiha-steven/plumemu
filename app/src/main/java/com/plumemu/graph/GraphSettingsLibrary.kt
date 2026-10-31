package com.plumemu.graph

import com.plumemu.telemetry.PickPlace
import com.plumemu.telemetry.ScanTrigger
import com.plumemu.telemetry.TelemetryEvent
import android.content.Context
import android.util.Log
import com.plumemu.coredata.db.AnsweredFolderRow
import com.plumemu.coredata.db.FolderGameCount
import com.plumemu.coredata.db.UnknownFolderRow
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.SystemGameCount
import com.plumemu.game.EmulatorItem
import com.plumemu.game.automaticChoice
import com.plumemu.launcher.detect.InstalledEmulators
import com.plumemu.library.scan.AddFolderResult
import com.plumemu.library.scan.AndroidFolderPermissions
import com.plumemu.library.scan.FolderPermissions
import com.plumemu.library.scan.RomFolders
import com.plumemu.library.scan.SubfolderListing
import com.plumemu.platforms.Emulator
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.settings.screen.EmulatorEntry
import com.plumemu.settings.screen.FolderAnswer
import com.plumemu.settings.screen.FolderKey
import com.plumemu.settings.screen.FolderSystemChoice
import com.plumemu.settings.screen.SettingsFolderRow
import com.plumemu.settings.screen.FolderAddState
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsGame
import com.plumemu.settings.screen.SettingsLibraryActions
import com.plumemu.settings.screen.SystemEmulators
import com.plumemu.setup.SetupFolder
import com.plumemu.setup.folderNameOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Settings › Library and Emulators on the app graph: the folders with their counts
 * and grants, the scan, each system's emulators resolved in the launch's order (`automaticChoice`,
 * the same as the game menu's), and the work Settings asks for. Work runs in the process's scope,
 * so leaving Settings does not stop it; answers go back on [main]. Logs carry ids and classes only,
 * never names, paths or URIs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GraphSettingsLibrary(
    private val graph: AppGraph,
    context: Context,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val permissions: FolderPermissions = AndroidFolderPermissions(context.applicationContext.contentResolver),
    private val log: (String) -> Unit = { Log.w(TAG, it) },
    private val info: (String) -> Unit = { Log.i(TAG, it) },
) : SettingsLibraryActions {
    private val romFolders by lazy { RomFolders(graph.database.scanDao(), permissions) }

    /** Hidden and Unidentified games, Fix name…. */
    val names: GraphSettingsNames by lazy { GraphSettingsNames(graph, io, main) }

    /** Answers for unknown folders, stored and applied in the process's scope so closing Settings loses none. */
    private val folderAnswers by lazy {
        FolderAnswers<String?>(
            scope = graph.appScope,
            main = main,
            store = { key, systemId ->
                val scanner = checkNotNull(graph.scanner()) { "the scanner is not set up" }
                scanner.chooseFolderSystem(key.folderId, key.relativePath, systemId)
            },
            scanAfter = { graph.startup.requestScan(ScanTrigger.Manual) },
            log = log,
        )
    }

    /** Skipped sub-folders, stored and scanned in the process's scope like the answers above. */
    private val skips by lazy {
        FolderAnswers<Boolean>(
            scope = graph.appScope,
            main = main,
            store = { key, skip ->
                val scanner = checkNotNull(graph.scanner()) { "the scanner is not set up" }
                scanner.skipSubfolder(key.folderId, key.relativePath, skip)
            },
            scanAfter = { graph.startup.requestScan(ScanTrigger.Manual) },
            log = log,
        )
    }

    /** Folders whose answer names a system the platform data no longer has, warned about once each. */
    private val warnedStale = HashSet<FolderKey>()

    /** Bumped when a grant may have changed (a pick, a removal): no table says so. */
    private val grantsChanged = MutableStateFlow(0)

    /** Bumped after a choice or a detection: choices are read, not observed. */
    private val choicesChanged = MutableStateFlow(0)

    private val mutableFolderAdd = MutableStateFlow(FolderAddState.Idle)

    /** The last folder picked from Settings; back to idle when its scan ends or the person leaves ROM folders. */
    val folderAdd: StateFlow<FolderAddState> = mutableFolderAdd.asStateFlow()

    /** Folders whose removal was asked for and has not ended, for every Settings of the process. */
    private val removing = MutableStateFlow<Set<Long>>(emptySet())

    private val detecting = MutableStateFlow(false)

    /** The running removal of each folder and the running detection; a repeated ask joins it. */
    private val runningLock = Any()
    private val removals = HashMap<Long, Deferred<Boolean>>()
    private var detection: Deferred<Boolean>? = null

    /** A detection runs (one at a time in the process). */
    val isDetecting: StateFlow<Boolean> = detecting.asStateFlow()

    private val installed: Flow<InstalledEmulators?> = flow { emitAll(graph.launching()?.detector?.installed ?: flowOf(null)) }

    /** What Settings shows; [SettingsContent.isHomeApp] is the screen's to ask. */
    val content: Flow<SettingsContent> =
        // Built on collection, so a database that cannot open fails inside the flow (caught below), not here.
        flow { emitAll(liveContent()) }
            .flowOn(io)
            .catch { e ->
                // Settings shows a plain line; opening Settings again collects (and so reads) anew.
                if (e is CancellationException) throw e
                log("Settings: library content failed (${e.javaClass.simpleName})")
                emit(SettingsContent(loadFailed = true))
            }.distinctUntilChanged()

    private fun liveContent(): Flow<SettingsContent> {
        val dao = graph.database.scanDao()
        val games = graph.database.gameDao()
        val folders =
            combine(dao.observeFolders(), games.observeFolderCounts(), grantsChanged, graph.startup.scanState) { rows, counts, _, scan ->
                settingsFolders(rows, counts, permissions::isHeld, scan.unavailableFolderIds)
            }
        val systems = combine(games.observeSystemsWithGames(), installed, choicesChanged) { rows, found, _ -> Triple(rows, found, systemsOf(rows, found)) }
        val shown =
            combine(folders, graph.startup.scanState, systems, removing, detecting) { folderList, scan, (rows, found, emulators), gone, checking ->
                SettingsContent(
                    folders = folderList,
                    isScanning = scan.isBusy,
                    gameCount = rows.sumOf { it.gameCount },
                    systemCount = rows.size,
                    systems = emulators,
                    librarySystems = librarySystemsOf(graph.catalog(), rows),
                    emulatorAppCount = found?.packages?.size,
                    removingFolderIds = gone,
                    isDetecting = checking,
                )
            }
        // Hidden games and Unidentified games, with each game's system named as the platform data names it.
        val withNames =
            combine(shown, names.hiddenGames(), names.unidentifiedGames(), graph.startup.scanReport) { content, hidden, unidentified, report ->
                content.copy(hiddenGames = hidden, unidentifiedGames = unidentified, scan = report)
            }
        val folderSystems = graph.database.folderSystemDao()
        val unknown = combine(folderSystems.observeUnknownFolders(), folderSystems.observeAnsweredFolders(), folderAnswers.saving) { open, answered, saving -> Triple(open, answered, saving) }
        val skipped = combine(graph.database.skippedSubfolderDao().observeAll(), skips.saving) { rows, saving -> rows to saving }
        return combine(withNames, unknown, skipped) { content, (open, answered, saving), (skippedRows, skipSaving) ->
            val catalog = graph.catalog()
            content.copy(
                skippedSubfolders = skippedRows.groupBy({ it.folderId }, { it.relativePath }),
                skippingFolders = skipSaving,
                unknownFolders = settingsUnknownFolders(open, answered, catalog, ::warnStaleAnswer),
                folderSystemChoices = folderSystemChoicesOf(catalog),
                answeringFolders = saving,
            )
        }
    }

    private fun warnStaleAnswer(key: FolderKey, systemId: String) {
        val first = synchronized(warnedStale) { warnedStale.add(key) }
        if (first) log("Settings: folder ${key.folderId} is answered with system $systemId, which the platform data no longer has; listed as unknown again")
    }

    override fun chooseFolderSystem(
        folderId: Long,
        relativePath: String,
        systemId: String?,
        onDone: (Boolean) -> Unit,
    ) = folderAnswers.answer(FolderKey(folderId, relativePath), systemId, onDone)

    override fun skipSubfolder(
        folderId: Long,
        relativePath: String,
        skip: Boolean,
        onDone: (Boolean) -> Unit,
    ) = skips.answer(FolderKey(folderId, relativePath), skip, onDone)

    /** Read off the main thread by the scanner; null (said on the page) when the folder or the scanner cannot be read. */
    override fun listSubfolders(
        folderId: Long,
        onDone: (List<String>?) -> Unit,
    ) {
        graph.appScope.launch {
            val names =
                try {
                    when (val listing = checkNotNull(graph.scanner()) { "the scanner is not set up" }.subfolders(folderId)) {
                        is SubfolderListing.Listed -> listing.names
                        SubfolderListing.Unreadable, SubfolderListing.NoFolder -> null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Settings: sub-folders of folder $folderId not listed (${e.javaClass.simpleName})")
                    null
                }
            withContext(main) { onDone(names) }
        }
    }

    /** The game's lists on the main screen follow the database at once (Room flows). */
    override fun unhideGame(
        gameId: Long,
        onDone: (Boolean) -> Unit,
    ) = names.unhideGame(gameId, onDone)

    /** Android's picker answered a pick Settings asked for: the folder is kept, recorded and read. */
    fun onPicked(treeUri: String?) {
        if (treeUri == null) {
            mutableFolderAdd.value = FolderAddState.Idle
            return
        }
        mutableFolderAdd.value = FolderAddState.Adding
        graph.appScope.launch {
            val answer =
                try {
                    withContext(io) { addPicked(treeUri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Settings: picked folder could not be added (${e.javaClass.simpleName})")
                    FolderAddState.CouldNotOpen
                } finally {
                    grantsChanged.update { it + 1 }
                }
            mutableFolderAdd.value = answer
            if (answer == FolderAddState.Added) {
                // "Reading its games now" is true until the scan it asked for ends (requestScan marked it busy).
                graph.startup.scanState.first { !it.isBusy }
                mutableFolderAdd.compareAndSet(FolderAddState.Added, FolderAddState.Idle)
            }
        }
    }

    private suspend fun addPicked(treeUri: String): FolderAddState {
        val wasHeld = permissions.isHeld(treeUri)
        return when (val added = romFolders.addPickedFolder(treeUri, folderNameOf(treeUri))) {
            is AddFolderResult.Added -> {
                graph.startup.requestScan(ScanTrigger.FolderAdded)
                FolderAddState.Added
            }
            is AddFolderResult.AlreadyAdded -> {
                if (wasHeld) {
                    FolderAddState.AlreadyAdded
                } else {
                    // Picked again after its grant was lost: read it again (setup does the same).
                    log("Settings: folder ${added.folderId} picked again; reading it")
                    graph.startup.requestScan(ScanTrigger.FolderAdded)
                    FolderAddState.Added
                }
            }
            AddFolderResult.PermissionNotGranted -> FolderAddState.NotKept
            AddFolderResult.NotRecorded -> FolderAddState.CouldNotOpen
        }
    }

    override fun startFolderPick() {
        mutableFolderAdd.value = FolderAddState.Idle
    }

    override fun forgetFolderAnswer() {
        mutableFolderAdd.value = FolderAddState.Idle
    }

    override fun removeFolder(
        folderId: Long,
        onDone: (Boolean) -> Unit,
    ) {
        // A second ask for the same folder (another Settings, or one opened again) does not remove
        // twice: it waits for the running removal and gets its outcome, so its caller never sticks.
        val (removal, joined) =
            synchronized(runningLock) {
                removals[folderId]?.let { return@synchronized it to true }
                val started = graph.appScope.async(start = CoroutineStart.LAZY) { runRemoval(folderId) }
                removals[folderId] = started
                removing.update { it + folderId }
                started to false
            }
        if (joined) info("Settings: folder $folderId is already being removed") else removal.start()
        graph.appScope.launch {
            val removed = removal.await()
            withContext(main) { onDone(removed) }
        }
    }

    private suspend fun runRemoval(folderId: Long): Boolean =
        try {
            graph.startup.removeFolder {
                        withContext(io) {
                            // Only this folder's row (its games go with it) and only its grant.
                            val folder = graph.database.scanDao().folders().firstOrNull { it.id == folderId }
                            if (folder == null) {
                                info("Settings: no folder $folderId to remove")
                            } else {
                                romFolders.removeFolder(folder)
                                info("Settings: folder $folderId removed")
                            }
                        }
                    }.await()
        } finally {
            synchronized(runningLock) {
                removals.remove(folderId)
                removing.update { it - folderId }
            }
            grantsChanged.update { it + 1 }
        }

    override fun forgetScanResult() = graph.startup.clearScanResult()

    override fun rescanLibrary(): Boolean {
        if (graph.startup.scanState.value.isBusy) return false
        graph.startup.requestScan(ScanTrigger.Manual)
        return true
    }

    override fun chooseEmulator(
        systemId: String,
        emulatorId: String?,
        onDone: (Boolean) -> Unit,
    ) {
        graph.appScope.launch {
            val stored =
                attempt("emulator choice") {
                    val launching = checkNotNull(graph.launching()) { "launching is not set up" }
                    val system = checkNotNull(graph.catalog()?.system(systemId)) { "system $systemId is unknown" }
                    if (emulatorId == null) {
                        launching.choices.forgetSystemChoice(system, launching.detector.installed.value)
                    } else {
                        launching.choices.chooseForSystem(system, emulatorId)
                    }
                }
            if (stored && emulatorId != null) {
                graph.trackForSystem(systemId, emulatorId) { system, emulator -> emulator?.let { TelemetryEvent.EmulatorPicked(system, it, PickPlace.Settings) } }
            }
            choicesChanged.update { it + 1 }
            withContext(main) { onDone(stored) }
        }
    }

    override fun detectEmulators(onDone: (Boolean) -> Unit) {
        // One at a time for the process: a second ask (a Settings opened again) joins the running one.
        val (run, joined) =
            synchronized(runningLock) {
                detection?.let { return@synchronized it to true }
                val started = graph.appScope.async(start = CoroutineStart.LAZY) { runDetection() }
                detection = started
                detecting.value = true
                started to false
            }
        if (!joined) run.start()
        graph.appScope.launch {
            val detected = run.await()
            withContext(main) { onDone(detected) }
        }
    }

    private suspend fun runDetection(): Boolean =
        try {
            attempt("emulator detection") {
                val catalog = checkNotNull(graph.catalog()) { "platform data is not read" }
                val launching = checkNotNull(graph.launching()) { "launching is not set up" }
                val found = launching.detector.detect(catalog)
                val systems = graph.database.gameDao().observeSystemsWithGames().first().map { it.id }
                launching.choices.refreshDefaults(systems, found)
            }
        } finally {
            synchronized(runningLock) {
                detection = null
                detecting.value = false
            }
            choicesChanged.update { it + 1 }
        }

    private suspend fun attempt(
        what: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            withContext(io) { block() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Settings: $what failed (${e.javaClass.simpleName})")
            false
        }

    private suspend fun systemsOf(
        rows: List<SystemGameCount>,
        found: InstalledEmulators?,
    ): List<SystemEmulators> {
        val catalog = graph.catalog() ?: return emptyList()
        val choices = graph.database.emulatorChoiceDao()
        val personChoices = rows.associate { row -> row.id to choices.systemChoice(row.id)?.takeIf { it.isPersonChoice }?.emulatorId }
        return systemEmulatorsOf(catalog, rows, found?.emulatorIds.orEmpty(), personChoices)
    }

    private companion object {
        const val TAG = "PlumSettingsLibrary"
    }
}

/**
 * Settings' folders: every recorded one, unreadable when Android no longer holds its grant or the
 * last scan could not read it; counted once a scan of it has ended.
 */
internal fun settingsFolders(
    folders: List<RomFolderEntity>,
    counts: List<FolderGameCount>,
    isHeld: (treeUri: String) -> Boolean,
    unreadable: Set<Long>,
): List<SetupFolder> {
    val byFolder = counts.associateBy(FolderGameCount::folderId)
    return folders.map { folder ->
        val count = byFolder[folder.id]
        SetupFolder(
            id = folder.id,
            treeUri = folder.treeUri,
            displayName = folder.displayName,
            gameCount = count?.gameCount ?: 0,
            systemCount = count?.systemCount ?: 0,
            isCounted = folder.lastScanFinishedAt != null,
            isUnavailable = folder.id in unreadable || !isHeld(folder.treeUri),
        )
    }
}

/**
 * The Emulators rows: systems with games the platform data knows, in its order, each with what
 * Automatic starts and the person's choice, resolved as the launch does ([automaticChoice]).
 */
internal fun systemEmulatorsOf(
    catalog: PlatformCatalog,
    rows: List<SystemGameCount>,
    installed: Set<String>,
    personChoices: Map<String, String?>,
): List<SystemEmulators> =
    // A system of installed apps (Android games) has no emulator to choose: not listed.
    rows.mapNotNull { row -> catalog.system(row.id) }.filterNot { it.fromInstalledApps }.sortedBy { it.order }.map { system ->
        val all = catalog.emulatorsOf(system).map(::itemOf)
        val defaults = catalog.defaultCandidates(system.id).map(::itemOf)
        val automatic = automaticChoice(null, all, defaults, installed)?.first
        val chosenId = personChoices[system.id]
        val chosen = chosenId?.let { automaticChoice(it, all, defaults, installed) }
        SystemEmulators(
            systemId = system.id,
            name = system.displayName,
            automatic = automatic?.let { entryOf(it, isInstalled = true) },
            chosen = chosen?.let { (item, isInstalled) -> entryOf(item, isInstalled) },
            installed = all.filter { it.id in installed }.map { entryOf(it, isInstalled = true) },
        )
    }

/**
 * Library › Systems' rows: every system with games, named as the main screen names it (the
 * platform data's `displayName`, else the stored name), with its maker and year for Maker & year.
 */
internal fun librarySystemsOf(
    catalog: PlatformCatalog?,
    rows: List<SystemGameCount>,
): List<LibrarySystem> =
    rows.map { row ->
        val system = catalog?.system(row.id)
        LibrarySystem(row.id, system?.displayName ?: row.name, row.gameCount, system?.maker, system?.year)
    }

private fun itemOf(emulator: Emulator) = EmulatorItem(emulator.id, emulator.name, emulator.primary.packageName, emulator.legalRisk)

private fun entryOf(
    item: EmulatorItem,
    isInstalled: Boolean,
) = EmulatorEntry(item.id, item.name, item.isLegalRisk, isInstalled)

/**
 * Library's unknown-folder rows: first the folders still waiting for an answer, then the answered ones. An answer
 * whose system the platform data no longer has is not used by the scan, so its folder is listed as waiting again
 * ([onStale] hears of it, to warn once); [catalog] null (not read yet) takes every answer as good.
 */
internal fun settingsUnknownFolders(
    unknown: List<UnknownFolderRow>,
    answered: List<AnsweredFolderRow>,
    catalog: PlatformCatalog?,
    onStale: (FolderKey, String) -> Unit = { _, _ -> },
): List<SettingsFolderRow> {
    val waiting = unknown.map { SettingsFolderRow(FolderKey(it.folderId, it.relativePath), it.files, FolderAnswer.Unanswered) }.toMutableList()
    val done = mutableListOf<SettingsFolderRow>()
    for (row in answered) {
        val key = FolderKey(row.folderId, row.relativePath)
        val answerId = row.systemId
        val system = answerId?.let { catalog?.system(it) }
        when {
            answerId == null -> done += SettingsFolderRow(key, 0, FolderAnswer.Ignored)
            catalog == null -> done += SettingsFolderRow(key, 0, FolderAnswer.OfSystem(answerId, answerId))
            system == null -> {
                onStale(key, answerId)
                waiting += SettingsFolderRow(key, row.files, FolderAnswer.Unanswered)
            }
            else -> done += SettingsFolderRow(key, 0, FolderAnswer.OfSystem(system.id, system.displayName))
        }
    }
    val byPlace = compareBy<SettingsFolderRow>({ it.key.folderId }, { it.key.relativePath })
    return waiting.sortedWith(byPlace) + done.sortedWith(byPlace)
}

/** The systems a folder can be answered with: every system with file extensions, named as the main screen names it, A to Z. */
internal fun folderSystemChoicesOf(
    catalog: PlatformCatalog?,
    locale: Locale = Locale.getDefault(),
): List<FolderSystemChoice> {
    val collator = Collator.getInstance(locale)
    return catalog?.systems.orEmpty().filter { it.extensions.isNotEmpty() }.map { FolderSystemChoice(it.id, it.displayName) }.sortedWith { a, b -> collator.compare(a.name, b.name) }
}
