package com.plumemu.restart

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The install the process last ended itself for; [write] is synchronous (null removes it) and says whether it was stored. */
internal interface RestartMark {
    fun read(): String?

    fun write(installKey: String?): Boolean
}

/**
 * When to look and what to do (the Android side is [CompiledCodeRestart]). The screen going off
 * starts a look after [firstLookMs], then one every [lookAgainMs] while it stays off; the screen
 * coming on cancels them. A restart commits the [mark] first, records the event, then checks the
 * screen and [stillBusy] once more right before [kill]; whatever stops it there (the screen on, work
 * started, the looks cancelled) gives the mark back, so a later screen-off may try again.
 *
 * @param stillBusy the reason not to end the process now (background work, a Settings write), or null.
 * @param recordEvent queues the restart's statistics event; it bounds its own wait.
 * @param trace the routine "no restart" of each look (debug level); [log] is for what happened.
 */
internal class RestartLooks(
    private val scope: CoroutineScope,
    private val isScreenOn: () -> Boolean,
    private val inputs: suspend () -> RestartInputs,
    private val stillBusy: suspend () -> String?,
    private val mark: RestartMark,
    private val recordEvent: suspend () -> Unit,
    private val kill: () -> Unit,
    private val log: (String) -> Unit,
    private val trace: (String) -> Unit = log,
    private val firstLookMs: Long = FIRST_LOOK_MS,
    private val lookAgainMs: Long = LOOK_AGAIN_MS,
) {
    /** The looks of the current screen-off; touched on the thread that reports the screen (the main thread). */
    private var looks: Job? = null

    fun screenChanged(on: Boolean) {
        looks?.cancel()
        looks = if (on) null else scope.launch { lookWhileOff() }
    }

    private suspend fun lookWhileOff() {
        delay(firstLookMs)
        while (scope.isActive && !isScreenOn()) {
            if (look()) return
            delay(lookAgainMs)
        }
    }

    /** One evaluation; true when nothing more can happen in this process (already restarted for this install). */
    private suspend fun look(): Boolean {
        val now =
            try {
                inputs()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log("Compiled-code check failed (${e.javaClass.simpleName}); nothing done")
                return false
            }
        when (val verdict = decideRestart(now)) {
            is RestartVerdict.Hold -> trace("No restart: ${verdict.why}")
            is RestartVerdict.Restart -> restart(verdict.stale, now.installKey, now.restartedFor)
        }
        return now.restartedFor == now.installKey
    }

    private suspend fun restart(
        stale: StaleCode.Stale,
        installKey: String,
        before: String?,
    ) {
        // Committed first: a process that comes back never ends itself again for this install.
        if (isScreenOn() || !mark.write(installKey)) {
            log("No restart: the screen came on or the mark could not be stored")
            return
        }
        var isKilled = false
        try {
            recordEvent()
            val why = if (isScreenOn()) "the screen came on" else stillBusy()
            if (why != null) {
                log("No restart: $why")
            } else {
                log("Restarting the process: its compiled code was replaced on disk (${stale.why}: ${stale.path})")
                isKilled = true
                kill()
            }
        } finally {
            // Cancelled (the screen came on) or held: the mark goes back, even while cancelling.
            if (!isKilled) withContext(NonCancellable) { mark.write(before) }
        }
    }

    companion object {
        const val FIRST_LOOK_MS = 10_000L
        const val LOOK_AGAIN_MS = 15 * 60_000L
    }
}
