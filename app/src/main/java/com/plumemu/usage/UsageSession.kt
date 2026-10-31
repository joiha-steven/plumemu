package com.plumemu.usage

import com.plumemu.telemetry.ControllerKind
import com.plumemu.telemetry.InputUsed
import com.plumemu.telemetry.OpenFrom
import com.plumemu.telemetry.ScreenId
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.TelemetrySink

/**
 * One stretch of Plumemu in front, from the Activity's start to its stop (docs/conventions/telemetry.md):
 * `app_open` when it comes to the front, `screen_opened` on each real change of screen, and
 * `app_background` when it leaves, with the time in front, the screens opened and what the person
 * used (gamepad, touch). One per process ([com.plumemu.graph.AppGraph.usage]); every call is made
 * on the main thread and only sets a field or hands an event to [sink], which never blocks.
 *
 * A cold start's `app_open` waits for the first frame ([onFirstFrame]), so it carries the time from
 * the process start to that frame; a later start sends it at once, without that time. The time is
 * sent only when the process was started to show the Activity: in front at its start
 * ([processStartedInForeground]) and the Activity created within [COLD_START_WINDOW_MS]. A process
 * started in the background (`MY_PACKAGE_REPLACED` after an update, a box-art job) and opened later
 * would otherwise report hours.
 */
internal class UsageSession(
    private val sink: TelemetrySink?,
    /** Milliseconds since boot, counting deep sleep (`SystemClock.elapsedRealtime`). */
    private val clock: () -> Long,
    /** When this process started, on [clock]'s scale. */
    private val processStartMs: Long,
    /** Android ranked the process as in front when it started (`ActivityManager.getMyMemoryState`). */
    private val processStartedInForeground: Boolean,
) {
    /** Whether the first frame's time may be sent as a cold start; decided at the first Activity's creation. */
    private var isColdStart = false
    private var hasCreatedActivity = false

    private var hasOpened = false
    private var waitsForFirstFrame = false
    private var waitsForResume = false
    private var openFrom = OpenFrom.Other
    private var startedAtMs: Long? = null
    private var screens = 0
    private var lastScreen: ScreenId? = null
    private var usedGamepad = false
    private var usedTouch = false

    /**
     * The Activity's `onCreate`, opened by [from] (its intent). [isRecreated] (a saved state: a
     * rotation, a theme change, a restore from recents): the intent is the old one and does not count again.
     */
    fun onActivityCreated(
        from: OpenFrom,
        isRecreated: Boolean,
    ) {
        if (!hasCreatedActivity) {
            hasCreatedActivity = true
            isColdStart = processStartedInForeground && clock() - processStartMs in 0..COLD_START_WINDOW_MS
        }
        if (!isRecreated) openedBy(from)
    }

    /** The Activity was opened by [from] (a new intent in `onNewIntent`); the next open says so. */
    fun openedBy(from: OpenFrom) {
        openFrom = from
    }

    /** The Activity started; after a stop that was only a configuration change (still in front), nothing new. */
    fun onStart() {
        if (startedAtMs != null) return
        startedAtMs = clock()
        screens = 0
        usedGamepad = false
        usedTouch = false
        if (!hasOpened) {
            hasOpened = true
            waitsForFirstFrame = true
            return
        }
        waitsForResume = true
    }

    /**
     * The Activity resumed: a warm start's `app_open`. Sent here, not in [onStart], because the
     * intent that brought Plumemu back (the Home key) arrives between the two ([openedBy] from `onNewIntent`).
     */
    fun onResume() {
        if (!waitsForResume) return
        waitsForResume = false
        sink?.track(TelemetryEvent.AppOpen(coldStartMs = null, from = takeOpenFrom()))
    }

    /** The first frame of the process's first Activity is drawn: the cold start's `app_open`. */
    fun onFirstFrame() {
        if (!waitsForFirstFrame) return
        sendColdOpen(coldStartMs = if (isColdStart) (clock() - processStartMs).coerceAtLeast(0) else null)
    }

    private fun sendColdOpen(coldStartMs: Long?) {
        waitsForFirstFrame = false
        sink?.track(TelemetryEvent.AppOpen(coldStartMs = coldStartMs, from = takeOpenFrom()))
    }

    /** The Activity stopped. [isChangingConfigurations]: it is being recreated and stays in front. */
    fun onStop(isChangingConfigurations: Boolean = false) {
        if (isChangingConfigurations) return
        val started = startedAtMs ?: return
        startedAtMs = null
        // A stop before the first frame or the resume (opened and left at once) still counts the open,
        // without a cold start time: the stop is not a frame.
        if (waitsForFirstFrame) sendColdOpen(coldStartMs = null)
        onResume()
        val seconds = ((clock() - started) / MILLIS_PER_SECOND).coerceAtLeast(0)
        sink?.track(TelemetryEvent.AppBackground(seconds, screens, inputUsed()))
    }

    /** A screen is shown; the same screen again (a recomposition, a return to it) is not a new one. */
    fun onScreen(screen: ScreenId) {
        if (screen == lastScreen) return
        lastScreen = screen
        screens++
        sink?.track(TelemetryEvent.ScreenOpened(screen))
    }

    /** A key or a stick moved: a gamepad (or the device's own buttons) was used. Called per input event: only sets a flag. */
    fun onGamepadInput() {
        usedGamepad = true
    }

    /** The screen was touched. Called per touch event: only sets a flag. */
    fun onTouchInput() {
        usedTouch = true
    }

    /** An error message is shown on [where] (`error_shown`): the app's code for it, never its words. */
    fun onError(
        where: ScreenId,
        code: Enum<*>,
    ) {
        sink?.track(TelemetryEvent.ErrorShown(where, code))
    }

    /** Search closed after a search (`search_used`): only the ranges of the length and the results are sent. */
    fun onSearchUsed(
        queryLength: Int,
        results: Int,
        openedResult: Boolean,
    ) {
        sink?.track(TelemetryEvent.SearchUsed(queryLength, results, openedResult))
    }

    private val controllersSeen = HashSet<String>()

    /** True the first time this process sees the controller [descriptor] (`controller_connected` once per device). */
    fun isNewController(descriptor: String): Boolean = controllersSeen.add(descriptor)

    /** A gamepad was found ([ControllerWatch]). */
    fun onController(kind: ControllerKind) {
        sink?.track(TelemetryEvent.ControllerConnected(kind))
    }

    private fun inputUsed(): InputUsed =
        when {
            usedGamepad && usedTouch -> InputUsed.Both
            usedGamepad -> InputUsed.Gamepad
            usedTouch -> InputUsed.Touch
            else -> InputUsed.None
        }

    private fun takeOpenFrom(): OpenFrom = openFrom.also { openFrom = OpenFrom.Other }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L

        /** The first Activity must be created this soon after the process start for its first frame to be a cold start. */
        const val COLD_START_WINDOW_MS = 10_000L
    }
}
