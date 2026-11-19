package com.plumemu.restart

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.plumemu.BuildConfig
import com.plumemu.MainActivity
import com.plumemu.graph.AppGraph
import com.plumemu.graph.BackupState
import com.plumemu.graph.GraphCollections
import com.plumemu.graph.RestoreState
import com.plumemu.secondscreen.SecondScreenCardActivity
import com.plumemu.telemetry.Telemetry
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.update.UpdateStep
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ends the app's own process, with the screen off and the home in front, once Android has compiled
 * the app's code after the process started ([staleCodeOf]): the home process runs for days, and
 * one started before the compile keeps interpreting the old code (measured: about a third more CPU
 * per frame). Android starts the process again when the screen comes on and recreates the main
 * screen from its saved state, so the person finds it as left.
 *
 * This is the Android side (the screen broadcasts, the graph's states, the stored mark, the kill);
 * when to look and in what order to act is [RestartLooks]. Every condition of [decideRestart] must
 * hold. The timer counts awake time, so a sleeping device looks again only once awake, and at the
 * latest at the next screen-off. A look reads `/proc/self/maps` once, off the main thread.
 */
internal class CompiledCodeRestart(
    private val app: Context,
    private val graph: AppGraph,
    private val telemetry: Telemetry?,
) {
    private val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val front = HomeFront(HomeFrontState(), { it is MainActivity }, { it is SecondScreenCardActivity }, ::isInteractive)
    private val store by lazy { app.getSharedPreferences(STORE, Context.MODE_PRIVATE) }

    private val looks =
        RestartLooks(
            scope = graph.appScope,
            isScreenOn = ::isInteractive,
            inputs = ::inputs,
            stillBusy = { busyWork() ?: if (graph.settingsWriter.isIdle) null else "a Settings write is pending" },
            mark = StoredRestartMark(),
            recordEvent = ::recordEvent,
            // Not a force-stop: Android keeps the activities and their saved state, and recreates them.
            kill = { Process.killProcess(Process.myPid()) },
            log = { Log.i(TAG, it) },
            trace = { Log.d(TAG, it) },
        )

    /** Call once, from the application's `onCreate`: registers two listeners, reads nothing. */
    fun start() {
        (app as android.app.Application).registerActivityLifecycleCallbacks(front)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = looks.screenChanged(on = intent.action == Intent.ACTION_SCREEN_ON)
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        // Screen on/off are protected system broadcasts: they arrive whatever the export flag. The flag
        // exists from Android 13; EXPORTED as for the app's other system broadcasts (SystemStatus).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }

    private suspend fun inputs(): RestartInputs {
        val launching = graph.launching()
        return RestartInputs(
            isScreenOff = !isInteractive(),
            isProcessInFront = isInFrontImportance(importance()),
            wasHomeInFront = front.state.wasInFrontAtSleep(),
            // Not known (the launcher could not be loaded) counts as pending: hold.
            isPlayPending = launching == null || launching.playTime.isPending(),
            busyWork = busyWork(),
            isSettingsIdle = graph.settingsWriter.isIdle,
            code = staleCodeNow(app.applicationInfo.sourceDir),
            installKey = installKey(),
            restartedFor = store.getString(KEY_RESTARTED_FOR, null),
        )
    }

    /** The first background work found running, by name; a check that fails counts as running. */
    private fun busyWork(): String? =
        try {
            when {
                graph.startup.scanState.value.isBusy -> "library scan"
                graph.backup.state.value is BackupState.Running -> "backup"
                graph.restore.state.value.let { it is RestoreState.Reading || it is RestoreState.Replacing || it is RestoreState.Confirm } -> "restore"
                graph.updates.view.value.let { it.isChecking || it.isUpdateRunning || it.step is UpdateStep.Installing } -> "update"
                GraphCollections.isWriting -> "a collection write"
                // Box art, the content read and the statistics send are WorkManager work.
                WorkManager.getInstance(app).getWorkInfos(WorkQuery.fromStates(WorkInfo.State.RUNNING)).get().isNotEmpty() -> "WorkManager work"
                else -> null
            }
        } catch (e: Exception) {
            "unknown work (${e.javaClass.simpleName})"
        }

    /** Queues `process_restarted` when statistics are on, waiting (bounded) until it is in the queue file. */
    private suspend fun recordEvent() {
        val statistics = telemetry ?: return
        statistics.track(TelemetryEvent.ProcessRestarted)
        if (withTimeoutOrNull(EVENT_WAIT_MS) { statistics.recorded() } == null) Log.i(TAG, "The restart event may not be queued")
    }

    private inner class StoredRestartMark : RestartMark {
        override fun read(): String? = store.getString(KEY_RESTARTED_FOR, null)

        override fun write(installKey: String?): Boolean = store.edit().putString(KEY_RESTARTED_FOR, installKey).commit()
    }

    private fun isInteractive(): Boolean = power?.isInteractive ?: true

    private fun importance(): Int = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance

    @Suppress("DEPRECATION") // lastUpdateTime is not deprecated; getPackageInfo(String, Int) is from API 33.
    private fun installKey(): String = "${BuildConfig.VERSION_CODE}:${app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime}"

    private companion object {
        const val TAG = "CompiledCodeRestart"
        const val STORE = "compiled_code_restart"
        const val KEY_RESTARTED_FOR = "restarted_for"
        const val EVENT_WAIT_MS = 2_000L
    }
}

/**
 * Foreground, or on top while the device sleeps. `IMPORTANCE_TOP_SLEEPING` is 325 since Android 9
 * and was 150 before (`IMPORTANCE_TOP_SLEEPING_PRE_28`); both constants are deprecated, so their values are named here.
 */
internal fun isInFrontImportance(importance: Int): Boolean =
    importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        importance == IMPORTANCE_TOP_SLEEPING ||
        importance == IMPORTANCE_TOP_SLEEPING_PRE_28

private const val IMPORTANCE_TOP_SLEEPING = 325
private const val IMPORTANCE_TOP_SLEEPING_PRE_28 = 150
