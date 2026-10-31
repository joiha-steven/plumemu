package com.plumemu.coredata.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Names of games: what the identification pass reads and writes, and a person's name
 * fixes ([NameFixEntity]). Which name wins is decided by library's `NameDecision`
 * (`NameTrust.winner`); this DAO only stores it. The fix methods for the Fix name screens change the
 * shown name and the fix rows in one transaction, so the pass, which re-reads both inside its own
 * write transaction, never puts back a name the person just replaced.
 */
@Dao
abstract class IdentifyDao {
    /** Rows of [folderId] the pass still has to name, by id after [afterId], at most [limit]. */
    @Query(
        "SELECT id, document_id, parent_document_id, file_name, size_bytes, modified_at, system_id, file_title, " +
            "file_regions, fingerprint, fingerprint_size, fingerprint_mtime FROM game " +
            "WHERE folder_id = :folderId AND needs_identify = 1 AND id > :afterId ORDER BY id LIMIT :limit",
    )
    abstract suspend fun gamesToIdentify(folderId: Long, afterId: Long, limit: Int): List<GameToIdentify>

    @Query("SELECT COUNT(id) FROM game WHERE folder_id = :folderId AND needs_identify = 1")
    abstract suspend fun countToIdentify(folderId: Long): Int

    /** The systems of the rows still to name, so the pass unpacks only the packs it needs. */
    @Query("SELECT DISTINCT system_id FROM game WHERE folder_id = :folderId AND needs_identify = 1")
    abstract suspend fun systemsToIdentify(folderId: Long): List<String>

    /** Each system of [folderId] with the pack versions its named rows were named with. */
    @Query("SELECT DISTINCT system_id, pack_version FROM game WHERE folder_id = :folderId AND needs_identify = 0")
    abstract suspend fun packVersions(folderId: Long): List<SystemPackVersion>

    /** [systemId]'s pack is now [packVersion]: rows named with another version (or none) are named again. */
    @Query(
        "UPDATE game SET needs_identify = 1 WHERE folder_id = :folderId AND system_id = :systemId " +
            "AND needs_identify = 0 AND (pack_version IS NULL OR pack_version != :packVersion)",
    )
    abstract suspend fun markForNewPack(folderId: Long, systemId: String, packVersion: Long): Int

    /**
     * The naming rules of [systemIds] changed (library's `NamingRules`): their named rows, in every
     * folder, are named again by the next pass, from what is cached (pinned shortcuts never: nothing
     * would clear the mark). Fixes, marks and play time are not touched; running it twice marks
     * the same rows.
     *
     * @return how many rows were marked.
     */
    @Query(
        "UPDATE game SET needs_identify = 1 WHERE needs_identify = 0 AND system_id IN (:systemIds) " +
            "AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW,
    )
    abstract suspend fun markSystemsToNameAgain(systemIds: Collection<String>): Int

    /** [markSystemsToNameAgain] for every system: the naming rules that changed reach them all. */
    @Query("UPDATE game SET needs_identify = 1 WHERE needs_identify = 0 AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW)
    abstract suspend fun markAllToNameAgain(): Int

    @Query("SELECT id, name_trust, fix_keys FROM game WHERE id IN (:ids)")
    protected abstract suspend fun nameStatesChunk(ids: List<Long>): List<GameNameState>

    suspend fun nameStates(ids: List<Long>): List<GameNameState> = ids.chunked(MAX_IDS_PER_STATEMENT).flatMap { nameStatesChunk(it) }

    @Query("SELECT * FROM name_fix WHERE fix_key IN (:keys)")
    protected abstract suspend fun fixesChunk(keys: List<String>): List<NameFixEntity>

    /** Every fix stored under any of [keys]. */
    suspend fun fixesByKeys(keys: Collection<String>): List<NameFixEntity> =
        keys.distinct().chunked(MAX_IDS_PER_STATEMENT).flatMap { fixesChunk(it) }

    /**
     * Stores what the identifier found for [id] and marks it named, but only if the file is still
     * the one that was read ([sizeBytes], [modifiedAt], [fileName]): a rescan that changed it in
     * between has set `needs_identify` again and the next pass reads the new file.
     *
     * @return 1 when the row was written, 0 when it changed or is gone.
     */
    @Query(
        "UPDATE game SET fingerprint = :fingerprint, fingerprint_size = :fingerprintSize, " +
            "fingerprint_mtime = :fingerprintMtime, fix_keys = :fixKeys, id_method = :method, " +
            "id_confidence = :confidence, pack_version = :packVersion, needs_identify = 0 " +
            "WHERE id = :id AND size_bytes = :sizeBytes AND modified_at = :modifiedAt AND file_name = :fileName",
    )
    abstract suspend fun recordIdentification(
        id: Long,
        sizeBytes: Long,
        modifiedAt: Long,
        fileName: String,
        fingerprint: String?,
        fingerprintSize: Long?,
        fingerprintMtime: Long?,
        fixKeys: String,
        method: String,
        confidence: String,
        packVersion: Long?,
    ): Int

    /**
     * The name [id] shows, whose it is ([trust], a `NameTrust` name) and whether it is listed. A
     * null [title] or [sortName] keeps the stored one ("Not a game" names nothing).
     */
    @Query(
        "UPDATE game SET title = COALESCE(:title, title), sort_name = COALESCE(:sortName, sort_name), " +
            "regions = :regions, name_trust = :trust, is_not_game = :isNotGame WHERE id = :id",
    )
    abstract suspend fun setShownName(id: Long, title: String?, sortName: String?, regions: String, trust: String, isNotGame: Boolean)

    /**
     * Listed games of [folderId] that show the identifier's arcade name (`ARCADE_NAME`, exact), so
     * library's pass can tell sets apart that would show the same short title. A
     * person's fix, a ScreenScraper name and rows still to name are not returned.
     */
    @Query(
        "SELECT id, system_id, file_name, title, is_primary_copy FROM game WHERE folder_id = :folderId AND needs_identify = 0 " +
            "AND is_not_game = 0 AND name_trust = '" + NameTrustNames.IDENTIFIER_EXACT + "' AND id_method = '" + ARCADE_NAME + "'",
    )
    abstract suspend fun arcadeShownGames(folderId: Long): List<ArcadeShownGame>

    /**
     * Shows [title] on [id] instead of [shown], only while the row still shows [shown] as the
     * identifier's arcade name: a fix or a new identification made in between is left as it is.
     *
     * @return 1 when the title changed, 0 otherwise.
     */
    @Query(
        "UPDATE game SET title = :title, sort_name = :sortName WHERE id = :id AND title = :shown AND needs_identify = 0 " +
            "AND name_trust = '" + NameTrustNames.IDENTIFIER_EXACT + "' AND id_method = '" + ARCADE_NAME + "'",
    )
    abstract suspend fun setArcadeTitle(id: Long, shown: String, title: String, sortName: String): Int

    /**
     * Rows of [folderId] whose system is one of [systemIds] (library passes the arcade systems),
     * with the marks that decide which copy of a game shows. Rows off
     * every list as "not a game" (BIOS and device sets, the person's Not a game) are left out.
     */
    @Query("SELECT " + ARCADE_COPY_COLUMNS + " WHERE game.folder_id = :folderId AND game.is_not_game = 0 AND game.system_id IN (:systemIds)")
    abstract suspend fun arcadeCopies(folderId: Long, systemIds: List<String>): List<ArcadeCopyRow>

    /**
     * The rows of [gameId]'s folder, directory and system that are not "not a game", [gameId]
     * included (when it is not one): the candidates for the copies of its arcade game, which
     * library's `ArcadeCopies` picks by their sets. Empty when the game is gone.
     */
    @Query(
        "SELECT " + ARCADE_COPY_COLUMNS + " JOIN game AS own ON own.id = :gameId AND own.folder_id = game.folder_id " +
            "AND own.parent_document_id = game.parent_document_id AND own.system_id = game.system_id WHERE game.is_not_game = 0",
    )
    abstract suspend fun copiesBesides(gameId: Long): List<ArcadeCopyRow>

    /**
     * Rows of [folderId] the arcade grouping left out of the lists (`is_primary_copy = 0`) while
     * named as arcade sets (`ARCADE_NAME`): read before a pass names anything, so the pass can tell
     * a row that leaves the grouping (a newer pack no longer knows its set).
     */
    @Query("SELECT id FROM game WHERE folder_id = :folderId AND is_primary_copy = 0 AND id_method = '" + ARCADE_NAME + "'")
    abstract suspend fun arcadeLeftOut(folderId: Long): List<Long>

    /**
     * Makes [ids] the copies their groups show ([isPrimaryCopy] true) or copies the lists leave
     * out. Touches nothing else: marks stay on the row they were made on.
     *
     * @return the number of rows that changed.
     */
    @Transaction
    open suspend fun setPrimaryCopy(ids: List<Long>, isPrimaryCopy: Boolean): Int =
        ids.chunked(MAX_IDS_PER_STATEMENT).sumOf { setPrimaryCopyChunk(it, isPrimaryCopy) }

    @Query("UPDATE game SET is_primary_copy = :isPrimaryCopy WHERE id IN (:ids) AND is_primary_copy != :isPrimaryCopy")
    protected abstract suspend fun setPrimaryCopyChunk(ids: List<Long>, isPrimaryCopy: Boolean): Int

    /** Copies a fix found under one key to keys it is missing from (a fix made before the keys were known). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFixesIfMissing(fixes: List<NameFixEntity>)

    @Upsert
    protected abstract suspend fun upsertFixes(fixes: List<NameFixEntity>)

    @Query("DELETE FROM name_fix WHERE fix_key IN (:keys)")
    protected abstract suspend fun deleteFixes(keys: List<String>)

    @Query("SELECT fix_keys FROM game WHERE id = :gameId")
    protected abstract suspend fun fixKeysOf(gameId: Long): String?

    /**
     * Fix name..., Use my own name, Not a game: stores the fix under the file's fingerprint
     * keys ([FixKeys.fixTargets], never a product code) and its row key, and shows it at once on
     * that row; other rows sharing a fingerprint key are named again by the next pass. A file with
     * no fingerprint key gets one later from a whole-file read ([addContentKeys]). [title] is null for
     * [NameFixKind.NOT_A_GAME], which keeps the name and takes the game off every list;
     * [sortName] comes from `TitleCleaner.sortName(title)`.
     *
     * @return false when the game is gone.
     */
    @Transaction
    open suspend fun setFix(
        gameId: Long,
        kind: NameFixKind,
        title: String?,
        sortName: String?,
        titleSource: String?,
        fixedAt: Long,
    ): Boolean {
        val stored = fixKeysOf(gameId) ?: return false
        val rowKey = NameFixEntity.rowKey(gameId)
        val fixes = (FixKeys.fixTargets(FixKeys.split(stored)) + rowKey).map { key ->
            NameFixEntity(key, kind, title, titleSource, gameId.takeIf { key == rowKey }, fixedAt)
        }
        upsertFixes(fixes)
        val isNotGame = kind == NameFixKind.NOT_A_GAME
        showFix(gameId, title, sortName, isNotGame)
        markOthersSharingKeys(gameId, FixKeys.fixTargets(FixKeys.split(stored)))
        return true
    }

    /**
     * After a fix, a whole-file read of [gameId]'s file found its content keys
     * [fixKeys] (a loose cartridge's `crc:`, which a QUICK scan never reads): caches that read
     * ([fingerprint], so the pass and Back to automatic see the new keys) and stores the row's
     * fix under its fingerprint keys too, so it follows the file's content. Does nothing, and
     * answers false, when the file changed since it was read ([sizeBytes], [modifiedAt]) or the
     * row no longer holds a fix under its row key (Back to automatic ran meanwhile); a newer fix
     * made meanwhile is the one copied.
     */
    @Transaction
    open suspend fun addContentKeys(
        gameId: Long,
        sizeBytes: Long,
        modifiedAt: Long,
        fingerprint: String,
        fixKeys: List<String>,
    ): Boolean {
        val fix = fixesByKeys(listOf(NameFixEntity.rowKey(gameId))).firstOrNull() ?: return false
        val stored = fixKeysOf(gameId) ?: return false
        val keys = (fixKeys + FixKeys.split(stored)).distinct()
        if (cacheWholeFileRead(gameId, sizeBytes, modifiedAt, fingerprint, FixKeys.join(keys)) == 0) return false
        val targets = FixKeys.fixTargets(FixKeys.split(FixKeys.join(keys)))
        upsertFixes(targets.map { NameFixEntity(it, fix.kind, fix.title, fix.titleSource, null, fix.fixedAt) })
        markOthersSharingKeys(gameId, targets)
        return true
    }

    @Query(
        "UPDATE game SET fingerprint = :fingerprint, fingerprint_size = :sizeBytes, fingerprint_mtime = :modifiedAt, " +
            "fix_keys = :fixKeys WHERE id = :gameId AND size_bytes = :sizeBytes AND modified_at = :modifiedAt",
    )
    protected abstract suspend fun cacheWholeFileRead(
        gameId: Long,
        sizeBytes: Long,
        modifiedAt: Long,
        fingerprint: String,
        fixKeys: String,
    ): Int

    /** [gameId]'s fingerprint keys ([FixKeys.fixTargets]); null when the game is gone. */
    suspend fun contentKeysOf(gameId: Long): List<String>? = fixKeysOf(gameId)?.let { FixKeys.fixTargets(FixKeys.split(it)) }

    /** The row a whole-file read opens ([addContentKeys]); null when the game is gone. */
    @Query(
        "SELECT id, document_id, parent_document_id, file_name, size_bytes, modified_at, system_id, file_title, " +
            "file_regions, fingerprint, fingerprint_size, fingerprint_mtime FROM game WHERE id = :gameId",
    )
    abstract suspend fun gameToRead(gameId: Long): GameToIdentify?

    /**
     * The next page of rows library's content pass reads whole, after [after], at most [limit]:
     * named rows (`needs_identify` 0) whose cached fingerprint is of the file as it is now, of a
     * system and container [filter] names, at most [ContentReadFilter.maxBytes], with no content
     * CRC and not stamped done. Hidden rows ("Not a game", the person's Hide) and pinned shortcuts
     * are left out; copies the lists leave out (`is_primary_copy` 0) are read too (Other versions
     * shows their names). Order: played games, then favourites, then the rest
     * ([GameToReadContent.readGroup]); smallest file first, then id. Paged by that order, so a run
     * never loads the whole library and never offers a row twice.
     */
    suspend fun contentReadPage(filter: ContentReadFilter, after: ContentReadCursor, limit: Int): List<GameToReadContent> =
        contentReadPage(
            filter.looseSystems.toList(), filter.zippedSystems.toList(), filter.maxBytes,
            filter.looseMark, filter.zipMark, filter.wholeFileCrcMark, filter.zipContentCrcMark, filter.doneMark,
            after.group, after.sizeBytes, after.id, limit,
        )

    @Query(
        "SELECT * FROM (SELECT id, document_id, parent_document_id, file_name, size_bytes, modified_at, system_id, " +
            "file_title, file_regions, fingerprint, fingerprint_size, fingerprint_mtime, folder_id, " +
            "(CASE WHEN EXISTS (SELECT 1 FROM play WHERE play.game_id = game.id) THEN 0 WHEN is_favorite = 1 THEN 1 ELSE 2 END) AS read_group " +
            "FROM game WHERE needs_identify = 0 AND is_not_game = 0 AND is_hidden = 0 AND fingerprint IS NOT NULL " +
            "AND fingerprint_size = size_bytes AND fingerprint_mtime = modified_at AND size_bytes <= :maxBytes " +
            "AND ((system_id IN (:looseSystems) AND instr(fingerprint, :looseMark) > 0) " +
            "OR (system_id IN (:zippedSystems) AND instr(fingerprint, :zipMark) > 0)) " +
            "AND instr(fingerprint, :wholeFileCrcMark) = 0 AND instr(fingerprint, :zipContentCrcMark) = 0 " +
            "AND instr(fingerprint, :doneMark) = 0 AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW + ") " +
            // Android 6's SQLite (3.8) has no row values: (group, size, id) > cursor spelled out.
            "WHERE read_group > :afterGroup OR (read_group = :afterGroup AND (size_bytes > :afterSize " +
            "OR (size_bytes = :afterSize AND id > :afterId))) ORDER BY read_group, size_bytes, id LIMIT :limit",
    )
    protected abstract suspend fun contentReadPage(
        looseSystems: List<String>,
        zippedSystems: List<String>,
        maxBytes: Long,
        looseMark: String,
        zipMark: String,
        wholeFileCrcMark: String,
        zipContentCrcMark: String,
        doneMark: String,
        afterGroup: Int,
        afterSize: Long,
        afterId: Long,
        limit: Int,
    ): List<GameToReadContent>

    /** [gameId] as the content pass's write checks it, inside its transaction; null when gone. */
    @Query("SELECT id, size_bytes, modified_at, file_name, fingerprint, needs_identify, fix_keys, title FROM game WHERE id = :gameId")
    abstract suspend fun contentReadState(gameId: Long): ContentReadState?

    /**
     * Replaces [gameId]'s cached fingerprint [old] with [new] (the content pass's count of failed
     * reads), only while the file and the cache are still those that were read.
     *
     * @return 1 when written, 0 when the row changed or is gone.
     */
    @Query(
        "UPDATE game SET fingerprint = :new WHERE id = :gameId AND size_bytes = :sizeBytes AND modified_at = :modifiedAt " +
            "AND fingerprint = :old AND fingerprint_size = :sizeBytes AND fingerprint_mtime = :modifiedAt AND needs_identify = 0",
    )
    abstract suspend fun replaceCachedFingerprint(gameId: Long, sizeBytes: Long, modifiedAt: Long, old: String, new: String): Int

    @Query("SELECT folder_id FROM game WHERE id = :gameId")
    abstract suspend fun folderOf(gameId: Long): Long?

    /**
     * Other rows sharing one of [keys] (the same file in another folder) go back to the pass rather than take this fix: a row may hold its own
     * fix under a stronger key, and only the pass's strongest-key-first lookup knows which wins.
     */
    private suspend fun markOthersSharingKeys(gameId: Long, keys: List<String>) {
        for (key in keys) markForNamingOnKey(key, gameId)
    }

    @Query("UPDATE game SET needs_identify = 1 WHERE id != :gameId AND " + HAS_KEY + " AND NOT " + PinnedShortcutDao.IS_SHORTCUT_ROW + " AND NOT " + AppGameDao.IS_APP_GAME_ROW)
    protected abstract suspend fun markForNamingOnKey(key: String, gameId: Long)

    @Query(
        "UPDATE game SET title = COALESCE(:title, title), sort_name = COALESCE(:sortName, sort_name), " +
            "name_trust = '" + NameTrustNames.PERSON_FIX + "', is_not_game = :isNotGame WHERE id = :gameId",
    )
    protected abstract suspend fun showFix(gameId: Long, title: String?, sortName: String?, isNotGame: Boolean)

    /**
     * Back to automatic: deletes the fix rows of all of [gameId]'s keys (with the content
     * key a whole-file read added) and its row key,
     * shows the file name again and lets the next pass name the game anew (its fingerprints stay
     * cached, so nothing is read).
     *
     * @return false when the game is gone.
     */
    @Transaction
    open suspend fun backToAutomatic(gameId: Long): Boolean {
        val stored = fixKeysOf(gameId) ?: return false
        val keys = FixKeys.split(stored)
        deleteFixes((keys + NameFixEntity.rowKey(gameId)).distinct())
        showFileName(gameId)
        markOthersSharingKeys(gameId, FixKeys.fixTargets(keys))
        return true
    }

    @Query(
        "UPDATE game SET title = file_title, sort_name = file_sort_name, regions = file_regions, " +
            "name_trust = '" + NameTrustNames.FILE_NAME + "', is_not_game = 0, " +
            // A pinned shortcut or an installed app has no file to identify; nothing would ever clear the mark.
            "needs_identify = NOT (" + PinnedShortcutDao.IS_SHORTCUT_ROW + " OR " + AppGameDao.IS_APP_GAME_ROW + ") WHERE id = :gameId",
    )
    protected abstract suspend fun showFileName(gameId: Long)

    /** The fix [gameId] shows, looked up by its strongest fingerprint key first, then its row key; null when it has none. */
    suspend fun fixOf(gameId: Long): NameFixEntity? {
        val stored = fixKeysOf(gameId) ?: return null
        val keys = (FixKeys.fixTargets(FixKeys.split(stored)) + NameFixEntity.rowKey(gameId)).distinct()
        val byKey = fixesByKeys(keys).associateBy { it.fixKey }
        return keys.firstNotNullOfOrNull { byKey[it] }
    }

    /** Info › How it was identified. */
    @Query(
        "SELECT id, system_id, title, file_title, name_trust, id_method, id_confidence, fingerprint, fix_keys, " +
            "is_not_game FROM game WHERE id = :gameId",
    )
    abstract suspend fun identificationOf(gameId: Long): GameIdentificationRow?

    /**
     * Settings › Library › Unidentified games: games the identifier looked at and could name
     * only from the file name, by sort name. Rows not looked at yet are left out, and so are the
     * games of [namedByDesign]: systems whose files are never identified (identify's
     * `LaunchFileSystems`, steam: a `.steam` file holds only an app id), named from the file on purpose.
     */
    fun observeUnidentified(namedByDesign: Collection<String>): Flow<List<HiddenGameRow>> =
        unidentified(namedByDesign.toList()).distinctUntilChanged()

    @Query(
        "SELECT id, title, regions, system_id FROM game WHERE name_trust = '" + NameTrustNames.FILE_NAME + "' " +
            "AND id_method IS NOT NULL AND is_not_game = 0 AND system_id NOT IN (:namedByDesign) ORDER BY sort_name, id",
    )
    protected abstract fun unidentified(namedByDesign: List<String>): Flow<List<HiddenGameRow>>

    /**
     * Settings › Library › Hidden games: games the person marked "Not a game" (on this row
     * or, through a shared key, on another copy), by sort name. Arcade BIOS and device sets the
     * identifier took off the lists are not person data and are not listed.
     */
    fun observeNotAGame(): Flow<List<HiddenGameRow>> = notAGame().distinctUntilChanged()

    @Query(
        "SELECT id, title, regions, system_id FROM game WHERE is_not_game = 1 " +
            "AND name_trust = '" + NameTrustNames.PERSON_FIX + "' ORDER BY sort_name, id",
    )
    protected abstract fun notAGame(): Flow<List<HiddenGameRow>>

    companion object {
        /** What [arcadeCopies] and [copiesBesides] read: the row, its play total and the person's choices. */
        private const val ARCADE_COPY_COLUMNS =
            "game.id, game.system_id, game.parent_document_id, game.file_name, game.name_trust, game.is_hidden, " +
                "game.is_favorite, game.is_primary_copy, COALESCE(play.total_seconds, 0) AS total_seconds, " +
                "(game.name_trust = '" + NameTrustNames.SCRAPER_PERSON_CHOICE + "' OR EXISTS (SELECT 1 FROM scraped_game WHERE scraped_game.scrape_key = " +
                ScrapedGameDao.SCRAPE_KEY + " AND scraped_game.is_person_choice = 1)) AS has_scraper_choice, " +
                "EXISTS (SELECT 1 FROM emulator_choice WHERE emulator_choice.game_id = game.id) AS has_emulator_choice " +
                "FROM game LEFT JOIN play ON play.game_id = game.id"

        /** identify's `Method.ARCADE_NAME`, as `game.id_method` stores it. */
        const val ARCADE_NAME = "ARCADE_NAME"

        // SQLite on Android 6 allows 999 bound parameters per statement.
        private const val MAX_IDS_PER_STATEMENT = 500

        // `fix_keys` holds one key per line (FixKeys); the whole key must match, never a prefix.
        private const val HAS_KEY = "instr(char(10) || fix_keys || char(10), char(10) || :key || char(10)) > 0"
    }
}
