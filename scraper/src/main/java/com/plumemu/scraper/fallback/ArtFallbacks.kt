package com.plumemu.scraper.fallback

import com.plumemu.scraper.FallbackArt
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScraperConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Every [ArtFallbackSource], asked in turn after ScreenScraper has no box front. They share the
 * scraper's HTTP client (timeouts, trust) but never its `ScreenScraperApi`: ScreenScraper's
 * credentials are added only there. A new source is one more entry in [forConfig].
 */
internal class ArtFallbacks(private val sources: List<ArtFallbackSource>) {
    /** The first source with a picture wins; a failure is kept over "nothing found", so the run counts it. */
    suspend fun lookup(request: ScrapeRequest, stopped: Set<String> = emptySet()): FallbackArt {
        var answer: FallbackArt = FallbackArt.NotApplicable
        for (source in sources) {
            if (source.name in stopped) {
                // Given up on for this run: not asked, but a game it covers is not "no match" (it is asked again next run).
                if (source.covers(request)) answer = FallbackArt.Failed("${source.name}-failed")
                continue
            }
            when (val found = source.lookup(request)) {
                FallbackArt.NotApplicable -> Unit
                is FallbackArt.NotFound -> if (answer !is FallbackArt.Failed) answer = found
                is FallbackArt.Failed -> answer = found
                is FallbackArt.Found -> return found
            }
        }
        return answer
    }

    /** The source that made the picture URL [url], or null for a ScreenScraper one. */
    fun ownerOf(url: String): ArtFallbackSource? = sources.firstOrNull { it.owns(url) }

    companion object {
        fun forConfig(config: ScraperConfig, http: OkHttpClient): ArtFallbacks =
            ArtFallbacks(
                listOf(
                    SteamArt(http, config.steamStoreSearchUrl.toHttpUrl(), config.steamImagesUrl.toHttpUrl(), config.log),
                    LibretroArt(http, config.libretroThumbnailsUrl.toHttpUrl(), config.cacheDir, config.clock, config.ioDispatcher, config.log),
                ),
            )
    }
}
