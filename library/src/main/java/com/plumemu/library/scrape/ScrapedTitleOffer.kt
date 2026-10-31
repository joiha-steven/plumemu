package com.plumemu.library.scrape

import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.identify.NameTrust
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.library.naming.NameDecision

/**
 * Offers a stored ScreenScraper title to one game's shown name: on delivery (every copy of the
 * file) and when the naming pass names the game again (a rescan, Back to automatic), so the title
 * is not lost. Call inside the transaction that read [storedTrust] and [fix].
 */
internal object ScrapedTitleOffer {
    /** @return true when [title] is now shown. */
    suspend fun offer(dao: ScrapedGameDao, gameId: Long, storedTrust: String, fix: NameFixEntity?, title: String?, candidate: NameTrust): Boolean {
        val shown = title?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!NameDecision.takesScraperName(storedTrust, candidate, fix)) return false
        return dao.showScraperName(gameId, shown, TitleCleaner.sortName(shown), candidate.name, storedTrust) == 1
    }
}
