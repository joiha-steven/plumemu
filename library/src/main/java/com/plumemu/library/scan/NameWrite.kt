package com.plumemu.library.scan

import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameToIdentify
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.identify.Identification
import com.plumemu.library.naming.NameDecision
import com.plumemu.library.naming.ShownName
import com.plumemu.library.scrape.ScrapedTitleOffer
import com.plumemu.library.scrape.ScraperTrust

/** What the identifier answered for [game], and the `game.fingerprint` value to store with it. */
internal class NamedFile(val game: GameToIdentify, val identification: Identification, val fingerprint: String?)

/** A row [NameWrite.write] wrote: the name it now shows ([shown], null: kept) and the fix that named it. */
internal class NameWritten(val file: NamedFile, val shown: ShownName?, val fix: NameFixEntity?)

/**
 * The naming pass's write, shared by [IdentifyPass] and [ContentPass]: stores each
 * answer only while the file is the one read (`recordIdentification`), decides the shown name
 * from the stored trust and the person's fixes ([NameDecision]), offers a stored ScreenScraper
 * match its title again ([ScrapedTitleOffer]) and lets a fix made under the row key follow the
 * file's fingerprint keys. Callers run it inside `database.withTransaction`, so a fix made while
 * they read is re-read here and never undone.
 */
internal object NameWrite {
    /**
     * @param beforeOffer runs for each written row after its shown name, before the stored match's
     *   title is offered; answers true when it changed `scraped_game` under the row's new key (the
     *   match is then read again).
     */
    suspend fun write(
        database: PlumDatabase,
        results: List<NamedFile>,
        beforeOffer: suspend (NameWritten) -> Boolean = { false },
    ): List<NameWritten> {
        if (results.isEmpty()) return emptyList()
        val dao = database.identifyDao()
        val scrapedDao = database.scrapedGameDao()
        val states = dao.nameStates(results.map { it.game.id }).associateBy { it.id }
        // A person's fix is looked up by fingerprint keys and the row key, never by a product
        // code: revisions sharing a code must not share a fix (FixKeys.fixTargets).
        val keysOf = results.associate { it.game.id to (FixKeys.fixTargets(it.identification.fixKeys) + NameFixEntity.rowKey(it.game.id)) }
        val fixes = dao.fixesByKeys(keysOf.values.flatten()).associateBy { it.fixKey }
        val scrapeKeys = results.associate { it.game.id to FixKeys.forGame(it.game.id, FixKeys.join(it.identification.fixKeys)).first() }
        val scraped = scrapedDao.matchedTitles(scrapeKeys.values).associateBy { it.scrapeKey }
        val written = ArrayList<NameWritten>(results.size)
        for (result in results) {
            val game = result.game
            val identification = result.identification
            val state = states[game.id] ?: continue
            val fingerprints = identification.fingerprints
            val recorded = dao.recordIdentification(
                id = game.id,
                sizeBytes = game.sizeBytes,
                modifiedAt = game.modifiedAt,
                fileName = game.fileName,
                fingerprint = result.fingerprint,
                fingerprintSize = fingerprints?.let { game.sizeBytes },
                fingerprintMtime = fingerprints?.let { game.modifiedAt },
                fixKeys = FixKeys.join(identification.fixKeys),
                method = identification.method.name,
                confidence = identification.confidence.name,
                packVersion = identification.packVersion,
            )
            if (recorded == 0) continue
            val foundUnder = keysOf.getValue(game.id).firstOrNull { it in fixes }
            val fix = foundUnder?.let(fixes::get)
            val shown = NameDecision.decide(state.nameTrust, identification, fix, game.fileRegions)
            if (shown != null) dao.setShownName(game.id, shown.title, shown.sortName, shown.regions, shown.trust.name, shown.isNotGame)
            val row = NameWritten(result, shown, fix)
            written += row
            val scrapeKey = scrapeKeys.getValue(game.id)
            val match = if (beforeOffer(row)) scrapedDao.matchedTitles(listOf(scrapeKey)).firstOrNull() else scraped[scrapeKey]
            // A ScreenScraper match stored for this content keeps its title through a new decision
            // (a rescan, Back to automatic), by the same trust rules as on delivery.
            match?.takeIf { fix == null && !identification.hide }?.let {
                val trustNow = shown?.trust?.name ?: state.nameTrust
                ScrapedTitleOffer.offer(scrapedDao, game.id, trustNow, null, it.title, ScraperTrust.of(it.matchedBy, it.isPersonChoice))
            }
            if (shown == null) continue
            if (fix != null && foundUnder != null && FixKeys.isCopyableFrom(foundUnder)) {
                // A fix made before the file's keys were known (row key) follows the file from
                // now on, on its fingerprint keys only (FixKeys.fixTargets).
                val missing = FixKeys.fixTargets(identification.fixKeys).filter { it !in fixes }
                dao.insertFixesIfMissing(missing.map { NameFixEntity(it, fix.kind, fix.title, fix.titleSource, null, fix.fixedAt) })
            }
        }
        return written
    }
}
