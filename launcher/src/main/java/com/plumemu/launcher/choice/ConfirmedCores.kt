package com.plumemu.launcher.choice

import android.content.Context
import android.content.SharedPreferences

/**
 * A RetroArch core a launch asked for and nobody has yet seen start: [packageName] is the RetroArch
 * build started, [core] the core file (`mesen_libretro_android.so`). RetroArch keeps its cores in its
 * private folder, so Plumemu cannot list them; the person is asked once, on returning from the game
 *.
 */
data class CoreToConfirm(val packageName: String, val core: String)

/**
 * The cores the person said started, per RetroArch build and core: those are never asked about again.
 * Kept in its own small file, not in the database: it is a yes per core, never shown or edited
 * anywhere, and losing it (clearing Plumemu's data) only means one more question per core. Calls
 * block on the disk: call them off the main thread.
 */
interface ConfirmedCores {
    fun isConfirmed(core: CoreToConfirm): Boolean

    /** Returns false when the write did not reach the disk (then the question comes again next time). */
    fun confirm(core: CoreToConfirm): Boolean

    /** Benchmark and test builds: no core is confirmed, nothing is kept. */
    object None : ConfirmedCores {
        override fun isConfirmed(core: CoreToConfirm): Boolean = false

        override fun confirm(core: CoreToConfirm): Boolean = false
    }
}

/** One boolean key per `package/core`, written with `commit()` like the pending play session. */
internal class SharedPreferencesConfirmedCores(context: Context) : ConfirmedCores {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun isConfirmed(core: CoreToConfirm): Boolean = preferences.getBoolean(keyOf(core), false)

    override fun confirm(core: CoreToConfirm): Boolean = preferences.edit().putBoolean(keyOf(core), true).commit()

    private fun keyOf(core: CoreToConfirm) = "${core.packageName}/${core.core}"

    private companion object {
        const val FILE_NAME = "plumemu_confirmed_cores"
    }
}
