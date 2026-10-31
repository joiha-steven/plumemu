package com.plumemu.library.scrape

import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource

/**
 * A delivery of the run's main pass (lookup and box front), as most tests here deliver; a test of
 * the disc pass passes [ScrapePass.Support] to the member itself.
 */
internal suspend fun ScrapeSource.deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>) =
    deliver(request, result, media, ScrapePass.Main)
