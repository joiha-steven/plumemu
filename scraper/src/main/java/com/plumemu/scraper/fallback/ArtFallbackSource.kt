package com.plumemu.scraper.fallback

import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.MediaDownload
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeRequest
import java.io.File

/**
 * One place box art can come from when ScreenScraper has none. `ScreenScraperScraper` holds a list
 * of them and asks each in turn; add a source (libretro thumbnails, a hack's parent game) by
 * implementing this and listing it there. A source never receives ScreenScraper's credentials: its
 * downloads go through [download], not through the ScreenScraper downloader.
 */
internal interface ArtFallbackSource {
    /**
     * The source's short name, the word its timing outcomes start with (`steam`: `steam-none`, `steam-failed`,
     * `matched-steam-cdn`); the run counts failures per name ([com.plumemu.scraper.work.FallbackBox]).
     */
    val name: String

    /** True for a game this source covers (its system), decided without a request. */
    fun covers(request: ScrapeRequest): Boolean

    /** Looks [request]'s game up; [FallbackArt.NotApplicable] (no request made) for a game this source does not cover. */
    suspend fun lookup(request: ScrapeRequest): FallbackArt

    /** True for a picture URL this source made in [lookup]. */
    fun owns(url: String): Boolean

    /** Downloads one of this source's pictures into [target]; one request at a time. */
    suspend fun download(media: MediaRef, target: File): MediaDownload
}
