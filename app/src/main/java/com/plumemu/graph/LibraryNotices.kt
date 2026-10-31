package com.plumemu.graph

import com.plumemu.coredata.db.AddedSession
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.library.scan.ScanStats
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What a change check found, for the home's top-left header ("+24 games · Nintendo 64"). [added] and
 * [removed] are game counts; [addedSystems] the ids of the systems that got new games. [serial]
 * makes two equal notices in a row two notices, so the header shows the second one too.
 */
data class LibraryNotice(
    val serial: Long,
    val added: Int,
    val removed: Int,
    val addedSystems: List<String>,
    /**
     * Names of folders that no system claims and that the check found for the first time; when
     * there are no game changes the header says so alone ("Unknown folder: Xbox 360 · Settings › Library").
     */
    val unknownFolders: List<String> = emptyList(),
)

/**
 * An emulator that Plumemu started returned in under `PlayTimeTracker.MIN_SESSION_SECONDS`: it crashed, showed its own
 * error or could not open the game. [serial] makes two in a row two messages.
 */
data class ClosedAtOnce(
    val serial: Long,
    val gameKey: String,
    val gameTitle: String,
    val emulatorName: String,
)

/**
 * A launch asked RetroArch for a core nobody has confirmed (Plumemu cannot list RetroArch's cores):
 * on return the home asks whether [gameTitle] started.
 * [emulatorId] / [emulatorName] are the RetroArch entry ("RetroArch: Mesen"); [added] is the play
 * session the return recorded, taken back on No (null when nothing was recorded: under ten seconds).
 * Questions for the same game, emulator and core that were still waiting to be shown collapse into
 * one (repeated tries stacked several "Did it start?"); [plays] is every session
 * the one question stands for ([added] first), so No takes them all back and Yes or B sends them all.
 */
data class CoreQuestion(
    val serial: Long,
    val gameKey: String,
    val gameTitle: String,
    val emulatorId: String,
    val emulatorName: String,
    val core: CoreToConfirm,
    val added: AddedSession?,
) {
    private val collected = CopyOnWriteArrayList(listOfNotNull(added))

    /** Every play recorded for this question, in order; a later try for the same game and core joins it until it is shown. */
    val plays: List<AddedSession> get() = collected.toList()

    internal fun collect(play: AddedSession?) {
        if (play != null) collected.add(play)
    }

    internal fun asksTheSameAs(gameKey: String, emulatorId: String, core: CoreToConfirm) =
        this.gameKey == gameKey && this.emulatorId == emulatorId && this.core == core
}

/**
 * The home's quiet news about the library: [notice] is what the header says for
 * a few seconds, [newGameSystems] the systems that show a dot until they are opened. Both live in
 * memory only: a restart forgets them (no stored flag exists for them), and the library the person
 * sees after a restart is simply the library. Fed by change checks only, so the first full scan
 * after setup and a manual rescan stay silent.
 */
class LibraryNotices {
    private val mutableNotice = MutableStateFlow<LibraryNotice?>(null)
    private val mutableNewGameSystems = MutableStateFlow<Set<String>>(emptySet())
    private val mutablePickerMissing = MutableStateFlow(false)
    private var serial = 0L

    /** Unknown folders found together with game changes: said after that notice, so neither replaces the other. */
    private var pendingUnknown: List<String> = emptyList()

    val notice: StateFlow<LibraryNotice?> get() = mutableNotice
    val newGameSystems: StateFlow<Set<String>> get() = mutableNewGameSystems

    private val mutableClosedAtOnce = MutableStateFlow<ClosedAtOnce?>(null)

    /** The emulator that closed at once, until the home has shown its message. */
    val closedAtOnce: StateFlow<ClosedAtOnce?> get() = mutableClosedAtOnce

    fun emulatorClosedAtOnce(gameKey: String, gameTitle: String, emulatorName: String) {
        mutableClosedAtOnce.value = ClosedAtOnce(++serial, gameKey, gameTitle, emulatorName)
    }

    /** The home showed [message] (or could not, and will not try again). */
    fun closedAtOnceShown(message: ClosedAtOnce) {
        mutableClosedAtOnce.update { current -> if (current?.serial == message.serial) null else current }
    }

    private val mutableCoreQuestion = MutableStateFlow<CoreQuestion?>(null)

    /** One lock for the questions and the held sessions: a question and the plays it holds appear and go together. */
    private val lock = Any()

    /** Questions posted while another waits to be shown: each keeps its own session to take back. */
    private val waitingQuestions = ArrayDeque<CoreQuestion>()

    /** Whether the game started with a core never confirmed, until the home has asked; the next one waits its turn. */
    val coreQuestion: StateFlow<CoreQuestion?> get() = mutableCoreQuestion

    /**
     * Posts the question, or adds [added] to the same question that still waits to be shown (same game, emulator and
     * core). [held], the return's `game_session`, is held in the same step, so no home made again can release it as B
     * between the hold and the question.
     */
    fun askAboutCore(
        gameKey: String,
        gameTitle: String,
        emulatorId: String,
        emulatorName: String,
        core: CoreToConfirm,
        added: AddedSession?,
        held: PlaySessionOutcome.Recorded? = null,
    ) {
        synchronized(lock) {
            if (held != null && added != null) heldSessions[added.sessionId] = held
            val waiting = listOfNotNull(mutableCoreQuestion.value) + waitingQuestions
            val same = waiting.firstOrNull { it.asksTheSameAs(gameKey, emulatorId, core) }
            if (same != null) {
                same.collect(added)
                return
            }
            val question = CoreQuestion(++serial, gameKey, gameTitle, emulatorId, emulatorName, core, added)
            if (mutableCoreQuestion.value == null) mutableCoreQuestion.value = question else waitingQuestions.addLast(question)
        }
    }

    /** The home asked [question] (or could not, and will not try again): the next waiting one follows. */
    fun coreQuestionShown(question: CoreQuestion) {
        synchronized(lock) {
            if (mutableCoreQuestion.value?.serial == question.serial) mutableCoreQuestion.value = waitingQuestions.removeFirstOrNull()
        }
    }

    /**
     * `game_session` of a return that carries a core question, held until the question is over: No
     * takes the play back and drops it ([dropHeldSession]); Yes or B sends it ([releaseHeldSession]).
     * Memory only: a process that dies with the question open sends nothing.
     */
    private val heldSessions = HashMap<Long, PlaySessionOutcome.Recorded>()

    fun holdSession(added: AddedSession, session: PlaySessionOutcome.Recorded) {
        synchronized(lock) { heldSessions[added.sessionId] = session }
    }

    fun dropHeldSession(added: AddedSession) {
        synchronized(lock) { heldSessions.remove(added.sessionId) }
    }

    /**
     * The held sessions of questions already shown, for a home made again (rotation, theme, language)
     * whose panel went with the old one: such a question can no longer be answered, so it counts as
     * B and the play is sent. The question still to show and those waiting keep theirs.
     */
    fun releaseHeldOffScreen(): List<PlaySessionOutcome.Recorded> {
        return synchronized(lock) {
            val stillAsked = (listOfNotNull(mutableCoreQuestion.value) + waitingQuestions).flatMap { it.plays }.map { it.sessionId }.toSet()
            val offScreen = heldSessions.keys.filter { it !in stillAsked }
            offScreen.mapNotNull { heldSessions.remove(it) }
        }
    }

    /** The held sessions of [question], once each; none when it had none or No dropped them. */
    fun releaseHeldSession(question: CoreQuestion): List<PlaySessionOutcome.Recorded> =
        synchronized(lock) { question.plays.mapNotNull { heldSessions.remove(it.sessionId) } }

    /** The empty home asked for the folder picker and this device has none. */
    val pickerMissing: StateFlow<Boolean> get() = mutablePickerMissing

    fun pickerAsked(isMissing: Boolean) {
        mutablePickerMissing.value = isMissing
    }

    /**
     * A change check ended with [stats] (one per scanned folder); nothing is shown when nothing was added or removed
     * and no unknown folder is new. Only change checks come here: the full scan after an upgrade that made every
     * folder be listed again reports every existing unknown folder as new, and it never reaches this.
     */
    fun checked(stats: Collection<ScanStats>) {
        val added = stats.sumOf { it.gamesAdded }
        val removed = stats.sumOf { it.gamesRemoved }
        val unknown = stats.flatMap { found -> found.newUnknownFolders.map { it.relativePath } }.distinct()
        if (added <= 0 && removed <= 0) {
            if (unknown.isNotEmpty()) {
                val current = mutableNotice.value
                if (current != null && (current.added > 0 || current.removed > 0)) {
                    // A games notice is still up: the folders follow it rather than replace it.
                    pendingUnknown = (pendingUnknown + unknown).distinct()
                } else {
                    mutableNotice.value = LibraryNotice(++serial, 0, 0, emptyList(), (current?.unknownFolders.orEmpty() + unknown).distinct())
                }
            }
            return
        }
        val systems = stats.flatMap { it.addedBySystem.filterValues { n -> n > 0 }.keys }.distinct()
        mutableNewGameSystems.update { it + systems }
        // An unknown-only notice that this one replaces keeps its folder names for after it.
        mutableNotice.value?.takeIf { it.added <= 0 && it.removed <= 0 }?.let { pendingUnknown = (pendingUnknown + it.unknownFolders).distinct() }
        mutableNotice.value = LibraryNotice(++serial, added.coerceAtLeast(0), removed.coerceAtLeast(0), systems)
        if (unknown.isNotEmpty()) pendingUnknown = (pendingUnknown + unknown).distinct()
    }

    /** The header showed [notice] for its time. */
    fun noticeShown(notice: LibraryNotice) {
        var cleared = false
        mutableNotice.update { current -> if (current?.serial == notice.serial) { cleared = true; null } else current }
        if (cleared && pendingUnknown.isNotEmpty()) {
            mutableNotice.value = LibraryNotice(++serial, 0, 0, emptyList(), pendingUnknown)
            pendingUnknown = emptyList()
        }
    }

    /** [systemId] was opened: its dot goes. */
    fun opened(systemId: String) {
        mutableNewGameSystems.update { it - systemId }
    }
}
