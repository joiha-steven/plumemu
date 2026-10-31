package com.plumemu.scraper

/**
 * What [Scraper.fallbackArt] found for a game ScreenScraper has no box art for. Each source of
 * box art other than ScreenScraper (Steam's image CDN today) answers in these four ways; the run
 * saves a [Found] result's pictures through the same [com.plumemu.scraper.work.RunPictures] as a
 * ScreenScraper match.
 *
 * [outcome] is the word the game's timing line (tag `PlumScrapeTiming`) shows.
 */
public sealed interface FallbackArt {
    /** No source covers this game (a NES game is no Steam game): nothing was asked anywhere. */
    public data object NotApplicable : FallbackArt

    /** A source covers the game and has nothing for it (no app id, no single exact name). */
    public data class NotFound(val outcome: String) : FallbackArt

    /**
     * A box front to download. [result] holds [MatchedBy.Fallback], an id of the form
     * `<source>_<id>` (the folder its pictures are saved in, kept in the row's ScreenScraper id
     * column: that prefix is how a stored row says where its art came from), no info and the
     * pictures to try in order; [words] are the timing words for what the run then finds.
     */
    public data class Found(val result: ScrapeResult.Matched, val words: OutcomeWords) : FallbackArt

    /** The network or the source's server failed; asking again later may work. */
    public data class Failed(val outcome: String) : FallbackArt
}

/** The timing words of one source: a picture saved, none of its pictures usable, its server or network failing. */
public data class OutcomeWords(val saved: String, val none: String, val failed: String)
