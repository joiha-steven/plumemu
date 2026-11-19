package com.plumemu.restart

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether the main screen was in front when the screen went off, from the app's own activities.
 *
 * The process's importance alone cannot tell: on a two-screen handheld the card on the second
 * screen keeps it high while a game or another app holds the top screen. So: the last of the app's
 * screens to come to the front (the card left out) is the home, its last pause came while the
 * device was no longer interactive (going to sleep pauses it with the screen already off; leaving
 * for a game or an app pauses it with the screen on), and it has handed its saved state to Android
 * since, which a new process gets back.
 *
 * Every call comes on the main thread; [wasInFrontAtSleep] is read from a background thread.
 */
internal class HomeFrontState {
    private var homes = 0
    private var isHomeLast = false
    private var pausedAsleep = false
    private var savedSincePause = false

    @Synchronized
    fun created(isHome: Boolean) {
        if (isHome) homes++
    }

    @Synchronized
    fun destroyed(isHome: Boolean) {
        if (isHome) homes = (homes - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun resumed(isHome: Boolean) {
        isHomeLast = isHome
        if (isHome) {
            pausedAsleep = false
            savedSincePause = false
        }
    }

    /** [isInteractive]: the device was interactive at the pause. */
    @Synchronized
    fun paused(
        isHome: Boolean,
        isInteractive: Boolean,
    ) {
        if (!isHome) return
        pausedAsleep = !isInteractive
        savedSincePause = false
    }

    @Synchronized
    fun saved(isHome: Boolean) {
        if (isHome && pausedAsleep) savedSincePause = true
    }

    @Synchronized
    fun wasInFrontAtSleep(): Boolean = homes > 0 && isHomeLast && pausedAsleep && savedSincePause
}

/** Feeds [state] from the activities' lifecycle; [isIgnored] activities (the second-screen card) count for nothing. */
internal class HomeFront(
    val state: HomeFrontState,
    private val isHome: (Activity) -> Boolean,
    private val isIgnored: (Activity) -> Boolean,
    private val isInteractive: () -> Boolean,
) : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (!isIgnored(activity)) state.created(isHome(activity))
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isIgnored(activity)) state.resumed(isHome(activity))
    }

    override fun onActivityPaused(activity: Activity) {
        if (!isIgnored(activity)) state.paused(isHome(activity), isInteractive())
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
        if (!isIgnored(activity)) state.saved(isHome(activity))
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (!isIgnored(activity)) state.destroyed(isHome(activity))
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit
}
