package com.plumemu.launcher.play

import android.content.Context
import android.content.SharedPreferences
import com.plumemu.launcher.choice.CoreToConfirm

/**
 * A game started and not yet come back from. Both clocks and the boot count are kept: the
 * elapsed-since-boot clock measures a session within one boot, the wall clock covers a reboot
 * ([PlaySessions.elapsedMs]). [bootCount] is null on Android 6, which does not count boots.
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
)

/** Keeps the pending session on disk, so the time is still counted if Android kills Plumemu meanwhile. */
internal interface PendingSessionStore {
    fun read(): PendingSession?

    /** Returns false when the write did not reach the disk. */
    fun write(session: PendingSession): Boolean

    fun clear(): Boolean
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
        )
    }

    override fun write(session: PendingSession): Boolean {
        val editor = preferences.edit().clear()
            .putLong(KEY_GAME, session.gameId)
            .putLong(KEY_WALL, session.startedAtWallMs)
            .putLong(KEY_REALTIME, session.startedAtRealtimeMs)
        session.bootCount?.let { editor.putInt(KEY_BOOT, it) }
        session.emulatorId?.let { editor.putString(KEY_EMULATOR, it) }
        session.coreToConfirm?.let { editor.putString(KEY_CORE_PACKAGE, it.packageName).putString(KEY_CORE, it.core) }
        return editor.commit()
    }

    override fun clear(): Boolean = preferences.edit().clear().commit()

    private fun coreOf(packageName: String?, core: String?): CoreToConfirm? =
        if (packageName != null && core != null) CoreToConfirm(packageName, core) else null

    private companion object {
        const val FILE_NAME = "plumemu_play_session"
        const val KEY_GAME = "game_id"
        const val KEY_WALL = "started_wall_ms"
        const val KEY_REALTIME = "started_realtime_ms"
        const val KEY_BOOT = "boot_count"
        const val KEY_EMULATOR = "emulator_id"
        const val KEY_CORE_PACKAGE = "core_package"
        const val KEY_CORE = "core_to_confirm"
    }
}
