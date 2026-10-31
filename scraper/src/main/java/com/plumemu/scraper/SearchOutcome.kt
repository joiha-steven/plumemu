package com.plumemu.scraper

/**
 * What one [Scraper.search] call found: the person's own query on `jeuRecherche.php`, for the
 * "Fix box art..." list. Like [ScrapeResult], every expected failure is a value.
 */
public sealed interface SearchOutcome {
    /**
     * ScreenScraper's results in its own order (no useful one), each game once. [truncated]: the
     * answer held as many games as `jeuRecherche.php` returns at most (30), so more may exist;
     * one exact title among them is then not proof that it is the only one.
     */
    public data class Found(val games: List<SearchHit>, val truncated: Boolean = false) : SearchOutcome

    /** No game for this query (ScreenScraper answers `"jeux":[{}]` or 404). */
    public data object NothingFound : SearchOutcome

    /** Nothing more can be asked before [resumeAtMillis] (epoch millis). */
    public data class Paused(val resumeAtMillis: Long, val reason: PauseReason) : SearchOutcome

    /** Searching cannot work until something changes (a build, the person's sign-in). */
    public data class Unavailable(val reason: UnavailableReason) : SearchOutcome

    /** This call failed; asking again later may work. */
    public data class Failed(val reason: FailureReason) : SearchOutcome
}

/**
 * One game in a search answer, enough for a list row. Pick one with [Scraper.byGameId], which
 * fetches the whole game.
 *
 * @property name the game's name in the request's region order (then world, ScreenScraper's
 *   default, then any); null when the answer gives none.
 * @property year the release year in the same region order.
 * @property systemName ScreenScraper's name of the game's system, as given.
 * @property boxFront the box front in the same region order, for a thumbnail: download it with
 *   [Scraper.download] and a small `maxWidth` / `maxHeight`. Its URL carries no credentials.
 */
public data class SearchHit(
    val screenScraperGameId: String,
    val name: String?,
    val year: Int?,
    val publisher: String?,
    val systemName: String?,
    val boxFront: MediaRef?,
)
