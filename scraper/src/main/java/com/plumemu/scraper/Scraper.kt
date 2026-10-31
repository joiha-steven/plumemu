package com.plumemu.scraper

import com.plumemu.scraper.timing.ScrapeTimingLog
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.plumemu.scraper.fallback.LibretroArt
import com.plumemu.scraper.fallback.SteamArt
import java.io.File

/**
 * The scraper's door: the library asks it about one game at a time.
 *
 * Calls may run in parallel; the scraper itself keeps to the account's thread limit, spaces
 * requests and pauses on the daily quota. It never throws for an expected failure: every
 * outcome is a [ScrapeResult]. Cancelling the caller's coroutine stops hashing and the request.
 */
public interface Scraper {
    /** False when this build has no developer credentials: every call is [ScrapeResult.Unavailable]. */
    public val available: Boolean

    /**
     * How many requests may run at once now: 1 until an answer gives the account's `maxthreads`,
     * then that, and 1 again for the rest of the session after a "too many threads" answer. The
     * background run works on this many games together; the scraper still
     * holds every request to it. 1 by default.
     */
    public val requestSlots: StateFlow<Int> get() = ONE_REQUEST_SLOT

    /**
     * The account's limits from the latest answer that carried an `ssuser` (a lookup, a search, a
     * lookup by id or the sign-in check); null until one did in this process. Never stored.
     */
    public val accountLimits: StateFlow<AccountLimits?> get() = NO_LIMITS

    public suspend fun scrape(request: ScrapeRequest): ScrapeResult

    /**
     * The person's own search ("Fix box art..."): `jeuRecherche.php` with [title]
     * on [systemId] (Plumemu's id; a system without a ScreenScraper id searches every system),
     * through the same gate as [scrape]: thread limit, spacing, retries, pauses, refusals. Every
     * game of the answer is returned, not only an exact title; names, years and box fronts are
     * picked in [regions] order. Never cached: a search answer is 2 MB and the person expects
     * ScreenScraper's current list. A blank [title] asks nothing and is [SearchOutcome.NothingFound].
     *
     * The default answers [SearchOutcome.Unavailable]: only the engine's scraper searches (test
     * fakes need not).
     */
    public suspend fun search(title: String, systemId: String, regions: List<String> = emptyList()): SearchOutcome =
        SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials)

    /**
     * The game the person picked from [search]: `jeuInfos.php?gameid=` [gameId], answered as
     * [scrape] answers a match (info, media, details, in [request]'s regions and languages) with
     * [MatchedBy.PersonChoice], or [ScrapeResult.NoMatch] when ScreenScraper does not know the id
     * (or it is not a number: nothing is asked). Cached like a scrape for 30 days, unless
     * [ScrapeRequest.fresh]; [request]'s ROM is never read.
     *
     * The default answers [ScrapeResult.Unavailable]: only the engine's scraper looks games up by
     * id (test fakes need not).
     */
    public suspend fun byGameId(gameId: String, request: ScrapeRequest): ScrapeResult =
        ScrapeResult.Unavailable(UnavailableReason.NoDeveloperCredentials)

    /**
     * After a person renames a game: [search] narrowed to the games a run's name search accepts.
     * [SearchOutcome.Found] lists, each game id once, only the games of [systemId] one of whose
     * names equals [title] after `TitleMatch.normalise`; none is [SearchOutcome.NothingFound]. The
     * caller decides what more than one means (the rename lookup takes only exactly one). A system
     * without a ScreenScraper id, or a blank [title], asks nothing and is
     * [SearchOutcome.NothingFound]: a same-titled game of another system is not this one. Never
     * cached, through the same gate as [search].
     *
     * The default answers [SearchOutcome.Unavailable], like [search].
     */
    public suspend fun searchExactTitle(title: String, systemId: String, regions: List<String> = emptyList()): SearchOutcome =
        SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials)

    /**
     * Downloads [media] into [target] through the same request gate as [scrape]: the same
     * thread limit, spacing and pause. Credentials are added to the URL only for the request.
     *
     * [maxWidth] and [maxHeight] ask ScreenScraper for a picture scaled to fit inside them (sent
     * as `maxwidth` / `maxheight`; a box front of 173 KB came as 62 KB at 320). A scaled picture no
     * longer matches the md5 in the game's JSON, so the md5 is checked only when neither is given;
     * a scaled one is checked by decoding it when it is saved.
     */
    public suspend fun download(
        media: MediaRef,
        target: File,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ): MediaDownload

    /**
     * Asks `ssuserInfos.php` whether ScreenScraper knows the person's username and password,
     * through the same gate as [scrape]; never cached. Needed because a scrape with a wrong
     * password is answered as anonymous instead of refused.
     */
    public suspend fun checkAccount(): AccountCheck

    /**
     * Box art from a source other than ScreenScraper, for a game ScreenScraper has none for (the run
     * asks after a no-match, or a match without a box front). [FallbackArt.NotApplicable] with no
     * request for a game no source covers; today Steam games (Steam's image CDN). Its pictures are
     * downloaded with [download], which sends them to their own source, never ScreenScraper's
     * credentials. Never throws for a network failure. [stopped] names sources (`steam`, `libretro`) the run gave
     * up on: they are not asked, and a game one of them covers answers [FallbackArt.Failed] (left for the next run).
     */
    public suspend fun fallbackArt(request: ScrapeRequest, stopped: Set<String> = emptySet()): FallbackArt = FallbackArt.NotApplicable

    public companion object {
        public fun create(config: ScraperConfig): Scraper =
            if (config.developer.isBlank) UnavailableScraper else ScreenScraperScraper(config)
    }
}

/** [Scraper.accountLimits] of a scraper that never learns more. */
private val NO_LIMITS: StateFlow<AccountLimits?> = MutableStateFlow<AccountLimits?>(null).asStateFlow()

/** [Scraper.requestSlots] of a scraper that never learns more. */
private val ONE_REQUEST_SLOT: StateFlow<Int> = MutableStateFlow(1).asStateFlow()

/**
 * Everything the scraper needs from outside.
 *
 * @property cacheDir where the hash cache and the response cache live (the app's cache dir).
 * @property user the person's ScreenScraper account; null scrapes with the developer quota only.
 *   When set, an answer that comes back as anonymous (`ssuser.numid` "0") stops the scraper with
 *   [UnavailableReason.BadUserCredentials].
 * @property hashCache where ROM hashes are kept; null keeps them in a file in [cacheDir].
 * @property pauseStore where a pause outlives the process ([SharedPreferencesPauseStore]); null
 *   keeps it in memory only.
 * @property clock epoch millis; tests pass a virtual clock.
 * @property timingLog where the per-request timing lines go (never a URL or a credential).
 */
public class ScraperConfig(
    public val cacheDir: File,
    public val user: UserCredentials? = null,
    public val developer: DeveloperCredentials = DeveloperCredentials.fromBuildConfig(),
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val softname: String = SOFTNAME,
    public val hashCache: HashCache? = null,
    public val pauseStore: PauseStore? = null,
    public val clock: () -> Long = System::currentTimeMillis,
    public val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    public val log: (String) -> Unit = { Log.w(LOG_TAG, it) },
    public val timingLog: ScrapeTimingLog = ScrapeTimingLog.Android,
    public val steamStoreSearchUrl: String = SteamArt.STORE_SEARCH_URL,
    public val steamImagesUrl: String = SteamArt.IMAGES_URL,
    public val libretroThumbnailsUrl: String = LibretroArt.BASE_URL,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.screenscraper.fr/api2/"
        public const val SOFTNAME: String = "Plumemu"
        internal const val LOG_TAG: String = "Scraper"
    }
}

/** The app's ScreenScraper developer account. [toString] never shows the password. */
public class DeveloperCredentials(public val devId: String, public val devPassword: String) {
    public val isBlank: Boolean get() = devId.isBlank() || devPassword.isBlank()

    override fun toString(): String = "DeveloperCredentials(devId=***, devPassword=***)"

    public companion object {
        /** Set at build time from secrets.properties or CI secrets; empty in a plain build. */
        public fun fromBuildConfig(): DeveloperCredentials =
            DeveloperCredentials(BuildConfig.SCREENSCRAPER_DEV_ID, BuildConfig.SCREENSCRAPER_DEV_PASSWORD)

        /** Whether this build carries both credentials. Safe to log: it never exposes a value. */
        public val buildHasCredentials: Boolean get() = BuildConfig.HAS_DEVELOPER_CREDENTIALS
    }
}

/** The person's ScreenScraper account. [toString] never shows the password. */
public class UserCredentials(public val username: String, public val password: String) {
    public val isBlank: Boolean get() = username.isBlank() || password.isBlank()

    override fun toString(): String = "UserCredentials(username=***, password=***)"
}

/** A build without developer credentials: no network call, ever. */
internal object UnavailableScraper : Scraper {
    override val available: Boolean = false

    override suspend fun scrape(request: ScrapeRequest): ScrapeResult =
        ScrapeResult.Unavailable(UnavailableReason.NoDeveloperCredentials)

    override suspend fun search(title: String, systemId: String, regions: List<String>): SearchOutcome =
        SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials)

    override suspend fun byGameId(gameId: String, request: ScrapeRequest): ScrapeResult =
        ScrapeResult.Unavailable(UnavailableReason.NoDeveloperCredentials)

    override suspend fun searchExactTitle(title: String, systemId: String, regions: List<String>): SearchOutcome =
        SearchOutcome.Unavailable(UnavailableReason.NoDeveloperCredentials)

    override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload =
        MediaDownload.Unavailable(UnavailableReason.NoDeveloperCredentials)

    override suspend fun checkAccount(): AccountCheck = AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials)
}
