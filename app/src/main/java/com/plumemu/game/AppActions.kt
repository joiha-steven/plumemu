package com.plumemu.game

import android.util.Log
import com.plumemu.coredata.db.AppKind

/**
 * What A (or a second tap) on a row of the Apps entry asks of the launcher: a seam so the screen's
 * tests run on a fake. An app is named by its list row id (`package/activity`,
 * `LibraryHomeContent.appRowId`). Production: [GraphAppActions][com.plumemu.graph.GraphAppActions].
 */
internal interface AppActions {
    /** Starts the app of the row [rowId]; never throws. The failures have been logged (ids only). */
    suspend fun open(rowId: String): AppOpenOutcome

    /** Whether the app menu offers Uninstall for [rowId]: false for Plumemu, a system app Android cannot remove, or a row that is gone. */
    suspend fun canUninstall(rowId: String): Boolean

    /** Whether the app menu offers Show as game / Show as port for [rowId]: false for Plumemu, Android's Settings, or a row that is gone. */
    suspend fun canChooseKind(rowId: String): Boolean

    /**
     * The person's answer for [rowId]'s app: Show as game, Show as port or Not a game. Returns when the
     * library has followed (true); false when the answer is stored but the library could not follow yet (the caller
     * says so); throws when the answer could not be stored.
     */
    suspend fun setKind(rowId: String, kind: AppKind): Boolean

    /** Opens Android's App info page of [rowId]'s app; never throws. The failures have been logged (ids only). */
    suspend fun showInfo(rowId: String): AppToolResult

    /** Asks Android to uninstall [rowId]'s app (Android asks to confirm); never throws. The failures have been logged. */
    suspend fun uninstall(rowId: String): AppToolResult

    /** Benchmark and preview builds: nothing is started (one log line, the message says it could not be opened). */
    object None : AppActions {
        override suspend fun open(rowId: String): AppOpenOutcome {
            Log.i(TAG, "apps: $rowId not opened, apps are off in this build")
            return AppOpenOutcome.Unavailable
        }

        override suspend fun canUninstall(rowId: String): Boolean = false

        override suspend fun canChooseKind(rowId: String): Boolean = false

        override suspend fun setKind(rowId: String, kind: AppKind): Boolean {
            Log.i(TAG, "apps: kind of $rowId not set, apps are off in this build")
            throw IllegalStateException("apps are off in this build")
        }

        override suspend fun showInfo(rowId: String): AppToolResult = unavailable("info", rowId)

        override suspend fun uninstall(rowId: String): AppToolResult = unavailable("uninstall", rowId)

        private fun unavailable(what: String, rowId: String): AppToolResult {
            Log.i(TAG, "apps: $what $rowId not done, apps are off in this build")
            return AppToolResult.Failed
        }

        private const val TAG = "PlumApps"
    }
}

/** How App info or Uninstall ended; a failure shows `appToolMessage`. */
internal enum class AppToolResult {
    /** Android's screen is up (or coming); the menu closes. */
    Started,

    /** Android could not open it, or this build cannot. */
    Failed,

    /** The row is not in the app list the graph holds now; the list is read again. */
    NotListed,
}

/** How opening an app ended; every case but [Started] has its message (`appOpenMessage`). */
internal enum class AppOpenOutcome {
    Started,

    /** Uninstalled or disabled since the list was read; the list is read again. */
    Missing,

    /** Android refused the start, or it threw; nothing was started. */
    Refused,

    /** The row is not in the app list the graph holds now (it changed since it was drawn); the list is read again. */
    NotListed,

    /** The launcher could not be set up (platform data unreadable), or this build launches nothing. */
    Unavailable,
}
