package com.plumemu.scraper

import java.io.File

/** What one [Scraper.download] call came back with. */
public sealed interface MediaDownload {

    /**
     * The picture's bytes are in [file]. For a full-size download its md5 matched when
     * ScreenScraper gave one; a scaled one (maxwidth / maxheight) is not checked here.
     */
    public data class Done(val file: File) : MediaDownload

    /**
     * This picture cannot be used: not there (404), an empty or oversized body, a wrong md5 (full
     * size only), a URL on a host that must not get the credentials. Logged; the target file is
     * gone. Another picture of the same game may still work.
     */
    public data object Broken : MediaDownload

    /** The server failed (5xx, or 429 after every retry); asking again later may work. */
    public data class ServerError(val code: Int) : MediaDownload

    /** Nothing more can be asked before [resumeAtMillis], like [ScrapeResult.Paused]. */
    public data class Paused(val resumeAtMillis: Long, val reason: PauseReason) : MediaDownload

    /** Scraping cannot work until something changes, like [ScrapeResult.Unavailable]. */
    public data class Unavailable(val reason: UnavailableReason) : MediaDownload

    /** No connection, a timeout, or the connection broke (also mid-body); asking again later may work. */
    public data object NetworkDown : MediaDownload

    /** The picture arrived but could not be written to disk (full, read-only): not the network's fault, the run ends and retries later. */
    public data object StorageFailed : MediaDownload
}
