package com.plumemu.scraper

import com.plumemu.scraper.api.ScreenScraperApi
import com.plumemu.scraper.fallback.ArtFallbacks
import com.plumemu.scraper.fallback.LibretroArt
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The scraper of "Box art only": box fronts from libretro's public thumbnail server and nothing
 * else. It never calls ScreenScraper, sends no credential and needs no account or developer key;
 * [scrape] answers [ScrapeResult.NoMatch] without a request, so the run's no-match step asks
 * [fallbackArt] (libretro) for every game and keeps the poster cover where libretro has none.
 * Its pictures are saved by the run like any other box front.
 */
internal class LibretroOnlyScraper(
    config: ScraperConfig,
    http: OkHttpClient = ScreenScraperApi.defaultHttpClient(config.log),
) : Scraper {
    override val available: Boolean = true

    private val libretro = LibretroArt(http, config.libretroThumbnailsUrl.toHttpUrl(), config.cacheDir, config.clock, config.ioDispatcher, config.log)
    private val fallbacks = ArtFallbacks(listOf(libretro))

    override val pictureFailure: StateFlow<PictureFailure?> get() = libretro.failure

    override suspend fun scrape(request: ScrapeRequest): ScrapeResult = ScrapeResult.NoMatch

    override suspend fun fallbackArt(request: ScrapeRequest, stopped: Set<String>): FallbackArt = fallbacks.lookup(request, stopped)

    override suspend fun download(media: MediaRef, target: File, maxWidth: Int?, maxHeight: Int?): MediaDownload =
        // libretro's own pictures only; a URL of any other host is never fetched.
        if (libretro.owns(media.url)) libretro.download(media, target) else MediaDownload.Broken

    override suspend fun checkAccount(): AccountCheck = AccountCheck.Refused(UnavailableReason.NoDeveloperCredentials)
}
