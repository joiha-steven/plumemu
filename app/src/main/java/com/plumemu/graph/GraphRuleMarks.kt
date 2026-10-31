package com.plumemu.graph

import com.plumemu.library.scan.LibraryScanner

/**
 * Before a scan: rows judged by older naming rules or older scrape rules are marked, so this scan
 * names them again (NamingRulesMark.kt, ScrapeRulesMark.kt). Each step is guarded: a failure is
 * logged and the scan goes on.
 */
internal suspend fun AppGraph.markRowsForNewRules(scanner: LibraryScanner) {
    // Rows named by older naming rules are marked first; this scan names them.
    guarded("naming rules version") {
        markForNamingRules(settings.value?.flags?.namingRulesVersion, scanner::nameAgainForNewRules) { v ->
            settingsStore.update { it.copy(flags = it.flags.copy(namingRulesVersion = v)) }
        }
    }
    // Box-art results judged by older scrape rules are forgotten first; this scan names the games
    // named from them and the run after it asks them again (ScrapeRulesMark.kt). A run going
    // is cancelled: it would stop on a game it delivered coming back (as GraphScrapes.askAgain).
    guarded("scrape rules version") {
        markForScrapeRules(settings.value?.flags?.scrapeRulesVersion, { v ->
            scanner.askAgainForNewScrapeRules(v).also { forgotten -> if (forgotten > 0) scrapes.cancelRuns() }
        }) { v ->
            settingsStore.update { it.copy(flags = it.flags.copy(scrapeRulesVersion = v)) }
        }
    }
}
