package com.plumemu.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What one [Scraper.scrape] call found. */
public sealed interface ScrapeResult {

    /**
     * ScreenScraper knows the game. [info] holds the values picked for the person; [details]
     * everything the answer gave, for the library to store. [media] is every picture or manual
     * reference kept, by kind, the person's region first; the same list is in [details]. The run
     * downloads only [MediaKind.BoxFront] and [MediaKind.Support].
     */
    public data class Matched(
        val match: GameMatch,
        val info: GameInfo,
        val media: List<MediaRef>,
        val details: GameDetails,
    ) : ScrapeResult

    /** ScreenScraper does not know the game, by hash nor by name. */
    public data object NoMatch : ScrapeResult

    /** Nothing more can be asked before [resumeAtMillis] (epoch millis). */
    public data class Paused(val resumeAtMillis: Long, val reason: PauseReason) : ScrapeResult

    /** Scraping cannot work until something changes (a build, the person's sign-in). */
    public data class Unavailable(val reason: UnavailableReason) : ScrapeResult

    /** This call failed; asking again later may work. */
    public data class Failed(val reason: FailureReason) : ScrapeResult
}

public enum class PauseReason {
    /** The account's requests for today are used up; resumes at midnight Paris time. */
    DailyQuota,

    /** ScreenScraper closed its API (maintenance or overload); resumes in an hour. */
    ServerClosed,
}

public enum class UnavailableReason {
    /** This build carries no developer credentials. */
    NoDeveloperCredentials,

    /** ScreenScraper refused the developer credentials. */
    BadDeveloperCredentials,

    /** ScreenScraper blocked this software (softname). */
    SoftwareBlocked,

    /** ScreenScraper refused the person's username or password. */
    BadUserCredentials,
}

public sealed interface FailureReason {
    /** No connection, a timeout, or the connection broke. */
    public data object Network : FailureReason

    /** The server answered with an error [code]. */
    public data class Server(val code: Int) : FailureReason

    /** The ROM could not be read to hash it (moved, permission gone). */
    public data object RomUnreadable : FailureReason
}

public enum class MatchedBy {
    /** ScreenScraper's ROM lookup (`jeuInfos.php` by hash, serial, file name and size). */
    Hash,

    /** The name search, an exact title on the requested system. */
    Name,

    /** The person picked this game from a search ([Scraper.byGameId], "Fix box art..."). */
    PersonChoice,

    /** Not ScreenScraper: a box front from another source ([FallbackArt], Steam's image CDN). */
    Fallback,

    /** A `[Hack]` file given its parent game's box art and info ([ScrapeRequest.hack]); the title stays the hack's. */
    Parent,
}

/** Which ScreenScraper game was found, and how. */
public data class GameMatch(val screenScraperGameId: String, val matchedBy: MatchedBy)

/**
 * Game details in the request's language and region preference. Any field ScreenScraper does
 * not have is null. [rating] is ScreenScraper's note out of 20.
 */
public data class GameInfo(
    val title: String?,
    val synopsis: String?,
    val year: Int?,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val players: String?,
    val rating: Int?,
)

/**
 * A kind of media reference. The serial names are part of the stored [GameDetails] JSON: never
 * rename one.
 */
@Serializable
public enum class MediaKind {
    /** The front of the box (ScreenScraper media type "box-2D"). Downloaded by the run. */
    @SerialName("box-front")
    BoxFront,

    /**
     * The disc or cartridge (ScreenScraper media type "support-2D"; "support-texture", a flat
     * scan, is not used). Downloaded by the run, after the box front.
     */
    @SerialName("support")
    Support,

    /** A gameplay screenshot ("ss"). Reference only: never downloaded by the run. */
    @SerialName("screenshot")
    Screenshot,

    /** The title screen ("sstitle"). Reference only: never downloaded by the run. */
    @SerialName("title-screen")
    TitleScreen,

    /** The manual ("manuel", usually a PDF). Reference only: never downloaded by the run. */
    @SerialName("manual")
    Manual,
}

/**
 * A picture or manual to download. [url] carries no credentials: the downloader adds them at
 * request time, so nothing stored or logged ever holds them. [size] is ScreenScraper's size in
 * bytes, when given. Serial names are part of the stored [GameDetails] JSON.
 */
@Serializable
public data class MediaRef(
    @SerialName("kind") val kind: MediaKind,
    @SerialName("region") val region: String? = null,
    @SerialName("url") val url: String,
    @SerialName("format") val format: String? = null,
    @SerialName("md5") val md5: String? = null,
    @SerialName("size") val size: Long? = null,
)
