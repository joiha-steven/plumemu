package com.plumemu.graph

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.plumemu.scraper.work.ScrapeScheduler
import com.plumemu.settings.screen.SettingsHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts Plumemu again in a new process after a restore swapped the library: the database, the
 * settings and everything built on them are read anew, which rebuilding the graph's parts one by
 * one could not promise (the scanner, the launcher, the box-art source and the screens' flows
 * all hold the old database). The new task is asked for first; Android then starts the new
 * process for it, and, Plumemu being the home app, would start it anyway.
 */
internal fun relaunchApp(
    app: Context,
    log: (String) -> Unit,
) {
    try {
        val component = app.packageManager.getLaunchIntentForPackage(app.packageName)?.component
        if (component != null) app.startActivity(Intent.makeRestartActivityTask(component))
    } catch (e: Exception) {
        log("Restore: the app could not ask to be started again (${e.javaClass.simpleName}); the home button brings it back")
    }
    Process.killProcess(Process.myPid())
}

/**
 * After a cancel, waits until WorkManager no longer runs the box-art work (its own state, read every
 * 100 ms), at most [timeoutMs]: a run writing pictures and results must not outlive the swap.
 * False when it still runs; a failure to ask counts as still running.
 */
internal suspend fun waitForBoxArtToStop(
    app: Context,
    timeoutMs: Long,
): Boolean =
    withTimeoutOrNull(timeoutMs) {
        val manager = WorkManager.getInstance(app)
        while (manager.getWorkInfosForUniqueWork(ScrapeScheduler.UNIQUE_WORK_NAME).get().any { it.state == WorkInfo.State.RUNNING }) delay(POLL_MS)
        true
    } ?: false

private const val POLL_MS = 100L

/** What Settings drives of [restore] (Settings › Advanced › Restore… and Undo restore). */
internal fun restoreWorkOf(restore: GraphRestore): SettingsHost.RestoreWork =
    object : SettingsHost.RestoreWork {
        override fun cancel() = restore.cancel()

        override fun confirm() {
            restore.confirm()
        }

        override fun undo() {
            restore.undo()
        }

        override fun forgetCopy() = restore.forgetCopy()

        override fun forgetResult() = restore.forgetResult()
    }
