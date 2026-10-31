package com.plumemu.library.scan

import android.util.Log
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.GameFold
import com.plumemu.coredata.db.GameMove
import com.plumemu.coredata.db.GameScanFields
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanDao
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.ScannedDirectoryEntity
import com.plumemu.coredata.db.StoredGame
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.library.catalog.CataloguedGame
import com.plumemu.library.catalog.DirectoryCataloguer
import com.plumemu.library.catalog.GameFolderCopies
import com.plumemu.library.catalog.JunkFilter
import com.plumemu.library.matching.FolderMatch
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.library.matching.SystemRule
import com.plumemu.coredata.db.FolderSystemDao
import com.plumemu.coredata.db.SkippedSubfolderDao
import com.plumemu.coredata.db.UnknownFolderRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * One scan of one picked folder. With no directory records it walks everything (a full walk);
 * otherwise it asks each recorded directory for its modified time (one single-row query) and
 * lists only those that changed, plus any new directory found inside them.
 *
 * New and changed games are written in batches as they are found. Nothing is deleted until the
 * end: then rows that disappeared are first matched with rows inserted by this scan, or inserted by
 * an earlier scan that did not finish and seen again now ([RenameMatcher]), so a moved or renamed
 * file keeps its row, and only the rest is deleted, in one transaction with the directory records
 * ([ScanFinish]). A row whose file the grouping now counts as part of another game (an older
 * scan's `Game - Disc 2` entry, a disc set an `.m3u` now stands for) is folded into that game's
 * row instead: its favourite and play time join it ([GameFold]). A scan that stops halfway therefore deletes nothing and leaves the old directory
 * records and its inserted rows pending, and the next scan redoes the work. A scan in which any
 * directory failed to list ends the same way: the unread directory may hold the new place of a row
 * that looks deleted, so deletions and rename matching wait for a scan without failures.
 */
internal class FolderScan(
    private val folder: RomFolderEntity,
    private val tree: DocumentTree,
    private val dao: ScanDao,
    private val matcher: SystemMatcher,
    /** The person's answers for unknown folders (Settings › Library) and the unknown folders before and after. */
    private val folderSystems: FolderSystemDao,
    /** Sub-folders the person skips (Settings › ROM folders): not read, their games leave like gone files. */
    private val skippedSubfolders: SkippedSubfolderDao,
    private val cataloguer: DirectoryCataloguer,
    private val batchSize: Int,
    private val clock: () -> Long,
    private val emit: suspend (ScanEvent) -> Unit,
) {
    private class FolderUnavailable(val reason: UnavailableReason) : Exception()

    private val newGames = ArrayList<GameEntity>()
    private val changedGames = ArrayList<StoredGame>()
    private val insertedThisScan = ArrayList<StoredGame>()

    /** Ids of rows a scan that did not finish inserted (stopped, folder gone at the end). */
    private var pendingIds: Set<Long> = emptySet()

    /** Pending rows found again by this scan, with what the scan found now; match candidates too. */
    private val pendingSeen = LinkedHashMap<Long, StoredGame>()
    private val changedPendingIds = HashSet<Long>()
    private val removalCandidates = LinkedHashMap<Long, StoredGame>()

    /** Row id of a removal candidate whose file is now part of another game → that game's file. */
    private val foldTargets = HashMap<Long, FoldTarget>()

    /**
     * [existingId] is the game's row when a finished scan stored it; a row new in this scan, or
     * pending from an interrupted one, is found by document id at the end (as [RenameMatcher]
     * treats them: the old row moves onto it, keeping its id and marks).
     */
    private data class FoldTarget(val documentId: String, val existingId: Long?)
    private val seenDocumentIds = HashSet<String>()
    private val removedPaths = ArrayList<String>()

    /** Reads the sheets (`.cue`, `.gdi`, `.m3u`) of each directory it walks; their files are parts of the sheet's game. */
    private val sheetReader = SheetReader(tree)

    private val directoryRecords = ArrayList<ScannedDirectoryEntity>()

    /** The directory records the scan started with; with [directoryRecords], every directory after it. */
    private var knownDirectories: List<ScannedDirectoryEntity> = emptyList()

    /** Paths of the directories this scan listed: their parents' game folders are grouped again ([groupGameFolderCopies]). */
    private val visitedPaths = HashSet<String>()
    private val walkedDirectoryIds = HashSet<String>()

    private var failedDirectories = 0
    private var listed = 0
    private var unchanged = 0
    private var filesSeen = 0
    private var added = 0
    private var updated = 0
    private var removed = 0
    private var addedBySystem: Map<String, Int> = emptyMap()

    /** Paths below the picked folder the person gave a system ([FolderSystemDao.choose]); Ignore is left out (scanned as before). */
    private var chosenSystems: Map<String, SystemRule> = emptyMap()
    private var newUnknownFolders: List<UnknownFolderRow> = emptyList()

    /** [skippedSubfolders] of this folder as [SkippedSubfolderDao.matchKey]s, read at the start. */
    private var skippedKeys: Set<String> = emptySet()
    private var removedBySystem: Map<String, Int> = emptyMap()
    private var junk = 0
    private var unmatched = 0
    private var stillWriting = 0

    /** Time spent in this scan's database writes (batches, the finish, the copy grouping), for the phase log. */
    var databaseWriteNanos = 0L
        private set

    /** Time [run] took, writes included, for the phase log. */
    var runNanos = 0L
        private set

    suspend fun run(): FolderOutcome {
        val started = System.nanoTime()
        try {
            return runTimed()
        } finally {
            runNanos = System.nanoTime() - started
        }
    }

    private inline fun <T> timedWrite(write: () -> T): T {
        val started = System.nanoTime()
        try {
            return write()
        } finally {
            databaseWriteNanos += System.nanoTime() - started
        }
    }

    private suspend fun runTimed(): FolderOutcome {
        val root = when (val result = tree.stat(tree.rootDocumentId)) {
            is TreeResult.Found -> result.value
            else -> return FolderOutcome.Unavailable(reasonFor(result, "picked folder"))
        }
        try {
            chosenSystems = folderSystems.choices(folder.id)
                .mapNotNull { choice -> choice.systemId?.let(matcher::rule)?.let { choice.relativePath to it } }
                .toMap()
            skippedKeys = skippedSubfolders.paths(folder.id).mapTo(HashSet(), SkippedSubfolderDao::matchKey)
            if (skippedKeys.isNotEmpty()) Log.i(LOG_TAG, "Folder ${folder.id}: ${skippedKeys.size} sub-folders skipped by the person")
            val unknownBefore = folderSystems.unknownFolders(folder.id).mapTo(HashSet()) { it.relativePath }
            val known = dao.directories(folder.id)
            knownDirectories = known
            pendingIds = dao.pendingGameIds(folder.id).toHashSet()
            val isFullWalk = known.isEmpty()
            if (isFullWalk) visit(root, "", knownChildren = emptyList()) else rescan(root, known)
            flush()
            if (isFullWalk && failedDirectories == 0) collectGamesOfUnwalkedDirectories()
            if (finish()) newUnknownFolders = folderSystems.unknownFolders(folder.id).filter { it.relativePath !in unknownBefore }
        } catch (e: FolderUnavailable) {
            return FolderOutcome.Unavailable(e.reason)
        }
        return FolderOutcome.Scanned(
            ScanStats(
                listed, unchanged, filesSeen, added, updated, removed, junk, unmatched, stillWriting, addedBySystem, removedBySystem,
                newUnknownFolders.map { UnknownFolder(it.relativePath, it.files) },
            ),
        )
    }

    private suspend fun rescan(root: DocumentEntry, known: List<ScannedDirectoryEntity>) {
        val childrenByParent = known.filter { it.relativePath.isNotEmpty() }.groupBy { parentPath(it.relativePath) }
        for (record in known.sortedBy { depth(it.relativePath) }) {
            if (isRemoved(record.relativePath)) continue
            // Skipped since the last scan: its games leave like those of a deleted directory, read nothing.
            if (isSkippedByPerson(record.relativePath)) {
                removeSubtree(record.relativePath)
                continue
            }
            val entry = if (record.documentId == root.documentId) {
                root
            } else {
                when (val result = tree.stat(record.documentId)) {
                    is TreeResult.Found -> result.value
                    else -> {
                        // Not "deleted": a deleted directory is found missing from its parent's list.
                        val reason = reasonFor(result, "directory ${record.documentId}")
                        if (reason == UnavailableReason.PERMISSION_REVOKED) throw FolderUnavailable(reason)
                        failedDirectories++
                        continue
                    }
                }
            }
            if (isUnchanged(entry, record)) {
                unchanged++
                continue
            }
            visit(entry, record.relativePath, childrenByParent[record.relativePath].orEmpty())
        }
    }

    private suspend fun visit(directory: DocumentEntry, path: String, knownChildren: List<ScannedDirectoryEntity>) {
        currentCoroutineContext().ensureActive()
        val children = when (val result = tree.listChildren(directory.documentId)) {
            is TreeResult.Found -> result.value
            else -> {
                val reason = reasonFor(result, "directory ${directory.documentId}")
                if (path.isEmpty() || reason == UnavailableReason.PERMISSION_REVOKED) throw FolderUnavailable(reason)
                failedDirectories++
                return
            }
        }
        listed++
        walkedDirectoryIds += directory.documentId
        visitedPaths += path
        // A file still being copied changes its own time with every write, but its directory's
        // time changed only when the file was created: trusting it would never read the rest.
        // Only files that can become games: an emulator's fresh save (.srm, .state) beside its ROMs
        // would otherwise have the directory read again after every game, and stored as 0 again.
        val writing = children.count { !it.isDirectory && isRecent(it.lastModified) && !JunkFilter.isJunkFile(it.name) }
        stillWriting += writing
        val recordedTime = if (writing > 0) 0 else trustedTime(directory.lastModified)
        val unknownFiles = addGames(directory.documentId, path, children)
        directoryRecords += ScannedDirectoryEntity(folder.id, directory.documentId, path, recordedTime, unknownFiles)

        val subdirectories = children.filter { it.isDirectory && !JunkFilter.isSkippedDirectory(it.name) && !isSkippedByPerson(childPath(path, it.name)) }
        val current = subdirectories.associateBy { it.documentId to childPath(path, it.name) }
        val knownKeys = knownChildren.mapTo(HashSet()) { it.documentId to it.relativePath }
        for (gone in knownChildren) {
            if ((gone.documentId to gone.relativePath) !in current) removeSubtree(gone.relativePath)
        }
        for ((key, child) in current) {
            if (key !in knownKeys) visit(child, key.second, knownChildren = emptyList())
        }
    }

    /**
     * Directory times have a granularity of 1 s (2 s on FAT32 SD cards): a file added in the same
     * second as the listing leaves the time unchanged. A time that close to now is stored as 0,
     * which makes the next scan list the directory again.
     */
    private fun trustedTime(lastModified: Long): Long =
        if (abs(clock() - lastModified) < MTIME_GRANULARITY_MS) 0 else lastModified

    /** A file time this close to the scan: the file may still be growing ([RECENT_FILE_MS]). */
    private fun isRecent(lastModified: Long): Boolean = lastModified != 0L && abs(clock() - lastModified) < RECENT_FILE_MS

    /**
     * Catalogues one directory's files into the batches; returns the directory's unknown files: those
     * some system could open, when no folder on its path names a system and the person chose none
     * (0 for the picked folder itself, whose files are matched by extension and listed nowhere).
     */
    private suspend fun addGames(parentDocumentId: String, path: String, children: List<DocumentEntry>): Int {
        val files = children.filter { !it.isDirectory }
        filesSeen += files.size
        val sheetParts = sheetReader.read(parentDocumentId, children)
        // A folder the person chose a system for is a system's folder, never one game's folder.
        val directoryName = if (path.isEmpty() || path in chosenSystems) null else path.substringAfterLast('/')
        val match = matcher.matchPath(folder.displayName, path, chosenSystems)
        val unknownFiles = if (path.isEmpty() || match != FolderMatch.Unknown) {
            0
        } else {
            files.count { !JunkFilter.isJunkFile(it.name) && matcher.isCandidateFile(it.name) }
        }
        val catalogue = cataloguer.catalogue(match, files, directoryName, sheetParts.bySheet)
        junk += catalogue.skippedJunk
        unmatched += catalogue.skippedUnmatched
        val stored = dao.gamesInDirectory(folder.id, parentDocumentId).associateBy { it.fields.documentId }
        // Which copy of an arcade game the pack knows shows is the naming pass's (ArcadeCopies, by
        // the pack and the person's marks): the walk, which sees file names only, keeps it. Sets the
        // pack does not know take the catalogue's answer like any file.
        val arcadeOwned =
            if (catalogue.games.any { ArcadeSystems.isArcade(it.systemId) && it.entry.documentId in stored }) {
                dao.arcadeNamedIn(folder.id, parentDocumentId).toHashSet()
            } else {
                emptySet()
            }
        // A subdirectory with one game is a game folder: whether its row shows is decided among its
        // sibling folders after the walk (GameFolderCopies), so listing it again keeps that answer.
        val gameFolder = path.isNotEmpty() && catalogue.games.size == 1
        val seen = HashSet<String>()
        for (game in catalogue.games) {
            val existing = stored[game.entry.documentId]
            val fields = game.toFields(parentDocumentId, path).let { fields ->
                val keepsShown = existing != null && existing.fields.systemId == fields.systemId &&
                    (existing.id in arcadeOwned || (gameFolder && GameFolderCopies.applies(fields.systemId)))
                if (keepsShown && existing != null) {
                    fields.copy(isPrimaryCopy = existing.fields.isPrimaryCopy)
                } else {
                    fields
                }
            }
            seen += fields.documentId
            if (existing != null && existing.id in pendingIds) {
                pendingSeen[existing.id] = StoredGame(existing.id, fields)
                if (existing.fields != fields) changedPendingIds += existing.id
            }
            when {
                existing == null -> newGames += fields.toEntity(folder.id)
                existing.fields != fields -> changedGames += StoredGame(existing.id, fields)
            }
        }
        seenDocumentIds += seen
        stored.values.filter { it.fields.documentId !in seen }.forEach { gone ->
            removalCandidates[gone.id] = gone
            catalogue.partOf[gone.fields.documentId]?.let { into ->
                foldTargets[gone.id] = FoldTarget(into, stored[into]?.id?.takeIf { it !in pendingIds })
            }
        }
        if (newGames.size + changedGames.size >= batchSize) flush()
        return unknownFiles
    }

    private suspend fun removeSubtree(path: String) {
        if (isRemoved(path)) return
        removedPaths += path
        dao.gamesInSubtree(folder.id, path).forEach { removalCandidates[it.id] = it }
    }

    /** [path] is a sub-folder the person skips, or inside one; compared ignoring case and stray slashes. */
    private fun isSkippedByPerson(path: String): Boolean {
        if (skippedKeys.isEmpty() || path.isEmpty()) return false
        val key = SkippedSubfolderDao.matchKey(path)
        return skippedKeys.any { key == it || key.startsWith("$it/") }
    }

    private fun isRemoved(path: String) = removedPaths.any { path == it || path.startsWith("$it/") }

    /** After a full walk: games whose directory was not walked belong to directories that are gone. */
    private suspend fun collectGamesOfUnwalkedDirectories() {
        for (parent in dao.parentDocumentIds(folder.id)) {
            if (parent in walkedDirectoryIds) continue
            dao.gamesInDirectory(folder.id, parent).forEach { removalCandidates[it.id] = it }
        }
    }

    private suspend fun flush() {
        if (newGames.isEmpty() && changedGames.isEmpty()) return
        newGames.chunked(batchSize).ifEmpty { listOf(emptyList()) }.forEachIndexed { index, chunk ->
            val changed = if (index == 0) changedGames.toList() else emptyList()
            val batch = ScanBatch(
                folderId = folder.id,
                systems = (chunk.map { it.systemId } + changed.map { it.fields.systemId }).distinct()
                    .map { SystemEntity(it, it) },
                newGames = chunk,
                changedGames = changed,
            )
            val inserted = timedWrite { dao.applyBatch(batch) }
            insertedThisScan += inserted
            added += inserted.size
            // A new game that was not inserted was already stored under the same id: it moved.
            updated += changed.size + (chunk.size - inserted.size)
            emit(ScanEvent.Progress(folder.id, listed, filesSeen, added + updated))
        }
        newGames.clear()
        changedGames.clear()
    }

    /** Ends the scan; false when it wrote nothing (a directory failed to list). */
    private suspend fun finish(): Boolean {
        if (failedDirectories > 0) {
            // Failures may mean the whole folder went (SD card out): report that as unavailable.
            confirmRootStillThere()
            // Like a stopped scan: no deletions, no directory records (so the next scan lists the
            // same directories again), and this scan's rows stay pending for the rename matching.
            Log.w(LOG_TAG, "Folder ${folder.id}: $failedDirectories directories unreadable; deletions deferred to the next scan")
            // The rows added so far are real (counted in `added`); nothing is removed, so the per-system counts agree with them.
            addedBySystem = insertedThisScan.groupingBy { it.fields.systemId }.eachCount()
            return false
        }
        // A row whose document id was seen again (a move under a stable id) is not gone.
        val gone = removalCandidates.values.filter { it.fields.documentId !in seenDocumentIds }
        val renames = RenameMatcher.match(gone, insertedThisScan + pendingSeen.values)
        val (launchMoves, folds) = regrouped(gone.filter { gone -> renames.none { it.removed === gone } }, renames)
        val moves = renames + launchMoves
        val movedIds = moves.mapTo(HashSet()) { it.removed.id }
        val foldedIds = folds.mapTo(HashSet()) { it.foldedGameId }
        val removedIds = gone.map { it.id }.filter { it !in movedIds && it !in foldedIds }
        if (moves.isNotEmpty() || folds.isNotEmpty() || removedIds.isNotEmpty() || removedPaths.isNotEmpty()) confirmRootStillThere()
        timedWrite {
            dao.finishScan(
                ScanFinish(
                    folderId = folder.id,
                    moves = renames.map { GameMove(it.removed.id, it.inserted) } +
                        launchMoves.map { GameMove(it.removed.id, it.inserted, ontoNewLaunchRow = true) },
                    folds = folds,
                    removedGameIds = removedIds,
                    removedDirectoryPaths = removedPaths.toList(),
                    directories = directoryRecords.filter { !isRemoved(it.relativePath) },
                ),
            )
        }
        // Directory records are written: a scan stopped from here on would not list these
        // directories again, so the grouping (database only, no file read) finishes regardless.
        withContext(NonCancellable) { groupGameFolderCopies() }
        // A row inserted by this scan was counted as added; an earlier scan's row was counted as
        // updated only if its fields changed. Either way the kept row now has a new place.
        val insertedIds = insertedThisScan.mapTo(HashSet()) { it.id }
        added -= moves.count { it.inserted.id in insertedIds }
        updated += moves.count { it.inserted.id in insertedIds || it.inserted.id !in changedPendingIds }
        removed += removedIds.size + folds.size
        // Moves are not additions: the kept row already counted (see `added` above).
        val keptInserted = moves.mapTo(HashSet()) { it.inserted.id }
        addedBySystem = insertedThisScan.filter { it.id !in keptInserted }.groupingBy { it.fields.systemId }.eachCount()
        removedBySystem = gone.filter { it.id in removedIds || it.id in foldedIds }.groupingBy { it.fields.systemId }.eachCount()
        if (moves.isNotEmpty()) Log.i(LOG_TAG, "Folder ${folder.id}: ${moves.size} moved or renamed games kept their rows")
        if (folds.isNotEmpty()) Log.i(LOG_TAG, "Folder ${folder.id}: ${folds.size} rows folded into the games they are now part of")
        return true
    }

    /**
     * What the grouping did to the gone rows [unmatched] by the [renames], whose file is now part
     * of another row's game ([foldTargets]); returns moves onto new launch rows, and folds:
     * - that game's row is new this scan, or pending from an interrupted one (an `.m3u` with
     *   another name now stands for the discs): the game's launch file changed, so the gone row
     *   with the lowest id moves onto it (`GameMove.ontoNewLaunchRow`: its id, marks and hidden
     *   mark stay; the new row's "Not a game" does not come along); any other gone row of that
     *   game folds into it;
     * - that game's row was there before (a `Game - Disc 2` entry of an older scan, now part of
     *   `Game - Disc 1`'s game): the gone row folds into it ([GameFold]: hidden or "Not a game" only
     *   if every row of the game was).
     * A new row that a rename hands to an old one is reached through the rename's kept row.
     */
    private fun regrouped(
        unmatched: List<StoredGame>,
        renames: List<RenameMatcher.Match>,
    ): Pair<List<RenameMatcher.Match>, List<GameFold>> {
        if (foldTargets.isEmpty()) return emptyList<RenameMatcher.Match>() to emptyList()
        val insertedByDocument = (insertedThisScan + pendingSeen.values).associateBy { it.fields.documentId }
        val keptInsteadOf = renames.associate { it.inserted.id to it.removed.id }
        val moves = mutableListOf<RenameMatcher.Match>()
        val folds = mutableListOf<GameFold>()
        val byTarget = unmatched.filter { it.id in foldTargets }.sortedBy { it.id }.groupBy { foldTargets.getValue(it.id) }
        for ((target, rows) in byTarget) {
            val inserted = if (target.existingId == null) insertedByDocument[target.documentId] ?: continue else null
            val keptByRename = inserted?.let { keptInsteadOf[it.id] }
            when {
                inserted == null || keptByRename != null -> {
                    val kept = keptByRename ?: target.existingId?.let { keptInsteadOf[it] ?: it } ?: continue
                    for (row in rows) folds += GameFold(keptGameId = kept, foldedGameId = row.id)
                }
                else -> {
                    moves += RenameMatcher.Match(removed = rows.first(), inserted = inserted)
                    for (row in rows.drop(1)) folds += GameFold(keptGameId = rows.first().id, foldedGameId = row.id)
                }
            }
        }
        return moves to folds
    }

    /**
     * Groups regional copies kept one game per folder across sibling folders ([GameFolderCopies]),
     * from the stored rows only: the game folders below every directory this scan listed and below
     * the parent of each (a game folder that changed, came or went re-decides its siblings, which
     * are not listed again). One indexed read of those directories' rows; nothing is opened.
     */
    private suspend fun groupGameFolderCopies() {
        val directories = HashMap<String, String>()
        for (record in knownDirectories + directoryRecords) {
            if (!isRemoved(record.relativePath)) directories[record.relativePath] = record.documentId
        }
        val parents = visitedPaths.flatMapTo(HashSet()) { if (it.isEmpty()) listOf(it) else listOf(it, parentPath(it)) }
        val childIds = directories.filterKeys { it.isNotEmpty() && parentPath(it) in parents }.values
        if (childIds.isEmpty()) return
        val decided = GameFolderCopies.decide(dao.gamesInDirectories(folder.id, childIds), cataloguer.preferredRegions)
        val changed = timedWrite {
            dao.setPrimaryCopies(
                shown = decided.filterValues { it }.keys.toList(),
                leftOut = decided.filterValues { !it }.keys.toList(),
            )
        }
        if (changed > 0) Log.i(LOG_TAG, "Folder ${folder.id}: $changed rows in game folders changed which copy shows")
    }

    /** Before deleting anything: a folder that vanished (SD card out) must not empty the library. */
    private fun confirmRootStillThere() {
        val result = tree.stat(tree.rootDocumentId)
        if (result !is TreeResult.Found) throw FolderUnavailable(reasonFor(result, "picked folder"))
    }

    private fun reasonFor(result: TreeResult<*>, what: String): UnavailableReason {
        val (reason, cause) = when (result) {
            is TreeResult.PermissionDenied -> UnavailableReason.PERMISSION_REVOKED to result.cause
            is TreeResult.Failed -> UnavailableReason.READ_FAILED to result.cause
            else -> UnavailableReason.FOLDER_MISSING to null
        }
        val causeClass = cause?.let { ", ${it.javaClass.simpleName}" }.orEmpty()
        Log.w(LOG_TAG, "Folder ${folder.id}: $what unreadable ($reason$causeClass)")
        return reason
    }

    private fun CataloguedGame.toFields(parentDocumentId: String, path: String) = GameScanFields(
        documentId = entry.documentId,
        parentDocumentId = parentDocumentId,
        relativePath = childPath(path, entry.name),
        fileName = entry.name,
        sizeBytes = entry.sizeBytes,
        modifiedAt = entry.lastModified,
        title = title,
        sortName = sortName,
        regions = regions.joinToString(",") { it.code },
        discGroup = discGroup,
        discCount = discCount,
        systemId = systemId,
        isPrimaryCopy = isPrimaryCopy,
    )

    private fun GameScanFields.toEntity(folderId: Long) = GameEntity(
        folderId = folderId,
        documentId = documentId,
        parentDocumentId = parentDocumentId,
        relativePath = relativePath,
        fileName = fileName,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        title = title,
        sortName = sortName,
        regions = regions,
        discGroup = discGroup,
        discCount = discCount,
        systemId = systemId,
        isPrimaryCopy = isPrimaryCopy,
    )

    companion object {
        private const val MTIME_GRANULARITY_MS = 2_000L

        /**
         * A file modified this recently may still be written (a copy from a file manager, MTP or
         * `adb push`): its directory is stored as "read again". A copy in progress touches the file
         * every few milliseconds; 60 s also covers a copy that stalls for a while (a slow card
         * flushing its cache, a USB link renegotiating) and FAT's 2 s times, and costs at most one
         * more listing of that one directory a minute after a copy ends.
         */
        const val RECENT_FILE_MS = 60_000L

        /**
         * Whether a recorded directory needs no listing: the provider's time equals the stored one.
         * A provider that gives no time (0) cannot say "unchanged", and a time stored as 0 means
         * "list again" ([trustedTime]). The rescan and the change check
         * ([FolderChangeCheck], `LibraryScanner.changedFolders`) both decide with this.
         */
        fun isUnchanged(entry: DocumentEntry, record: ScannedDirectoryEntity): Boolean =
            entry.lastModified != 0L && entry.lastModified == record.lastModified

        fun childPath(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

        fun parentPath(path: String) = path.substringBeforeLast('/', missingDelimiterValue = "")

        fun depth(path: String) = if (path.isEmpty()) 0 else path.count { it == '/' } + 1
    }
}
