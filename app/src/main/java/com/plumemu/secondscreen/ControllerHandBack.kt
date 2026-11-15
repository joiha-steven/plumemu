package com.plumemu.secondscreen

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** What the card needs from the top screen's Activity (`MainActivity`). */
internal interface TopScreenSide {
    val isResumed: Boolean

    /** Moves the Activity's task to the front; false when Android has no such task. */
    fun moveToFront(): Boolean

    /** A key the card received while it held the controller, handled as if pressed on the top screen. */
    fun takeKey(event: KeyEvent): Boolean

    /** The same for a joystick or hat event (many pads send the D-pad as a hat). */
    fun takeMotion(event: MotionEvent): Boolean
}

/** The top screen's Activity of this process, one at a time. Main thread only. */
internal object TopScreen {
    var side: TopScreenSide? = null
        private set

    /** Follows [activity] until it is destroyed (`MainActivity.onCreate`). */
    fun follow(activity: ComponentActivity) {
        val follower = ActivityTopScreen(activity)
        side = follower
        activity.lifecycle.addObserver(follower)
    }

    /** For tests: [side] as given. */
    fun attach(given: TopScreenSide?) {
        side = given
    }

    private class ActivityTopScreen(
        private val activity: ComponentActivity,
    ) : TopScreenSide, DefaultLifecycleObserver {
        override val isResumed: Boolean get() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

        override fun moveToFront(): Boolean = moveOwnTaskToFront(activity)

        // Through Activity's public method: ComponentActivity redeclares it as restricted to androidx.
        override fun takeKey(event: KeyEvent): Boolean = (activity as Activity).dispatchKeyEvent(event)

        override fun takeMotion(event: MotionEvent): Boolean = activity.dispatchGenericMotionEvent(event)

        override fun onDestroy(owner: LifecycleOwner) {
            if (side === this) side = null
        }
    }
}

/**
 * Gives the controller back to the top screen when the card takes it (one controller for both
 * screens: Android moves it to the display last tapped or started on). The card becomes the top
 * resumed activity when it starts, when it is tapped, and when an app above it on the bottom screen
 * closes or Home is pressed there; while `MainActivity` is resumed, its task is moved to the front
 * with `ActivityManager.AppTask.moveToFront()` (public, no permission), which makes the top display
 * the focused one again.
 *
 * At once, no wait: the old window waited 300 ms because a game coming in front on the top screen
 * took the controller from `MainActivity` before pausing it, and the window must not turn focusable
 * in between. A game on the top screen never makes the card top resumed (it takes the controller
 * on its own display), so here nothing can be mistaken for it; on the Thor the card's hand-back
 * after 300 ms brought `MainActivity` back within 5 ms (measured on the AYN Thor).
 *
 * Never while `MainActivity` is not resumed: moving its task would cover a game on the top screen.
 * Main thread only.
 */
internal class ControllerHandBack(
    private val displayId: () -> Int,
    private val top: () -> TopScreenSide? = { TopScreen.side },
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val info: (String) -> Unit = { Log.i(SecondScreenHost.TAG, it) },
    private val warn: (String) -> Unit = { Log.w(SecondScreenHost.TAG, it) },
) {
    /** Whether Android last said the card holds the controller. */
    var holds = false
        private set

    /** Hand-backs tried since the card took the controller. */
    private var tries = 0

    private val retry = Runnable { if (holds) handBack() }

    /** A moment after a hand-back Android has not taken the controller from the card: it did not come back. */
    private val checkBack = Runnable { if (holds) tryAgainOrGiveUp("still on the second screen") }

    /** From the card's `onTopResumedActivityChanged` (Android 10+), or its window focus before that. */
    fun onCardTop(isTop: Boolean) {
        holds = isTop
        handler.removeCallbacks(retry)
        handler.removeCallbacks(checkBack)
        tries = 0
        if (!isTop) return
        info("Second screen card: the controller is on display ${displayId()}; giving it back to the top screen")
        handBack()
    }

    /** The card is going: nothing more to try. */
    fun stop() {
        holds = false
        handler.removeCallbacks(retry)
        handler.removeCallbacks(checkBack)
    }

    /**
     * A key the card received: before the hand-back lands, a press is meant for the top screen and
     * goes there once (Android delivers each event to one window only, so it is never doubled);
     * with a game in front it is dropped (no key does anything on the card). Only the keys the top
     * screen's input reads are passed on. A system key (volume, mute, media, the headset button) is
     * not taken: false, so the card leaves it to Android.
     */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (isLeftToAndroid(event)) return false
        val side = top()
        if (isPadKey(code) && side != null && side.isResumed) side.takeKey(event)
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        val side = top()
        if (side != null && side.isResumed) side.takeMotion(event)
        return true
    }

    private fun handBack() {
        val side = top()
        if (side == null || !side.isResumed) {
            info("Controller on the second screen: not taken back (Plumemu is not in front)")
            return
        }
        tries++
        val failure =
            try {
                if (side.moveToFront()) null else "our task was not found"
            } catch (e: RuntimeException) {
                // A refusal must not crash the home app; a tap on the top screen still gives the keys back.
                e.javaClass.simpleName
            }
        if (failure == null) {
            info("Controller on the second screen: taken back to the top screen")
            handler.postDelayed(checkBack, RETRY_MS)
        } else {
            tryAgainOrGiveUp(failure)
        }
    }

    private fun tryAgainOrGiveUp(reason: String) {
        if (tries < TRIES) {
            info("Controller on the second screen: not taken back ($reason), trying again")
            handler.postDelayed(retry, RETRY_MS)
        } else {
            warn("Controller on the second screen: gave up after $tries tries ($reason); a tap on the top screen brings it back")
        }
    }

    companion object {
        /** Between two hand-backs, and how long one has to bring the controller back. */
        const val RETRY_MS = 500L

        /** Hand-backs tried before giving up until the card next takes the controller. */
        const val TRIES = 3
    }
}

/** The controller's keys the top screen's input reads: the D-pad, gamepad buttons, Enter and Back. */
internal fun isPadKey(code: Int): Boolean =
    KeyEvent.isGamepadButton(code) ||
        code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
        code in KeyEvent.KEYCODE_DPAD_UP_LEFT..KeyEvent.KEYCODE_DPAD_DOWN_RIGHT ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        code == KeyEvent.KEYCODE_BACK ||
        code == KeyEvent.KEYCODE_ESCAPE

/** Keys Android handles itself (volume, mute, media, the headset button, power, brightness): never taken by the card. */
internal fun isLeftToAndroid(event: KeyEvent): Boolean = !isPadKey(event.keyCode) && (event.isSystem || event.keyCode in ALWAYS_ANDROIDS)

private val ALWAYS_ANDROIDS =
    setOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    )

/** `AppTask.moveToFront` for the Activity's own task: no permission needed, unlike `ActivityManager.moveTaskToFront`. */
private fun moveOwnTaskToFront(activity: ComponentActivity): Boolean {
    // AppTask.getTaskInfo().taskId is API 23, moveToFront API 23: both are there from minSdk.
    val task =
        activity.getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull { it.taskInfo?.let(::idOf) == activity.taskId }
            ?: return false
    task.moveToFront()
    return true
}

@Suppress("DEPRECATION")
private fun idOf(info: ActivityManager.RecentTaskInfo): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else info.persistentId
