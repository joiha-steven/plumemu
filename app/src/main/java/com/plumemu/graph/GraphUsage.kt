package com.plumemu.graph

import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.telemetry.GridSize
import com.plumemu.BuildConfig
import kotlinx.coroutines.Dispatchers
import com.plumemu.telemetry.ScanResult
import kotlinx.coroutines.flow.first
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.telemetry.EmulatorRef
import com.plumemu.telemetry.LaunchFrom
import com.plumemu.telemetry.SystemRef
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.usage.launchEmulatorIdOf
import com.plumemu.usage.launchResultOf
import com.plumemu.usage.settingChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * The usage events that need the graph (docs/conventions/telemetry.md; the screens' and the
 * session's are in usage/). Each call returns at once: the platform data and the library are read
 * on the process's scope, never on the main thread, and a failure is one log line
 * (ids and exception classes only).
 */

/** When this process started, on `SystemClock.elapsedRealtime`'s scale; on Android 6 (no such record) now. */
internal fun processStartElapsedMs(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartElapsedRealtime() else SystemClock.elapsedRealtime()

/**
 * Whether Android ranks this process as in front now; read once, when the graph is made in
 * `Application.onCreate`, to tell a process started to show the Activity from one started in the
 * background (an update's `MY_PACKAGE_REPLACED`, a WorkManager job). One binder call.
 */
internal fun isProcessInForeground(): Boolean =
    try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (e: RuntimeException) {
        Log.w(TAG, "Usage: process importance not read (${e.javaClass.simpleName})")
        false
    }

/**
 * Tracks the event [make] builds for the platform data's system [systemId] (and emulator
 * [emulatorId], null when unknown or none). Nothing is sent for anything the platform data does
 * not list: an Android app, a pinned shortcut, a system a data update dropped.
 */
internal fun AppGraph.trackForSystem(
    systemId: String?,
    emulatorId: String? = null,
    make: (system: SystemRef, emulator: EmulatorRef?) -> TelemetryEvent?,
) {
    val sink = telemetry ?: return
    if (systemId == null) return
    appScope.launch {
        val catalog = catalog() ?: return@launch
        val system = catalog.system(systemId) ?: return@launch
        val emulator = emulatorId?.let(catalog::emulator)?.let(EmulatorRef::of)
        make(SystemRef.of(system), emulator)?.let(sink::track)
    }
}

/** As [trackForSystem], for the system of the game row [gameId] (read from the library); a pinned shortcut or an app game sends nothing. */
internal fun AppGraph.trackForGame(
    gameId: Long,
    emulatorId: String? = null,
    make: (system: SystemRef, emulator: EmulatorRef?) -> TelemetryEvent?,
) {
    if (telemetry == null) return
    appScope.launch {
        val systemId =
            try {
                // A pinned shortcut sits under a system of the platform data (Steam) but is no game of it; nor is an
                // installed app listed as a game (Android games, Ports apps): nothing is sent for either.
                val isAppStarted = database.pinnedShortcutDao().byGame(gameId) != null || database.appGameDao().isAppGameRow(gameId)
                if (isAppStarted) null else database.gameDao().game(gameId)?.systemId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Usage: system of game $gameId not read (${e.javaClass.simpleName})")
                null
            }
        trackForSystem(systemId, emulatorId, make)
    }
}

/** `game_launched` for a Play pressed at [from]. A pinned shortcut, an app game or an app has no platform-data system: nothing. */
internal fun AppGraph.trackLaunch(
    gameId: Long,
    from: LaunchFrom,
    outcome: LaunchOutcome,
) {
    val result = launchResultOf(outcome)
    trackForGame(gameId, launchEmulatorIdOf(outcome)) { system, emulator -> TelemetryEvent.GameLaunched(system, emulator, from, result) }
}

/**
 * `game_session` for a play the launcher recorded (launch to Plumemu back in front, at least 10
 * seconds), in whole minutes. An app of the Apps entry or an older record names no emulator: nothing.
 */
internal fun AppGraph.trackSession(recorded: PlaySessionOutcome.Recorded) {
    val emulatorId = recorded.emulatorId ?: return
    val minutes = (recorded.seconds / SECONDS_PER_MINUTE).toInt()
    trackForGame(recorded.gameId, emulatorId) { system, emulator -> emulator?.let { TelemetryEvent.GameSession(system, it, minutes) } }
}

/** `view_changed`: SELECT made [systemId] a grid ([isGrid], at the stored grid size) or a list. Apps' grid is no platform system: nothing. */
internal fun AppGraph.trackViewChanged(
    systemId: String,
    isGrid: Boolean,
    stored: PlumSettings,
) {
    val size =
        when (stored.appearance.gridSize) {
            GridSizeSetting.Small -> GridSize.Small
            GridSizeSetting.Medium -> GridSize.Medium
            GridSizeSetting.Large -> GridSize.Large
        }
    trackForSystem(systemId) { system, _ -> if (isGrid) TelemetryEvent.ViewChanged.ToGrid(system, size) else TelemetryEvent.ViewChanged.ToList(system) }
}

/** `setting_changed` for each fixed choice a stored write changed (Settings, the main screen's grid size keys). */
internal fun AppGraph.trackSettingChanges(
    before: PlumSettings,
    after: PlumSettings,
) {
    val sink = telemetry ?: return
    settingChanges(before, after).forEach(sink::track)
}

/**
 * `update_installed` once, when this process runs a version other than the one seen last: an
 * update by any way (in-app, a store, a sideload). Read and written off the main thread.
 */
internal fun AppGraph.trackUpdateInstalled() {
    appScope.launch(Dispatchers.IO) {
        val previous =
            try {
                versionSeen.swap(BuildConfig.VERSION_NAME)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(TAG, "Usage: last version not read (${e.javaClass.simpleName})")
                null
            }
        if (previous != null && previous != BuildConfig.VERSION_NAME) telemetry?.track(TelemetryEvent.UpdateInstalled(previous))
    }
}

/** `library_scanned` for a pass that ended: the library's size after it (as ranges), read off the scan's way. */
internal fun AppGraph.trackScan(ended: ScanEnded) {
    val sink = telemetry ?: return
    appScope.launch {
        val systems =
            try {
                database.gameDao().observeSystemsWithGames().first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Usage: library size not read after a scan (${e.javaClass.simpleName})")
                null
            }
        val result =
            when {
                !ended.isOk -> ScanResult.Error
                ended.hasFolderLost -> ScanResult.FolderLost
                else -> ScanResult.Ok
            }
        sink.track(
            TelemetryEvent.LibraryScanned(
                trigger = ended.trigger,
                games = systems?.sumOf { it.gameCount } ?: 0,
                systems = systems?.size ?: 0,
                seconds = ended.millis / MILLIS_PER_SECOND,
                result = result,
            ),
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val TAG = "PlumUsage"
