package com.plumemu.scraper.work

import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.timing.GameTiming
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The run's step for a game ScreenScraper has no box front for: asks [Scraper.fallbackArt] and
 * saves the picture it found through [RunPictures], like a ScreenScraper box front. Sets the
 * game's timing outcome (`matched-steam-cdn`, `steam-none`, `steam-failed`) when a source took part.
 */
internal class FallbackBox(private val scraper: Scraper, private val pictures: RunPictures, private val log: (String) -> Unit) {
    sealed interface Result {
        /** No source covers the game, or none has a picture: nothing to add. */
        data object None : Result

        /** The picture is saved; [result] is what to deliver when ScreenScraper had no match. */
        class Saved(val result: ScrapeResult.Matched, val box: List<SavedMedia>) : Result

        /**
         * The source's network or server failed, or it was given up on for this run ([MAX_FAILURES_IN_ROW]
         * failures in a row): nothing was asked or saved. The run leaves a no-match game for the next run.
         */
        data object Failed : Result

        /** The picture could not be stored (a full disk): the run stops, as for a ScreenScraper picture. */
        class Stopped(val outcome: PictureOutcome) : Result
    }

    /**
     * Failures in a row of each fallback source (by name), counted apart from the run's own network errors: a
     * source that is down must not end the run and starve the ScreenScraper games behind it. At
     * [MAX_FAILURES_IN_ROW] that source is not asked again in this run; the others still are. A game the
     * stopped source covers is [Result.Failed] (left for the next run).
     */
    private val failuresInRow = ConcurrentHashMap<String, AtomicInteger>()

    suspend fun fetch(request: ScrapeRequest): Result {
        val memory = fallbackMemoryOf(scraper)
        if (memory.givenUp(request.rom.key)) {
            log("fallback art: a game failed in $MAX_FAILED_RUNS runs in a row; delivered without it")
            memory.clear(request.rom.key)
            return Result.None
        }
        val result = fetchOnce(request)
        if (result == Result.Failed) memory.failedThisRun(request.rom.key) else memory.clear(request.rom.key)
        return result
    }

    private suspend fun fetchOnce(request: ScrapeRequest): Result {
        val stopped = failuresInRow.filterValues { it.get() >= MAX_FAILURES_IN_ROW }.keys
        val (result, source) = ask(request, stopped)
        if (source != null) {
            val count = failuresInRow.getOrPut(source) { AtomicInteger() }
            if (result == Result.Failed) {
                if (count.incrementAndGet() == MAX_FAILURES_IN_ROW) {
                    log("fallback art: $MAX_FAILURES_IN_ROW failures in a row ($source); not asked again in this run")
                }
            } else if (count.get() < MAX_FAILURES_IN_ROW) {
                count.set(0)
            }
        }
        return result
    }

    /** The source an outcome word belongs to: `steam-none`, `matched-steam-cdn` and `steam-failed` are `steam`. */
    private fun sourceOf(word: String) = word.removePrefix("matched-").substringBefore('-')

    private suspend fun ask(request: ScrapeRequest, stopped: Set<String>): Pair<Result, String?> {
        val game = currentCoroutineContext()[GameTiming]
        return when (val found = scraper.fallbackArt(request, stopped)) {
            FallbackArt.NotApplicable -> Result.None to null
            is FallbackArt.NotFound -> {
                game?.outcome(found.outcome)
                Result.None to sourceOf(found.outcome)
            }
            is FallbackArt.Failed -> {
                game?.outcome(found.outcome)
                Result.Failed to sourceOf(found.outcome)
            }
            is FallbackArt.Found -> {
                val source = sourceOf(found.words.saved)
                when (val saved = pictures.save(found.result, MediaKind.BoxFront, request.fresh)) {
                    is PictureOutcome.Saved ->
                        if (saved.media.isEmpty()) {
                            game?.outcome(found.words.none)
                            Result.None to source
                        } else {
                            game?.outcome(found.words.saved)
                            Result.Saved(found.result, saved.media) to source
                        }
                    is PictureOutcome.Server, PictureOutcome.Network -> {
                        game?.outcome(found.words.failed)
                        Result.Failed to source
                    }
                    else -> Result.Stopped(saved) to null
                }
            }
        }
    }

    companion object {
        const val MAX_FAILURES_IN_ROW = 3

        /** A game whose fallback failed in this many runs in a row is delivered as it is (a no-match), so it cannot loop for ever. */
        const val MAX_FAILED_RUNS = 3
    }
}

/**
 * Per scraper, in memory: in how many runs in a row each game's fallback failed. A failure leaves the
 * game for the next run; at [FallbackBox.MAX_FAILED_RUNS] it is delivered without art instead (like
 * [NetworkMemory]), so one game that always fails (a name the server refuses) cannot make every run
 * spend ScreenScraper's quota on it again.
 */
internal class FallbackMemory {
    private val failedRuns = HashMap<String, Int>()

    @Synchronized fun givenUp(key: String): Boolean = (failedRuns[key] ?: 0) >= FallbackBox.MAX_FAILED_RUNS

    @Synchronized fun failedThisRun(key: String) {
        failedRuns[key] = (failedRuns[key] ?: 0) + 1
    }

    @Synchronized fun clear(key: String) {
        failedRuns.remove(key)
    }
}

private val fallbackMemories = java.util.WeakHashMap<Scraper, FallbackMemory>()

internal fun fallbackMemoryOf(scraper: Scraper): FallbackMemory = synchronized(fallbackMemories) {
    fallbackMemories.getOrPut(scraper) { FallbackMemory() }
}
