package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * ScreenScraper results per game ([ScrapedGameEntity]) and the queries library's
 * `LibraryScrapeSource` pages the run with.
 *
 * Every game query is driven by `game` and probes `scraped_game` by its primary key: the key is
 * an expression of `game.fix_keys` ([SCRAPE_KEY]), which no index of `game` can serve, and a
 * full pass over 10,000 games with one primary-key probe each stays in milliseconds
 * (`ScrapedGameDaoTest`, library's `LibraryScrapeSourceTest`). Games still to be named
 * (`needs_identify`) are left out: their fix keys and fingerprints are not known yet, so they
 * would be asked without hints and stored under a key that is about to change.
 */
@Dao
abstract class ScrapedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(row: ScrapedGameEntity)

    @Query("SELECT * FROM scraped_game WHERE scrape_key = :scrapeKey")
    abstract suspend fun get(scrapeKey: String): ScrapedGameEntity?

    /**
     * The stored result of [from] (every column: pictures, details, the person's pick, marks) also
     * under [to]. Library's content pass calls it when a whole-file read gave a game a new first key,
     * so its box art follows without a request. A row already under [to] is kept, except that
     * [replaceAutomatic] lets a person's pick replace a row that is not one (the same content, so the
     * pick applies to every copy under [to]); a pick already under [to] is never replaced. [from]
     * stays: other copies of the file may still be under it. The copy carries no `game_id` (only
     * `game:<id>` rows do, CASCADE), so [to] must be a content key: a row key copies nothing.
     *
     * @return true when a row was written under [to].
     */
    @Transaction
    open suspend fun copyToKey(from: String, to: String, replaceAutomatic: Boolean = false): Boolean {
        if (to.startsWith(NameFixEntity.ROW_PREFIX) || from == to) return false
        val row = get(from) ?: return false
        val there = get(to)
        if (there != null && (!replaceAutomatic || there.isPersonChoice)) return false
        insertOrReplace(row.copy(scrapeKey = to, gameId = null))
        return true
    }

    /**
     * Stores what a run found, in one transaction, unless the stored row must stay:
     * - a person's choice is never replaced by a run;
     * - a no-match never replaces a match (the disc pass asking again after the answer left the
     *   cache): the match stays and only its time moves; a match still missing its disc or
     *   cartridge counts that as one more failed attempt when [supportAsked].
     *
     * [supportAsked] tells whether the run asked for the disc or cartridge picture (its disc pass)
     * or only for the box front (its main pass). Asked: a match still without
     * that picture counts one more failed attempt ([ScrapedGameEntity.supportAttempts]); one with
     * it, or with none to fetch, starts again at 0. Not asked: the count starts at 0, since the
     * disc pass has not tried yet (and a game the person asked for again gets its full tries).
     *
     * [answersAskAgain]: the request was sent because the person asked for the game again (the
     * main pass's `fresh` request), so the stored [ScrapedGameEntity.askAgain] mark is cleared.
     * Otherwise a mark set while the game was in flight (Re-download pressed during its lookup or
     * its disc) stays, and the main pass asks the game again, fresh.
     */
    @Transaction
    open suspend fun storeFromRun(row: ScrapedGameEntity, supportAsked: Boolean = true, answersAskAgain: Boolean = true): RunStore {
        val stored = get(row.scrapeKey)
        // An answer arrived (a match or a no-match): the failed days start again at none.
        forgetFailure(row.scrapeKey)
        return when {
            stored?.isPersonChoice == true -> RunStore.KEPT_PERSON_CHOICE
            stored?.outcome == ScrapeOutcome.MATCHED && row.outcome == ScrapeOutcome.NO_MATCH -> {
                touch(row.scrapeKey, row.scrapedAt, counted = if (supportAsked) 1 else 0, keepAskAgain = if (answersAskAgain) 0 else 1)
                RunStore.KEPT_MATCH
            }
            else -> {
                val failedBefore = stored?.takeIf { it.outcome == ScrapeOutcome.MATCHED }?.supportAttempts ?: 0
                val attempts = if (supportAsked && row.supportMissing) failedBefore + 1 else 0
                val askAgain = !answersAskAgain && stored?.askAgain == true
                insertOrReplace(row.copy(supportAttempts = attempts, askAgain = askAgain))
                RunStore.STORED
            }
        }
    }

    /**
     * [counted] is 1 when the run asked for the disc or cartridge picture, else 0; [keepAskAgain]
     * is 1 to keep an `ask_again` mark the delivery did not answer, 0 to clear it.
     */
    @Query(
        "UPDATE scraped_game SET scraped_at = :scrapedAt, support_attempts = support_attempts + support_missing * :counted, " +
            "ask_again = ask_again * :keepAskAgain WHERE scrape_key = :scrapeKey",
    )
    protected abstract suspend fun touch(scrapeKey: String, scrapedAt: Long, counted: Int, keepAskAgain: Int)

    /**
     * Visible games to look up, at most [limit], in the run's order ([PRIORITY]): no row for
     * their key, a row the person asked for again ([ScrapedGameEntity.askAgain]), or a no-match
     * stored before [retryBefore] that the person did not choose.
     */
    @Query("SELECT " + GAME_COLUMNS + FROM_GAMES + " WHERE " + PENDING + " ORDER BY " + PRIORITY + " LIMIT :limit")
    abstract suspend fun pending(retryBefore: Long, currentSystemId: String?, limit: Int): List<GameToScrape>

    @Query("SELECT COUNT(game.id)" + FROM_GAMES + " WHERE " + PENDING)
    abstract suspend fun countPending(retryBefore: Long): Int

    /**
     * Matched games whose disc or cartridge picture is missing ([ScrapedGameEntity.supportMissing]),
     * not the person's choice, not asked for again (those go through [pending] first), tried
     * fewer than [MAX_SUPPORT_ATTEMPTS] times, in the [pending] order: the run's disc pass, after
     * every box front. A game the main pass stored a moment ago is among them,
     * so it gets its disc in the same run; the run itself remembers which games its disc pass
     * already tried (the app's `ScrapeRunSource`), so a failed one does not come back in it.
     */
    @Query(
        "SELECT " + GAME_COLUMNS + FROM_GAMES + " WHERE " + VISIBLE + " AND scraped_game.outcome = 'MATCHED' " +
            "AND scraped_game.support_missing = 1 AND scraped_game.is_person_choice = 0 AND scraped_game.ask_again = 0 " +
            "AND scraped_game.support_attempts < " + MAX_SUPPORT_ATTEMPTS + " ORDER BY " + PRIORITY + " LIMIT :limit",
    )
    abstract suspend fun pendingSupport(currentSystemId: String?, limit: Int): List<GameToScrape>

    /**
     * Settings' Download missing: every stored result of a visible game without a
     * box front (a no-match of any age, a match whose picture failed) is asked again by the next
     * run; games with no row are pending already. Never a person's choice. Each row and its
     * pictures stay until the new answer is stored ([storeFromRun] clears the mark).
     *
     * @return the rows marked.
     */
    @Query("UPDATE scraped_game SET ask_again = 1 WHERE is_person_choice = 0 AND has_box_front = 0 AND scrape_key IN (" + VISIBLE_KEYS + ")")
    abstract suspend fun markMissingToAskAgain(): Int

    /** [markMissingToAskAgain] for the games of [systemIds] only. */
    @Query(
        "UPDATE scraped_game SET ask_again = 1 WHERE is_person_choice = 0 AND has_box_front = 0 " +
            "AND scrape_key IN (" + VISIBLE_KEYS + " AND game.system_id IN (:systemIds))",
    )
    abstract suspend fun markMissingToAskAgainIn(systemIds: List<String>): Int

    /**
     * Settings' Re-download: every stored result of a visible game is asked again,
     * except a person's choice. The rows stay as they are until replaced: a no-match then never
     * replaces a match ([storeFromRun]), and the pictures shown stay meanwhile.
     *
     * @return the rows marked.
     */
    @Query("UPDATE scraped_game SET ask_again = 1 WHERE is_person_choice = 0 AND scrape_key IN (" + VISIBLE_KEYS + ")")
    abstract suspend fun markAllToAskAgain(): Int

    /** [markAllToAskAgain] for the games of [systemIds] only. */
    @Query("UPDATE scraped_game SET ask_again = 1 WHERE is_person_choice = 0 AND scrape_key IN (" + VISIBLE_KEYS + " AND game.system_id IN (:systemIds))")
    abstract suspend fun markAllToAskAgainIn(systemIds: List<String>): Int

    /**
     * The one-time repair after the scraper started checking a serial answer's system
     * (library's `SCRAPE_RULES_VERSION`): every match found by a cartridge or disc
     * serial alone ([SERIAL_ONLY_HASH_MATCH]: a `code:` key, `HASH`, not the person's choice) is
     * deleted, so the next run asks it as never asked. Not an `ask_again` mark on purpose: a mark
     * makes the request `fresh`, which skips the scraper's 30-day caches and fetches every answer
     * and picture again; unmarked, the cached answer is judged again by the new check (a right one
     * matches again with its saved pictures, a wrong one falls back to the name search).
     *
     * A game whose shown name came from ScreenScraper and from one of these rows (trust
     * `SCRAPER_EXACT` or `SCRAPER_LIKELY`) shows its file's name again and is named again by the
     * next pass (`needs_identify`): with its row gone nothing would ever correct a wrong title, and
     * the naming pass alone keeps a scraper name ([NameTrustNames]). Favourites, hidden marks,
     * play, emulator choices, name fixes and a person's choice are not touched. Safe to run again.
     *
     * @return the rows deleted.
     */
    @Transaction
    open suspend fun forgetSerialOnlyHashMatches(): Int {
        showFileNamesOfSerialOnlyHashMatches()
        return deleteSerialOnlyHashMatches()
    }

    @Query(
        "UPDATE game SET title = CASE WHEN file_title = '' THEN title ELSE file_title END, " +
            "sort_name = CASE WHEN file_title = '' THEN sort_name ELSE file_sort_name END, " +
            "regions = CASE WHEN file_title = '' THEN regions ELSE file_regions END, " +
            "name_trust = '" + NameTrustNames.FILE_NAME + "', needs_identify = 1 " +
            "WHERE name_trust IN ('" + SCRAPER_EXACT_TRUST + "', '" + SCRAPER_LIKELY_TRUST + "') " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + " " +
            "AND " + SCRAPE_KEY + " IN (SELECT scrape_key FROM scraped_game WHERE " + SERIAL_ONLY_HASH_MATCH + ")",
    )
    protected abstract suspend fun showFileNamesOfSerialOnlyHashMatches(): Int

    @Query("DELETE FROM scraped_game WHERE " + SERIAL_ONLY_HASH_MATCH)
    protected abstract suspend fun deleteSerialOnlyHashMatches(): Int

    /**
     * The scrape-rules repair, first half: the automatic hash matches of which some game
     * has a `code:` fix key (its lookup sent that code as `serialnum`: library's hints), one entry per
     * game sharing the row's key, with the game's system and fix keys (the code's kind is the format
     * it was read from). A fan translation's CRC is in no database, so ScreenScraper answered by its
     * code, across systems. The caller judges each answer's system from [SerialHashMatch.detailsJson]
     * the way a live lookup does now and passes the rows wrong for every game sharing them to
     * [forgetHashMatches]. Shortcut and app rows have no code keys and never come here.
     */
    @Query(
        "SELECT DISTINCT scraped_game.scrape_key AS scrape_key, game.system_id AS system_id, game.fix_keys AS fix_keys, " +
            "scraped_game.details_json AS details_json " +
            "FROM game JOIN scraped_game ON scraped_game.scrape_key = " + SCRAPE_KEY + " " +
            "WHERE scraped_game.outcome = 'MATCHED' AND scraped_game.matched_by = 'HASH' AND scraped_game.is_person_choice = 0 " +
            "AND scraped_game.details_json IS NOT NULL " +
            "AND scraped_game.scrape_key IN (SELECT " + SCRAPE_KEY + " FROM game WHERE " +
            "substr(game.fix_keys, 1, 5) = '" + FixKeys.CODE_PREFIX + "' OR instr(game.fix_keys, char(10) || '" + FixKeys.CODE_PREFIX + "') > 0)",
    )
    abstract suspend fun hashMatchesAskedWithSerial(): List<SerialHashMatch>

    /**
     * Second half: deletes the automatic hash matches under [keys] (a person's choice and any other
     * row are never touched, even when named) and shows the file's name again on the games named
     * from them, as [forgetSerialOnlyHashMatches] does, for the same reasons. Safe to run again.
     *
     * @return the rows deleted.
     */
    @Transaction
    open suspend fun forgetHashMatches(keys: List<String>): Int {
        var deleted = 0
        keys.distinct().chunked(MAX_KEYS_PER_STATEMENT).forEach { chunk ->
            showFileNamesOfHashMatches(chunk)
            deleted += deleteHashMatches(chunk)
        }
        return deleted
    }

    @Query(
        "UPDATE game SET title = CASE WHEN file_title = '' THEN title ELSE file_title END, " +
            "sort_name = CASE WHEN file_title = '' THEN sort_name ELSE file_sort_name END, " +
            "regions = CASE WHEN file_title = '' THEN regions ELSE file_regions END, " +
            "name_trust = '" + NameTrustNames.FILE_NAME + "', needs_identify = 1 " +
            "WHERE name_trust IN ('" + SCRAPER_EXACT_TRUST + "', '" + SCRAPER_LIKELY_TRUST + "') " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + " " +
            "AND " + SCRAPE_KEY + " IN (SELECT scrape_key FROM scraped_game WHERE " + AUTOMATIC_HASH_MATCH + " AND scrape_key IN (:keys))",
    )
    protected abstract suspend fun showFileNamesOfHashMatches(keys: List<String>): Int

    @Query("DELETE FROM scraped_game WHERE " + AUTOMATIC_HASH_MATCH + " AND scrape_key IN (:keys)")
    protected abstract suspend fun deleteHashMatches(keys: List<String>): Int

    /**
     * Scrape rules 4: the automatic hash matches of which some game has no `code:` key,
     * one entry per game sharing the row (its system, `fix_keys` and file name), shortcuts and apps
     * left out. Identify drops the cartridge code of a hack or translation (`HackNames`),
     * so its `fix_keys` no longer show the serial its lookup sent and [hashMatchesAskedWithSerial]
     * missed it; library judges which of these games could have sent one. Without `details_json`, read
     * per chunk of keys by [detailsOf], so 10,000 matches never sit in memory with their answers.
     */
    @Query(
        "SELECT DISTINCT scraped_game.scrape_key AS scrape_key, game.system_id AS system_id, game.fix_keys AS fix_keys, " +
            "game.file_name AS file_name " +
            "FROM game JOIN scraped_game ON scraped_game.scrape_key = " + SCRAPE_KEY + " " +
            "WHERE scraped_game.outcome = 'MATCHED' AND scraped_game.matched_by = 'HASH' AND scraped_game.is_person_choice = 0 " +
            "AND scraped_game.details_json IS NOT NULL " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + " " +
            "AND scraped_game.scrape_key IN (SELECT " + SCRAPE_KEY + " FROM game WHERE NOT (" + HAS_CODE_KEY + ") " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + ")",
    )
    abstract suspend fun hashMatchGamesWithoutCode(): List<HashMatchGame>

    /** The stored answers (`details_json`) of the automatic hash matches under [keys]. */
    suspend fun detailsOf(keys: List<String>): List<StoredDetails> =
        keys.distinct().chunked(DETAILS_PER_STATEMENT).flatMap { detailsChunk(it) }

    @Query("SELECT scrape_key, details_json FROM scraped_game WHERE " + AUTOMATIC_HASH_MATCH + " AND details_json IS NOT NULL AND scrape_key IN (:keys)")
    protected abstract suspend fun detailsChunk(keys: List<String>): List<StoredDetails>

    /**
     * The games under [keys] with no stored file-name title (`file_title` ''), not shortcuts or apps: a
     * repair that shows the file's name again writes it first ([fillFileName]), or the wrong title
     * would stay under `FILE_NAME`, which the naming pass never replaces with a file-name result.
     */
    suspend fun gamesWithoutFileTitle(keys: List<String>): List<GameFileName> =
        keys.distinct().chunked(MAX_KEYS_PER_STATEMENT).flatMap { gamesWithoutFileTitleChunk(it) }

    @Query(
        "SELECT id, file_name, system_id, title FROM game WHERE file_title = '' " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + " " +
            "AND " + SCRAPE_KEY + " IN (:keys)",
    )
    protected abstract suspend fun gamesWithoutFileTitleChunk(keys: List<String>): List<GameFileName>

    /** The games shown by their file's name (`FILE_NAME`) with no stored file-name title: version 3's leftovers. */
    @Query(
        "SELECT id, file_name, system_id, title FROM game WHERE file_title = '' AND name_trust = '" + NameTrustNames.FILE_NAME + "' " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW,
    )
    abstract suspend fun gamesShownWithoutFileTitle(): List<GameFileName>

    /** Writes the file name's title into a row that has none; never over a stored one. @return 1 when written. */
    @Query("UPDATE game SET file_title = :title, file_sort_name = :sortName, file_regions = :regions WHERE id = :gameId AND file_title = ''")
    abstract suspend fun fillFileName(gameId: Long, title: String, sortName: String, regions: String): Int

    /**
     * Shows [gameId]'s file name and sends it to the naming pass, only while it is still shown by its
     * file's name (`FILE_NAME`) and has a file-name title. @return 1 when written.
     */
    @Query(
        "UPDATE game SET title = file_title, sort_name = file_sort_name, regions = file_regions, needs_identify = 1 " +
            "WHERE id = :gameId AND name_trust = '" + NameTrustNames.FILE_NAME + "' AND file_title != '' " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW,
    )
    abstract suspend fun showFileNameAgain(gameId: Long): Int

    /**
     * Makes every stored no-match (not a person's choice) due now, by dating it 0: [pending] offers it
     * on the next run instead of after [NO_MATCH_RETRY_MILLIS]. For a new scrape-rules version that
     * finds games the old rules missed. Matched rows are not touched.
     *
     * @return the rows made due.
     */
    @Query("UPDATE scraped_game SET scraped_at = 0 WHERE outcome = 'NO_MATCH' AND is_person_choice = 0")
    abstract suspend fun makeNoMatchesDueNow(): Int

    /** Listed games (named or not) that have a saved box front: "2,012 of 2,054 have box art". */
    @Query("SELECT COUNT(game.id)" + FROM_GAMES + " WHERE " + LISTED + " AND " + PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART + " AND " + AppGameDao.NOT_APP_GAME + " AND scraped_game.has_box_front = 1")
    abstract suspend fun countWithBoxFront(): Int

    /**
     * The pixel size of every listed game's saved box front of size [sizeName], with its system
     * ([BoxFrontShapes]). Read in SQL from the `pictures` text so 10,000 games
     * return three numbers each, not their JSON; Android 6's SQLite has no JSON functions, so
     * the width and height are cut after the picture's own start ([ScrapedPictures.objectStart],
     * which `BoxFrontShapesTest.theSqlReadsTheLargeBoxFrontsSizeFromWhatTheEncoderWrote` checks against [ScrapedPictures.encode]). A row whose text does
     * not read gives 0 and is left out by the caller.
     */
    suspend fun boxFrontSizes(sizeName: String): List<BoxFrontSize> =
        boxFrontSizesAfter(ScrapedPictures.objectStart(ScrapedPicture.BOX_FRONT, sizeName))

    @Query(
        "SELECT game.system_id, " + PICTURE_WIDTH + " AS width, " + PICTURE_HEIGHT + " AS height " +
            "FROM game JOIN scraped_game ON scraped_game.scrape_key = " + SCRAPE_KEY +
            " WHERE " + LISTED + " AND scraped_game.outcome = 'MATCHED' AND scraped_game.has_box_front = 1 " +
            "AND instr(scraped_game.pictures, :start) > 0",
    )
    protected abstract suspend fun boxFrontSizesAfter(start: String): List<BoxFrontSize>

    /** Listed games (named or not), pinned shortcuts too: Statistics' library total. */
    @Query("SELECT COUNT(id) FROM game WHERE is_hidden = 0 AND is_not_game = 0 AND is_primary_copy = 1")
    abstract suspend fun countListed(): Int

    /**
     * Listed games a box-art run is for: [countListed] without the pinned shortcuts a run skips (all but
     * `steam` ones, [PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART]). The "of 2,054" of the progress line and of Statistics' box-art coverage.
     */
    @Query("SELECT COUNT(game.id) FROM game WHERE " + LISTED + " AND " + PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART + " AND " + AppGameDao.NOT_APP_GAME)
    abstract suspend fun countListedForBoxArt(): Int

    /**
     * Shows a ScreenScraper title on [gameId] with [trust] (a `NameTrust` name), only while its
     * shown name still has [expectedTrust]: the caller decided against that trust.
     *
     * @return 1 when the name was written.
     */
    @Query(
        "UPDATE game SET title = :title, sort_name = :sortName, name_trust = :trust " +
            "WHERE id = :gameId AND name_trust = :expectedTrust",
    )
    abstract suspend fun showScraperName(gameId: Long, title: String, sortName: String, trust: String, expectedTrust: String): Int

    /**
     * Marks [gameId] to be named again (`needs_identify`) when its shown name has [trust] (a
     * `NameTrust` name): "No box art" after a pick, so the identifier's name replaces the pick's
     * title on the next naming pass. The shown name is unchanged until then.
     *
     * @return 1 when the game was marked.
     */
    @Query("UPDATE game SET needs_identify = 1 WHERE id = :gameId AND name_trust = :trust AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW)
    abstract suspend fun nameAgainIfTrust(gameId: Long, trust: String): Int

    /** [gameId] as a run would ask it, if it is named ("Fix box art..."); listed or not; never a pinned shortcut. */
    @Query("SELECT " + GAME_COLUMNS + FROM_GAMES + " WHERE game.id = :gameId AND game.needs_identify = 0 AND " + PinnedShortcutDao.NOT_SHORTCUT_GAME + " AND " + AppGameDao.NOT_APP_GAME)
    abstract suspend fun toScrape(gameId: Long): GameToScrape?

    /** [gameId]'s state as a delivered result checks it; null when the game is gone. */
    @Query("SELECT " + TARGET_COLUMNS + " FROM game WHERE id = :gameId")
    abstract suspend fun target(gameId: Long): ScrapeTarget?

    /** Every listed-or-not game (not "not a game") named and stored under [scrapeKey]: all copies of one file. */
    @Query("SELECT " + TARGET_COLUMNS + " FROM game WHERE " + SCRAPE_KEY + " = :scrapeKey AND needs_identify = 0 AND is_not_game = 0")
    abstract suspend fun targetsWithKey(scrapeKey: String): List<ScrapeTarget>

    /** The matches stored under [keys] (any number; asked 500 at a time), for the naming pass. */
    suspend fun matchedTitles(keys: Collection<String>): List<ScrapedTitle> =
        keys.distinct().chunked(MAX_KEYS_PER_STATEMENT).flatMap { matchedTitlesChunk(it) }

    @Query("SELECT scrape_key, title, matched_by, is_person_choice FROM scraped_game WHERE outcome = 'MATCHED' AND title IS NOT NULL AND scrape_key IN (:keys)")
    protected abstract suspend fun matchedTitlesChunk(keys: List<String>): List<ScrapedTitle>

    /** The rows of [keys] (any number; asked 500 at a time), for the screens. */
    fun observeByKeys(keys: Collection<String>): Flow<List<ScrapedGameSummary>> {
        val chunks = keys.distinct().chunked(MAX_KEYS_PER_STATEMENT)
        if (chunks.isEmpty()) return flowOf(emptyList())
        val flows = chunks.map { summaries(it) }
        val combined = if (flows.size == 1) flows.single() else combine(flows) { parts -> parts.flatMap { it } }
        return combined.distinctUntilChanged()
    }

    @Query(
        "SELECT scrape_key, outcome, screenscraper_id, title, year, publisher, players, genre, pictures, scraped_at " +
            "FROM scraped_game WHERE scrape_key IN (:keys) ORDER BY scrape_key",
    )
    protected abstract fun summaries(keys: List<String>): Flow<List<ScrapedGameSummary>>

    /** One more day on which [scrapeKey]'s lookup failed on a server error or a timeout; the same day twice counts once. */
    @Transaction
    open suspend fun recordFailure(scrapeKey: String, day: Long) {
        val before = failure(scrapeKey)
        when {
            before == null -> insertFailure(ScrapeFailureEntity(scrapeKey, 1, day))
            before.lastDay != day -> insertFailure(before.copy(failedDays = before.failedDays + 1, lastDay = day))
        }
    }

    @Query("SELECT * FROM scrape_failure WHERE scrape_key = :scrapeKey")
    abstract suspend fun failure(scrapeKey: String): ScrapeFailureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFailure(row: ScrapeFailureEntity)

    /** [scrapeKey]'s failed days start again (an answer arrived, or the person fixed its box art). */
    @Query("DELETE FROM scrape_failure WHERE scrape_key = :scrapeKey")
    abstract suspend fun forgetFailure(scrapeKey: String)

    /** Games [pending] no longer offers: failed on [MAX_FAILED_DAYS] days. */
    @Query("SELECT COUNT(*) FROM scrape_failure WHERE failed_days >= " + MAX_FAILED_DAYS)
    abstract suspend fun countGivenUp(): Int

    /** [countGivenUp], again on every change of the table. */
    @Query("SELECT COUNT(*) FROM scrape_failure WHERE failed_days >= " + MAX_FAILED_DAYS)
    abstract fun observeGivenUp(): Flow<Int>

    /** Re-download all (every system): every failed day is forgotten, so capped games are asked again. */
    @Query("DELETE FROM scrape_failure")
    abstract suspend fun forgetFailures(): Int

    /** Re-download all for [systemIds] only. */
    @Query("DELETE FROM scrape_failure WHERE scrape_key IN (SELECT " + SCRAPE_KEY + " FROM game WHERE game.system_id IN (:systemIds))")
    abstract suspend fun forgetFailuresIn(systemIds: List<String>): Int

    /**
     * Settings' Clear box art: every automatic result forgets its pictures (the person's choices keep
     * theirs: Download missing never asks them again). The row stays with its details and its
     * ScreenScraper id, now without a box front, so [markMissingToAskAgain] finds it and no run asks
     * it by itself (a deleted row would be "never asked", and the next scan's run would download
     * everything again). A disc or cartridge no longer waits for a disc pass.
     *
     * @return the rows changed.
     */
    @Query(
        "UPDATE scraped_game SET pictures = '', has_box_front = 0, support_missing = 0, support_attempts = 0 " +
            "WHERE is_person_choice = 0 AND (pictures != '' OR has_box_front = 1)",
    )
    abstract suspend fun forgetPictures(): Int

    /** The ScreenScraper ids whose picture folder is still needed: every row with pictures. */
    @Query("SELECT DISTINCT screenscraper_id FROM scraped_game WHERE screenscraper_id IS NOT NULL AND pictures != ''")
    abstract suspend fun idsWithPictures(): List<String>

    /** The stored pictures of every row that has some (JSON, [ScrapedPictures.decode]): a row's files may sit in another source's folder than its ScreenScraper id. */
    @Query("SELECT pictures FROM scraped_game WHERE pictures != ''")
    abstract suspend fun allPictures(): List<String>

    /** True when the library holds any game at all (hidden ones too): a sweep of an empty table would delete everything. */
    @Query("SELECT EXISTS(SELECT 1 FROM game)")
    abstract suspend fun hasAnyGame(): Boolean

    /**
     * The sweep's database half: deletes the pictures' rows of games the library no longer has (no
     * game, hidden or not, has the row's key) unless the person chose them, so a game that comes
     * back is asked as never asked. Rows without pictures (no-matches) stay: they weigh nothing and
     * save a request when the file comes back.
     *
     * @return the rows deleted.
     */
    @Query(
        "DELETE FROM scraped_game WHERE is_person_choice = 0 AND pictures != '' " +
            "AND scrape_key NOT IN (SELECT " + SCRAPE_KEY + " FROM game)",
    )
    abstract suspend fun deleteOrphanPictureRows(): Int

    /** Failure rows of games the library no longer has: a few bytes each, dropped by the sweep. */
    @Query("DELETE FROM scrape_failure WHERE scrape_key NOT IN (SELECT " + SCRAPE_KEY + " FROM game)")
    abstract suspend fun deleteOrphanFailures(): Int

    /** What [storeFromRun] did. */
    enum class RunStore { STORED, KEPT_PERSON_CHOICE, KEPT_MATCH }

    companion object {
        /** A disc or cartridge picture is asked at most this many times in a row by the disc pass. */
        const val MAX_SUPPORT_ATTEMPTS = 3

        /** A game whose lookup failed on this many different days is no longer asked (until Re-download all). */
        const val MAX_FAILED_DAYS = 3

        private const val TARGET_COLUMNS = "id, name_trust, fix_keys, needs_identify, size_bytes, modified_at"

        /** A no-match is asked again after 30 days (ScreenScraper keeps adding games). */
        const val NO_MATCH_RETRY_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        // SQLite on Android 6 allows 999 bound parameters per statement.
        private const val MAX_KEYS_PER_STATEMENT = 500

        // A few KB of JSON per answer: 100 answers a read keep memory small on a 2 GB device.
        private const val DETAILS_PER_STATEMENT = 100

        /** The game's first fix key, or `game:<id>`: [FixKeys.forGame]'s first key, in SQL. */
        const val SCRAPE_KEY =
            "(CASE WHEN game.fix_keys = '' THEN '" + NameFixEntity.ROW_PREFIX + "' || game.id " +
                "WHEN instr(game.fix_keys, char(10)) > 0 THEN substr(game.fix_keys, 1, instr(game.fix_keys, char(10)) - 1) " +
                "ELSE game.fix_keys END)"

        private const val GAME_COLUMNS =
            "game.id, rom_folder.tree_uri, game.document_id, game.file_name, game.size_bytes, game.modified_at, " +
                "game.system_id, game.title, game.regions, game.fingerprint, " + SCRAPE_KEY + " AS scrape_key"

        private const val FROM_GAMES =
            " FROM game JOIN rom_folder ON rom_folder.id = game.folder_id " +
                "LEFT JOIN scraped_game ON scraped_game.scrape_key = " + SCRAPE_KEY +
                " LEFT JOIN play ON play.game_id = game.id"

        // `pictures` from the start of one picture's object (`:start`, its kind and size); the
        // width and height are the first `"w":` and `"h":` after it: its path comes before them,
        // and a quote inside a JSON string is escaped, so no path can hold either. SQLite's CAST
        // keeps the digits before the next comma.
        private const val PICTURE = "substr(scraped_game.pictures, instr(scraped_game.pictures, :start))"
        private const val PICTURE_WIDTH = "CAST(substr(" + PICTURE + ", instr(" + PICTURE + ", '\"w\":') + 4) AS INTEGER)"
        private const val PICTURE_HEIGHT = "CAST(substr(" + PICTURE + ", instr(" + PICTURE + ", '\"h\":') + 4) AS INTEGER)"

        /** What the lists show: not hidden, not "not a game", one copy per regional group. */
        private const val LISTED = "game.is_hidden = 0 AND game.is_not_game = 0 AND game.is_primary_copy = 1"

        /**
         * What a run may ask: listed and named. Pinned shortcuts are left out
         * ([PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART]) unless filed under `steam`: those are asked by
         * name like a `.steam` file. The rest show the no-art look until the person settles their art.
         */
        private const val VISIBLE =
            LISTED + " AND game.needs_identify = 0 AND " + PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART + " AND " + AppGameDao.NOT_APP_GAME

        private const val PENDING =
            VISIBLE + " AND (scraped_game.scrape_key IS NULL " +
                "OR (scraped_game.ask_again = 1 AND scraped_game.is_person_choice = 0) " +
                "OR (scraped_game.outcome = 'NO_MATCH' " +
                "AND scraped_game.is_person_choice = 0 AND scraped_game.scraped_at < :retryBefore)) " +
                "AND " + SCRAPE_KEY + " NOT IN (SELECT scrape_key FROM scrape_failure WHERE failed_days >= " + MAX_FAILED_DAYS + ")"

        /**
         * A stored match found by a serial alone: under a `code:` key (no CRC key, so the lookup
         * sent the serial), by the ROM lookup, not the person's pick. Older scrapers took ScreenScraper's
         * answer for a serial even when it was another system's game.
         */
        private const val AUTOMATIC_HASH_MATCH = "outcome = 'MATCHED' AND matched_by = 'HASH' AND is_person_choice = 0"

        private const val SERIAL_ONLY_HASH_MATCH =
            "outcome = 'MATCHED' AND matched_by = 'HASH' AND is_person_choice = 0 AND substr(scrape_key, 1, 5) = '" + FixKeys.CODE_PREFIX + "'"

        /** The game has a `code:` fix key: its lookup sends that code as the serial. */
        private const val HAS_CODE_KEY =
            "substr(game.fix_keys, 1, 5) = '" + FixKeys.CODE_PREFIX + "' OR instr(game.fix_keys, char(10) || '" + FixKeys.CODE_PREFIX + "') > 0"

        // identify's NameTrust names (core-data does not depend on identify); stored, never renamed.
        private const val SCRAPER_EXACT_TRUST = "SCRAPER_EXACT"
        private const val SCRAPER_LIKELY_TRUST = "SCRAPER_LIKELY"

        /** The row keys of the games a run may ask (listed, named); a `game.system_id` filter may follow. */
        private const val VISIBLE_KEYS = "SELECT " + SCRAPE_KEY + " FROM game WHERE " + VISIBLE

        /** Recent (latest played first), then Favorites, then the system being viewed, then by id. */
        private const val PRIORITY =
            "CASE WHEN play.last_played_at IS NOT NULL THEN 0 WHEN game.is_favorite = 1 THEN 1 " +
                "WHEN game.system_id = :currentSystemId THEN 2 ELSE 3 END, play.last_played_at DESC, game.id"
    }
}
