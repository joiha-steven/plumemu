package com.plumemu.graph

import android.util.Log
import com.plumemu.content.LibraryHomeContent
import com.plumemu.coredata.db.AppKind
import com.plumemu.game.AppActions
import com.plumemu.game.AppOpenOutcome
import com.plumemu.game.AppToolResult
import com.plumemu.launcher.apps.AppToolOutcome
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [AppActions] on the app's graph: the row is looked up in the app list the graph holds (the one
 * the Apps entry was drawn from), and started by the launcher's `InstalledApps.launch`, the only
 * place that builds the Intent. That start tells `AppGraph.gameStarts` (the second screen) as a
 * game's does, through the observer `Launching.create` was given. A missing app, or a row the list
 * no longer has, reads the list again in [scope] (the process's), so the row goes even if the
 * screen does not wait. Log lines carry the row id (package and activity) only.
 */
internal class GraphAppActions(
    private val listed: () -> List<LaunchableApp>,
    /** Starts [LaunchableApp]; null when the launcher could not be set up (the graph has logged why). */
    private val launch: suspend (LaunchableApp) -> AppLaunchOutcome?,
    private val refreshApps: suspend () -> Unit,
    private val scope: CoroutineScope,
    /** Whether Uninstall is offered for a package (`InstalledApps.canUninstall`); a PackageManager read, so off the main thread. */
    private val canUninstallPackage: suspend (LaunchableApp) -> Boolean = { false },
    /** `InstalledApps.showInfo`; null when the launcher could not be set up. */
    private val showInfoOf: suspend (LaunchableApp) -> AppToolOutcome? = { null },
    /** `InstalledApps.uninstall`; null when the launcher could not be set up. */
    private val uninstallOf: suspend (LaunchableApp) -> AppToolOutcome? = { null },
    /** Whether Show as game / Show as port is offered for a package (`InstalledApps.canShowAsGame`); a PackageManager read. */
    private val canChooseKindOf: suspend (LaunchableApp) -> Boolean = { false },
    /** `AppGraph.appGames.setChoice`: stores the answer, then the library follows; throws when it could not be stored. */
    private val setChoiceOf: suspend (packageName: String, kind: AppKind) -> Boolean = { _, _ -> throw IllegalStateException("app kinds are not wired") },
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : AppActions {
    constructor(graph: AppGraph) : this(
        listed = { graph.listedApps.value },
        launch = { app -> graph.launching()?.apps?.launch(app) },
        refreshApps = graph::refreshApps,
        scope = graph.appScope,
        canUninstallPackage = { app -> withContext(Dispatchers.IO) { graph.launching()?.apps?.canUninstall(app.packageName) == true } },
        showInfoOf = { app -> graph.launching()?.apps?.showInfo(app) },
        uninstallOf = { app -> graph.launching()?.apps?.uninstall(app) },
        canChooseKindOf = { app -> withContext(Dispatchers.IO) { graph.launching()?.apps?.canShowAsGame(app.packageName) == true } },
        setChoiceOf = { packageName, kind -> graph.appGames.setChoice(packageName, kind) is AppGamesSyncOutcome.Synced },
    )

    override suspend fun open(rowId: String): AppOpenOutcome {
        val app = listed().firstOrNull { LibraryHomeContent.appRowId(it) == rowId }
        if (app == null) {
            log("apps: row $rowId is not in the app list; reading it again")
            readAgain()
            return AppOpenOutcome.NotListed
        }
        val outcome =
            try {
                launch(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Setting up the launcher failed in a way the graph did not catch: class only.
                log("apps: $rowId not opened, the launcher could not be set up (${e.javaClass.simpleName})")
                return AppOpenOutcome.Unavailable
            }
        // InstalledApps has written the line for a start, a missing app and a refusal.
        return when (outcome) {
            null -> AppOpenOutcome.Unavailable.also { log("apps: $rowId not opened, the launcher is not set up") }
            AppLaunchOutcome.Started -> AppOpenOutcome.Started
            AppLaunchOutcome.Missing -> AppOpenOutcome.Missing.also { readAgain() }
            is AppLaunchOutcome.Failed -> AppOpenOutcome.Refused
        }
    }

    override suspend fun canUninstall(rowId: String): Boolean {
        val app = listed().firstOrNull { LibraryHomeContent.appRowId(it) == rowId } ?: return false
        return try {
            canUninstallPackage(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("apps: $rowId uninstall check failed (${e.javaClass.simpleName})")
            false
        }
    }

    override suspend fun canChooseKind(rowId: String): Boolean {
        val app = listed().firstOrNull { LibraryHomeContent.appRowId(it) == rowId } ?: return false
        return try {
            canChooseKindOf(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("apps: $rowId kind check failed (${e.javaClass.simpleName})")
            false
        }
    }

    override suspend fun setKind(rowId: String, kind: AppKind): Boolean {
        val app = listed().firstOrNull { LibraryHomeContent.appRowId(it) == rowId }
        if (app == null) {
            log("apps: kind: row $rowId is not in the app list; reading it again")
            readAgain()
            throw IllegalStateException("the app is not in the list any more")
        }
        val followed = setChoiceOf(app.packageName, kind)
        if (!followed) log("apps: $rowId answer stored, the library follows at the next read")
        return followed
    }

    override suspend fun showInfo(rowId: String): AppToolResult = tool("info", rowId, showInfoOf)

    override suspend fun uninstall(rowId: String): AppToolResult = tool("uninstall", rowId, uninstallOf)

    /** The row's app handed to Android's own screen; InstalledApps has written the line for a start and a refusal. */
    private suspend fun tool(
        what: String,
        rowId: String,
        run: suspend (LaunchableApp) -> AppToolOutcome?,
    ): AppToolResult {
        val app = listed().firstOrNull { LibraryHomeContent.appRowId(it) == rowId }
        if (app == null) {
            log("apps: $what: row $rowId is not in the app list; reading it again")
            readAgain()
            return AppToolResult.NotListed
        }
        return try {
            when (run(app)) {
                AppToolOutcome.Started -> AppToolResult.Started
                AppToolOutcome.Failed -> AppToolResult.Failed
                null -> AppToolResult.Failed.also { log("apps: $what $rowId not done, the launcher is not set up") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("apps: $what $rowId not done (${e.javaClass.simpleName})")
            AppToolResult.Failed
        }
    }

    private fun readAgain() {
        scope.launch {
            try {
                refreshApps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("apps: the app list could not be read again (${e.javaClass.simpleName})")
            }
        }
    }

    private companion object {
        const val TAG = "PlumApps"
    }
}

/**
 * Reads the Apps entry's list again after a package change. Plumemu is only paused under Android's uninstall dialog,
 * so no onStart would: the receiver (registered while started) does it, and an uninstalled app leaves the list.
 * A failure is logged by class.
 */
internal suspend fun AppGraph.refreshAppsLogged() {
    if (settings.value?.homeScreen?.showApps == false) return // as StartupWork: no list while Show apps is off
    try {
        refreshApps()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("PlumApps", "the app list could not be read again after a package change (${e.javaClass.simpleName})")
    }
}
