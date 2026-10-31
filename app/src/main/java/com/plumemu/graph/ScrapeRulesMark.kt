package com.plumemu.graph

import com.plumemu.library.scrape.SCRAPE_RULES_VERSION

/**
 * Before a scan: box-art results judged by older scrape rules ([SCRAPE_RULES_VERSION] above
 * [storedVersion], the app's `flags.scrapeRulesVersion`; null, settings not read yet, counts as
 * current) are forgotten ([mark], `LibraryScanner.askAgainForNewScrapeRules`), then the new
 * version recorded ([record]); the scan names the games whose name came from them and the
 * box-art run after it (`StartupTasks.scanEnded`) asks them again. True when it marked. Nothing
 * is recorded when [mark] throws, so the next scan tries again (marking twice forgets nothing
 * more: the run's new answers are judged by the new rules).
 */
internal suspend fun markForScrapeRules(
    storedVersion: Int?,
    mark: suspend (Int) -> Int,
    record: suspend (Int) -> Unit,
): Boolean {
    if (storedVersion == null || storedVersion >= SCRAPE_RULES_VERSION) return false
    mark(storedVersion)
    record(SCRAPE_RULES_VERSION)
    return true
}
