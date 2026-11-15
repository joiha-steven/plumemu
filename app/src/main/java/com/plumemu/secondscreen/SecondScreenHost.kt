package com.plumemu.secondscreen

import androidx.compose.ui.Modifier
import com.plumemu.coreui.theme.ProvidePlumMotion
import com.plumemu.coreui.theme.fadingIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toArgb
import com.plumemu.battery.BatteryPaceNow
import com.plumemu.battery.LocalBatteryPaceNow
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.HomeAppIcons
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.game.BadgeImages
import com.plumemu.game.LocalBadgeImages
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue


/**
 * The second screen's card: [SecondScreenCardActivity] on the chosen display while there is a
 * second screen and it is on ([isSecondScreenOn]), showing [feed]'s content in the stored theme.
 * Owned by `MainActivity`: [start] after its first content frame, [stop] in its `onDestroy`. Main
 * thread only.
 *
 * Its mode follows [presence]: the picking cards while Plumemu is visible, the In game setting
 * (the clock card or black) while a game or an app Plumemu started runs, and nothing of Plumemu's
 * when Plumemu was left any other way: the card is closed then, or drawn blank while it is the
 * bottom display's home ([SecondScreenHome]), which Android would only bring back.
 */
internal class SecondScreenHost(
    /** The Activity: it starts the card. */
    private val context: Context,
    /** The Activity's scope (main thread); cancelled when it is destroyed. */
    private val scope: CoroutineScope,
    private val screens: StateFlow<SecondScreen?>,
    private val settings: StateFlow<PlumSettings?>,
    private val feed: SecondScreenFeed,
    /** Whether Plumemu is visible, playing, or left ([SecondScreenPresence.current]). */
    private val presence: StateFlow<Presence>,
    /** What the card paints before its first frame: the cached background of the top screen. */
    private val backgroundArgb: Int,
    /** Benchmark builds asked to measure the card on a screen that is off by default (the emulator's overlay). */
    private val forcedOn: Boolean = false,
    /** Decodes the box art the picking card shows; none: cards without art. */
    private val images: CoverImages = CoverImages.None,
    /** The game card's achievement badges (`BadgeImages.loadCached`: the disk cache only); none: the empty badge face. */
    private val badges: BadgeImages = BadgeImages.None,
    /** Android's icons for the card of an app game; none: the stand-in letter. */
    private val appIcons: HomeAppIcons = HomeAppIcons.None,
    /** Starts the card on a display ([startSecondScreenCard]); a test makes it throw or builds the activity itself. */
    private val startCard: (displayId: Int) -> Unit = { startSecondScreenCard(context, it) },
    /** Whether the card is the bottom display's home; null: never (tests that do not need it). */
    private val home: SecondScreenHome? = null,
    /** The in-game clock's time source; a test passes its own. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Reads the battery while the in-game card is up; none: no reads. */
    private val battery: BatteryReads = BatteryReads.None,
    /** Works the picking line's figures out again when the battery changes; null: the line keeps those read with the facts. */
    private val paceNow: BatteryPaceNow? = null,
    /** The in-game card's once-ever drift note started showing: stores `flags.in_game_drift_note_shown`. */
    private val onDriftNoteShown: () -> Unit = {},
    /** The system last viewed on the top screen, for Accent color › By system; null: the fallback accent. */
    private val viewedSystem: ViewedSystem? = null,
    private val info: (String) -> Unit = { Log.i(TAG, it) },
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var following: Job? = null
    private var wanted: Want = Want(null, "not started")

    /** The display this host shows the card on (it holds the card); null while it shows none. */
    private var shownOn: Int? = null

    private val goneListener: (String) -> Unit = { onCardGone(it) }

    /** Cards that went without Plumemu closing them since the wanted state last changed. */
    private var losses = 0

    /** Tries again once the quick tries are used up. */
    private val late =
        LateShowTries(
            context,
            mainHandler,
            warn,
            LATE_RETRY_MS,
            isWanted = { displayId -> following != null && shownOn == null && wanted.displayId == displayId },
            tryNow = { displayId -> show(displayId, wanted.reason, SHOW_ATTEMPTS) },
        )

    /** What the card draws; the card's composition reads it. */
    private val mode = MutableStateFlow(WindowMode.Picking)

    /** The mode the shown card was last logged in (one line per change while it is up). */
    private var loggedMode: WindowMode? = null

    /** What the card draws now, for tests. */
    internal val shownMode: WindowMode? get() = if (shownOn != null) mode.value else null

    /** The display the card is shown on now; null without one. */
    val shownDisplayId: Int? get() = shownOn

    /** The live card while this host shows it, for tests. */
    internal val shownWindow: SecondScreenCardActivity? get() = if (shownOn != null) CardInstances.live?.takeIf { cardHolder.value === this } else null

    /** Follows the screen and the switch until [stop]. */
    fun start() {
        if (following != null) return
        following =
            scope.launch {
                val claimed = presence.onEach { if (it == Presence.InPlumemu) activityInFront.value = this@SecondScreenHost }
                combine(screens, settings, cardHolder, claimed, activityInFront) { screen, stored, holder, where, inFront ->
                    // The home check is asked again on each change, so also each time Plumemu comes back to the front.
                    home?.update(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && stored != null && isSecondScreenOn(stored.secondScreen, screen))
                    when {
                        // Home makes a second Activity beside one opened from an icon, and the first is never destroyed: it must let go.
                        where != Presence.InPlumemu && inFront != null && inFront !== this@SecondScreenHost -> Want(null, "another activity is in front", keepsCard = true)
                        // That card is still held: not a reason to bring the top screen's column back.
                        holder != null && holder !== this@SecondScreenHost -> Want(null, "another activity's card is up", keepsCard = true)
                        else -> wantFor(screen, stored, where)
                    }
                }
                    .distinctUntilChanged()
                    .collect { want ->
                        wanted = want
                        losses = 0
                        mode.value = want.mode
                        sync(want)
                        followIfPicking()
                    }
            }
        CardInstances.onGone = goneListener
    }

    /** The Activity is going: the card goes with it, or stays blank while it is the bottom display's home. */
    fun stop() {
        following?.cancel()
        following = null
        if (activityInFront.value === this) activityInFront.value = null
        if (CardInstances.onGone === goneListener) CardInstances.onGone = null
        wanted = Want(null, "activity destroyed", keepsCard = true)
        late.stop()
        sync(wanted)
    }

    private fun wantFor(
        screen: SecondScreen?,
        stored: PlumSettings?,
        where: Presence,
    ): Want {
        val reason =
            when {
                screen == null -> return Want(null, "no second screen")
                stored == null -> return Want(null, "settings not read yet")
                // Before Android 10 one activity is resumed at a time: starting the card would pause MainActivity, and the
                // card could never give the controller back. No card there: the bottom screen shows nothing of Plumemu's.
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> return Want(null, "Android older than 10")
                isSecondScreenOn(stored.secondScreen, screen) -> "switched on"
                forcedOn -> "benchmark"
                else -> return Want(null, "switched off")
            }
        val shown = windowModeOf(where, stored.secondScreen.inGame) ?: return Want(null, "Plumemu left", keepsCard = true)
        return Want(screen.displayId, reason, shown)
    }

    /**
     * The top screen feeds the card only while it shows the picking cards. In game the feed
     * goes back to Calm and stops, so on the way back the card of the game just played is read
     * again (its new play time) as the card's first, without the settle, never shown stale.
     */
    private fun followIfPicking() {
        val up = shownOn ?: return
        val now = mode.value
        feed.setFollowing(now == WindowMode.Picking)
        if (now != loggedMode) {
            info("Second screen card: ${describe(now)} on display $up")
            loggedMode = now
        }
    }

    private fun describe(shown: WindowMode): String =
        when (shown) {
            WindowMode.Picking -> "picking"
            WindowMode.InGameClock -> "in game, clock and battery"
            WindowMode.InGameBlack -> "in game, black"
        }

    private fun sync(want: Want) {
        val target = want.displayId
        val current = shownOn
        if (current != null && current == target) return
        late.stop()
        if (current != null) dismiss(current, want)
        if (target != null) show(target, want.reason)
    }

    private fun show(
        displayId: Int,
        reason: String,
        attempt: Int = 1,
    ) {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
        if (display == null) {
            info("Second screen card: none, display $displayId is gone ($reason)")
            return
        }
        // Held and named before the start, so the card made by it knows its display is the second screen's and draws this
        // host's content from its first frame.
        shownOn = displayId
        cardHolder.value = this
        val live = CardInstances.live
        if (live == null || live.displayIdNow() != displayId) {
            try {
                startCard(displayId)
            } catch (e: RuntimeException) {
                // A refused start (SecurityException, a display gone): a crash here would loop when Plumemu is the home app.
                shownOn = null
                releaseHolder()
                val retries = attempt < SHOW_ATTEMPTS
                warn("Second screen card: could not start on display $displayId (${e.javaClass.simpleName})${if (retries) ", trying again" else ""}")
                if (retries) retryShow(displayId, reason, attempt + 1) else late.start(displayId)
                return
            }
        }
        feed.hasArtRoom = SecondScreenDimens.forShorterSide(shorterSideDp(display)).showsArt
        val wasLate = late.isActive
        late.stop()
        info("Second screen card: ${if (live == null) "started" else "shown"} on display $displayId (${if (wasLate) "late try, " else ""}$reason)")
        // Shown picking: nothing more to say; shown in game (a display changed during a game): said once.
        loggedMode = WindowMode.Picking
        followIfPicking()
    }

    /** Before the first frame: the top screen's background for the picking cards, black in game. */
    internal fun firstPaint(): Int = if (mode.value == WindowMode.Picking) backgroundArgb else InGameColors.background.toArgb()

    /**
     * Android refused the start. A refused window was seen right after the previous Activity's
     * window was removed (a warm start), so a few tries follow; only while the same display is still
     * wanted and no card has come meanwhile.
     */
    private fun retryShow(
        displayId: Int,
        reason: String,
        attempt: Int,
    ) {
        mainHandler.postDelayed(
            { if (following != null && shownOn == null && wanted.displayId == displayId) show(displayId, reason, attempt) },
            SHOW_RETRY_MS,
        )
    }

    /** No card of this host any more: another Activity's host waiting for it may show its own. */
    private fun releaseHolder() {
        if (cardHolder.value === this) cardHolder.value = null
    }

    /**
     * Stops showing on [from]. The card is closed, except while it is the bottom display's home and
     * Plumemu only left the front ([Want.keepsCard]): Android would make it again at once, so it
     * stays, drawing nothing of Plumemu's.
     */
    private fun dismiss(
        from: Int,
        want: Want,
    ) {
        shownOn = null
        loggedMode = null
        feed.setFollowing(false)
        val kept = want.keepsCard && home?.isOn == true
        // Closed before the card is let go: a host waiting for it would otherwise find this one still live.
        if (!kept) CardInstances.finishLive()
        info("Second screen card: ${if (kept) "blank" else "closed"} on display $from (${want.reason})")
        releaseHolder()
    }

    /**
     * The card went without Plumemu closing it (its display removed, Android ending it). Asked
     * again once the display callbacks have all run: the detection may not have heard of a removal
     * yet, and the new attempt finds the display gone. A few times per wanted state, never a loop.
     */
    private fun onCardGone(reason: String) {
        val on = shownOn ?: return
        if (cardHolder.value !== this) return
        shownOn = null
        loggedMode = null
        feed.setFollowing(false)
        info("Second screen card: gone from display $on ($reason)")
        if (++losses > SHOW_ATTEMPTS) {
            warn("Second screen card: gone $losses times; not started again until the next change")
            releaseHolder()
            return
        }
        mainHandler.post { if (following != null && shownOn == null) wanted.displayId?.let { show(it, "started again after: $reason") } }
    }

    /** The card's content: the mode, the cards, the clock. */
    @Composable
    internal fun CardContent() {
        val shown by mode.collectAsState()
        val animations by settings.collectAsState()
        // The in-game card settles in from smaller as a game starts and the cards from larger as it ends, with the top
        // screen's tokens; the card's first content shows at once. Only the motion setting is read here, not the theme.
        ProvidePlumMotion(animations?.advanced?.animations ?: true) {
            val swap = rememberModeSwap(shown)
            Box(Modifier.fillMaxSize().fadingIn(swap)) { ModeContent(shown) }
        }
    }

    @Composable
    private fun ModeContent(shown: WindowMode) {
        when (shown) {
            // Composed only while shown: the clock's tick and battery listeners exist only then.
            WindowMode.InGameClock -> {
                val stored by settings.collectAsState()
                val card =
                    @Composable {
                        InGameClockCard(
                            clock,
                            battery = battery,
                            driftNoteDue = stored?.flags?.inGameDriftNoteShown == false,
                            onDriftNoteShown = onDriftNoteShown,
                        )
                    }
                // The battery part's font and Text size are the person's; the clock card's are
                // fixed. Only the type, and only when the part can show: the whole theme would build a
                // palette and watch Android's animator scale all play for nothing.
                if (battery !== BatteryReads.None) SettingsType(stored ?: PlumSettings(), card) else card()
            }
            // Nothing that listens or animates: one black frame.
            WindowMode.InGameBlack -> InGameBlack()
            WindowMode.Picking -> {
                val stored by settings.collectAsState()
                val content by feed.content.collectAsState()
                stored?.let { current ->
                    cardTrace("SS:theme") {
                        SettingsTheme(current, viewedSystem) {
                            CompositionLocalProvider(LocalBatteryPaceNow provides paceNow) {
                                CompositionLocalProvider(LocalHomeAppIcons provides appIcons, LocalBadgeImages provides badges) {
                                    cardTrace("SS:card") { SecondScreenCard(content, images = images) }
                                }
                            }
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
        /** Plumemu left or another Activity takes over: a card that is the bottom display's home stays, blank. */
        val keepsCard: Boolean = false,
    )

    companion object {
        const val TAG = "PlumSecondScreen"

        /**
         * The host whose card is up, one per process (main thread): the card draws its content. A
         * new Activity can draw its first frame before the old one is destroyed (a warm start, Home
         * to a singleTask Activity that was finishing): it waits until the old host lets go.
         */
        internal val cardHolder = MutableStateFlow<SecondScreenHost?>(null)

        /** The host of the Activity last started; another host left in game gives up the card to it. */
        private val activityInFront = MutableStateFlow<SecondScreenHost?>(null)

        /** Tries of one start Android refuses, [SHOW_RETRY_MS] apart: about a second in all, then none until the next change. */
        const val SHOW_ATTEMPTS = 5
        const val SHOW_RETRY_MS = 250L

        /** After those, one more try each [LATE_RETRY_MS] and on each display change, while the same card is wanted (`LateShowTries`). */
        const val LATE_RETRY_MS = 5_000L

        /** Benchmark builds only (`BuildConfig.BENCHMARK_CONTENT`): show the card even on an Unsure screen. */
        const val EXTRA_BENCHMARK_SECOND_SCREEN = "com.plumemu.benchmark.SECOND_SCREEN"
    }
}
