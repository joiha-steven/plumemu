package com.plumemu.scraper.work

import com.plumemu.scraper.Scraper
import java.util.WeakHashMap

/**
 * Forgets which games of this scraper met network errors in earlier runs (the person asked for box
 * art again: Download missing, Re-download), so a game dropped for this process is asked again.
 */
public fun Scraper.forgetNetworkFailures() {
    networkMemoryOf(this).forget()
}

/**
 * Per scraper (one per account in a process), in memory only: how many runs in a row each game's
 * main pass met a network error ([ScrapeRun] asks it at the end of the main pass while it has
 * one), and the games dropped after [MAX_FAILED_RUNS] such runs, until [forget] (the person asks
 * for box art again) or a new process. A delivery clears a game's count.
 */
internal class NetworkMemory {
    private val failedRuns = HashMap<String, Int>()
    private val dropped = HashSet<String>()

    @Synchronized fun failedBefore(key: String): Boolean = key in failedRuns

    @Synchronized fun isDropped(key: String): Boolean = key in dropped

    /** Games left out of or moved in a page: they make the page bigger. */
    @Synchronized fun size(): Int = failedRuns.size + dropped.size

    @Synchronized fun delivered(key: String) {
        failedRuns.remove(key)
    }

    /** One more run with a network error for [key]; true when that drops it. */
    @Synchronized fun failedThisRun(key: String): Boolean {
        val runs = (failedRuns[key] ?: 0) + 1
        if (runs < MAX_FAILED_RUNS) {
            failedRuns[key] = runs
            return false
        }
        failedRuns.remove(key)
        dropped += key
        return true
    }

    @Synchronized fun forget() {
        failedRuns.clear()
        dropped.clear()
    }

    companion object {
        /** A game whose main pass met a network error in this many runs in a row is dropped. */
        const val MAX_FAILED_RUNS = 3
    }
}

private val networkMemories = WeakHashMap<Scraper, NetworkMemory>()

/** The one [NetworkMemory] of [scraper] (weak keys: it goes with the scraper). */
internal fun networkMemoryOf(scraper: Scraper): NetworkMemory = synchronized(networkMemories) {
    networkMemories.getOrPut(scraper) { NetworkMemory() }
}
