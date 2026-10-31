package com.plumemu.scraper.work

import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.Scraper
import com.plumemu.scraper.ScraperConfig
import com.plumemu.scraper.UnavailableReason
import com.plumemu.scraper.media.MediaSize
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.timing.ScrapeTimingLog
import java.io.File
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

/**
 * The games still to scrape; the library implements it over its Room database.
 *
 * This is how a stopped run resumes where it stopped: the worker asks only for what is not done.
 *
 * A run has two passes, so covers come first: the main pass ([pending]) asks
 * for the game and saves its box front only; the disc pass ([pendingSupport]) then saves the disc
 * or cartridge picture of matched games, once [pending] has nothing left and every main-pass game
 * of the run is done. The disc pass gives way as soon as [pending] has a game again.
 */
public interface ScrapeSource {
    /**
     * Up to [limit] requests not yet delivered, in a stable order (the same games first on every
     * call). A request [deliver]ed once must never come back from here, in this process or the
     * next: the library marks it done in the same transaction that stores the result. The worker
     * stops the run if one does come back, so a broken contract cannot loop. Requests the run has
     * in flight do come back (they are not delivered yet); the run leaves them out.
     */
    public suspend fun pending(limit: Int): List<ScrapeRequest>

    /**
     * Up to [limit] matched games whose disc or cartridge picture is still to fetch, in the
     * [pending] order; a game the main pass delivered in this run is among them. A request
     * delivered through [ScrapePass.Support] must not come back from here in the same run (the
     * worker stops the run if one does). Its [ScrapeRequest.fresh] asks for the disc or cartridge
     * picture again (the person asked for the game again); the run then still reads the answer
     * from the cache and reuses the saved box front. None by default.
     */
    public suspend fun pendingSupport(limit: Int): List<ScrapeRequest> = emptyList()

    /**
     * Stores what the scraper found for [request]: a [ScrapeResult.Matched] (with the pictures
     * saved for it in [media], possibly none) or [ScrapeResult.NoMatch]. Called once per request
     * and [pass]; a request whose run was stopped (Wi-Fi lost, cancelled, paused) is not delivered.
     *
     * [ScrapePass.Main] delivers a match with its box front only: the disc or cartridge was not
     * asked yet (for a [ScrapeRequest.fresh] request, the one saved before, if any, is in [media]
     * and is to be fetched again). [ScrapePass.Support] delivers the box front and, if it worked,
     * the disc or cartridge: when the result's media include a
     * [com.plumemu.scraper.MediaKind.Support] ref and [media] holds no support picture, that
     * download failed (or none of them was a picture), and the library may ask again later.
     * A box front that failed keeps the game undelivered in either pass.
     */
    public suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass)

    /**
     * The main pass asked [request] and it failed on the server's side (an HTTP error) or timed out
     * (a network error while other games of the run were answered, so the connection works), and
     * the game is not delivered. The library counts such attempts per day and stops offering a game
     * from [pending] after a few days of them. Not called for a game with no answer yet that was
     * paused, refused or stopped, nor for the disc pass (its own count is [pendingSupport]'s).
     * Does nothing by default.
     */
    public suspend fun failedAsking(request: ScrapeRequest) {}
}

/** Which pass of a run delivers a game (see [ScrapeSource]). */
public enum class ScrapePass {
    /** The lookup and the box front. */
    Main,

    /** The disc or cartridge picture of a matched game, after every box front. */
    Support,
}

/** How one run ended, as [ScrapeWorker] reports it to [ScraperWork.onEnded]. */
public enum class ScrapeRunEnd {
    /** Nothing left to ask: success. */
    Done,

    /** A pause (daily quota, server closed): a follow-up waits for the resume time. */
    Paused,

    /** The time budget was used: a follow-up starts at once. */
    Continue,

    /**
     * [ScraperWork.holdOff] said stop (a game is being played): nothing is queued; the app asks for
     * a run again when the person is back. Success.
     */
    HeldOff,

    /** No connection, the server unwell, a full disk: WorkManager runs the same work again after its backoff. */
    Retry,

    /** Refused credentials or a broken source: failure, not retried. */
    Stopped,
}

/**
 * Implemented by the app's Application class; the worker reads it with
 * `applicationContext as? ScraperHost`. An Application that is not one makes every run a no-op.
 */
public interface ScraperHost {
    /**
     * What one background run needs, or null when scraping is not set up. Called once per run, at
     * its start, on the worker's thread (suspending: the host may read its account file); give the
     * same [Scraper] for the whole process and account (it holds the request gate and the
     * credentials; a new password means a new one).
     */
    public suspend fun scraperWork(): ScraperWork?
}

/**
 * @property scraper made once per process and account with [Scraper.create]; give its
 *   [ScraperConfig] a [com.plumemu.scraper.SharedPreferencesPauseStore] so a pause outlives the
 *   process. It holds the credentials and stays stopped after a refusal: a new password (or a new
 *   account) means a new [Scraper].
 * @property mediaDir where pictures go: `<mediaDir>/<game id>/<kind>-<size>.webp`.
 * @property mediaSizes the named sizes each picture is saved at; empty saves no picture.
 * @property lowRamDevice null asks the device; tests force it.
 * @property clock epoch millis, to compute how long a pause lasts; same clock as the scraper's.
 * @property runBudgetMillis how long one run works before it hands over to a fresh one: Android
 *   stops a background job after about 10 minutes, and each stop grows the retry backoff.
 * @property onUnavailable called once when the run stops because [scraper] answered
 *   [com.plumemu.scraper.ScrapeResult.Unavailable] (a game lookup or a picture), with its reason,
 *   before the work fails; the app marks a refused account here
 *   ([UnavailableReason.BadUserCredentials]). A failure in it is logged, the run still stops.
 * @property onEnded called once at the end of every run, with how it ended and the WorkManager id
 *   of the work that ran (a retry keeps the id; a follow-up has a new one). WorkManager's own
 *   `runAttemptCount` grows on every start, also after a stop for a lost constraint, so it cannot
 *   tell a retry; the app keeps this instead. A failure in it is logged and changes nothing.
 * @property timingLog where the run writes its per-game and per-page timing lines; give the same
 *   one as the [Scraper]'s [ScraperConfig.timingLog] so a run's lines land together.
 * @property holdOff asked before each game is taken: true stops the run there (the games in flight
 *   are finished and delivered), and it ends [ScrapeRunEnd.HeldOff]. The app says true while a game
 *   is played, so the run does not compete with the emulator; it asks for a run again on the return.
 * @property runLock held for the whole of a run, including the unwinding after a cancel. Whoever must
 *   delete the saved pictures (the app's Clear box art and its sweep) takes it too, so no run can
 *   save a file or write a row meanwhile. Give every run of one process the same lock.
 */
public class ScraperWork(
    public val scraper: Scraper,
    public val source: ScrapeSource,
    public val mediaDir: File,
    public val mediaSizes: List<MediaSize>,
    public val lowRamDevice: Boolean? = null,
    public val clock: () -> Long = System::currentTimeMillis,
    public val log: (String) -> Unit = { android.util.Log.w(ScraperConfig.LOG_TAG, it) },
    public val runBudgetMillis: Long = DEFAULT_RUN_BUDGET_MILLIS,
    public val onUnavailable: suspend (UnavailableReason) -> Unit = {},
    public val onEnded: suspend (end: ScrapeRunEnd, workId: UUID) -> Unit = { _, _ -> },
    public val timingLog: ScrapeTimingLog = ScrapeTimingLog.Android,
    public val runLock: Mutex = Mutex(),
    public val holdOff: suspend () -> Boolean = { false },
) {
    public companion object {
        public const val DEFAULT_RUN_BUDGET_MILLIS: Long = 8L * 60 * 1000
    }
}
