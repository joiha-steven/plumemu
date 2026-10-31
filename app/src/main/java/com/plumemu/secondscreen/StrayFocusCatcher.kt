package com.plumemu.secondscreen

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps the controller on the top screen. On the Thor a tap on the AYN panel, which opens on the bottom screen, makes the bottom
 * display the focused one; once the panel closes, Android leaves the keys on that display, with
 * Launcher3's secondary home (hidden under our window) taking them, until the person taps the top
 * screen.
 *
 * While `MainActivity` stays resumed but is no longer the top resumed activity (Android 10+ says so
 * in `onTopResumedActivityChanged`), the window on the bottom screen may take the focus
 * ([SecondScreenHost.catchesStrayFocus]). It lies above that hidden home, so the focus lands on it
 * as soon as nothing above it wants keys, and [takeControllerBack] then moves our own task to the
 * front, which makes the top display the focused one again. Main thread only.
 */
internal class StrayFocusCatcher(
    private val activity: Activity,
    private val isResumed: () -> Boolean,
    /** Moves the Activity's task to the front; false when Android has no such task. */
    private val moveToFront: () -> Boolean = { moveOwnTaskToFront(activity) },
    private val info: (String) -> Unit = { Log.i(SecondScreenHost.TAG, it) },
    private val warn: (String) -> Unit = { Log.w(SecondScreenHost.TAG, it) },
) {
    private val handler = Handler(Looper.getMainLooper())

    /** The second screen's window; null before it is made and after `onDestroy`. */
    var host: SecondScreenHost? = null
        set(value) {
            field = value
            value?.catchesStrayFocus = isCatching
        }

    /** Whether the bottom window may take a focus left on its display now. */
    var isCatching = false
        private set(value) {
            field = value
            host?.catchesStrayFocus = value
        }

    /** Whether Android last said this activity is the top resumed one (holds the controller). */
    private var isTop = false

    /** Hand-backs tried since the controller was lost; reset when it is back or Plumemu pauses. */
    private var tries = 0

    private val startCatching = Runnable { if (isResumed()) isCatching = true }

    /** A moment after a hand-back Android said nothing: the controller did not come back. */
    private val checkBack = Runnable { if (isResumed() && !isTop) tryAgainOrGiveUp("still not on top") }

    fun onTopResumedChanged(isTop: Boolean) {
        this.isTop = isTop
        stopTrying()
        if (!isTop && isResumed()) {
            // Not at once: an activity coming in front on this screen (a game) also takes the
            // controller first, and onPause follows (13 ms later on the Thor). Set at once, the bottom
            // window turned focusable for those few ms; it must never be while a game starts.
            handler.postDelayed(startCatching, DELAY_MS)
        }
    }

    /**
     * Resumed without the controller: back from a game while the bottom display held it (the AYN
     * panel used in game). Android says nothing then, since nothing changed for this activity; it
     * says `onTopResumedChanged(true)` right after a resume that has the controller, which cancels this.
     */
    fun onResume() {
        // Older Android has one focus for every display: a focusable bottom window would take it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        stopTrying()
        if (!isTop) handler.postDelayed(startCatching, DELAY_MS)
    }

    fun onPause() {
        isTop = false
        stopTrying()
    }

    private fun stopTrying() {
        handler.removeCallbacks(startCatching)
        handler.removeCallbacks(checkBack)
        tries = 0
        isCatching = false
    }

    /**
     * The focus landed on the bottom window ([SecondScreenHost]'s `onStrayFocus`). Only while still
     * resumed and holding this screen's own focus, so never over a game or another app here. A
     * hand-back that fails or changes nothing is tried again, [TRIES] times in all.
     */
    fun takeControllerBack() {
        isCatching = false
        if (!isResumed()) {
            info("Controller left on the second screen: not taken back (paused)")
            return
        }
        tries++
        val failure =
            if (!activity.hasWindowFocus()) {
                "this screen's window has no focus"
            } else {
                try {
                    if (moveToFront()) null else "our task was not found"
                } catch (e: RuntimeException) {
                    // A refusal must not crash the home app; a tap on the top screen still gives the keys back.
                    e.javaClass.simpleName
                }
            }
        if (failure == null) {
            info("Controller left on the second screen: taken back to the top screen")
            handler.postDelayed(checkBack, RETRY_MS)
        } else {
            tryAgainOrGiveUp(failure)
        }
    }

    private fun tryAgainOrGiveUp(reason: String) {
        if (tries < TRIES) {
            info("Controller left on the second screen: not taken back ($reason), trying again")
            handler.postDelayed(startCatching, RETRY_MS)
        } else {
            warn("Controller left on the second screen: gave up after $tries tries ($reason); a tap on the top screen brings it back")
        }
    }

    companion object {
        /** How long Plumemu must stay resumed without the controller before its bottom window may take the focus. */
        const val DELAY_MS = 300L

        /** Between two hand-backs, and how long one has to bring the controller back. */
        const val RETRY_MS = 500L

        /** Hand-backs tried before giving up until the controller is next lost. */
        const val TRIES = 3
    }
}

/** `AppTask.moveToFront` for the Activity's own task: no permission needed, unlike `ActivityManager.moveTaskToFront`. */
private fun moveOwnTaskToFront(activity: Activity): Boolean {
    // Only Android 10+ tells an activity it lost the controller, so nothing calls this earlier.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val task =
        activity.getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull { it.taskInfo?.taskId == activity.taskId }
            ?: return false
    task.moveToFront()
    return true
}
