package com.plumemu.scraper

import android.content.Context
import android.content.SharedPreferences

/**
 * Keeps the scraper's pause (daily quota, server closed) across processes, so a new process does
 * not ask ScreenScraper anything before the resume time. The scraper loads it once, before its
 * first request, and saves every new pause; both on its IO dispatcher.
 */
public interface PauseStore {
    /** The last pause saved, or null; a pause already over is harmless (the scraper ignores it). */
    public fun load(): ScrapeResult.Paused?

    public fun save(pause: ScrapeResult.Paused)

    /** Forgets the saved pause (one saved under a wrong clock). */
    public fun clear()
}

/** [PauseStore] in a small SharedPreferences file of the scraper (`plumemu.scraper.pause`). */
public class SharedPreferencesPauseStore(
    context: Context,
    private val log: (String) -> Unit = { android.util.Log.w(ScraperConfig.LOG_TAG, it) },
) : PauseStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): ScrapeResult.Paused? {
        val until = prefs.getLong(KEY_UNTIL, NONE)
        val reason = prefs.getString(KEY_REASON, null)?.let { name ->
            PauseReason.entries.firstOrNull { it.name == name }
        }
        if (until == NONE || reason == null) return null
        return ScrapeResult.Paused(until, reason)
    }

    override fun save(pause: ScrapeResult.Paused) {
        // commit, not apply: the worker returns right after a pause and the process may end.
        val written = prefs.edit()
            .putLong(KEY_UNTIL, pause.resumeAtMillis)
            .putString(KEY_REASON, pause.reason.name)
            .commit()
        if (!written) log("could not save the scraper pause; a new process may ask before ${pause.resumeAtMillis}")
    }

    override fun clear() {
        if (!prefs.edit().clear().commit()) log("could not clear the scraper pause")
    }

    public companion object {
        public const val FILE_NAME: String = "plumemu.scraper.pause"
        private const val KEY_UNTIL = "resumeAtMillis"
        private const val KEY_REASON = "reason"
        private const val NONE = Long.MIN_VALUE
    }
}
