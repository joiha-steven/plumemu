package com.plumemu.graph

import android.content.Context

/** Background work that gives way to a game being played. */
internal enum class HeldWork { ContentPass, BoxArt }

/** Which held work waits for the person's return; kept on disk, since Android often ends Plumemu while a game runs. */
internal interface HeldWorkStore {
    /** False when the mark could not be stored. */
    fun mark(work: HeldWork): Boolean

    /** The marked work, cleared in the same step. */
    fun take(): Set<HeldWork>
}

/** Nothing is ever held (tests and graphs without play time). */
internal object NoHeldWork : HeldWorkStore {
    override fun mark(work: HeldWork) = true

    override fun take(): Set<HeldWork> = emptySet()
}

internal class SharedPreferencesHeldWorkStore(context: Context) : HeldWorkStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    override fun mark(work: HeldWork): Boolean = preferences.edit().putBoolean(work.name, true).commit()

    @Synchronized
    override fun take(): Set<HeldWork> {
        val marked = HeldWork.entries.filterTo(LinkedHashSet()) { preferences.getBoolean(it.name, false) }
        if (marked.isNotEmpty()) preferences.edit().clear().commit()
        return marked
    }

    private companion object {
        const val FILE = "plumemu.held_for_play"
    }
}

/**
 * The content pass and the box-art run stop while a game is played (a play is noted and the person
 * has not come back: `PlayTimeTracker.isPending`), between files and between games, so they do not
 * compete with the emulator for the SD card, CPU and memory. Each says it stopped ([holds] marks
 * it), and the return ([takeHeld], from `afterReturn` once the play is recorded) asks for it again.
 *
 * The order closes the gap with a return: [holds] marks before its second look at the play, and the
 * return clears the play before it reads the marks. A return between the two either finds the mark
 * (and asks for the work again: the running one is kept, as a request always keeps it) or is seen
 * by the second look (the work carries on; the mark left asks once more at the next return, which
 * finds the work done or still waiting, harmless).
 */
internal class HeldForPlay(
    private val store: HeldWorkStore,
    /**
     * A play is noted and not ended, and Plumemu is not in front: a note whose clearing failed
     * (`PlayTimeTracker.onResume` could not clear it) must not hold the work for good once the person is back.
     */
    private val isPlaying: suspend () -> Boolean,
    private val log: (String) -> Unit = {},
) {
    /**
     * True when [work] must stop now for a game; then it is marked to go on at the return. A mark that
     * cannot be stored does not hold: nothing would ask for the work again.
     */
    suspend fun holds(work: HeldWork): Boolean {
        if (!isPlaying()) return false
        if (!store.mark(work)) {
            log("held for play: the mark of $work could not be stored; it goes on instead of stopping")
            return false
        }
        return isPlaying()
    }

    fun takeHeld(): Set<HeldWork> = store.take()
}

/**
 * "A game is played": a play is noted ([pending]) and Plumemu is not in front. The front check stops
 * a note that could not be cleared (`PendingSessionStore.clear` failing) from holding work for good.
 */
internal fun playingAway(
    inFront: () -> Boolean,
    pending: suspend () -> Boolean,
): suspend () -> Boolean = { !inFront() && pending() }
