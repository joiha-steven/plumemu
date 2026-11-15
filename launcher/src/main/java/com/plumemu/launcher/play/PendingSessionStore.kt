package com.plumemu.launcher.play

import android.content.Context
import android.content.SharedPreferences
import com.plumemu.launcher.choice.CoreToConfirm

/**
 * A game started and not yet come back from. The awake clock measures the session, the
 * elapsed-since-boot clock, the wall clock and the boot count tell a reboot ([PlaySessions.elapsedMs]).
 * [bootCount] is null on Android 6, which does not count boots.
 */
internal data class PendingSession(
    val gameId: Long,
    val startedAtWallMs: Long,
    val startedAtRealtimeMs: Long,
    val bootCount: Int? = null,
    /** The platform data's id of the emulator started (for the usage statistics' `game_session`); null for an app or an older record. */
    val emulatorId: String? = null,
    /** The RetroArch core the launch asked for and nobody has confirmed yet; null otherwise (and in older records). */
    val coreToConfirm: CoreToConfirm? = null,
    /** [PlayClock.uptimeMs] at the start; null in a record written before it was kept. */
    val startedAtUptimeMs: Long? = null,
    /** The package of the emulator or app started, for the exact foreground time; null when unknown (and in older records). */
    val packageName: String? = null,
)

/**
 * The screen-off time of a pending play, kept apart from [PendingSession] so the note itself (compared
 * by equality when a start is taken back) never changes after it is written.
 * [totalMs]: the stretches that ended since the play started, on the awake clock; [openSinceMs]: the
 * [PlayClock.uptimeMs] at which the screen went off and has not come on since, null while it is on.
 */
internal data class ScreenOffNote(val totalMs: Long, val openSinceMs: Long?)

/** Keeps the pending session on disk, so the time is still counted if Android kills Plumemu meanwhile. */
internal interface PendingSessionStore {
    fun read(): PendingSession?

    /** Returns false when the write did not reach the disk. */
    fun write(session: PendingSession): Boolean

    fun clear(): Boolean

    /** The screen-off time stored with the pending note; null when none (no note, or one written by an older version). */
    fun readScreenOff(): ScreenOffNote? = null

    /** Stores [note] with the pending note; writes nothing, and returns false, when no note is pending. */
    fun writeScreenOff(note: ScreenOffNote): Boolean = false
}

/**
 * SharedPreferences written with `commit()` (synchronous, call off the main thread): an emulator
 * may take all the memory right after it starts, and Plumemu's process may die before an `apply()`
 * would have reached the disk.
 */
internal class SharedPreferencesSessionStore(context: Context) : PendingSessionStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): PendingSession? {
        if (!preferences.contains(KEY_GAME)) return null
        return PendingSession(
            preferences.getLong(KEY_GAME, 0),
            preferences.getLong(KEY_WALL, 0),
            preferences.getLong(KEY_REALTIME, 0),
            if (preferences.contains(KEY_BOOT)) preferences.getInt(KEY_BOOT, 0) else null,
            preferences.getString(KEY_EMULATOR, null),
            coreOf(preferences.getString(KEY_CORE_PACKAGE, null), preferences.getString(KEY_CORE, null)),
            if (preferences.contains(KEY_UPTIME)) preferences.getLong(KEY_UPTIME, 0) else null,
            preferences.getString(KEY_PACKAGE, null),
        )
    }

    override fun write(session: PendingSession): Boolean {
        val editor = preferences.edit().clear()
            .putLong(KEY_GAME, session.gameId)
            .putLong(KEY_WALL, session.startedAtWallMs)
            .putLong(KEY_REALTIME, session.startedAtRealtimeMs)
        session.bootCount?.let { editor.putInt(KEY_BOOT, it) }
        session.startedAtUptimeMs?.let { editor.putLong(KEY_UPTIME, it) }
        session.packageName?.let { editor.putString(KEY_PACKAGE, it) }
        session.emulatorId?.let { editor.putString(KEY_EMULATOR, it) }
        session.coreToConfirm?.let { editor.putString(KEY_CORE_PACKAGE, it.packageName).putString(KEY_CORE, it.core) }
        return editor.commit()
    }

    override fun clear(): Boolean = preferences.edit().clear().commit()

    override fun readScreenOff(): ScreenOffNote? {
        if (!preferences.contains(KEY_GAME) || !preferences.contains(KEY_SCREEN_OFF_MS)) return null
        return ScreenOffNote(
            preferences.getLong(KEY_SCREEN_OFF_MS, 0),
            if (preferences.contains(KEY_SCREEN_OFF_SINCE)) preferences.getLong(KEY_SCREEN_OFF_SINCE, 0) else null,
        )
    }

    // Only next to a pending note: a change after the clear must not bring a note back.
    override fun writeScreenOff(note: ScreenOffNote): Boolean {
        if (!preferences.contains(KEY_GAME)) return false
        val editor = preferences.edit().putLong(KEY_SCREEN_OFF_MS, note.totalMs)
        val since = note.openSinceMs
        if (since != null) editor.putLong(KEY_SCREEN_OFF_SINCE, since) else editor.remove(KEY_SCREEN_OFF_SINCE)
        return editor.commit()
    }

    private fun coreOf(packageName: String?, core: String?): CoreToConfirm? =
        if (packageName != null && core != null) CoreToConfirm(packageName, core) else null

    private companion object {
        const val FILE_NAME = "plumemu_play_session"
        const val KEY_GAME = "game_id"
        const val KEY_WALL = "started_wall_ms"
        const val KEY_REALTIME = "started_realtime_ms"
        const val KEY_BOOT = "boot_count"
        const val KEY_UPTIME = "started_uptime_ms"
        const val KEY_PACKAGE = "started_package"
        const val KEY_SCREEN_OFF_MS = "screen_off_ms"
        const val KEY_SCREEN_OFF_SINCE = "screen_off_since_ms"
        const val KEY_EMULATOR = "emulator_id"
        const val KEY_CORE_PACKAGE = "core_package"
        const val KEY_CORE = "core_to_confirm"
    }
}
