package com.plumemu.secondscreen

import android.app.Dialog
import android.app.Presentation
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.plumemu.battery.BatteryPaceNow
import com.plumemu.battery.LocalBatteryPaceNow
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.language.AppLocale
import com.plumemu.coreui.theme.InGameColors
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.CoverImages
import com.plumemu.launcher.play.battery.BatteryReads
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.SettingsType
import com.plumemu.settings.ViewedSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The second screen's window: one [Presentation] on
 * the chosen display while there is a second screen and it is on ([isSecondScreenOn]), showing
 * [feed]'s content in the stored theme. Never an Activity on that display (research: an ANR).
 * Owned by `MainActivity`: [start] after its first content frame, [stop] in its `onDestroy`. Main
 * thread only.
 *
 * Its mode follows [presence]: the picking cards while Plumemu is
 * visible, the In game setting (the clock card or black) while a game or an app Plumemu started
 * runs, and no window when Plumemu was left any other way.
 */
internal class SecondScreenHost(
    /** The Activity: the window belongs to it. */
    private val context: Context,
    /** The Activity's scope (main thread); cancelled when it is destroyed. */
    private val scope: CoroutineScope,
    private val screens: StateFlow<SecondScreen?>,
    private val settings: StateFlow<PlumSettings?>,
    private val feed: SecondScreenFeed,
    /** Whether Plumemu is visible, playing, or left ([SecondScreenPresence.current]). */
    private val presence: StateFlow<Presence>,
    /** What the window paints before its first frame: the cached background of the top screen. */
    private val backgroundArgb: Int,
    /** Benchmark builds asked to measure the window on a screen that is off by default (the emulator's overlay). */
    private val forcedOn: Boolean = false,
    /** Decodes the box art the picking card shows; none: cards without art. */
    private val images: CoverImages = CoverImages.None,
    /** `Dialog.show`; a test makes it throw. */
    private val showWindow: (Dialog) -> Unit = Dialog::show,
    /** The in-game clock's time source; a test passes its own. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Reads the battery while the in-game card is up with Show battery on; none: no reads. */
    private val battery: BatteryReads = BatteryReads.None,
    /** Works the picking line's figures out again when the battery changes; null: the line keeps those read with the facts. */
    private val paceNow: BatteryPaceNow? = null,
    /** The in-game card's once-ever drift note started showing: stores `flags.in_game_drift_note_shown`. */
    private val onDriftNoteShown: () -> Unit = {},
    /** The key focus landed on the window while [catchesStrayFocus]: the top screen takes the controller back. */
    private val onStrayFocus: () -> Unit = {},
    /** The system last viewed on the top screen, for Accent color › By system; null: the fallback accent. */
    private val viewedSystem: ViewedSystem? = null,
    private val info: (String) -> Unit = { Log.i(TAG, it) },
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var following: Job? = null
    private var wanted: Want = Want(null, "not started", isFinal = false)
    private var window: SecondScreenWindow? = null

    /** What the window draws; the window's composition reads it. */
    private val mode = MutableStateFlow(WindowMode.Picking)

    /** The mode the shown window was last logged in (one line per change while it is up). */
    private var loggedMode: WindowMode? = null

    /** What the window draws now, for tests. */
    internal val shownMode: WindowMode? get() = if (window != null) mode.value else null

    /** The display the window is on now; null without one. */
    val shownDisplayId: Int? get() = window?.displayId

    /** The window, for tests (flags, lifecycle). */
    internal val shownWindow: Dialog? get() = window

    /**
     * While true the window may take the key focus, so that a focus another app left on its display
     * lands here and goes to [onStrayFocus]. `MainActivity` sets it while it stays resumed but is no longer the top resumed activity:
     * the bottom display took the controller (a tap on the AYN panel there). False otherwise: never
     * focusable while Plumemu has the controller or is in the background.
     */
    var catchesStrayFocus: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            window?.setFocusable(value)
        }

    /** Follows the screen and the switch until [stop]. */
    fun start() {
        if (following != null) return
        following =
            scope.launch {
                combine(screens, settings, windowHolder, presence) { screen, stored, holder, where ->
                    if (holder != null && holder !== this@SecondScreenHost) {
                        // That window is still up: not a reason to bring the top screen's column back.
                        Want(null, "another activity's window is up", isFinal = false)
                    } else {
                        wantFor(screen, stored, where)
                    }
                }
                    .distinctUntilChanged()
                    .collect { want ->
                        wanted = want
                        mode.value = want.mode
                        sync(want.reason)
                        followIfPicking()
                    }
            }
    }

    /** The Activity is going: the window goes with it. */
    fun stop() {
        following?.cancel()
        following = null
        wanted = Want(null, "activity destroyed")
        sync("activity destroyed")
    }

    private fun wantFor(
        screen: SecondScreen?,
        stored: PlumSettings?,
        where: Presence,
    ): Want {
        val reason =
            when {
                screen == null -> return Want(null, "no second screen")
                stored == null -> return Want(null, "settings not read yet", isFinal = false)
                isSecondScreenOn(stored.secondScreen, screen) -> "switched on"
                forcedOn -> "benchmark"
                else -> return Want(null, "switched off")
            }
        val shown = windowModeOf(where, stored.secondScreen.inGame) ?: return Want(null, "Plumemu left")
        return Want(screen.displayId, reason, shown)
    }

    /**
     * The top screen feeds the window only while it shows the picking cards. In game the feed
     * goes back to Calm and stops, so on the way back the card of the game just played is read
     * again (its new play time) after the settle, never shown stale.
     */
    private fun followIfPicking() {
        val up = window ?: return
        val now = mode.value
        feed.setFollowing(now == WindowMode.Picking)
        if (now != loggedMode) {
            info("Second screen window: ${describe(now)} on display ${up.displayId}")
            loggedMode = now
        }
    }

    private fun describe(shown: WindowMode): String =
        when (shown) {
            WindowMode.Picking -> "picking"
            WindowMode.InGameClock -> "in game, clock and battery"
            WindowMode.InGameBlack -> "in game, black"
        }

    private fun sync(reason: String) {
        val target = wanted.displayId
        val current = window
        if (current != null && current.displayId == target) return
        if (current != null) dismiss(current, reason)
        if (target != null) {
            show(target, reason)
        } else if (wanted.isFinal) {
            // No window will come (switch off, no screen, Plumemu left): the top screen's guess made
            // before the first frame (MainActivity) is corrected, and its details column comes back.
            feed.setWindowShown(false)
        }
    }

    private fun show(
        displayId: Int,
        reason: String,
        attempt: Int = 1,
    ) {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
        if (display == null) {
            info("Second screen window: none, display $displayId is gone ($reason)")
            feed.setWindowShown(false)
            return
        }
        val next =
            try {
                SecondScreenWindow(context, display, ::onGone, ::onDisplayResized, ::onFocusLanded).also {
                    it.prepare(firstPaint()) { WindowContent() }
                    it.setFocusable(catchesStrayFocus)
                }
            } catch (e: RuntimeException) {
                // A display that went away between the lookup and the window: no window, never a crash.
                warn("Second screen window: could not be made for display $displayId (${e.javaClass.simpleName})")
                feed.setWindowShown(false)
                return
            }
        window = next
        try {
            showWindow(next)
        } catch (e: RuntimeException) {
            // InvalidDisplayException, BadTokenException: a crash here would loop when Plumemu is the home app.
            window = null
            // Refused: the top screen has its column while no window is up (a later try may bring it back).
            feed.setWindowShown(false)
            undoFailedShow(next, displayId)
            val retries = attempt < SHOW_ATTEMPTS
            warn("Second screen window: could not show on display $displayId (${e.javaClass.simpleName})${if (retries) ", trying again" else ""}")
            if (retries) retryShow(displayId, reason, attempt + 1)
            return
        }
        feed.hasArtRoom = SecondScreenDimens.forShorterSide(shorterSideDp(display)).showsArt
        feed.setWindowShown(true)
        windowHolder.value = this
        info("Second screen window: shown on display $displayId ($reason)")
        // Shown picking: nothing more to say; shown in game (a display changed during a game): said once.
        loggedMode = WindowMode.Picking
        followIfPicking()
    }

    /**
     * The key focus landed on [landed] while catching: nothing else on its display takes keys. The
     * host ([onStrayFocus], `StrayFocusCatcher`) stops the catching and takes the controller back.
     */
    private fun onFocusLanded(landed: SecondScreenWindow) {
        if (window !== landed || !catchesStrayFocus) return
        info("Second screen window: the controller was left on display ${landed.displayId}; giving it back to the top screen")
        onStrayFocus()
    }

    /** Before the first frame: the top screen's background for the picking cards, black in game. */
    private fun firstPaint(): Int = if (mode.value == WindowMode.Picking) backgroundArgb else InGameColors.background.toArgb()

    /**
     * Android refused the window. Seen right after the previous Activity's window was removed (a
     * warm start: the removal finishes on the system's side a moment later), so a few tries follow;
     * only while the same display is still wanted and no window has come meanwhile.
     */
    private fun retryShow(
        displayId: Int,
        reason: String,
        attempt: Int,
    ) {
        mainHandler.postDelayed(
            { if (following != null && window == null && wanted.displayId == displayId) show(displayId, reason, attempt) },
            SHOW_RETRY_MS,
        )
    }

    /**
     * [failed]'s `show()` threw. `Dialog.show()` runs `onStart` (where the Presentation registers a
     * display listener that holds the window, and so the Activity) before `WindowManager.addView`;
     * when the add is refused the dialog is not showing, so neither `dismiss()` nor `onStop` would
     * ever unregister it, and each refusal would leak an Activity.
     */
    private fun undoFailedShow(
        failed: SecondScreenWindow,
        displayId: Int,
    ) {
        try {
            failed.undoFailedShow()
        } catch (e: RuntimeException) {
            warn("Second screen window: could not undo the refused window on display $displayId (${e.javaClass.simpleName})")
        }
    }

    /** No window of this host any more: another Activity's host waiting for the display may show its own. */
    private fun releaseHolder() {
        if (windowHolder.value === this) windowHolder.value = null
    }

    private fun dismiss(
        current: SecondScreenWindow,
        reason: String,
    ) {
        window = null
        loggedMode = null
        feed.setFollowing(false)
        feed.setWindowShown(false)
        current.dismiss()
        info("Second screen window: dismissed from display ${current.displayId} ($reason)")
        releaseHolder()
    }

    /**
     * Android dismissed [gone] itself (its display was removed). Asked again once the display
     * callbacks have all run: the detection may not have heard of the removal yet, and the new
     * attempt finds the display gone.
     */
    private fun onGone(
        gone: SecondScreenWindow,
        reason: String,
    ) {
        if (window !== gone) return
        window = null
        loggedMode = null
        feed.setFollowing(false)
        feed.setWindowShown(false)
        info("Second screen window: dismissed by Android on display ${gone.displayId} ($reason)")
        releaseHolder()
        resyncSoon("recreated after: $reason")
    }

    /**
     * [changed]'s display has another size or density: its context still has the old ones, so a
     * new window is made. Android up to 10 dismisses a Presentation itself then; later versions
     * keep it, so the host does it the same way on both.
     */
    private fun onDisplayResized(changed: SecondScreenWindow) {
        if (window !== changed) return
        dismiss(changed, "display changed")
        resyncSoon("recreated after: display changed")
    }

    private fun resyncSoon(reason: String) {
        mainHandler.post { if (following != null) sync(reason) }
    }

    @Composable
    private fun WindowContent() {
        val shown by mode.collectAsState()
        when (shown) {
            // Composed only while shown: the clock's tick and battery listeners exist only then.
            WindowMode.InGameClock -> {
                val stored by settings.collectAsState()
                // Unread settings: the default, on, as the top bar before they arrive.
                val showBattery = stored?.homeScreen?.showBattery ?: true
                val card =
                    @Composable {
                        InGameClockCard(
                            clock,
                            showBattery = showBattery,
                            battery = battery,
                            driftNoteDue = stored?.flags?.inGameDriftNoteShown == false,
                            onDriftNoteShown = onDriftNoteShown,
                        )
                    }
                // The battery part's font and Text size are the person's; the clock card's are
                // fixed. Only the type, and only when the part can show: the whole theme would build a
                // palette and watch Android's animator scale all play for nothing.
                if (showBattery && battery !== BatteryReads.None) SettingsType(stored ?: PlumSettings(), card) else card()
            }
            // Nothing that listens or animates: one black frame.
            WindowMode.InGameBlack -> InGameBlack()
            WindowMode.Picking -> {
                val stored by settings.collectAsState()
                val content by feed.content.collectAsState()
                // Show battery off: no battery line on the cards and no battery receiver (as in game).
                stored?.let { current ->
                    SettingsTheme(current, viewedSystem) {
                        CompositionLocalProvider(LocalBatteryPaceNow provides paceNow) {
                            SecondScreenCard(content, images = images, showBattery = current.homeScreen.showBattery)
                        }
                    }
                }
            }
        }
    }

    private data class Want(
        val displayId: Int?,
        val reason: String,
        val mode: WindowMode = WindowMode.Picking,
        /** False while the answer is not known yet (settings unread, another Activity's window still up). */
        val isFinal: Boolean = true,
    )

    companion object {
        const val TAG = "PlumSecondScreen"

        /**
         * The host whose window is up, one per process (main thread). A new Activity can draw its
         * first frame before the old one is destroyed (a warm start, Home to a singleTask Activity
         * that was finishing); Android refuses its window then (`InvalidDisplayException`, seen on
         * the Android 17 emulator), so it waits until the old window has gone.
         */
        private val windowHolder = MutableStateFlow<SecondScreenHost?>(null)

        /** Tries of one `show()` Android refuses, [SHOW_RETRY_MS] apart: about a second in all, then none until the next change. */
        const val SHOW_ATTEMPTS = 5
        const val SHOW_RETRY_MS = 250L

        /** Benchmark builds only (`BuildConfig.BENCHMARK_CONTENT`): show the window even on an Unsure screen. */
        const val EXTRA_BENCHMARK_SECOND_SCREEN = "com.plumemu.benchmark.SECOND_SCREEN"
    }
}

/**
 * The window itself: not focusable (a tap must not take the controller from the top screen or a
 * game, research › 3) except while the host catches a stray focus ([setFocusable]); touchable
 * over the whole display and ignoring every tap, so a tap reaches nothing on the bottom screen
 * (with `FLAG_NOT_TOUCHABLE` it would fall through to the focusable window under ours, Launcher3's
 * secondary home on the Thor, and move the controller there: research › 5). Its
 * own lifecycle, RESUMED while shown: Compose pauses a window's recomposer when its lifecycle owner stops, and
 * the card must keep drawing while the Activity is stopped.
 */
private class SecondScreenWindow(
    context: Context,
    display: Display,
    private val onGone: (SecondScreenWindow, String) -> Unit,
    private val onResized: (SecondScreenWindow) -> Unit,
    private val onFocusLanded: (SecondScreenWindow) -> Unit,
) : Presentation(context, display) {
    val displayId: Int = display.displayId
    val owner = WindowOwner()
    private var goneReason = "dismissed"
    private val madeWith = metricsOf(display)

    fun prepare(
        backgroundArgb: Int,
        content: @Composable () -> Unit,
    ) {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setBackgroundDrawable(ColorDrawable(backgroundArgb))
        }
        // The Presentation's own context (the second display's size and density) in the picked
        // language: Presentation makes it with createDisplayContext, which drops the Activity's
        // language override before Android 13.
        val view =
            ComposeView(AppLocale.localized(this.context)).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                // Nothing there is needed to play: a tap is taken and does nothing.
                setOnTouchListener { _, _ -> true }
                setContent(content)
            }
        setContentView(view)
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        setOnDismissListener { onGone(this, goneReason) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
    }

    /** Only while the host catches a stray focus; the flag takes effect at the window's next layout. */
    fun setFocusable(focusable: Boolean) {
        val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        window?.apply { if (focusable) clearFlags(notFocusable) else addFlags(notFocusable) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) onFocusLanded(this)
    }

    /** Between `onStart` and `onStop`: `show()` starts the window before adding it, so a refused add leaves it started. */
    private var isStarted = false

    override fun onStart() {
        super.onStart()
        isStarted = true
        owner.resume()
    }

    override fun onStop() {
        isStarted = false
        owner.destroy()
        super.onStop()
    }

    /**
     * `show()` threw: stop what it started (the Presentation's own `onStop` unregisters its display
     * listener) and end the window's lifecycle, so nothing outside keeps this window or its Activity.
     */
    fun undoFailedShow() {
        setOnDismissListener(null)
        // Not `isShowing`: it reads the decor view's visibility, already set before the add was refused.
        if (isStarted) onStop()
        owner.destroy()
    }

    override fun onDisplayRemoved() {
        goneReason = "display removed"
        super.onDisplayRemoved()
    }

    /** Also called for a display turning off or another refresh rate: only a new size or density needs a new window. */
    override fun onDisplayChanged() {
        super.onDisplayChanged()
        goneReason = "display changed"
        if (metricsOf(display) != madeWith) onResized(this)
    }
}

private fun shorterSideDp(display: Display): Dp = metricsOf(display).let { (minOf(it.widthPixels, it.heightPixels) / it.density).dp }

private fun metricsOf(display: Display): DisplayMetrics = DisplayMetrics().also { display.getMetrics(it) }

/** The window's lifecycle and saved state: CREATED when made, RESUMED on show, DESTROYED on dismiss or a refused show. */
internal class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun resume() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
