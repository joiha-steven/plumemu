package com.plumemu.secondscreen

import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.launcher.GameStartObserver
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the person is, as far as the second screen's window cares. */
internal enum class Presence {
    /** Plumemu is visible (its Activity is started): the picking cards. */
    InPlumemu,

    /** Plumemu stopped after it started a game or an app itself: the In game setting. */
    Playing,

    /** Plumemu stopped any other way (Home to another launcher, recents, an app started from outside): no window. */
    Away,
}

/** What the window draws while it is up. */
internal enum class WindowMode { Picking, InGameClock, InGameBlack }

/** The window's mode for [presence]; null: no window (we never sit on someone else's screen). */
internal fun windowModeOf(
    presence: Presence,
    inGame: InGameSetting,
): WindowMode? =
    when (presence) {
        Presence.InPlumemu -> WindowMode.Picking
        Presence.Playing ->
            when (inGame) {
                InGameSetting.Clock -> WindowMode.InGameClock
                InGameSetting.Black -> WindowMode.InGameBlack
            }
        Presence.Away -> null
    }

/**
 * The Activity's side of [Presence], held by `MainActivity` (not the launcher module: modules
 * never depend on app). A game or app launch tells it through [GameStartRelay] right before it
 * starts the emulator or app ([onLaunching]) and when that start threw ([onLaunchFailed]); the
 * Activity calls [onStart], [onResume] and [onStop] on the main thread. [onLaunching] and
 * [onLaunchFailed] come from the launcher's I/O thread for a game, and from the main thread for an
 * app of the Apps entry (`InstalledApps.launch`, called from the home screen's controller); the
 * flag is atomic either way. [current] may be read anywhere.
 *
 * Away until the Activity's first `onStart`, so a window made before it would not show.
 */
internal class SecondScreenPresence {
    private val state = MutableStateFlow(Presence.Away)

    /** Set right before an emulator starts, cleared in [onStart] and [onResume]: the next stop is Plumemu's own. */
    private val launchedAway = AtomicBoolean(false)

    val current: StateFlow<Presence> = state.asStateFlow()

    fun onStart() {
        launchedAway.set(false)
        state.value = Presence.InPlumemu
    }

    /**
     * Also clears the flag: an emulator that finishes in its `onCreate`, or a translucent one, only
     * pauses Plumemu. Without this the flag would survive and a later Recents or store exit would
     * count as a game, leaving the card on someone else's screen.
     */
    fun onResume() {
        launchedAway.set(false)
    }

    fun onStop() {
        state.value = if (launchedAway.get()) Presence.Playing else Presence.Away
    }

    /** An emulator's activity is about to be started from Plumemu (just before `startActivity`). */
    fun onLaunching() {
        launchedAway.set(true)
    }

    /** That `startActivity` threw: nothing was started, Plumemu stays. Ignored once stopped. */
    fun onLaunchFailed() {
        if (state.value == Presence.InPlumemu) launchedAway.set(false)
    }
}

/**
 * The launcher's [GameStartObserver], one per process in `AppGraph` (the launcher lives as long as
 * the process), forwarding to the [SecondScreenPresence] of the Activity that is up now. A
 * recreated Activity sets itself before the old one is destroyed; the old one clears only itself.
 */
internal class GameStartRelay : GameStartObserver {
    @Volatile
    var target: SecondScreenPresence? = null

    override fun beforeStart() {
        target?.onLaunching()
    }

    override fun startFailed() {
        target?.onLaunchFailed()
    }

    /** [presence]'s Activity is going: forget it, unless a newer one already took its place. */
    fun release(presence: SecondScreenPresence) {
        if (target === presence) target = null
    }
}
