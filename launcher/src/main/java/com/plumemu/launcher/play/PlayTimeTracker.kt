package com.plumemu.launcher.play

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.PlayDao
import com.plumemu.coredata.db.SessionBattery
import com.plumemu.launcher.AndroidLauncherLog
import com.plumemu.launcher.LauncherLog
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.kind
import com.plumemu.launcher.rethrowIfCallerCancelled
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the Activity's resume did with the play time of the last game. */
sealed interface PlaySessionOutcome {
    data object NothingPending : PlaySessionOutcome

    /**
     * [emulatorId]: the platform data's emulator started, when the launch said (null for an app).
     * [coreToConfirm]: the RetroArch core not yet confirmed (the app asks whether the game started);
     * then [added] is the session written, for [PlayTimeTracker.takeBack] (null otherwise).
     */
    data class Recorded(
        val gameId: Long,
        val seconds: Long,
        val emulatorId: String? = null,
        val coreToConfirm: CoreToConfirm? = null,
        val added: AddedSession? = null,
    ) : PlaySessionOutcome

    /** Under [PlayTimeTracker.MIN_SESSION_SECONDS]: the emulator failed to open, or the person backed out. */
    data class TooShort(
        val gameId: Long,
        val seconds: Long,
        val emulatorId: String? = null,
        val coreToConfirm: CoreToConfirm? = null,
    ) : PlaySessionOutcome

    /** The game left the library meanwhile (rescan, folder removed); nothing to add the time to. */
    data class GameGone(val gameId: Long) : PlaySessionOutcome

    /** The clocks give a negative time (a clock set back, on Android 6 or across a reboot). */
    data class Unmeasurable(val gameId: Long) : PlaySessionOutcome

    data class Failed(val gameId: Long, val error: Throwable) : PlaySessionOutcome
}

/** The two clocks and the boot count; a seam so tests can move time and reboot. */
internal interface PlayClock {
    fun wallMs(): Long

    fun realtimeMs(): Long

    /** How many times the device has booted; null when unknown (Android 6 has no count). */
    fun bootCount(): Int?
}

internal class SystemPlayClock(private val resolver: ContentResolver) : PlayClock {
    override fun wallMs(): Long = System.currentTimeMillis()

    override fun realtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT)
        } catch (_: Settings.SettingNotFoundException) {
            null
        }
    }
}

/**
 * Play time "from launch to coming back" (SPEC > Library): [onGameLaunched] notes the start on
 * disk, the Activity's next [onResume] adds the elapsed time to `play`. The pending start survives
 * process death; it is cleared before the time is added, so a second resume never counts it twice.
 */
class PlayTimeTracker internal constructor(
    private val store: PendingSessionStore,
    private val playDao: PlayDao,
    private val gameDao: GameDao,
    private val clock: PlayClock,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
    /** The battery measured during the play (the app's meter), stored with its session. */
    private val battery: PlayBattery = PlayBattery.None,
) {
    constructor(context: Context, playDao: PlayDao, gameDao: GameDao, battery: PlayBattery = PlayBattery.None) :
        this(
            SharedPreferencesSessionStore(context), playDao, gameDao, SystemPlayClock(context.applicationContext.contentResolver),
            AndroidLauncherLog, Dispatchers.IO, battery,
        )

    private val mutex = Mutex()

    /**
     * Called right before the emulator starts; [GameLauncher] does it, the Activity need not. [emulatorId] is kept for the
     * statistics, [coreToConfirm] (a RetroArch core never confirmed) comes back with the outcome.
     */
    suspend fun onGameLaunched(
        gameId: Long,
        emulatorId: String? = null,
        coreToConfirm: CoreToConfirm? = null,
    ) {
        noteStart(gameId, emulatorId, coreToConfirm)
    }

    /** [onGameLaunched], returning the note written (what [takeBack] may remove later). */
    private suspend fun noteStart(
        gameId: Long,
        emulatorId: String?,
        coreToConfirm: CoreToConfirm?,
    ): PendingSession = mutex.withLock {
        withContext(io) {
            val previous = store.read()
            if (previous != null) log.info("play time: session of game ${previous.gameId} replaced before a resume")
            battery.onPlayStarting(usualDrawOf(gameId))
            val note = PendingSession(gameId, clock.wallMs(), clock.realtimeMs(), clock.bootCount(), emulatorId, coreToConfirm)
            if (!store.write(note)) log.warn("play time: could not save the start of game $gameId; its time may be lost")
            note
        }
    }

    /**
     * Removes [own] only if it is still the stored note. A start given up while its note was written
     * (slow storage) can reach here after the person pressed A again and the next start wrote its own
     * note: removing that one would lose the next game's play time (review of audit A4).
     */
    private suspend fun takeBack(own: PendingSession) =
        withContext(NonCancellable) {
            mutex.withLock {
                withContext(io) {
                    val stored = store.read()
                    when {
                        stored == own -> {
                            if (!store.clear()) log.warn("play time: note of game ${own.gameId} not taken back; the next return may count it")
                        }
                        else -> log.info("play time: note of game ${own.gameId} already replaced by a later start; kept")
                    }
                }
            }
        }

    /**
     * The game's usual battery pace for the in-game part; null when never measured,
     * with no meter, or when the read fails (logged: the part then waits for its own first minute).
     */
    private suspend fun usualDrawOf(gameId: Long): Long? {
        if (battery === PlayBattery.None) return null
        return try {
            playDao.usualDraw(gameId)
        } catch (e: CancellationException) {
            e.rethrowIfCallerCancelled()
            log.warn("play time: usual battery pace of game $gameId not read (${e.javaClass.simpleName})")
            null
        } catch (e: SQLiteException) {
            log.warn("play time: usual battery pace of game $gameId not read (${e.javaClass.simpleName})")
            null
        } catch (e: IllegalStateException) {
            log.warn("play time: usual battery pace of game $gameId not read (${e.javaClass.simpleName})")
            null
        }
    }

    /**
     * The launch did not happen after all (the emulator, or a pinned shortcut, could not be started).
     * Never cancelled half-way: a caller that was cancelled (the app stopped waiting for a slow start)
     * must still drop the note, or the next return would count a play that never was.
     */
    suspend fun cancelPending() = withContext(NonCancellable) { mutex.withLock { withContext(io) { store.clear() } } }

    /**
     * Notes the play ([onGameLaunched]) and then runs [start], the hand-off to the emulator or app,
     * as one step that a cancel cannot cut in two: a caller cancelled before it begins starts nothing
     * and notes nothing; one cancelled while the note is written gets the note taken back and
     * nothing started; once [start] runs it finishes (the note stays, the game was handed over).
     * The slow part of a launch (reading the library, probing the file) comes before, and stays
     * cancellable. A [start] that throws leaves the note for the caller to take back ([cancelPending]).
     * A [HandOffWatch] in the caller's context is claimed here: a caller that gave up on it gets
     * nothing started, one that finds it claimed waits for the real outcome.
     */
    suspend fun <T> handOff(
        gameId: Long,
        emulatorId: String? = null,
        coreToConfirm: CoreToConfirm? = null,
        start: suspend () -> T,
    ): T {
        val caller = currentCoroutineContext().job
        val watch = currentCoroutineContext()[HandOffWatch]
        caller.ensureActive()
        return withContext(NonCancellable) {
            val note = noteStart(gameId, emulatorId, coreToConfirm)
            // A caller waiting with a time limit settles the race through its HandOffWatch: it gave up, or it waits for this.
            val handsOver = caller.isActive && watch?.claim() != false
            if (!handsOver) {
                log.info("play time: start of game $gameId cancelled before the hand-off; note taken back")
                takeBack(note)
                throw CancellationException("start of game $gameId cancelled before the hand-off")
            }
            start()
        }
    }

    /** Whether a play is noted and not yet ended (the person is in a game, or has not come back yet); off the main thread. */
    suspend fun isPending(): Boolean = withContext(io) { store.read() != null }

    /**
     * Undoes [added], a session [onResume] just recorded (the person says the RetroArch core never
     * started): its time, its row and its move of the last play. False when nothing was undone (already
     * undone, or the database failed: logged).
     */
    suspend fun takeBack(added: AddedSession): Boolean = mutex.withLock {
        withContext(io) {
            try {
                playDao.takeBack(added).also { done ->
                    log.info("play time: session ${added.sessionId} of game ${added.gameId} " + if (done) "taken back" else "was already gone")
                }
            } catch (e: CancellationException) {
                e.rethrowIfCallerCancelled()
                log.warn("play time: session ${added.sessionId} of game ${added.gameId} not taken back (${e.javaClass.simpleName})")
                false
            } catch (e: SQLiteException) {
                log.warn("play time: session ${added.sessionId} of game ${added.gameId} not taken back (${e.javaClass.simpleName})")
                false
            } catch (e: IllegalStateException) {
                log.warn("play time: session ${added.sessionId} of game ${added.gameId} not taken back (${e.javaClass.simpleName})")
                false
            }
        }
    }

    /** Call from the Activity's `onResume`; adds the last game's time when one was pending. */
    suspend fun onResume(): PlaySessionOutcome = mutex.withLock {
        withContext(io) {
            val session = store.read() ?: return@withContext PlaySessionOutcome.NothingPending
            if (!store.clear()) log.warn("play time: could not clear the pending session of game ${session.gameId}")
            // Taken whatever the outcome, so a play that is not recorded leaves nothing for the next one.
            record(session, battery.takeMeasured()).also { log.info("play time: ${describe(it)}") }
        }
    }

    private suspend fun record(session: PendingSession, measured: SessionBattery?): PlaySessionOutcome {
        val nowWall = clock.wallMs()
        val elapsedMs = PlaySessions.elapsedMs(session, nowWall, clock.realtimeMs(), clock.bootCount())
            ?: return PlaySessionOutcome.Unmeasurable(session.gameId)
        val seconds = elapsedMs / MILLIS_PER_SECOND
        val core = session.coreToConfirm
        if (seconds < MIN_SESSION_SECONDS) return PlaySessionOutcome.TooShort(session.gameId, seconds, session.emulatorId, core)
        return try {
            if (gameDao.game(session.gameId) == null) return PlaySessionOutcome.GameGone(session.gameId)
            // The start is now minus the measured time, not the stored wall start: a clock changed
            // during the game must not stretch or shrink the session's span (play_session).
            val added = playDao.recordSession(session.gameId, endedAt = nowWall, seconds = seconds, startedAt = nowWall - elapsedMs, battery = measured)
            // The written session is kept only where the person may take it back (an unconfirmed core).
            PlaySessionOutcome.Recorded(session.gameId, seconds, session.emulatorId, core, added.takeIf { core != null })
        } catch (e: CancellationException) {
            e.rethrowIfCallerCancelled()
            PlaySessionOutcome.Failed(session.gameId, e)
        } catch (e: SQLiteException) {
            PlaySessionOutcome.Failed(session.gameId, e)
        } catch (e: IllegalStateException) {
            // Room reports a closed database this way.
            PlaySessionOutcome.Failed(session.gameId, e)
        }
    }

    /** Ids, seconds and an exception class only. */
    private fun describe(outcome: PlaySessionOutcome): String = when (outcome) {
        is PlaySessionOutcome.Failed -> "could not add the time of game ${outcome.gameId} (${outcome.error.kind()})"
        else -> outcome.toString()
    }

    companion object {
        /** Sessions shorter than this are not counted (ARCHITECTURE > Launching). */
        const val MIN_SESSION_SECONDS = 10L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

internal object PlaySessions {
    /** Android 6 only: how far the boot moment, seen through the two clocks, may drift within one boot. */
    private const val SAME_BOOT_TOLERANCE_MS = 60_000L

    /**
     * Milliseconds between the start and now, or null when that cannot be measured. Within one boot
     * the elapsed-since-boot clock decides, so a network time correction or a clock set by hand does
     * not count. After a reboot that clock restarted at zero and the wall clock is the only measure.
     * The boot count (Android 7+) tells a reboot apart; on Android 6 a boot moment that moved by more
     * than a minute is taken as a reboot, so a time change there falls back to the wall clock.
     */
    fun elapsedMs(session: PendingSession, nowWallMs: Long, nowRealtimeMs: Long, nowBootCount: Int?): Long? {
        val bootThen = session.bootCount
        val sameBoot = if (bootThen != null && nowBootCount != null) {
            bootThen == nowBootCount
        } else {
            val drift = (nowWallMs - nowRealtimeMs) - (session.startedAtWallMs - session.startedAtRealtimeMs)
            nowRealtimeMs >= session.startedAtRealtimeMs && abs(drift) <= SAME_BOOT_TOLERANCE_MS
        }
        val elapsed = if (sameBoot) nowRealtimeMs - session.startedAtRealtimeMs else nowWallMs - session.startedAtWallMs
        return elapsed.takeIf { it >= 0 }
    }
}
