package com.plumemu.game

import com.plumemu.graph.GraphRecent
import com.plumemu.graph.trackForGame
import com.plumemu.graph.trackForSystem
import com.plumemu.telemetry.FavoriteChange
import com.plumemu.telemetry.PickPlace
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.graph.trackLaunch
import com.plumemu.telemetry.LaunchFrom
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.plumemu.battery.batteryPaceOf
import com.plumemu.coredata.db.AppKind
import com.plumemu.graph.AppGameSystems
import com.plumemu.graph.AppGamesSyncOutcome
import com.plumemu.graph.AppGraph
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.launcher.AppGameProblem
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.Launching
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.ShortcutProblem
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.coredata.db.PinnedShortcutEntity
import com.plumemu.shortcuts.ShortcutHost
import com.plumemu.shortcuts.ShortcutStart
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.coredata.db.AddedSession
import com.plumemu.launcher.choice.EmulatorPick
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.IdentifyDao
import com.plumemu.library.naming.chooseCopy
import com.plumemu.library.naming.copiesOf
import com.plumemu.library.naming.copyToPlay
import com.plumemu.library.naming.setHiddenWithArcadeCopies
import com.plumemu.platforms.Emulator
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.PlatformCatalog
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [GameActions] on the app's graph: Room for the game's details and marks (the person's own data), the launcher for launches and emulator choices, the startup work for a rescan (never
 * two at once). Everything that reads or writes runs on [io].
 */
internal class AppGameActions(
    private val graph: AppGraph,
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Starts pinned shortcut games; null takes the graph's own when first needed (not at start-up), a fake in tests. */
    private val shortcutHost: ShortcutHost? = null,
    /** Starts an app game by its package; null takes the launcher's `InstalledApps.startGameApp`, a fake in tests. */
    private val appGameStarter: ((packageName: String) -> AppLaunchOutcome)? = null,
) : GameActions {
    private val app = context.applicationContext
    private val recent by lazy { GraphRecent(graph.database) }

    override suspend fun launch(
        gameKey: String,
        from: LaunchFrom,
    ): LaunchOutcome {
        val id = gameKey.toLongOrNull() ?: return LaunchOutcome.Failed(UNKNOWN_GAME_ID, FailureReason.GameNotInLibrary)
        var isAppStarted = false
        val outcome = launchOnly(id) { isAppStarted = true }
        graph.launching()?.let { forgetStaleDetection(it, outcome) }
        // Counted after the start, on the graph's scope: never in the way of the emulator. A pinned
        // shortcut or an app game is no game of the platform data: not counted (docs/conventions/telemetry.md).
        if (!isAppStarted) graph.trackLaunch(id, from, outcome)
        return outcome
    }

    /**
     * The start checks the emulator's package itself; when it found one gone that the last detection
     * lists as installed (removed while no change was seen), Play with… must not offer it again from
     * that list: the next listing detects afresh, once.
     */
    private fun forgetStaleDetection(
        launching: Launching,
        outcome: LaunchOutcome,
    ) {
        val listed = launching.detector.installed.value?.emulatorIds ?: return
        val stale =
            when (outcome) {
                is LaunchOutcome.EmulatorMissing -> outcome.emulator.emulatorId in listed
                is LaunchOutcome.NoEmulatorForSystem -> outcome.candidates.any { it.emulatorId in listed }
                is LaunchOutcome.Failed -> outcome.reason == FailureReason.ActivityNotFound
                else -> false
            }
        if (!stale) return
        Log.i(TAG, "Game ${outcome.gameId}: the start found an emulator gone that the last detection listed; detecting again next time")
        launching.detector.packagesChanged()
    }

    /** [onAppStarted]: the row is a pinned shortcut or an app game, started by Android rather than an emulator. */
    private suspend fun launchOnly(
        id: Long,
        onAppStarted: () -> Unit,
    ): LaunchOutcome {
        // Null when the platform data could not be read (AppGraph has logged why).
        val launching = graph.launching() ?: return LaunchOutcome.Failed(id, FailureReason.Internal)
        return withContext(io) {
            // A pinned shortcut has no file: the engine would ask for a Steam app and probe a
            // document that does not exist. Android starts it from this record.
            val shortcuts = graph.database.pinnedShortcutDao()
            val (shortcut, isShortcutRow) =
                try {
                    shortcuts.byGame(id).let { it to (it != null || shortcuts.isShortcutRow(id)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Game $id: shortcut record unreadable (${e.javaClass.simpleName})")
                    return@withContext LaunchOutcome.Failed(id, FailureReason.DatabaseError, e)
                }
            if (isShortcutRow) onAppStarted()
            if (shortcut != null) return@withContext startShortcut(id, shortcut, launching.playTime)
            if (isShortcutRow) {
                Log.e(TAG, "Game $id: a pinned shortcut row without its record; not started")
                return@withContext LaunchOutcome.Failed(id, FailureReason.Internal)
            }
            // An installed app listed as a game has no file either: started by its package.
            val appGames = graph.database.appGameDao()
            val (appPackage, isAppGameRow) =
                try {
                    appGames.byGame(id).let { it to (it != null || appGames.isAppGameRow(id)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Game $id: app game record unreadable (${e.javaClass.simpleName})")
                    return@withContext LaunchOutcome.Failed(id, FailureReason.DatabaseError, e)
                }
            if (isAppGameRow) onAppStarted()
            if (appPackage != null) return@withContext startAppGame(id, appPackage, launching)
            if (isAppGameRow) {
                Log.e(TAG, "Game $id: an app game row without its record; not started")
                return@withContext LaunchOutcome.Failed(id, FailureReason.Internal)
            }
            launching.launcher.launch(id, fileOf = graph.copyToOpen(id))
        }
    }

    /**
     * Starts a pinned shortcut game. The play is noted first, as
     * for an emulator (Android may kill Plumemu as soon as the app takes the memory), with the app's
     * package as its "emulator"; it is dropped again when Android refuses. No RetroArch core, so no
     * core question.
     */
    private suspend fun startShortcut(
        id: Long,
        shortcut: PinnedShortcutEntity,
        playTime: PlayTimeTracker,
    ): LaunchOutcome {
        val answer =
            try {
                // Noted and started as one step a cancel cannot split (PlayTimeTracker.handOff).
                playTime.handOff(id, emulatorId = shortcut.packageName) {
                    (shortcutHost ?: graph.pinnedShortcuts.host).startShortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // The host promises not to throw; a broken system service must still not crash the home app.
                Log.w(TAG, "Game $id: shortcut of ${shortcut.packageName} not started (${e.javaClass.simpleName})")
                ShortcutStart.Failed
            }
        if (answer == ShortcutStart.Started) {
            Log.i(TAG, "Game $id: pinned shortcut of ${shortcut.packageName} started")
            return LaunchOutcome.Started(id, shortcut.packageName, shortcut.packageName, ChoiceSource.PinnedShortcut, coreToConfirm = null)
        }
        playTime.cancelPending()
        val problem =
            when (answer) {
                ShortcutStart.NotHomeApp -> ShortcutProblem.NotHomeApp
                ShortcutStart.Unavailable -> ShortcutProblem.Unavailable
                else -> ShortcutProblem.StartFailed
            }
        Log.i(TAG, "Game $id: pinned shortcut of ${shortcut.packageName} not started ($problem)")
        return LaunchOutcome.ShortcutRefused(id, appLabelOf(shortcut.packageName), problem)
    }

    /**
     * Starts an installed app listed as a game, as a pinned
     * shortcut: the play is noted first with the package as its "emulator" and dropped again when the
     * app is gone or Android refuses. The message names the row's title: a gone app has no label.
     */
    private suspend fun startAppGame(
        id: Long,
        packageName: String,
        launching: Launching,
    ): LaunchOutcome {
        val answer =
            try {
                // Noted and started as one step a cancel cannot split (PlayTimeTracker.handOff).
                launching.playTime.handOff(id, emulatorId = packageName) { (appGameStarter ?: launching.apps::startGameApp)(packageName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // startGameApp promises not to throw; a broken system service must still not crash the home app.
                AppLaunchOutcome.Failed(e)
            }
        if (answer == AppLaunchOutcome.Started) {
            Log.i(TAG, "Game $id: app game $packageName started")
            return LaunchOutcome.Started(id, packageName, packageName, ChoiceSource.InstalledApp, coreToConfirm = null)
        }
        launching.playTime.cancelPending()
        val problem = if (answer == AppLaunchOutcome.Missing) AppGameProblem.NotInstalled else AppGameProblem.StartFailed
        Log.i(TAG, "Game $id: app game $packageName not started ($problem)")
        val title =
            try {
                graph.database.gameDao().game(id)?.title
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Game $id: title unreadable (${e.javaClass.simpleName})")
                null
            }
        return LaunchOutcome.AppGameRefused(id, title?.takeIf { it.isNotBlank() } ?: packageName, problem)
    }

    /** The label of the app that published a shortcut, for the message; null when it is gone or cannot be read. */
    private fun appLabelOf(packageName: String): String? =
        try {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString().takeIf { it.isNotBlank() }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }

    override suspend fun facts(gameKey: String): GameFacts? =
        withContext(io) {
            val id = gameKey.toLongOrNull() ?: return@withContext null
            val database = graph.database
            val game = database.gameDao().game(id) ?: return@withContext null
            val folder = database.scanDao().folders().firstOrNull { it.id == game.folderId }
            val play = database.playDao().play(id)
            val isShortcut = database.pinnedShortcutDao().byGame(id) != null
            val isAppGame = database.appGameDao().isAppGameRow(id)
            val catalog = graph.catalog()
            val system = catalog?.system(game.systemId)
            // An arcade set's versions come from the arcade pack: read once the menu shows (versions()).
            val canFixBoxArt = !isShortcut && !isAppGame && graph.boxArtFixes.isOffered()
            val versionsFromPack = !isShortcut && !isAppGame && game.idMethod == IdentifyDao.ARCADE_NAME && ArcadeSystems.isArcade(game.systemId)
            GameFacts(
                title = game.title,
                systemName = system?.displayName ?: game.systemId,
                regions = game.regions,
                fileName = game.fileName,
                sizeBytes = game.sizeBytes,
                folderName = listOfNotNull(folder?.displayName, game.relativePath.substringBeforeLast('/', "").ifEmpty { null }).joinToString("/"),
                // The emulator of the file A really opens: a chosen copy may be another file type.
                emulator = if (isAppGame) null else system?.let { emulatorToStart(id, it, graph.fileNameForEmulator(id, it, game.fileName)) },
                lastPlayedAt = play?.lastPlayedAt,
                totalPlaySeconds = play?.totalSeconds ?: 0,
                isFavorite = game.isFavorite,
                batteryPace = graph.batteryPaceOf(id),
                name = graph.nameFixes.nameFacts(id),
                canFixBoxArt = canFixBoxArt,
                isShortcut = isShortcut,
                isAppGame = isAppGame,
                isPortApp = isAppGame && game.systemId == AppGameSystems.PORTS,
                isArcade = !isShortcut && !isAppGame && ArcadeSystems.isArcade(game.systemId),
                versions = if (isShortcut || isAppGame || versionsFromPack) null else copiesOf(database, null, id)?.let(::versionListingOf),
                versionsPending = versionsFromPack,
                // The signed-in condition of Fix box art: without an account Info is as before.
                scraped = if (canFixBoxArt) readScrapedFacts(database.scrapedGameDao(), FixKeys.forGame(id, game.fixKeys).first()) else null,
            )
        }

    override suspend fun emulators(gameKey: String): EmulatorListing? =
        withContext(io) {
            val id = gameKey.toLongOrNull() ?: return@withContext null
            val game = graph.database.gameDao().game(id) ?: return@withContext null
            val catalog = graph.catalog()
            val system = catalog?.system(game.systemId)
            val launching = graph.launching()
            if (catalog == null || system == null || launching == null) {
                return@withContext EmulatorListing(game.systemId, null, emptyList(), emptyList(), null)
            }
            // The last detection, unless a package changed since (PackageChanges) or a start found a listed one gone: then checked now.
            val installed = launching.detector.current(catalog).emulatorIds
            val choices = graph.database.emulatorChoiceDao()
            val systemChoice = choices.systemChoice(system.id)?.takeIf { it.isPersonChoice }?.emulatorId
            // Only emulators that can open the file A really opens (a chosen copy may be another file type).
            val fileName = graph.fileNameForEmulator(id, system, game.fileName)
            emulatorListingOf(catalog, system, fileName, installed, systemChoice, choices.gameChoice(id)?.emulatorId)
        }

    override suspend fun setFavorite(gameKey: String, isFavorite: Boolean) {
        val id = requireId(gameKey)
        withContext(io) { graph.database.gameDao().setFavorite(id, isFavorite) }
        val change = if (isFavorite) FavoriteChange.Added else FavoriteChange.Removed
        graph.trackForGame(id) { system, _ -> TelemetryEvent.FavoriteChanged(system, change) }
    }

    override suspend fun hide(gameKey: String) {
        val id = requireId(gameKey)
        // An arcade game's other sets are hidden with it, or another copy would show at the next scan.
        withContext(io) { setHiddenWithArcadeCopies(graph.database, graph.naming, id, isHidden = true) }
    }

    override suspend fun removeFromRecent(gameKey: String) {
        val id = requireId(gameKey)
        withContext(io) { recent.removeFromRecent(id) }
    }

    override suspend fun chooseEmulator(gameKey: String, emulatorId: String?) {
        val id = requireId(gameKey)
        withContext(io) {
            val launching = checkNotNull(graph.launching()) { "launching is not set up" }
            if (emulatorId == null) {
                launching.choices.forgetGameChoice(id)
            } else {
                val game = checkNotNull(graph.database.gameDao().game(id)) { "game $id is not in the library" }
                val system = checkNotNull(graph.catalog()?.system(game.systemId)) { "system of game $id is unknown" }
                launching.choices.chooseForGame(id, system, emulatorId)
                graph.trackForSystem(system.id, emulatorId) { picked, emulator -> emulator?.let { TelemetryEvent.EmulatorPicked(picked, it, PickPlace.PerGame) } }
            }
        }
    }

    override suspend fun chooseEmulatorForSystem(gameKey: String, emulatorId: String) {
        val id = requireId(gameKey)
        withContext(io) {
            val launching = checkNotNull(graph.launching()) { "launching is not set up" }
            val game = checkNotNull(graph.database.gameDao().game(id)) { "game $id is not in the library" }
            val system = checkNotNull(graph.catalog()?.system(game.systemId)) { "system of game $id is unknown" }
            launching.choices.chooseForSystem(system, emulatorId)
            // Asked on the system's first game: picked from a game, for its whole system.
            graph.trackForSystem(system.id, emulatorId) { picked, emulator -> emulator?.let { TelemetryEvent.EmulatorPicked(picked, it, PickPlace.PerGame) } }
        }
    }

    override suspend fun confirmCore(core: CoreToConfirm) {
        withContext(io) {
            val launching = checkNotNull(graph.launching()) { "launching is not set up" }
            check(launching.confirmedCores.confirm(core)) { "the confirmed core did not reach the disk" }
        }
    }

    override suspend fun takeBackPlay(added: AddedSession): Boolean {
        // No: the play did not happen, so its game_session is never sent (graph/ClosedAtOnceNotice.kt).
        graph.libraryNotices.dropHeldSession(added)
        return graph.launching()?.playTime?.takeBack(added) ?: false
    }

    override suspend fun forgetFailedCore(gameKey: String, emulatorId: String) {
        val id = requireId(gameKey)
        withContext(io) {
            val launching = checkNotNull(graph.launching()) { "launching is not set up" }
            val game = checkNotNull(graph.database.gameDao().game(id)) { "game $id is not in the library" }
            val system = checkNotNull(graph.catalog()?.system(game.systemId)) { "system of game $id is unknown" }
            launching.choices.forgetChoicesOf(id, system, emulatorId)
        }
    }

    override fun openStorePage(packageName: String): Boolean = openStorePage(packageName) { app.startActivity(it) }

    override fun rescanLibrary(): Boolean {
        if (graph.startup.isScanning) return false
        graph.appScope.launch { graph.startup.scanIfIdle() }
        return true
    }

    override suspend fun versions(gameKey: String): VersionListing? {
        val id = gameKey.toLongOrNull() ?: return null
        return withContext(io) { copiesOf(graph.database, graph.naming, id)?.let(::versionListingOf) }
    }

    override suspend fun chooseVersion(gameKey: String, copyKey: String): Boolean {
        val id = requireId(gameKey)
        val copyId = requireNotNull(copyKey.toLongOrNull()) { "version $copyKey is not a game of the library" }
        return withContext(io) { chooseCopy(graph.database, graph.naming, id, copyId, at = System.currentTimeMillis()) }
    }

    override suspend fun nameSource(gameKey: String): String? = gameKey.toLongOrNull()?.let { graph.nameFixes.packSource(it) }

    override suspend fun arcadeSet(gameKey: String): ArcadeSetFacts? = gameKey.toLongOrNull()?.let { graph.nameFixes.arcadeSet(it) }

    override suspend fun titleList(gameKey: String): TitleList? {
        val id = gameKey.toLongOrNull() ?: return null
        val game = withContext(io) { graph.database.gameDao().game(id) } ?: return null
        val systemName = graph.catalog()?.system(game.systemId)?.displayName ?: game.systemId
        return graph.nameFixes.titleList(game.systemId, systemName)
    }

    override suspend fun setChosenTitle(gameKey: String, choice: TitleChoice) = graph.nameFixes.setChosenTitle(requireId(gameKey), choice)

    override suspend fun setOwnName(gameKey: String, name: String) = graph.nameFixes.setOwnName(requireId(gameKey), name)

    override suspend fun markNotAGame(gameKey: String) = graph.nameFixes.markNotAGame(requireId(gameKey))

    override suspend fun setAppKind(gameKey: String, kind: AppKind): Boolean {
        val id = requireId(gameKey)
        val packageName = withContext(io) { graph.database.appGameDao().byGame(id) } ?: error("row $gameKey is no installed app")
        val outcome = graph.appGames.setChoice(packageName, kind)
        if (outcome is AppGamesSyncOutcome.Skipped) Log.w(TAG, "Game $gameKey: answer stored, the library follows at the next read (${outcome.why})")
        return outcome is AppGamesSyncOutcome.Synced
    }

    override suspend fun backToAutomatic(gameKey: String) {
        graph.nameFixes.backToAutomatic(requireId(gameKey))
    }

    override suspend fun searchBoxArt(gameKey: String, query: String): BoxArtSearchOutcome {
        val id = gameKey.toLongOrNull() ?: return BoxArtSearchOutcome.GameNotReady
        return graph.boxArtFixes.search(id, query)
    }

    override suspend fun boxArtThumbnail(hit: BoxArtHit): File? = graph.boxArtFixes.thumbnail(hit)

    override suspend fun pickBoxArt(gameKey: String, hit: BoxArtHit, onSavingPictures: () -> Unit): BoxArtStoreOutcome =
        graph.boxArtFixes.pick(requireId(gameKey), hit, onSavingPictures)

    override suspend fun storeNoBoxArt(gameKey: String): BoxArtStoreOutcome = graph.boxArtFixes.noBoxArt(requireId(gameKey))

    private suspend fun emulatorToStart(
        gameId: Long,
        system: GameSystem,
        fileName: String,
    ): EmulatorToStart? = graph.emulatorToStart(gameId, system, fileName)

    private fun requireId(gameKey: String): Long = requireNotNull(gameKey.toLongOrNull()) { "row $gameKey is not a game of the library" }
}

private fun itemOf(emulator: Emulator) = EmulatorItem(emulator.id, emulator.name, emulator.primary.packageName, emulator.legalRisk)

/**
 * "Play with" for the game [fileName] of [system]: only the emulators that can open the file (the
 * data's `accepts`; a `.steam` file never lists WinNative), as the launch resolves it
 * (`EmulatorResolver.resolve`). A [systemChoiceId] that cannot open the file is passed over: Automatic
 * then names the recommendation, which Play starts without asking. A [gameChoiceId] the system does
 * not list or that cannot open the file is not current (the launch passes it over). [installed]
 * holds emulator ids.
 */
internal fun emulatorListingOf(
    catalog: PlatformCatalog,
    system: GameSystem,
    fileName: String,
    installed: Set<String>,
    systemChoiceId: String?,
    gameChoiceId: String?,
): EmulatorListing {
    val all = catalog.emulatorsFor(system, fileName).map(::itemOf)
    val (present, absent) = all.partition { it.id in installed }
    val usableSystemChoice = systemChoiceId?.takeIf { id -> catalog.emulatorsOf(system).firstOrNull { it.id == id }?.acceptsFile(fileName) != false }
    val automatic = automaticChoice(usableSystemChoice, all, catalog.defaultCandidatesFor(system.id, fileName).map(::itemOf), installed)
    return EmulatorListing(
        systemName = system.displayName,
        automatic = automatic?.first,
        installed = present,
        notInstalled = absent.sortedBy { it.isLegalRisk },
        currentEmulatorId = gameChoiceId?.takeIf { id -> all.any { it.id == id } },
        isAutomaticInstalled = automatic?.second ?: true,
        // A system choice, even one passed over for this file type: Play does not ask.
        isSystemChosen = systemChoiceId != null,
    )
}

/**
 * The emulator Play would start for [gameId] of [system], by the launch's own resolution
 * (`EmulatorChoices.resolve`: the game's choice, else the system's); null when none is installed or
 * the platform data could not be read. The game's Info panel and the second screen's played line.
 * [fileName] is the game's file (only emulators that can open it count); null only where the file
 * does not matter (a timing test).
 */
internal suspend fun AppGraph.emulatorToStart(
    gameId: Long,
    system: GameSystem,
    fileName: String? = null,
): EmulatorToStart? {
    val launching = launching() ?: return null
    return when (val pick = launching.choices.resolve(gameId, system, fileName)) {
        is EmulatorPick.Ready -> EmulatorToStart(pick.emulator.name, isInstalled = true)
        is EmulatorPick.ChosenMissing -> EmulatorToStart(pick.emulator?.name ?: pick.emulatorId, isInstalled = false)
        is EmulatorPick.NotChosen -> EmulatorToStart("", isInstalled = true, isNotChosenYet = true)
        is EmulatorPick.NoneInstalled -> null
    }
}

/**
 * The emulator Play would start for the library's game [gameId] (its system read from the
 * database); null as for [emulatorToStart], or when the game or its system is not known. Call off
 * the main thread.
 */
internal suspend fun AppGraph.emulatorToStart(gameId: Long): EmulatorToStart? {
    val game = database.gameDao().game(gameId) ?: return null
    val system = catalog()?.system(game.systemId) ?: return null
    return emulatorToStart(gameId, system, fileNameForEmulator(gameId, system, game.fileName))
}

/**
 * The row whose file a launch of [gameId] opens (the game menu's Other versions, library's
 * `copyToPlay`); [gameId] itself when nothing is chosen or the lookup fails (logged): a failed
 * lookup must not stop the game from starting. Call off the main thread.
 */
internal suspend fun AppGraph.copyToOpen(gameId: Long): Long =
    try {
        copyToPlay(database, naming, gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Game $gameId: chosen version unreadable, playing its own file (${e.javaClass.simpleName})")
        gameId
    }

/**
 * The file name a launch of [gameId] opens ([ownFileName] is the row's): the chosen copy's when one
 * is (Other versions). The second screen's File line; may read the arcade pack when a set near an
 * arcade row is chosen.
 */
internal suspend fun AppGraph.launchedFileName(gameId: Long, ownFileName: String): String {
    val copy = copyToOpen(gameId)
    if (copy == gameId) return ownFileName
    return database.gameDao().game(copy)?.fileName ?: ownFileName
}

/** [launchedFileName] of the library's game [gameId]; null when it is gone. Call off the main thread. */
internal suspend fun AppGraph.launchedFileName(gameId: Long): String? =
    database.gameDao().game(gameId)?.let { launchedFileName(gameId, it.fileName) }

/**
 * The file name to work out [gameId]'s emulator for (Play with's list, Info, the second screen's
 * played line). Only a system where an emulator opens some file types only (`accepts`, Steam's
 * `.steam` and `.desktop`) can start another emulator for a chosen copy of another type: there it
 * is the file A really opens ([launchedFileName]). Any other system names the same emulators for
 * every copy, so the row's own [ownFileName] is used and no copy is looked up: the menu's facts and
 * the details column never wait for the arcade pack (no arcade emulator declares `accepts`).
 */
internal suspend fun AppGraph.fileNameForEmulator(
    gameId: Long,
    system: GameSystem,
    ownFileName: String,
): String {
    val fileTypesMatter = catalog()?.emulatorsOf(system)?.any { it.accepts != null } == true
    return if (fileTypesMatter) launchedFileName(gameId, ownFileName) else ownFileName
}

/**
 * What "Automatic" starts once the game's own choice is forgotten, in the order of
 * `EmulatorChoices.resolve`: the person's [systemChoiceId], installed or not (a missing one is
 * reported, never swapped, so Automatic would fail; the second value is then false). With no
 * system choice, the first installed of [defaults] (never legal-risk): only the recommendation the
 * first launch's question puts first, since nothing starts before the person chooses.
 * Null when nothing is installed.
 */
internal fun automaticChoice(
    systemChoiceId: String?,
    systemEmulators: List<EmulatorItem>,
    defaults: List<EmulatorItem>,
    installed: Set<String>,
): Pair<EmulatorItem, Boolean>? {
    if (systemChoiceId != null) {
        // The data may no longer list it: named by its id, like the launch's message.
        val chosen = systemEmulators.firstOrNull { it.id == systemChoiceId } ?: EmulatorItem(systemChoiceId, systemChoiceId, "", isLegalRisk = false)
        return chosen to (chosen.id in installed)
    }
    return defaults.firstOrNull { it.id in installed }?.let { it to true }
}

private const val TAG = "PlumGameActions"
