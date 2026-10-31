package com.plumemu.library.scrape

import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.identify.NameTrust
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.RomHints

/**
 * How sure a ScreenScraper match is, from how it was found. Only a ROM
 * lookup that sent a fingerprint (a CRC or a serial) is exact: a lookup by file name and size
 * alone, or a name search, is likely.
 */
internal object ScraperTrust {
    fun matchedBy(result: MatchedBy, hints: RomHints?): ScrapeMatchedBy = when (result) {
        MatchedBy.Hash -> if (hints == null || !hints.crc32.isNullOrBlank() || !hints.serial.isNullOrBlank()) {
            // Without hints the scraper hashed the file itself; with them, only what they carried counts.
            ScrapeMatchedBy.HASH
        } else {
            ScrapeMatchedBy.FILE_NAME_SIZE
        }
        MatchedBy.Name -> ScrapeMatchedBy.NAME
        // The person picked it from a name search ("Fix box art..."). The row's
        // `is_person_choice` is what marks it the person's, keeps runs off it and gives its
        // title the person's trust ([of]).
        MatchedBy.PersonChoice -> ScrapeMatchedBy.NAME
        // Box art of another source found by the game's name: as likely as a name search (only the person's Fix box art replaces such a row).
        MatchedBy.Fallback -> ScrapeMatchedBy.NAME
        // A hack given its parent's art: a guess by name, never exact.
        MatchedBy.Parent -> ScrapeMatchedBy.NAME
    }

    /**
     * The trust a stored match's title has: the person's pick (`is_person_choice`) above every
     * automatic name, else by how it was found; an unknown (null) way counts as likely.
     */
    fun of(matchedBy: ScrapeMatchedBy?, isPersonChoice: Boolean): NameTrust = if (isPersonChoice) NameTrust.SCRAPER_PERSON_CHOICE else of(matchedBy)

    /** The trust of a run's match, by how it was found; an unknown (null) way counts as likely. */
    fun of(matchedBy: ScrapeMatchedBy?): NameTrust = when (matchedBy) {
        ScrapeMatchedBy.HASH -> NameTrust.SCRAPER_EXACT
        ScrapeMatchedBy.FILE_NAME_SIZE, ScrapeMatchedBy.NAME, null -> NameTrust.SCRAPER_LIKELY
    }
}
