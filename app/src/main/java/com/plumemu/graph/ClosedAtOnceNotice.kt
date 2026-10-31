package com.plumemu.graph

import android.util.Log
import com.plumemu.coredata.db.AddedSession
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the play time's answer means for the rest of the app: a counted session goes to the statistics
 * (once its core question is over, [coreQuestionOver]), a too short one may need a word. A launch with a RetroArch core never confirmed asks whether the
 * game started instead, whatever the time (never both: the question covers the early close too).
 */
internal suspend fun AppGraph.afterPlay(outcome: PlaySessionOutcome) {
    when (outcome) {
        is PlaySessionOutcome.Recorded -> {
            val core = outcome.coreToConfirm
            val added = outcome.added
            // game_session waits for the answer: No takes the play back, and then nothing is sent. The hold and the
            // question are one step (askAboutCore), so the play is never held without its question.
            val asked = core != null && askAboutCore(outcome.gameId, outcome.emulatorId, core, added, held = outcome)
            if (!asked) trackSession(outcome)
        }
        is PlaySessionOutcome.TooShort -> {
            val core = outcome.coreToConfirm
            if (core != null) askAboutCore(outcome.gameId, outcome.emulatorId, core, added = null) else tellClosedAtOnce(outcome)
        }
        else -> Unit
    }
}

/**
 * What the Activity's `onResume` starts once the person is back from a game: the pending play session is
 * cleared and written, then [afterPlay] counts it or asks about the core. It runs in [AppGraph.appScope], not the
 * Activity's scope: a hung emulator brings Plumemu back as a recreated Activity, and the old one's scope is cancelled
 * while the work runs; the session is already cleared by then, so a cancelled return would lose the question and the
 * play would stay. A stray failure is logged, never thrown.
 */
internal fun AppGraph.afterReturn(): Job =
    appScope.launch {
        try {
            // The same time Settings › Statistics counts goes to the statistics (game_session).
            launching()?.playTime?.onResume()?.let { afterPlay(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Play time could not be recorded (${e.javaClass.simpleName})")
        }
        // After the play is cleared (HeldForPlay's order), and even when recording it failed: what stopped for the game goes on.
        try {
            resumeHeldWork()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Work held for a game could not be asked for again (${e.javaClass.simpleName})")
        }
    }

/** Asks again for the content pass and the box-art run that stopped while a game was played. */
internal suspend fun AppGraph.resumeHeldWork() = resumeHeld(content.held, content::scanEnded, { scrapes.requestRun() }) { Log.i(TAG, it) }

/** [resumeHeldWork] without the graph: takes the marks once, asks for each marked work. */
internal suspend fun resumeHeld(
    held: HeldForPlay,
    contentPass: suspend () -> Unit,
    boxArt: () -> Unit,
    log: (String) -> Unit,
) {
    val marked = withContext(Dispatchers.IO) { held.takeHeld() }
    if (marked.isEmpty()) return
    log("Back from a game: ${marked.joinToString()} goes on")
    if (HeldWork.ContentPass in marked) contentPass()
    if (HeldWork.BoxArt in marked) boxArt()
}

/**
 * Posts the core question for the home. Nothing when the
 * launch named no emulator or the game cannot be read (logged; false): the core stays unconfirmed, so the
 * next launch asks.
 */
private suspend fun AppGraph.askAboutCore(gameId: Long, emulatorId: String?, core: CoreToConfirm, added: AddedSession?, held: PlaySessionOutcome.Recorded? = null): Boolean {
    if (emulatorId == null) return false
    return try {
        val game = database.gameDao().game(gameId) ?: return false
        val emulatorName = catalog()?.emulator(emulatorId)?.name ?: emulatorId
        libraryNotices.askAboutCore(gameId.toString(), game.title, emulatorId, emulatorName, core, added, held)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Game $gameId: whether core ${core.core} started was not asked (${e.javaClass.simpleName})")
        false
    }
}

/** A home screen started: plays held for a question shown by an earlier one count now (as B), once. */
internal fun AppGraph.coreQuestionsRestarted() {
    libraryNotices.releaseHeldOffScreen().forEach(::trackSession)
}

/** The home closed the panel of [question] (Yes, B, or No's follow-up): its held `game_session` goes now, unless No dropped it. */
internal fun AppGraph.coreQuestionOver(question: CoreQuestion) {
    libraryNotices.releaseHeldSession(question).forEach(::trackSession)
}

private const val TAG = "ClosedAtOnce"

/**
 * An emulator Plumemu started came back in under `PlayTimeTracker.MIN_SESSION_SECONDS` (10): the
 * home says so, naming the emulator. Nothing for an app of the Apps entry or an older record
 * (no emulator id), a pinned shortcut (its app may hand the game to another process and come back at
 * once, and it has no Play with… to point to), an installed app listed as a game (likewise), or when the game or emulator cannot be read.
 * The play time is counted as before (a short session is simply not counted).
 */
internal suspend fun AppGraph.tellClosedAtOnce(outcome: PlaySessionOutcome.TooShort) {
    val emulatorId = outcome.emulatorId ?: return
    try {
        val game = database.gameDao().game(outcome.gameId) ?: return
        if (database.pinnedShortcutDao().byGame(outcome.gameId) != null || database.appGameDao().isAppGameRow(outcome.gameId)) return
        val emulatorName = catalog()?.emulator(emulatorId)?.name ?: emulatorId
        libraryNotices.emulatorClosedAtOnce(outcome.gameId.toString(), game.title, emulatorName)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Game ${outcome.gameId}: the emulator's early close was not told (${e.javaClass.simpleName})")
    }
}
