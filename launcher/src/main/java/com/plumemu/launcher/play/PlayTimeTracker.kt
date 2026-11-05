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
     * [seconds] may be under the minimum, or 0 (nothing written then), when Usage access found the game barely in front
     * although the device was awake for the minimum: the start was real.
     */
    data class Recorded(
        val gameId: Long,
        val seconds: Long,
        val emulatorId: String? = null,
        val coreToConfirm: CoreToConfirm? = null,
        val added: AddedSession? = null,
    ) : PlaySessionOutcome

    /**
     * Under [PlayTimeTracker.MIN_SESSION_SECONDS] awake (the exact Usage access count plays no part): the emulator failed to open,
     * or the person backed out.
     */
    data class TooShort(
        val gameId: Long,
        val seconds: Long,
        val emulatorId: String? = null,
        val coreToConfirm: CoreToConfirm? = null,
    ) : PlaySessionOutcome

    /** The game left the library meanwhile (rescan, folder removed); nothing to add the time to. */
    data class GameGone(val gameId: Long) : PlaySessionOutcome

    /**
     * The device rebooted in between (switched off, battery run out): no clock tells how much of
     * that was play, so none is counted. Also a negative time (a clock set back on Android 6).
     */
    data class Unmeasurable(val gameId: Long) : PlaySessionOutcome

    data class Failed(val gameId: Long, val error: Throwable) : PlaySessionOutcome
}

/** The two clocks and the boot count; a seam so tests can move time and reboot. */
internal interface PlayClock {
    fun wallMs(): Long

    fun realtimeMs(): Long

    /** Time since boot without deep sleep: play time is measured on this clock. */
    fun uptimeMs(): Long

    /** How many times the device has booted; null when unknown (Android 6 has no count). */
    fun bootCount(): Int?
}

internal class SystemPlayClock(private val resolver: ContentResolver) : PlayClock {
    override fun wallMs(): Long = System.currentTimeMillis()

    override fun realtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun uptimeMs(): Long = SystemClock.uptimeMillis()

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
 * disk, the Activity's next [onResume] adds the time the device was awake with its screen on,
 * at most [MAX_SESSION_SECONDS], to `play`. The pending start survives
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
    /** The screen-off time of this process, left out of a play. */
    private val screenOff: ScreenOffTime = ScreenOffTime(clock),
    /** The exact time the started package was in front; only ever lowers a play ([record]). */
    private val foregroundTime: ForegroundTime = ForegroundTime.None,
) {
    /** Listens to the screen for the life of the process: build one per process ([com.plumemu.launcher.Launching.create]). */
    constructor(context: Context, playDao: PlayDao, gameDao: GameDao, battery: PlayBattery = PlayBattery.None,
        foregroundTime: ForegroundTime = ForegroundTime.None,
    ) : this(
        context.applicationContext, playDao, gameDao, battery, SystemPlayClock(context.applicationContext.contentResolver), foregroundTime,
    )

    private constructor(
        app: Context,
        playDao: PlayDao,
        gameDao: GameDao,
        battery: PlayBattery,
        clock: SystemPlayClock,
        foregroundTime: ForegroundTime,
    ) : this(
        SharedPreferencesSessionStore(app), playDao, gameDao, clock, AndroidLauncherLog, Dispatchers.IO, battery,
        ScreenOffTime(clock).also { it.watch(app) }, foregroundTime,
    )

    private val mutex = Mutex()

    /** The note of the play in progress and [screenOff]'s total when it was written; this process only. */
    @Volatile private var screenOffAtStart: Pair<PendingSession, Long>? = null

    /**
     * Called right before the emulator starts; [GameLauncher] does it, the Activity need not. [emulatorId] is kept for the
     * statistics, [coreToConfirm] (a RetroArch core never confirmed) comes back with the outcome.
     */
    suspend fun onGameLaunched(
        gameId: Long,
        emulatorId: String? = null,
        coreToConfirm: CoreToConfirm? = null,
        packageName: String? = null,
    ) {
        noteStart(gameId, emulatorId, coreToConfirm, packageName)
    }

    /** [onGameLaunched], returning the note written (what [takeBack] may remove later). */
    private suspend fun noteStart(
        gameId: Long,
        emulatorId: String?,
        coreToConfirm: CoreToConfirm?,
        packageName: String?,
    ): PendingSession = mutex.withLock {
        withContext(io) {
            val previous = store.read()
            if (previous != null) log.info("play time: session of game ${previous.gameId} replaced before a resume")
            battery.onPlayStarting(usualDrawOf(gameId))
            val note = PendingSession(
                gameId, clock.wallMs(), clock.realtimeMs(), clock.bootCount(), emulatorId, coreToConfirm, clock.uptimeMs(), packageName,
            )
            screenOffAtStart = note to screenOff.totalMs()
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
        packageName: String? = null,
        start: suspend () -> T,
    ): T {
        val caller = currentCoroutineContext().job
        val watch = currentCoroutineContext()[HandOffWatch]
        caller.ensureActive()
        return withContext(NonCancellable) {
            val note = noteStart(gameId, emulatorId, coreToConfirm, packageName)
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
        val elapsedMs = PlaySessions.elapsedMs(session, nowWall, clock.realtimeMs(), clock.uptimeMs(), clock.bootCount())
            ?: return PlaySessionOutcome.Unmeasurable(session.gameId)
        // Known only when this process wrote the note; otherwise the awake clock alone leaves out deep sleep.
        val screenOffMs = screenOffAtStart?.takeIf { it.first == session }?.let { screenOff.totalMs() - it.second } ?: 0
        val awakeMs = (elapsedMs - screenOffMs).coerceAtLeast(0)
        val cappedMs = awakeMs.coerceAtMost(MAX_SESSION_SECONDS * MILLIS_PER_SECOND)
        // Too short is decided on the awake count: the exact source only lowers what is recorded, it never says the start failed
        // (a game left at once for another app, a shortcut handing off to another package, still started).
        val core = session.coreToConfirm
        if (cappedMs / MILLIS_PER_SECOND < MIN_SESSION_SECONDS) {
            return PlaySessionOutcome.TooShort(session.gameId, cappedMs / MILLIS_PER_SECOND, session.emulatorId, core)
        }
        val exactMs = session.packageName?.let { foregroundTime.inFrontMs(it, session.startedAtWallMs, nowWall) }
        // The exact source only lowers; unknown (null) keeps the awake count.
        val countedMs = if (exactMs != null && exactMs < cappedMs) exactMs.coerceAtLeast(0) else cappedMs
        if (countedMs < cappedMs) log.info("play time: ${cappedMs / MILLIS_PER_SECOND} s awake counted as ${countedMs / MILLIS_PER_SECOND} s in front")
        if (screenOffMs > 0) log.info("play time: ${screenOffMs / MILLIS_PER_SECOND} s with the screen off not counted")
        if (cappedMs < awakeMs) log.info("play time: ${awakeMs / MILLIS_PER_SECOND} s counted as $MAX_SESSION_SECONDS s")
        val seconds = countedMs / MILLIS_PER_SECOND
        return try {
            if (gameDao.game(session.gameId) == null) return PlaySessionOutcome.GameGone(session.gameId)
            // Nothing in front at all: a real start with no time to add (no empty session row).
            if (seconds == 0L) return PlaySessionOutcome.Recorded(session.gameId, 0, session.emulatorId, core)
            // The start is now minus the measured time, not the stored wall start: a clock changed
            // during the game must not stretch or shrink the session's span (play_session).
            val added = playDao.recordSession(session.gameId, endedAt = nowWall, seconds = seconds, startedAt = nowWall - countedMs, battery = measured)
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
        /** A play counts at most this long: past it, the emulator was most likely left open (ARCHITECTURE > Launching). */
        const val MAX_SESSION_SECONDS = 4 * 3_600L

        /** Sessions shorter than this are not counted (ARCHITECTURE > Launching). */
        const val MIN_SESSION_SECONDS = 10L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

internal object PlaySessions {
    /** Android 6 only: how far the boot moment, seen through the two clocks, may drift within one boot. */
    private const val SAME_BOOT_TOLERANCE_MS = 60_000L

    /**
     * Milliseconds the device was awake between the start and now, or null when that cannot be
     * measured. Within one boot the awake clock decides (deep sleep with the game in front is not
     * play; a network time correction or a clock set by hand does not count either); a note written
     * before it was kept falls back to the elapsed-since-boot clock. A reboot in between cannot be
     * measured: the wall clock would count the hours the device was off. The boot count (Android 7+)
     * tells a reboot apart; on Android 6 a boot moment that moved by more than a minute is taken as one.
     */
    fun elapsedMs(session: PendingSession, nowWallMs: Long, nowRealtimeMs: Long, nowUptimeMs: Long, nowBootCount: Int?): Long? {
        val bootThen = session.bootCount
        val sameBoot = if (bootThen != null && nowBootCount != null) {
            bootThen == nowBootCount
        } else {
            val drift = (nowWallMs - nowRealtimeMs) - (session.startedAtWallMs - session.startedAtRealtimeMs)
            nowRealtimeMs >= session.startedAtRealtimeMs && abs(drift) <= SAME_BOOT_TOLERANCE_MS
        }
        if (!sameBoot) return null
        val uptimeThen = session.startedAtUptimeMs
        val elapsed = if (uptimeThen != null) nowUptimeMs - uptimeThen else nowRealtimeMs - session.startedAtRealtimeMs
        return elapsed.takeIf { it >= 0 }
    }
}
