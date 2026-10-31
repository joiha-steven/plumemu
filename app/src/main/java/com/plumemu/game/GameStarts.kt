package com.plumemu.game

import com.plumemu.launcher.LaunchOutcome
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.plumemu.launcher.play.HandOffWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Play and an app's open for [GameMenuController]: one start at a
 * time, "Starting game…" while it runs, and a start given up after [limit] (a
 * sleeping SD card left A dead with no word). Reads and sets the controller's open panel through
 * [panel] and [show]; [ignored] is told of a press made while a start runs (the sound stays quiet).
 * Runs on the main thread like the controller.
 */
internal class GameStarts(
    private val scope: CoroutineScope,
    private val limit: Duration,
    private val log: (String) -> Unit,
    private val panel: () -> OpenPanel?,
    private val show: (OpenPanel?) -> Unit,
    private val ignored: () -> Unit,
) {
    private var launch: Job? = null

    /** The "Starting game…" notice while it is shown; the outcome replaces it as it would the panel Play came from. */
    private var startingNotice: OpenPanel? = null

    /** How to word the notice of the start running now; null when none runs. */
    private var noticeOfStart: (() -> OpenPanel)? = null

    val isLaunching: Boolean get() = launch?.isActive == true

    /**
     * Starts [game] with [start]. The outcome applies to the panel that was open when Play was pressed:
     * a start closes it, a failure replaces it. If another panel opened meanwhile, a start leaves it
     * alone and a failure is shown over it (B returns to it); with nothing open a failure is shown.
     * The notice counts as the panel Play came from. [listing] reads the system's emulators for the
     * no-emulator message.
     */
    fun play(
        game: GameRef,
        start: suspend () -> LaunchOutcome,
        listing: suspend () -> EmulatorListing?,
    ): Boolean {
        if (refused("play: game ${game.key}")) return false
        val startedGame = panel()?.game
        val startedScreen = panel()?.screen
        launch =
            scope.launch {
                val outcome = startWithin("play", game, { OpenPanel(game, PanelScreen.Message(startingMessage()), parent = panel()) }, start)
                // Still "starting" while the no-emulator message's list is read: a second A shows the notice.
                val emulators = if (outcome is LaunchOutcome.NoEmulatorForSystem) listing() else null
                val content = if (outcome == null) noStartMessage() else launchMessage(outcome, emulators)
                val message = content?.let { OpenPanel(game, PanelScreen.Message(it)) }
                val current = panel()
                when {
                    current != null && current === startingNotice -> show(message)
                    current?.screen === startedScreen && current?.game == startedGame -> show(message)
                    message == null -> Unit
                    else -> show(message.copy(parent = current))
                }
                startingNotice = null
                noticeOfStart = null
            }
        return true
    }

    /**
     * Opens [app] with [start]. Apps have no menu, so nothing is open when it starts; a failure is
     * shown over the list as a message, or over a panel the person opened meanwhile (B returns to it);
     * over the notice it takes the notice's place.
     */
    fun openApp(
        app: GameRef,
        start: suspend () -> AppOpenOutcome,
    ): Boolean {
        if (refused("apps: row ${app.key}")) return false
        launch =
            scope.launch {
                val outcome = startWithin("apps", app, { OpenPanel(app, PanelScreen.Message(appOpeningMessage(app.title)), parent = panel()) }, start)
                val content = if (outcome == null) appNoStartMessage(app.title) else appOpenMessage(outcome, app.title)
                val notice = startingNotice
                startingNotice = null
                noticeOfStart = null
                val current = panel()
                when {
                    current != null && current === notice -> show(content?.let { OpenPanel(app, PanelScreen.Message(it), parent = notice.parent) } ?: notice.parent)
                    content == null -> Unit
                    else -> show(OpenPanel(app, PanelScreen.Message(content)).let { if (current == null) it else it.copy(parent = current) })
                }
            }
        return true
    }

    /** A press while a start runs: ignored, but the notice shows at once (not silent). */
    private fun refused(what: String): Boolean {
        if (!isLaunching) return false
        log("$what ignored, a launch is running")
        showStartingNotice()
        ignored()
        return true
    }

    /**
     * Runs [start] and waits at most [limit] for it; null when it did not end in time. The start runs
     * apart from the wait, so a start stuck in a blocking read (a sleeping SD card) cannot hold the
     * wait: it is cancelled and left to unwind on its own, and starts nothing after that
     * (PlayTimeTracker.handOff; the [HandOffWatch] settles which of the two came first: a start
     * already in its hand-off is waited for). The notice made by [notice] shows after [STARTING_NOTICE_DELAY] (a
     * start is usually quicker: no flash), or at once on a second press.
     */
    private suspend fun <T : Any> startWithin(
        what: String,
        game: GameRef,
        notice: () -> OpenPanel,
        start: suspend () -> T,
    ): T? {
        noticeOfStart = notice
        val shown =
            scope.launch {
                delay(STARTING_NOTICE_DELAY)
                showStartingNotice()
            }
        val watch = HandOffWatch()
        val work = scope.async(watch) { start() }
        try {
            val outcome = withTimeoutOrNull(limit) { work.await() } ?: return giveUpOrWait(what, game, watch, work)
            return outcome
        } finally {
            shown.cancel()
        }
    }

    /**
     * The limit is reached: the start is given up unless its hand-off already began (the emulator is
     * being opened: then its real outcome is waited for and reported, never "did not start").
     */
    private suspend fun <T : Any> giveUpOrWait(
        what: String,
        game: GameRef,
        watch: HandOffWatch,
        work: Deferred<T>,
    ): T? {
        if (!watch.giveUp()) {
            log("$what: row ${game.key} over ${limit.inWholeSeconds} s, but the hand-off began; waiting for it")
            return work.await()
        }
        work.cancel()
        log("$what: row ${game.key} did not start within ${limit.inWholeSeconds} s; stopped waiting (a slow or sleeping storage, or a busy system)")
        return null
    }

    /** Shows the notice of the start running now, once, unless it already shows. */
    private fun showStartingNotice() {
        if (startingNotice != null && panel() === startingNotice) return
        val notice = noticeOfStart?.invoke() ?: return
        startingNotice = notice
        show(notice)
    }

    companion object {
        /** A start that has not handed over to the emulator by then is given up. */
        val START_LIMIT: Duration = 20.seconds

        /** "Starting game…" waits this long, so a quick start shows no flash. */
        val STARTING_NOTICE_DELAY: Duration = 500.milliseconds
    }
}
