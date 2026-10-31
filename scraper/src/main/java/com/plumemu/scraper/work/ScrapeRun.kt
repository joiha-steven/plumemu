package com.plumemu.scraper.work

import com.plumemu.scraper.FailureReason
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.media.MediaSaver
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.timing.GameTiming
import com.plumemu.scraper.timing.RunTiming
import com.plumemu.scraper.timing.guarded
import com.plumemu.scraper.timing.msSince
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One background run, without WorkManager: the worker turns its [End] into a WorkManager result.
 * Cancelling the coroutine (Wi-Fi lost, cancel) leaves the games in flight undelivered, so the
 * next run asks them again.
 *
 * Two passes, so covers come first (see [ScrapeSource]):
 * 1. **Main** ([ScrapeSource.pending]): the lookup and the box front, delivered as soon as the box
 *    front is saved ([ScrapePass.Main]). Games that met a network error in an earlier run of this
 *    scraper ([NetworkMemory]) come at its end, after every other game, in parallel like any
 *    other: one slow game (a 30 s timeout) must not hold the head of every run.
 * 2. **Disc** ([ScrapeSource.pendingSupport]): only once the main pass has nothing left and no main
 *    game in flight. The answer comes from the scraper's cache (`fresh = false`), the saved box
 *    front is reused, and the disc or cartridge is saved ([ScrapePass.Support]). The main pass is
 *    checked again at most every [MAIN_RECHECK_MILLIS], and once more before the run ends, so a
 *    game added meanwhile goes before the remaining discs.
 *
 * As many workers as [Scraper.requestSlots] (the gate's slots: the account's `maxthreads`, 1 after
 * a "too many threads" answer), at most [MAX_WORKERS], take games from shared queues under one
 * lock ([state]); a game in flight is never handed out again, and the source's pages are asked
 * outside that lock, one at a time. The scraper's gate still holds every request to the account's
 * limits and stops every worker's next request on a pause or a refusal.
 *
 * A game that failed on the server's side, whose ROM could not be read or that met a network error
 * is skipped for the rest of this run (not delivered, so it comes back next run); three server
 * errors in a row, or three network errors in a row among games not set aside (the connection is
 * down), end the run with [End.Retry] at once. A run whose main pass left games unanswered after a
 * network error ends with [End.Retry], so WorkManager asks them again after its backoff; a game
 * that failed so in [NetworkMemory.MAX_FAILED_RUNS] runs in a row is dropped for the process (see
 * [NetworkMemory]). When several workers stop, the worst stop wins ([worse]).
 */
internal class ScrapeRun(
    private val work: ScraperWork,
    private val saver: MediaSaver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageSize: Int = PAGE_SIZE,
) {
    sealed interface End {
        /** Nothing left to ask. */
        data object Done : End

        /** Stopped by a pause; run again at [resumeAtMillis]. */
        data class Paused(val resumeAtMillis: Long) : End

        /** No connection or the server is unwell: let WorkManager retry with its backoff. */
        data object Retry : End

        /** Asking again cannot help (credentials refused, broken source): do not retry. */
        data object Stopped : End

        /** The run used its time budget after delivering something; a fresh run carries on at once. */
        data object Continue : End

        /** [ScraperWork.holdOff] stopped the run between games; the app asks for one again later. */
        data object HeldOff : End
    }

    /** What one game's step came to; [finishLocked] books it. */
    private sealed interface Step {
        /** Delivered; [stop] ends the run after it, [networkFailed] when its disc met a network error. */
        data class Delivered(val stop: End? = null, val networkFailed: Boolean = false) : Step

        data class Skipped(val why: SkipReason) : Step

        data class Stopped(val end: End) : Step
    }

    private sealed interface SkipReason {
        data object RomUnreadable : SkipReason

        data class Server(val code: Int) : SkipReason

        data object FallbackFailed : SkipReason // asked again next run; counts as neither server nor network error

        data object Network : SkipReason
    }

    private class Taken(val request: ScrapeRequest, val pass: ScrapePass, val setAside: Boolean = false)

    /** A page to ask outside the lock: [limit] games of [pass]; [finishesBefore] dates it. */
    private class Fetch(val pass: ScrapePass, val limit: Int, val finishesBefore: Long, val recheck: Boolean)

    private sealed interface Next {
        class Game(val taken: Taken) : Next

        class Page(val fetch: Fetch) : Next

        /** Nothing to take now, but games or a page are in flight: wait for [version] to change. */
        class Wait(val version: Long) : Next

        data object Exit : Next
    }

    private val log = work.log
    private val pictures = RunPictures(work.scraper, work.mediaDir, work.mediaSizes, saver, work.log, ioDispatcher)
    private val fallbackBox = FallbackBox(work.scraper, pictures, work.log)
    private val timingLog = work.timingLog.guarded()
    private val runTiming = RunTiming()
    private val memory = networkMemoryOf(work.scraper)

    // Everything below is read and written under [state] only.
    private val state = Mutex()
    private val mainQueue = ArrayDeque<ScrapeRequest>()
    private val laterQueue = ArrayDeque<ScrapeRequest>()
    private val supportQueue = ArrayDeque<ScrapeRequest>()
    private val inFlight = HashMap<String, ScrapePass>()
    private val skipped = HashSet<String>()

    /** Rom key to the [finishes] count at its delivery, per pass. */
    private val deliveredMain = HashMap<String, Long>()
    private val deliveredSupport = HashMap<String, Long>()
    private var finishes = 0L
    private var fetching = false
    private var mainDry = false
    private var mainCheckedAt = 0L
    private var finalCheckDone = false
    private var supportDry = false
    private val pagesLogged = HashSet<ScrapePass>()
    private var serverErrorsInRow = 0
    private var networkErrorsInRow = 0

    /** Main-pass games that met a network error in this run and were not delivered. */
    private val networkFailed = LinkedHashSet<String>()
    private var unavailableReported = false
    private var stop: End? = null
    private var budgetUsed = false
    @Volatile private var heldOff = false
    private var finished = false
    private var workers = 0
    private var startedAt = 0L
    private val allLeft = CompletableDeferred<Unit>()

    /** Bumped (under [state]) whenever a waiting worker may find something new to do or must leave. */
    private val changes = MutableStateFlow(0L)

    suspend fun run(): End {
        pictures.removeLeftoverDownloads()
        startedAt = work.clock()
        coroutineScope {
            val scope = this
            // The gate's slots grow after the first answer (maxthreads) and drop to 1 after a 429.
            val spawner = launch { work.scraper.requestSlots.collect { state.withLock { spawnLocked(scope) } } }
            allLeft.await()
            spawner.cancel()
        }
        return state.withLock { endLocked() }
    }

    private fun workerLimit(): Int = work.scraper.requestSlots.value.coerceIn(1, MAX_WORKERS)

    private fun spawnLocked(scope: CoroutineScope) {
        while (!overLocked() && workers < workerLimit()) {
            workers++
            scope.launch { worker() }
        }
    }

    private fun overLocked(): Boolean = finished || stop != null || budgetUsed || heldOff

    private suspend fun worker() {
        while (true) {
            // Between games: a game being played stops the run here (the others finish the game they hold).
            if (!heldOff && work.holdOff()) {
                state.withLock {
                    if (!heldOff) log("scrape run: held off (a game is being played); the app asks again later")
                    heldOff = true
                    changes.value++
                }
            }
            when (val next = state.withLock { nextLocked() }) {
                Next.Exit -> return
                is Next.Wait -> changes.first { it != next.version }
                is Next.Page -> {
                    val page = try {
                        fetch(next.fetch)
                    } catch (e: Throwable) {
                        withContext(kotlinx.coroutines.NonCancellable) { state.withLock { fetching = false; changes.value++ } }
                        throw e
                    }
                    state.withLock { acceptLocked(next.fetch, page) }
                }
                is Next.Game -> {
                    val step = step(next.taken)
                    reportFailure(next.taken, step)
                    state.withLock { finishLocked(next.taken, step) }
                }
            }
        }
    }

    /**
     * Tells the source a main-pass game failed on a server error, or on a network error while the run
     * got other games answered (a dead connection would count every game's day). A source that
     * throws is logged and ignored: the count is a courtesy, the run goes on.
     */
    private suspend fun reportFailure(taken: Taken, step: Step) {
        if (taken.pass != ScrapePass.Main || step !is Step.Skipped) return
        val counts =
            when (step.why) {
                // 429 is the server saying it is busy with our own parallel requests, not a fault of this game.
                is SkipReason.Server -> step.why.code != HTTP_TOO_MANY_REQUESTS
                SkipReason.Network -> state.withLock { deliveredMain.isNotEmpty() || deliveredSupport.isNotEmpty() }
                SkipReason.RomUnreadable, SkipReason.FallbackFailed -> false
            }
        if (!counts) return
        try {
            work.source.failedAsking(taken.request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("scrape run: the source could not count a failed game (${e.javaClass.simpleName})")
        }
    }

    private fun nextLocked(): Next {
        if (overLocked()) return leaveLocked()
        // Fewer slots than workers (a 429): this one stops after its game; the others carry on.
        if (workers > workerLimit()) return leaveLocked()

        mainQueue.removeFirstOrNull()?.let { return takeLocked(it, ScrapePass.Main) }
        if (!mainDry) return pageLocked(ScrapePass.Main, recheck = false)
        // The main pass's end: games set aside in an earlier run, in parallel like the others.
        laterQueue.removeFirstOrNull()?.let { return takeLocked(it, ScrapePass.Main, setAside = true) }
        if (inFlight.containsValue(ScrapePass.Main)) return Next.Wait(changes.value)

        if (work.clock() - mainCheckedAt >= MAIN_RECHECK_MILLIS) return pageLocked(ScrapePass.Main, recheck = true)
        supportQueue.removeFirstOrNull()?.let { return takeLocked(it, ScrapePass.Support) }
        if (!supportDry) return pageLocked(ScrapePass.Support, recheck = false)
        if (inFlight.isNotEmpty() || fetching) return Next.Wait(changes.value)
        // A game added since the last check gets its turn before the run ends.
        if (!finalCheckDone) {
            finalCheckDone = true
            return pageLocked(ScrapePass.Main, recheck = true)
        }
        finished = true
        changes.value++
        return leaveLocked()
    }

    /** The run's clock is read once per game taken (and when the main pass is found empty), as tests count on. */
    private fun takeLocked(request: ScrapeRequest, pass: ScrapePass, setAside: Boolean = false): Next {
        if (work.clock() - startedAt >= work.runBudgetMillis) {
            budgetUsed = true
            changes.value++
            return leaveLocked()
        }
        inFlight[request.rom.key] = pass
        return Next.Game(Taken(request, pass, setAside))
    }

    private fun leaveLocked(): Next {
        workers--
        if (workers <= 0) allLeft.complete(Unit)
        return Next.Exit
    }

    /** One page at a time: a second worker that needs one waits for the first's. */
    private fun pageLocked(pass: ScrapePass, recheck: Boolean): Next {
        if (fetching) return Next.Wait(changes.value)
        fetching = true
        // Big enough to hold a page past the games left out (in flight, skipped, queued, set aside, dropped).
        val leftOut = skipped.size + inFlight.size + mainQueue.size + laterQueue.size + supportQueue.size + memory.size()
        return Next.Page(Fetch(pass, pageSize + leftOut, finishes, recheck))
    }

    /** Asks the source for a page; never under [state] (a database query). */
    private suspend fun fetch(fetch: Fetch): Pair<List<ScrapeRequest>, Long> {
        val pageAt = System.nanoTime()
        val page = when (fetch.pass) {
            ScrapePass.Main -> work.source.pending(fetch.limit)
            ScrapePass.Support -> work.source.pendingSupport(fetch.limit)
        }
        return page to msSince(pageAt)
    }

    private fun acceptLocked(fetch: Fetch, fetched: Pair<List<ScrapeRequest>, Long>) {
        fetching = false
        changes.value++
        val (page, ms) = fetched
        val delivered = if (fetch.pass == ScrapePass.Main) deliveredMain else deliveredSupport
        // Delivered before this page was asked: the source broke its contract. Delivered while it
        // was asked: the page is a moment old, the game is simply left out.
        if (page.any { (delivered[it.rom.key] ?: Long.MAX_VALUE) <= fetch.finishesBefore }) {
            val call = if (fetch.pass == ScrapePass.Main) "pending()" else "pendingSupport()"
            log("scrape run: a delivered request came back from $call; run stopped")
            stop = worseStop(stop, End.Stopped)
            return
        }
        val queued = (mainQueue + laterQueue + supportQueue).mapTo(HashSet()) { it.rom.key }
        val fresh = page.filter { val key = it.rom.key; key !in skipped && key !in inFlight && key !in delivered && key !in queued }
        when (fetch.pass) {
            ScrapePass.Main -> {
                val usable = fresh.filter { !memory.isDropped(it.rom.key) }
                val (later, now) = usable.partition { memory.failedBefore(it.rom.key) }
                laterQueue.addAll(later)
                mainQueue.addAll(now)
                mainDry = now.isEmpty()
                if (mainDry) mainCheckedAt = work.clock()
                if (now.isNotEmpty() || later.isNotEmpty()) {
                    // The disc pass gives way; its queue is asked again once the main pass is empty.
                    supportQueue.clear()
                    supportDry = false
                    finalCheckDone = false
                }
            }
            ScrapePass.Support -> {
                supportQueue.addAll(fresh)
                supportDry = fresh.isEmpty()
            }
        }
        // An empty page after the first of its pass says nothing: no line for it.
        val firstOfPass = pagesLogged.add(fetch.pass)
        if (page.isNotEmpty() || firstOfPass) {
            timingLog.line("page pending=${ms}ms games=${page.size} pass=${passWord(fetch.pass)}")
            // Logcat's ring buffer drops the run's first lines on a long run: the limits come with every page.
            runTiming.lastLimits()?.let { timingLog.line(it) }
        }
    }

    private fun finishLocked(taken: Taken, step: Step) {
        val key = taken.request.rom.key
        inFlight.remove(key)
        finishes++
        when (step) {
            is Step.Delivered -> {
                if (taken.pass == ScrapePass.Main) {
                    deliveredMain[key] = finishes
                    memory.delivered(key)
                    networkFailed.remove(key)
                    // A new match may have a disc to fetch.
                    supportDry = false
                } else {
                    deliveredSupport[key] = finishes
                }
                serverErrorsInRow = 0
                if (step.networkFailed) networkErrorLocked(countsInRow = true) else networkErrorsInRow = 0
                step.stop?.let { stop = worseStop(stop, it) }
            }
            is Step.Skipped -> {
                skipped += key
                when (val why = step.why) {
                    SkipReason.RomUnreadable, SkipReason.FallbackFailed -> Unit
                    is SkipReason.Server -> {
                        serverErrorsInRow++
                        if (serverErrorsInRow >= MAX_SERVER_ERRORS_IN_ROW) {
                            log("scrape run: $serverErrorsInRow server errors in a row (last HTTP ${why.code}); retry later")
                            stop = worseStop(stop, End.Retry)
                        }
                    }
                    SkipReason.Network -> {
                        // Only the main pass has a head to hold: a disc game is not asked again in this run anyway.
                        if (taken.pass == ScrapePass.Main) networkFailed += key
                        // A game set aside already says little about the connection.
                        networkErrorLocked(countsInRow = !taken.setAside)
                    }
                }
            }
            is Step.Stopped -> stop = worseStop(stop, step.end)
        }
        changes.value++
    }

    /** One more network error; at [MAX_NETWORK_ERRORS_IN_ROW] in a row the connection is down and the run retries later. */
    private fun networkErrorLocked(countsInRow: Boolean) {
        if (!countsInRow) return
        networkErrorsInRow++
        if (networkErrorsInRow >= MAX_NETWORK_ERRORS_IN_ROW) {
            log("scrape run: $networkErrorsInRow network errors in a row; retry later")
            stop = worseStop(stop, End.Retry)
        }
    }

    private fun endLocked(): End {
        // Each game that met a network error counts one more failed run; the third drops it for the process.
        val unanswered = networkFailed.filterNot { key ->
            memory.failedThisRun(key).also { dropped ->
                if (dropped) log("scrape run: a game met network errors in ${NetworkMemory.MAX_FAILED_RUNS} runs in a row; not asked again until the app restarts or box art is asked again")
            }
        }
        stop?.let { return it }
        if (heldOff) return End.HeldOff
        val delivered = deliveredMain.size + deliveredSupport.size
        if (budgetUsed) {
            // A run that got nothing done must not chain at once forever (every ROM unreadable and
            // slow): it backs off like any other retry.
            if (delivered == 0) {
                log("scrape run: time budget used with nothing delivered; retry with backoff")
                return End.Retry
            }
            log("scrape run: time budget used, $delivered delivered; a new run carries on")
            return End.Continue
        }
        if (unanswered.isNotEmpty()) {
            log("scrape run: ${unanswered.size} games met a network error, the rest done; retry later")
            return End.Retry
        }
        return End.Done
    }

    /**
     * Scrapes one game. Each game is timed ([GameTiming], its own coroutine context, so games in
     * flight together never mix): the layers below add to it, and one summary line is written at the end.
     */
    private suspend fun step(taken: Taken): Step {
        val request = taken.request
        val game = GameTiming(request.systemId, request.rom.fileName, request.rom.sizeBytes, passWord(taken.pass))
        val startedAt = System.nanoTime()
        try {
            return withContext(game + runTiming) { stepTimed(taken, game) }
        } finally {
            timingLog.line(game.summary(msSince(startedAt)))
        }
    }

    private suspend fun stepTimed(taken: Taken, game: GameTiming): Step {
        // The disc pass reads the answer the main pass cached: no second lookup on the network.
        val lookup = if (taken.pass == ScrapePass.Support) taken.request.copy(fresh = false) else taken.request
        return when (val result = work.scraper.scrape(lookup)) {
            is ScrapeResult.Matched -> {
                game.outcome("matched-${result.match.matchedBy.name.lowercase()}")
                if (taken.pass == ScrapePass.Main) matchedMain(taken, result) else matchedSupport(taken, result)
            }
            ScrapeResult.NoMatch -> {
                game.outcome("no-match")
                noMatch(taken)
            }
            is ScrapeResult.Paused -> Step.Stopped(End.Paused(result.resumeAtMillis)).also { game.outcome("paused-${result.reason.name.lowercase()}") }
            is ScrapeResult.Unavailable -> Step.Stopped(unavailable(result.reason)).also { game.outcome("unavailable-${result.reason.name.lowercase()}") }
            is ScrapeResult.Failed -> failed(result.reason).also { game.outcome("failed-${failureWord(result.reason)}") }
        }
    }

    /** No match: another source of box art may have one ([FallbackBox], main pass only); its failure never gives the game up. */
    private suspend fun noMatch(taken: Taken): Step {
        val fallback = if (taken.pass == ScrapePass.Main) fallbackBox.fetch(taken.request) else FallbackBox.Result.None
        if (fallback is FallbackBox.Result.Stopped) return Step.Stopped(stopOf(fallback.outcome))
        // Not delivered: a no-match row is not asked again for 30 days.
        if (fallback == FallbackBox.Result.Failed) return Step.Skipped(SkipReason.FallbackFailed)
        val saved = fallback as? FallbackBox.Result.Saved
        deliver(taken, saved?.result ?: ScrapeResult.NoMatch, saved?.box.orEmpty())
        return Step.Delivered()
    }

    private fun failureWord(reason: FailureReason): String = when (reason) {
        FailureReason.Network -> "network"
        FailureReason.RomUnreadable -> "rom-unreadable"
        is FailureReason.Server -> "server-${reason.code}"
    }

    /** The saved box front, or the step that ends the game without it. */
    private suspend fun boxFront(result: ScrapeResult.Matched, fresh: Boolean): Pair<List<SavedMedia>?, Step?> =
        when (val saved = pictures.save(result, MediaKind.BoxFront, fresh)) {
            is PictureOutcome.Saved -> saved.media to null
            // Not delivered, so the stored row and its pictures stay; the game comes back next run.
            // Its answer then comes from the cache, except for a fresh request (the row is still
            // marked to ask again): that costs one more API call every run until the picture
            // works. No cap: a run skips the game for its rest, three in a row end the run.
            is PictureOutcome.Server -> null to Step.Skipped(SkipReason.Server(saved.code))
            PictureOutcome.Network -> null to Step.Skipped(SkipReason.Network)
            else -> null to Step.Stopped(stopOf(saved))
        }

    /**
     * The main pass delivers the box front only. A disc or cartridge already saved goes with it
     * (the library then has nothing for the disc pass to do, unless the request is fresh: the
     * person asked again, so the disc pass fetches it again and the earlier one stays meanwhile).
     */
    private suspend fun matchedMain(taken: Taken, result: ScrapeResult.Matched): Step {
        val (box, failed) = boxFront(result, taken.request.fresh)
        if (box == null) return failed!!
        val savedSupport = pictures.savedBefore(result.match.screenScraperGameId, MediaKind.Support)
        // A match with no box front may have one elsewhere; the match stays.
        val fallback = if (box.isEmpty()) fallbackBox.fetch(taken.request) else FallbackBox.Result.None
        if (fallback is FallbackBox.Result.Stopped) return Step.Stopped(stopOf(fallback.outcome))
        deliver(taken, result, box + (fallback as? FallbackBox.Result.Saved)?.box.orEmpty() + savedSupport)
        return Step.Delivered()
    }

    /**
     * The disc pass: the box front saved by the main pass is reused (downloaded only if its files
     * are gone), then the disc or cartridge. That one never holds the game back: if it fails, the
     * game is delivered with its box front (no support in its media, so the library may ask again
     * later), or, for a fresh request, with the disc or cartridge saved before, if any. A stop met
     * on it (pause, refusal, full disk) then ends the run; a network error counts toward the
     * network errors in a row.
     */
    private suspend fun matchedSupport(taken: Taken, result: ScrapeResult.Matched): Step {
        val (box, failed) = boxFront(result, fresh = false)
        if (box == null) return failed!!
        val fresh = taken.request.fresh
        val support = pictures.save(result, MediaKind.Support, fresh)
        // A refusal is reported to the host before the delivery, as on the box front.
        val stop = when (support) {
            is PictureOutcome.Saved, is PictureOutcome.Server, PictureOutcome.Network -> null
            else -> stopOf(support)
        }
        val supportMedia = when {
            support is PictureOutcome.Saved -> support.media
            fresh -> pictures.savedBefore(result.match.screenScraperGameId, MediaKind.Support)
            else -> emptyList()
        }
        if (support !is PictureOutcome.Saved) {
            val kept = if (supportMedia.isEmpty()) "without its" else "with the earlier"
            log("scrape run: game ${result.match.screenScraperGameId} delivered $kept disc or cartridge picture")
        }
        deliver(taken, result, box + supportMedia)
        return Step.Delivered(stop, networkFailed = support == PictureOutcome.Network)
    }

    /** How a picture's stop ends the run: a pause waits, a refusal stops, no room retries. */
    private suspend fun stopOf(outcome: PictureOutcome): End = when (outcome) {
        is PictureOutcome.Paused -> End.Paused(outcome.resumeAtMillis)
        is PictureOutcome.Unavailable -> unavailable(outcome.reason)
        PictureOutcome.StorageFailed -> End.Retry
        is PictureOutcome.Saved, is PictureOutcome.Server, PictureOutcome.Network -> error("not a stop: ${outcome.javaClass.simpleName}")
    }

    /**
     * The scraper cannot work until something changes (a build, the person's sign-in): the host
     * hears why, once per run however many workers meet it.
     */
    private suspend fun unavailable(reason: UnavailableReason): End {
        val first = state.withLock { !unavailableReported.also { unavailableReported = true } }
        if (!first) return End.Stopped
        log("scrape run: scraper unavailable ($reason); run stopped, not retried")
        try {
            work.onUnavailable(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("scrape run: the host's unavailable hook failed (${e.javaClass.simpleName})")
        }
        return End.Stopped
    }

    private fun failed(reason: FailureReason): Step = when (reason) {
        FailureReason.Network -> Step.Skipped(SkipReason.Network)
        FailureReason.RomUnreadable -> Step.Skipped(SkipReason.RomUnreadable)
        is FailureReason.Server -> Step.Skipped(SkipReason.Server(reason.code))
    }

    private suspend fun deliver(taken: Taken, result: ScrapeResult, media: List<SavedMedia>) {
        val deliverAt = System.nanoTime()
        work.source.deliver(taken.request, result, media, taken.pass)
        currentCoroutineContext()[GameTiming]?.deliver(msSince(deliverAt))
    }

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429

        const val PAGE_SIZE = 20
        const val MAX_SERVER_ERRORS_IN_ROW = 3
        const val MAX_NETWORK_ERRORS_IN_ROW = 3

        /** How often the disc pass looks for new main-pass games (a scan added some). */
        const val MAIN_RECHECK_MILLIS = 10_000L

        /**
         * A cap on parallel games whatever the account allows: each holds a 1-2 MB answer while it
         * is parsed, a care on 2 GB devices. An account rarely allows more, and its bandwidth
         * limit is per account anyway, so more workers would not fetch faster.
         */
        const val MAX_WORKERS = 4

        private fun passWord(pass: ScrapePass): String = when (pass) {
            ScrapePass.Main -> "main"
            ScrapePass.Support -> "disc"
        }
    }
}
