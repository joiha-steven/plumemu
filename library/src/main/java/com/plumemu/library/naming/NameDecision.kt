package com.plumemu.library.naming

import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.identify.Identification
import com.plumemu.identify.NameTrust
import com.plumemu.identify.name.Region
import com.plumemu.identify.name.TitleCleaner

/** The name a game shows after the pass, and whether it is listed at all. */
internal data class ShownName(
    /** Null keeps the stored title (a "Not a game" fix names nothing). */
    val title: String?,
    val sortName: String?,
    /** Region codes, comma-separated, as `game.regions`. */
    val regions: String,
    val trust: NameTrust,
    val isNotGame: Boolean,
)

/**
 * Which name a game shows once the identifier answered (,
 * "Trust order"). Pure.
 *
 * - A person's fix, found under any of the game's keys, always wins.
 * - Otherwise the stored trust is compared with the new result through [NameTrust.winner]. The
 *   identifier's own earlier answer (`IDENTIFIER_*`, `FILE_NAME`) was about the file as it was
 *   then, so it counts as `FILE_NAME` and the new answer replaces it; a `PERSON_FIX` whose fix
 *   rows are gone ("Back to automatic" in another path) counts as `FILE_NAME` too. A ScreenScraper
 *   name keeps its trust: an exact one is replaced only by an exact identifier result. The
 *   title of a game the person picked (`SCRAPER_PERSON_CHOICE`, "Fix box art...") is always
 *   replaced here, and offered again by the naming pass while the pick is stored under the game's
 *   current key, so a file whose content changed loses the pick of its old content.
 */
internal object NameDecision {
    /** @return the name to show, or null to keep the shown name as it is. */
    fun decide(storedTrust: String, identification: Identification, fix: NameFixEntity?, fileRegions: String): ShownName? {
        if (fix != null) return fromFix(fix, identification, fileRegions)
        val stored = trustOf(storedTrust)
        val current = when (stored) {
            NameTrust.SCRAPER_EXACT, NameTrust.SCRAPER_LIKELY -> stored
            else -> NameTrust.FILE_NAME
        }
        val candidate = NameTrust.of(identification)
        // A pick's title is not kept here, even against a file-name result: the file may have
        // changed under another key with no pick. While the pick's row is under the game's key,
        // IdentifyPass offers its title again right after (ScrapedTitleOffer).
        if (stored != NameTrust.SCRAPER_PERSON_CHOICE && NameTrust.winner(current, candidate) != candidate) return null
        return ShownName(
            title = identification.title,
            sortName = TitleCleaner.sortName(identification.title),
            regions = regionCodes(identification.regions, fileRegions),
            trust = candidate,
            isNotGame = identification.hide,
        )
    }

    /**
     * Whether a ScreenScraper title with [candidate] trust (`SCRAPER_EXACT` for a match by hash,
     * `SCRAPER_LIKELY` by name) replaces the shown name, whose stored trust is [storedTrust]. Never
     * over a person's fix ([fix] found under any of the game's keys); otherwise only a stronger
     * trust wins ([NameTrust.winner]): an exact one upgrades `FILE_NAME` and `IDENTIFIER_LIKELY`,
     * a likely one only `FILE_NAME`, and nothing replaces `IDENTIFIER_EXACT` but the person's pick
     * (`SCRAPER_PERSON_CHOICE`), which replaces every automatic name and an earlier pick. A
     * `PERSON_FIX` whose fix rows are gone counts as `FILE_NAME`, as in [decide].
     */
    fun takesScraperName(storedTrust: String, candidate: NameTrust, fix: NameFixEntity?): Boolean {
        if (fix != null) return false
        val current = trustOf(storedTrust).takeIf { it != NameTrust.PERSON_FIX } ?: NameTrust.FILE_NAME
        // Above every automatic name; picking another game replaces the earlier pick's title (the
        // same level, the newer choice).
        if (candidate == NameTrust.SCRAPER_PERSON_CHOICE) return true
        return NameTrust.winner(current, candidate) == candidate && candidate != current
    }

    private fun fromFix(fix: NameFixEntity, identification: Identification, fileRegions: String): ShownName {
        val title = fix.title?.takeIf { fix.kind != NameFixKind.NOT_A_GAME }
        return ShownName(
            title = title,
            sortName = title?.let(TitleCleaner::sortName),
            regions = regionCodes(identification.regions, fileRegions),
            trust = NameTrust.PERSON_FIX,
            isNotGame = fix.kind == NameFixKind.NOT_A_GAME,
        )
    }

    /** A stored trust this version does not know (a later build wrote it) is taken as `FILE_NAME`. */
    fun trustOf(stored: String): NameTrust = NameTrust.entries.firstOrNull { it.name == stored } ?: NameTrust.FILE_NAME

    /** The identifier's region names as codes; the file name's when none of them is known here. */
    fun regionCodes(noIntroNames: List<String>, fileRegions: String): String {
        val regions = Region.fromNoIntroNames(noIntroNames)
        return if (regions.isEmpty()) fileRegions else regions.joinToString(",") { it.code }
    }
}
