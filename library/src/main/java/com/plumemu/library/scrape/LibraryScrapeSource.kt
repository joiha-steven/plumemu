package com.plumemu.library.scrape

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.room.withTransaction
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameToScrape
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.PinnedShortcutDao
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapeTarget
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.identify.NameTrust
import com.plumemu.library.naming.NameDecision
import com.plumemu.library.scan.ContentResolverDocumentTree
import com.plumemu.library.scan.DocumentTree
import com.plumemu.library.scan.LOG_TAG
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource
import java.util.Collections
import java.util.Locale

/**
 * The library's games for the ScreenScraper run, over core-data's `scraped_game`
 * ([ScrapedGameDao]).
 *
 * - [pending]: listed, named games whose first fix key has no stored result, a stored result the
 *   person asked for again ([askAgain]: `ask_again`, never a person's choice), or a no-match older
 *   than 30 days the person did not choose; Recent first (latest played), then Favorites, then
 *   [currentSystemId]'s games, then the rest by row id. One SQL page per call (`LIMIT`).
 * - [deliver] stores the result under the request's key and offers a ScreenScraper title to the
 *   naming of every copy, in one transaction; the stored row is what keeps the game out of
 *   [pending], in this process and the next, and a game it did not store (changed meanwhile) is
 *   skipped until the next process.
 * - [pendingSupport]: the run's disc pass, after every box front: the matched
 *   games whose disc or cartridge picture is still to fetch, the main pass's games of this run
 *   included; [deliver] with [ScrapePass.Support] stores them again with it.
 *
 * @param currentSystemId the system the person is viewing, or null.
 * @param preferredRegion Settings › Library › Box art region, a ScreenScraper code ("us").
 * @param documentUri the document URI of a file, the request's `rom.key`.
 */
class LibraryScrapeSource(
    private val database: PlumDatabase,
    private val openTree: (treeUri: String) -> DocumentTree,
    private val currentSystemId: () -> String?,
    private val preferredRegion: suspend () -> String,
    private val deviceLanguage: () -> String = { Locale.getDefault().language },
    private val clock: () -> Long = System::currentTimeMillis,
    private val documentUri: (treeUri: String, documentId: String) -> String = ::documentUriOf,
) : ScrapeSource {
    private val dao = database.scrapedGameDao()

    /**
     * Document URIs this source was given a result for but stored nothing (the file changed
     * between [pending] and [deliver]; its new key is asked later). The database alone would
     * offer such a game again at once and the worker would stop the run on "a delivered request
     * came back", so it is skipped until the next process. Only these: a stored game leaves
     * [pending] through its row, so the set stays a handful and pages stay one `LIMIT` query.
     */
    private val notStoredRomKeys: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** The same for the disc pass: games its delivery could not store, skipped by [pendingSupport]. */
    private val notStoredSupportKeys: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override suspend fun pending(limit: Int): List<ScrapeRequest> {
        if (limit <= 0) return emptyList()
        val skip = synchronized(notStoredRomKeys) { notStoredRomKeys.toSet() }
        val rows = dao.pending(clock() - ScrapedGameDao.NO_MATCH_RETRY_MILLIS, currentSystemId(), limit + skip.size)
        return requests(withoutSkipped(rows, skip, limit))
    }

    /**
     * The person asked for box art again (Settings): [which] games of [systemIds]
     * (null: every system) come back from [pending], a person's choice never. Each stored row and
     * its pictures stay until the run stores the new answer. Also forgets the games this source
     * could not store, so they are offered again too. The caller then starts a fresh run: one
     * going would stop on a game it delivered coming back.
     *
     * @return the stored rows marked (games with no row are pending already).
     */
    suspend fun askAgain(which: AskAgain, systemIds: Set<String>? = null): Int {
        val systems = systemIds?.toList()
        if (systems != null && systems.isEmpty()) return 0
        val marked =
            when (which) {
                AskAgain.Missing -> if (systems == null) dao.markMissingToAskAgain() else dao.markMissingToAskAgainIn(systems)
                AskAgain.All -> if (systems == null) dao.markAllToAskAgain() else dao.markAllToAskAgainIn(systems)
            }
        // Only Re-download all lifts the retry cap: Download missing asks the games that can still be asked.
        val lifted = if (which != AskAgain.All) 0 else if (systems == null) dao.forgetFailures() else dao.forgetFailuresIn(systems)
        notStoredRomKeys.clear()
        notStoredSupportKeys.clear()
        Log.i(LOG_TAG, "Scrape: ${which.name.lowercase()} asked again, $marked stored results marked, $lifted given-up games lifted")
        return marked
    }

    /**
     * The row key ([com.plumemu.coredata.db.FixKeys], first line) [request] stores under, or null
     * for a request this source did not make. Two copies of a file share it; the app's run source
     * remembers it per run (`ScrapeRunSource`).
     */
    fun scrapeKeyOf(request: ScrapeRequest): String? = (request.rom as? DocumentRomSource)?.scrapeKey

    private fun withoutSkipped(rows: List<GameToScrape>, skip: Set<String>, limit: Int): List<GameToScrape> =
        if (skip.isEmpty()) rows else rows.filter { documentUri(it.treeUri, it.documentId) !in skip }.take(limit)

    /**
     * Matched games whose disc or cartridge picture is still to fetch and was tried fewer than three
     * times by a disc pass ([ScrapedGameDao.MAX_SUPPORT_ATTEMPTS]), in the [pending] order; never a
     * person's choice or a game asked for again (that one goes through [pending] first). A game the
     * main pass delivered a moment ago is among them, so it gets its disc in the same run; the
     * app's run source remembers which games the disc pass already tried in a run.
     *
     * [ScrapeRequest.fresh] is set when a disc or cartridge is saved already (the person asked for
     * the game again, Re-download): the disc pass fetches it again, and the old one stays until the
     * new one replaces it. The run reads the answer from its cache either way.
     */
    override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> {
        if (limit <= 0) return emptyList()
        val skip = synchronized(notStoredSupportKeys) { notStoredSupportKeys.toSet() }
        val rows = dao.pendingSupport(currentSystemId(), limit + skip.size)
        return requests(withoutSkipped(rows, skip, limit), discPass = true)
    }

    /**
     * Stores [result] for [request] in one transaction: the row (a person's choice is never
     * replaced, a match never becomes a no-match) and, for a match, its title offered to every
     * copy of the file through [NameDecision.takesScraperName] ([ScraperTrust]: exact only when
     * the lookup sent a CRC or a serial). Nothing is stored when the file changed since [pending]
     * (still to be named, another size or time, another first key): it is asked again later
     * under its new key.
     *
     * @throws IllegalArgumentException for a request this source did not make, or a result that
     *   is neither a match nor a no-match (the run delivers no other).
     */
    override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
        if (store(request, result, media, pass)) return
        when (pass) {
            ScrapePass.Main -> notStoredRomKeys += request.rom.key
            ScrapePass.Support -> notStoredSupportKeys += request.rom.key
        }
    }

    /**
     * The run's main pass found [request] failing on a server error or a timeout: one more day of it
     * for the game's key ([ScrapedGameDao.recordFailure]). After [ScrapedGameDao.MAX_FAILED_DAYS]
     * different days [pending] stops offering it, until Re-download all or Fix box art.
     */
    override suspend fun failedAsking(request: ScrapeRequest) {
        val key = scrapeKeyOf(request) ?: return
        dao.recordFailure(key, clock() / DAY_MILLIS)
    }

    /**
     * @param pass the main pass stores a match with its box front and starts its disc attempts at
     *   0 (none was asked); the disc pass counts a missing disc or cartridge as one more attempt.
     * @return false when nothing was written (the game is gone with its row key, or its file changed).
     */
    private suspend fun store(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass): Boolean {
        val rom = requireNotNull(request.rom as? DocumentRomSource) { "a request this source did not make" }
        val now = clock()
        val rowGameId = rom.gameId.takeIf { rom.scrapeKey.startsWith(NameFixEntity.ROW_PREFIX) }
        val row = when (result) {
            is ScrapeResult.Matched ->
                ScrapedRows.matched(rom.scrapeKey, rowGameId, result, request.hints, media, now, discToFetchAgain = pass == ScrapePass.Main && request.fresh)
            ScrapeResult.NoMatch -> ScrapedRows.noMatch(rom.scrapeKey, rowGameId, now)
            else -> throw IllegalArgumentException("only a match or a no-match is delivered, not ${result.javaClass.simpleName}")
        }
        return database.withTransaction {
            val target = dao.target(rom.gameId)
            when {
                // Gone with its row key: nothing can show it. A content key outlives its game (the file may come back).
                target == null && rowGameId != null -> {
                    Log.i(LOG_TAG, "Scrape: game ${rom.gameId} was deleted before its result arrived")
                    return@withTransaction false
                }
                target != null && !target.isStill(rom) -> {
                    Log.i(LOG_TAG, "Scrape: game ${rom.gameId} changed before its result arrived; asked again later")
                    return@withTransaction false
                }
            }
            // Only the main pass's fresh request answers a Re-download or Download missing mark; one
            // set while this game was in flight stays, so the main pass asks it again.
            val stored = dao.storeFromRun(
                keepingBoxFront(row, pass),
                supportAsked = pass == ScrapePass.Support,
                answersAskAgain = pass == ScrapePass.Main && request.fresh,
            )
            if (stored == ScrapedGameDao.RunStore.STORED && result is ScrapeResult.Matched) {
                offerToCopies(rom.scrapeKey, result.info.title, ScraperTrust.of(row.matchedBy))
            }
            true
        }
    }

    /**
     * The disc pass delivers a match with the box front it saved again. A match ScreenScraper has no
     * box front for gets one from a fallback source in the main pass (its files in another folder);
     * the disc pass then has none to hand back, and replacing the row with it would lose that box
     * and ask for it again for ever. So a disc-pass row without a box front keeps the stored one.
     */
    private suspend fun keepingBoxFront(row: ScrapedGameEntity, pass: ScrapePass): ScrapedGameEntity {
        if (pass != ScrapePass.Support || row.outcome != ScrapeOutcome.MATCHED || row.hasBoxFront) return row
        val old = dao.get(row.scrapeKey)?.takeIf { it.outcome == ScrapeOutcome.MATCHED && it.hasBoxFront } ?: return row
        val boxes = ScrapedPictures.decode(old.pictures).filter { it.kind == ScrapedPicture.BOX_FRONT }
        return row.copy(pictures = ScrapedPictures.encode(boxes + ScrapedPictures.decode(row.pictures)), hasBoxFront = true)
    }

    /**
     * The request for [gameId] as a run would build it (system, title, regions, languages, hints),
     * [ScrapeRequest.fresh] set: what "Fix box art..." hands to `Scraper.byGameId` for the picked
     * game, and whose system and regions it searches with. Null when the game is gone or is being
     * named again.
     */
    suspend fun personChoiceRequest(gameId: Long): ScrapeRequest? {
        val row = dao.toScrape(gameId) ?: return null
        return requests(listOf(row)).single().copy(fresh = true)
    }

    /**
     * Stores the person's [choice] for [gameId]'s first fix key, in one transaction, over whatever
     * was stored (a run's match, a no-match, an earlier choice; an `ask_again` mark is cleared),
     * with `is_person_choice` set so runs never replace it.
     *
     * - [PersonChoice.Picked]: the game's row with its pictures; its title is offered to every copy
     *   of the file with `SCRAPER_PERSON_CHOICE` ([NameDecision.takesScraperName]): it replaces a
     *   name the identifier or a run gave and an earlier pick, never the person's own fix.
     * - [PersonChoice.NoBoxArt]: a no-match row with no pictures. The shown name is kept for now;
     *   a copy still named by an earlier pick is marked to be named again (`needs_identify`), so the
     *   identifier's name replaces the pick's on the next naming pass; then the answer is
     *   [PersonChoiceStore.STORED_TO_NAME_AGAIN] and the caller runs the pass
     *   (`LibraryScanner.nameWaiting()`, as Back to automatic does).
     *
     * The pictures of an earlier choice stay on disk (they are under its ScreenScraper id, and may
     * be another game's too).
     */
    suspend fun storePersonChoice(gameId: Long, choice: PersonChoice): PersonChoiceStore {
        val now = clock()
        return database.withTransaction {
            val target = dao.target(gameId) ?: return@withTransaction PersonChoiceStore.GAME_GONE
            if (target.needsIdentify) return@withTransaction PersonChoiceStore.NOT_NAMED_YET
            val scrapeKey = FixKeys.forGame(gameId, target.fixKeys).first()
            val rowGameId = gameId.takeIf { scrapeKey.startsWith(NameFixEntity.ROW_PREFIX) }
            when (choice) {
                is PersonChoice.Picked -> storePicked(scrapeKey, rowGameId, choice, now)
                PersonChoice.NoBoxArt -> {
                    dao.insertOrReplace(ScrapedRows.noMatch(scrapeKey, rowGameId, now).copy(isPersonChoice = true))
                    var toName = 0
                    for (copy in dao.targetsWithKey(scrapeKey)) {
                        toName += dao.nameAgainIfTrust(copy.id, NameTrust.SCRAPER_PERSON_CHOICE.name)
                    }
                    if (toName > 0) return@withTransaction PersonChoiceStore.STORED_TO_NAME_AGAIN
                }
            }
            PersonChoiceStore.STORED
        }
    }

    /**
     * The lookup after the person renamed [gameId] (a chosen title or own name): [RenameLookup.Ready] with the stored name and the request a run
     * would send ([personChoiceRequest]) only when the game shows no box art: no row under its
     * scrape key, or a run's no-match or match without a box front. The row key is checked too: a
     * game keyed by its row until the rename's whole-file read still had that row's art when the
     * person renamed it. A person's choice, a run's box front, a game being named, gone or no
     * longer renamed is a [RenameLookup.Skip].
     */
    suspend fun renameLookup(gameId: Long): RenameLookup {
        // Read without a transaction: nothing is written here, and [storeRenameMatch] checks it all again.
        val target = dao.target(gameId) ?: return RenameLookup.Skip(RenameSkip.GAME_GONE)
        if (target.needsIdentify) return RenameLookup.Skip(RenameSkip.BEING_NAMED)
        val title = renamedTitle(gameId) ?: return RenameLookup.Skip(RenameSkip.NAME_CHANGED)
        artSkip(gameId, target)?.let { return RenameLookup.Skip(it) }
        val request = personChoiceRequest(gameId) ?: return RenameLookup.Skip(RenameSkip.NOT_LOOKED_UP)
        return RenameLookup.Ready(title, request, fileMatchId(gameId, target))
    }

    /**
     * Stores [picked] (found by the name [title]) for [gameId] the way [storePersonChoice] stores
     * a pick (`is_person_choice`, the title offered to every copy, never over the person's own
     * fix), in one transaction, and only if it still holds: the game is there and named, its fix
     * still holds [title], and it still has no box art (a run or the person may have given it some
     * while the lookup ran). Null when stored, else why nothing was written.
     */
    suspend fun storeRenameMatch(gameId: Long, title: String, picked: PersonChoice.Picked): RenameSkip? {
        val now = clock()
        return database.withTransaction {
            val target = dao.target(gameId) ?: return@withTransaction RenameSkip.GAME_GONE
            if (target.needsIdentify) return@withTransaction RenameSkip.BEING_NAMED
            if (renamedTitle(gameId) != title) return@withTransaction RenameSkip.NAME_CHANGED
            artSkip(gameId, target)?.let { return@withTransaction it }
            val byFile = fileMatchId(gameId, target)
            if (byFile != null && byFile != picked.result.match.screenScraperGameId) return@withTransaction RenameSkip.MATCHED_BY_FILE
            val scrapeKey = FixKeys.forGame(gameId, target.fixKeys).first()
            storePicked(scrapeKey, gameId.takeIf { scrapeKey.startsWith(NameFixEntity.ROW_PREFIX) }, picked, now)
            null
        }
    }

    /** The name of [gameId]'s chosen-title or own-name fix; null for none, Not a game or a blank one. */
    private suspend fun renamedTitle(gameId: Long): String? {
        val fix = database.identifyDao().fixOf(gameId) ?: return null
        if (fix.kind != NameFixKind.CHOSEN_TITLE && fix.kind != NameFixKind.OWN_NAME) return null
        return fix.title?.takeIf { it.isNotBlank() }
    }

    /** Why [target]'s box art is not the lookup's to fill (under its scrape key or its row key), or null. */
    private suspend fun artSkip(gameId: Long, target: ScrapeTarget): RenameSkip? {
        val rows = storedRows(gameId, target)
        return when {
            rows.any { it.isPersonChoice } -> RenameSkip.PERSON_CHOICE
            rows.any { it.hasBoxFront } -> RenameSkip.HAS_BOX_ART
            else -> null
        }
    }

    /**
     * The ScreenScraper game a run matched [target]'s file to by the file itself (hash, serial, or
     * file name and size: anything but the name search), or null. Precision first: a name search
     * never replaces such a match with another game.
     */
    private suspend fun fileMatchId(gameId: Long, target: ScrapeTarget): String? =
        storedRows(gameId, target).firstOrNull { row ->
            !row.isPersonChoice && row.outcome == ScrapeOutcome.MATCHED && row.matchedBy != null &&
                row.matchedBy != ScrapeMatchedBy.NAME && !row.screenScraperId.isNullOrBlank()
        }?.screenScraperId

    /** The rows under [target]'s scrape key and its row key (the key it had before a content key landed). */
    private suspend fun storedRows(gameId: Long, target: ScrapeTarget): List<ScrapedGameEntity> =
        listOf(FixKeys.forGame(gameId, target.fixKeys).first(), NameFixEntity.rowKey(gameId)).distinct().mapNotNull { dao.get(it) }

    private suspend fun storePicked(scrapeKey: String, rowGameId: Long?, choice: PersonChoice.Picked, now: Long) {
        dao.forgetFailure(scrapeKey)
        dao.insertOrReplace(ScrapedRows.matched(scrapeKey, rowGameId, choice.result, hints = null, choice.media, now).copy(isPersonChoice = true))
        offerToCopies(scrapeKey, choice.result.info.title, NameTrust.SCRAPER_PERSON_CHOICE)
    }

    private fun ScrapeTarget.isStill(rom: DocumentRomSource): Boolean =
        !needsIdentify && sizeBytes == rom.sizeBytes && modifiedAt == rom.lastModifiedMillis &&
            FixKeys.forGame(id, fixKeys).first() == rom.scrapeKey

    /** Every named game under [scrapeKey] (the same file in several folders), each by its own trust and fix. */
    private suspend fun offerToCopies(scrapeKey: String, title: String?, candidate: NameTrust) {
        for (copy in dao.targetsWithKey(scrapeKey)) {
            ScrapedTitleOffer.offer(dao, copy.id, copy.nameTrust, database.identifyDao().fixOf(copy.id), title, candidate)
        }
    }

    /** [discPass]: requests of [pendingSupport], fresh when a disc or cartridge is saved already. */
    private suspend fun requests(rows: List<GameToScrape>, discPass: Boolean = false): List<ScrapeRequest> {
        if (rows.isEmpty()) return emptyList()
        val preferred = preferredRegion()
        val languages = ScrapeRegions.languages(deviceLanguage())
        // Two copies of one file (two folders) share a key: one lookup stores it for both.
        return rows.distinctBy { it.scrapeKey }.map { game ->
            // A row the person asked for again (Download missing, Re-download) skips the
            // scraper's 30-day caches: its answer and pictures are fetched again for real. In the
            // disc pass, only the disc or cartridge is: a saved one is still listed, to be replaced.
            val stored = dao.get(game.scrapeKey)
            val askedAgain = if (discPass) {
                stored != null && ScrapedPictures.decode(stored.pictures).any { it.kind == ScrapedPicture.SUPPORT }
            } else {
                stored?.askAgain == true
            }
            ScrapeRequest(
                rom = DocumentRomSource(
                    gameId = game.id,
                    scrapeKey = game.scrapeKey,
                    key = documentUri(game.treeUri, game.documentId),
                    fileName = game.fileName,
                    sizeBytes = game.sizeBytes,
                    lastModifiedMillis = game.modifiedAt,
                    documentId = game.documentId,
                    tree = { openTree(game.treeUri) },
                ),
                systemId = game.systemId,
                title = game.title,
                regions = ScrapeRegions.order(game.regions, preferred),
                languages = languages,
                hints = ScrapeHintsFromFingerprint.hints(game.fingerprint, game.sizeBytes),
                fresh = askedAgain,
                hack = ScrapeHackFlag.of(game.fileName),
                steamAppId = if (game.systemId == "steam") database.pinnedShortcutDao().byGame(game.id)?.shortcutId?.let(ShortcutSteamAppId::parse) else null,
            )
        }
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** The source wired to the device's documents providers. */
        fun forDevice(
            context: Context,
            database: PlumDatabase,
            currentSystemId: () -> String?,
            preferredRegion: suspend () -> String,
        ): LibraryScrapeSource {
            val resolver = context.applicationContext.contentResolver
            return LibraryScrapeSource(
                database = database,
                openTree = { ContentResolverDocumentTree(resolver, Uri.parse(it)) },
                currentSystemId = currentSystemId,
                preferredRegion = preferredRegion,
            )
        }

        // A pinned shortcut sits in a reserved folder that is not a content URI (`plumemu:pinned-shortcuts`),
        // which buildDocumentUriUsingTree rejects; its key only has to be unique, as the file is never opened.
        private fun documentUriOf(treeUri: String, documentId: String): String =
            if (treeUri == PinnedShortcutDao.FOLDER_TREE_URI) "$treeUri/$documentId"
            else DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()
    }
}

/** What [LibraryScrapeSource.askAgain] asks again. */
enum class AskAgain {
    /** Games without a box front: no row yet, a no-match of any age, a match whose picture failed. */
    Missing,

    /** Every game's result (Re-download). */
    All,
}
