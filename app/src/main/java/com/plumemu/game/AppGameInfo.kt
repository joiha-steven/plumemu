package com.plumemu.game

import android.util.Log
import com.plumemu.graph.AppGraph
import com.plumemu.launcher.apps.AppToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The game menu's App info for the app game row [gameKey]: Android's details page of its package, opened by the
 * same `InstalledApps.showInfo` as the Apps entry's menu. Never throws; every failure is logged (ids only) and answers
 * [AppToolResult.Failed], which the menu says in a message card.
 */
internal suspend fun appInfoOf(
    graph: AppGraph,
    io: CoroutineDispatcher,
    gameKey: String,
): AppToolResult {
    val packageName =
        try {
            gameKey.toLongOrNull()?.let { id -> withContext(io) { graph.database.appGameDao().byGame(id) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Game $gameKey: app info: record unreadable (${e.javaClass.simpleName})")
            null
        }
    if (packageName == null) {
        Log.w(TAG, "Game $gameKey: app info: row is no installed app")
        return AppToolResult.Failed
    }
    val launching = graph.launching()
    if (launching == null) {
        Log.w(TAG, "Game $gameKey: app info not opened, the launcher is not set up")
        return AppToolResult.Failed
    }
    return if (launching.apps.showInfo(packageName) == AppToolOutcome.Started) AppToolResult.Started else AppToolResult.Failed
}

private const val TAG = "PlumApps"
