package com.plumemu.graph

import android.content.Context

/**
 * The RetroArch cores a person answered No to for one game, kept for the core question of the core offered
 * next. On disk (not memory) because that question survives Plumemu being killed while the game runs, like
 * the pending play session; it is cleared when the question takes it. Calls block on the disk.
 */
internal interface CoreTriesStore {
    /** [emulatorId] was offered for [gameKey]; its question will carry [tried]. */
    fun expect(gameKey: String, emulatorId: String, tried: Set<String>)

    /** The cores answered No before the question of [emulatorId] for [gameKey]; removes the note. Empty when none. */
    fun take(gameKey: String, emulatorId: String): Set<String>

    /** Memory only: tests and previews. */
    class InMemory : CoreTriesStore {
        private val notes = HashMap<String, Pair<String, Set<String>>>()

        @Synchronized
        override fun expect(gameKey: String, emulatorId: String, tried: Set<String>) {
            notes[gameKey] = emulatorId to tried
        }

        @Synchronized
        override fun take(gameKey: String, emulatorId: String): Set<String> =
            notes.remove(gameKey)?.takeIf { it.first == emulatorId }?.second.orEmpty()
    }
}

/**
 * One note per game in its own SharedPreferences file (`commit()`, as the pending session): the emulator
 * offered, the cores answered No and when. A note older than [maxAgeMs] is ignored (the person did not play the
 * offer; a later launch of that core is a new attempt). No schema change.
 */
internal class SharedPreferencesCoreTries(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 12L * 60 * 60 * 1000,
) : CoreTriesStore {
    private val preferences = context.applicationContext.getSharedPreferences("plumemu_core_tries", Context.MODE_PRIVATE)

    override fun expect(gameKey: String, emulatorId: String, tried: Set<String>) {
        preferences.edit().putString(gameKey, "${now()}\t$emulatorId\t${tried.joinToString(",")}").commit()
    }

    override fun take(gameKey: String, emulatorId: String): Set<String> {
        val parts = preferences.getString(gameKey, null)?.split('\t') ?: return emptySet()
        preferences.edit().remove(gameKey).commit()
        val at = parts.getOrNull(0)?.toLongOrNull() ?: return emptySet()
        if (parts.getOrNull(1) != emulatorId || now() - at > maxAgeMs) return emptySet()
        return parts.getOrNull(2).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
    }
}
