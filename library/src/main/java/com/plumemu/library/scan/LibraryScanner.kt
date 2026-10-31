package com.plumemu.library.scan

import android.content.Context
import android.net.Uri
import android.util.Log
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.catalog.CatalogSettings
import com.plumemu.library.catalog.DirectoryCataloguer
import com.plumemu.library.catalog.JunkFilter
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.library.matching.SystemRule
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.naming.allSystemsNamedDifferentlySince
import com.plumemu.library.naming.systemsNamedDifferentlySince
import com.plumemu.library.scrape.CODE_DROPPED_REPAIR_SINCE_VERSION
import com.plumemu.library.scrape.FileNameTitles
import com.plumemu.library.scrape.NO_MATCH_RETRY_SINCE_VERSION
import com.plumemu.library.scrape.SCRAPE_RULES_VERSION
import com.plumemu.library.scrape.SERIAL_ONLY_REPAIR_SINCE_VERSION
import com.plumemu.library.scrape.WRONG_SYSTEM_REPAIR_SINCE_VERSION
import com.plumemu.library.scrape.WrongSystemMatches
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Scans every picked folder into the database. The first scan of a folder walks it all; later
 * scans list only directories whose modified time changed. Writes land in batches of [batchSize]
 * rows, so the library shows games while the scan goes on.
 *
 * With [naming], every folder's walk is followed by its identification pass ([IdentifyPass]):
 * the walk lists games under their file names first, for every folder, and then the rows that
 * are new or whose file changed are named, the lists updating page by page. Without it (tests of
 * the walk alone) games keep their file names.
 *
 * One scan runs at a time in the whole process, whichever `LibraryScanner` instance starts it: a
 * second scan waits for the first and then finds little to do.
 */
class LibraryScanner(
    private val database: PlumDatabase,
    private val permissions: FolderPermissions,
    private val openTree: (RomFolderEntity) -> DocumentTree,
    rules: List<SystemRule>,
    settings: CatalogSettings = CatalogSettings(),
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val naming: GameNaming? = null,
    /** Files the naming pass opens at the same time ([IdentifyPass]); 1 opens them one after another. */
    private val namingReaders: Int = NAMING_READERS,
    /** Proves a folder that no longer answers was deleted, so the scan forgets it ([FolderOutcome.Deleted]). */
    private val presence: FolderPresence = FolderPresence.NEVER_PROVEN,
) {
    private val matcher = SystemMatcher(rules)
    private val cataloguer = DirectoryCataloguer(matcher, settings)
    private val preferredRegions = settings.preferredRegions

    /** Scans all picked folders. Cold: nothing happens until collected. */
    fun scan(): Flow<ScanEvent> = flow {
        processWideScanLock.withLock { scanAll() }
    }.flowOn(ioDispatcher)

    /**
     * Scans only the picked folders in [folderIds] (the ones [changedFolders] found changed), the
     * same way [scan] scans each: unchanged directories in them are not listed either. Ids of
     * folders no longer picked are passed over. Cold, and waits for a running scan, like [scan].
     */
    fun scan(folderIds: Set<Long>): Flow<ScanEvent> = flow {
        processWideScanLock.withLock { scanAll(folderIds) }
    }.flowOn(ioDispatcher)

    /**
     * Which picked folders a rescan would list something in, without listing any directory: each
     * folder's recorded directories are asked for their modified time, one single-row query each,
     * with the rescan's own comparison ([FolderChangeCheck]), stopping at a folder's first change.
     * Reads only, so it does not wait for a running scan; the caller keeps scans from overlapping.
     * Logs one line with what it found and what it cost. [onlyFolderIds]: ask only those folders.
     */
    suspend fun changedFolders(onlyFolderIds: Set<Long>? = null): FolderChanges = withContext(ioDispatcher) {
        val startedNanos = System.nanoTime()
        val changed = LinkedHashSet<Long>()
        val neverScanned = LinkedHashSet<Long>()
        val unavailable = LinkedHashMap<Long, UnavailableReason>()
        var asked = 0
        val folders = database.scanDao().folders().filter { onlyFolderIds == null || it.id in onlyFolderIds }
        for (folder in folders) {
            if (!permissions.isHeld(folder.treeUri)) {
                unavailable[folder.id] = UnavailableReason.PERMISSION_REVOKED
                continue
            }
            val check = FolderChangeCheck(folder, openTree(folder), database.scanDao())
            when (val state = check.run()) {
                FolderChangeState.Changed -> changed += folder.id
                FolderChangeState.NeverScanned -> {
                    changed += folder.id
                    neverScanned += folder.id
                }
                FolderChangeState.Unchanged -> Unit
                is FolderChangeState.Unavailable -> unavailable[folder.id] = state.reason
            }
            asked += check.directoriesAsked
        }
        val unavailableText = if (unavailable.isEmpty()) "" else ", unavailable ${unavailable.map { (id, reason) -> "$id ($reason)" }}"
        Log.i(
            LOG_TAG,
            "Change check: ${folders.size} folders, changed ${changed.toList()}$unavailableText; " +
                "$asked directories asked in ${(System.nanoTime() - startedNanos) / 1_000_000} ms",
        )
        FolderChanges(changed, unavailable, foldersChecked = folders.size, directoriesAsked = asked, neverScannedFolderIds = neverScanned)
    }

    /**
     * Lists every directory again, for when the rules or settings changed (preferred region,
     * new platform data) and unchanged directories need their games recomputed. Games keep their
     * rows, favourites and play history; only changed scan columns are written.
     */
    fun rescanEverything(): Flow<ScanEvent> = flow {
        processWideScanLock.withLock {
            database.scanDao().forgetDirectories()
            scanAll()
        }
    }.flowOn(ioDispatcher)

    /**
     * Names the rows waiting for a name ([IdentifyPass]) in every picked folder, without walking
     * any: Back to automatic names its game at once this way, and the other rows it
     * sent back to the pass (copies sharing a key) with it. Waits for a running scan, like
     * [scan]. A file that cannot be read keeps the name it shows and waits for the next scan.
     * Without [naming] nothing happens.
     */
    fun nameWaiting(): Flow<ScanEvent> = flow {
        val naming = naming ?: return@flow
        processWideScanLock.withLock {
            for (folder in database.scanDao().folders()) nameFolder(folder, naming)
        }
    }.flowOn(ioDispatcher)

    /**
     * Settings › Library › Unknown folder: stores the person's system for [relativePath] (a folder
     * right below picked folder [folderId] whose name matches no system; [systemId] null for
     * "Ignore this folder") and marks that folder and the folders below it to be listed again, so
     * the next [scan] of [folderId] scans its files by the answer. Waits for a running scan: that
     * scan writes its directory times at its end and would otherwise undo the marks.
     */
    suspend fun chooseFolderSystem(folderId: Long, relativePath: String, systemId: String?) {
        withContext(ioDispatcher) {
            processWideScanLock.withLock {
                database.folderSystemDao().choose(folderId, relativePath, systemId, clock())
            }
        }
    }

    /**
     * Settings › ROM folders › a folder: skips [relativePath] (a sub-folder of picked folder
     * [folderId]) when [skip], else stops skipping it, and marks its parent to be listed again, so
     * the next [scan] drops its games like gone files or finds them again. Nothing on
     * disk is touched. Waits for a running scan, which writes its directory times at its end
     * and would otherwise undo the mark. False when nothing changed (already so, or the empty path).
     */
    suspend fun skipSubfolder(folderId: Long, relativePath: String, skip: Boolean): Boolean =
        withContext(ioDispatcher) {
            processWideScanLock.withLock {
                val dao = database.skippedSubfolderDao()
                val changed = if (skip) dao.skip(folderId, relativePath, clock()) else dao.unskip(folderId, relativePath)
                Log.i(LOG_TAG, "Folder $folderId: a sub-folder ${if (skip) "skipped" else "no longer skipped"}${if (changed) "" else " (no change)"}")
                changed
            }
        }

    /**
     * The first-level sub-folders of picked folder [folderId] as the walk sees them, read from disk
     * through the same documents access (Settings' "Skip a sub-folder…"), off the caller's thread.
     * Folders the walk always skips (hidden, `bios`, `media`…) are left out; names A to Z ignoring
     * case. Takes no scan lock: it only lists one directory.
     */
    suspend fun subfolders(folderId: Long): SubfolderListing =
        withContext(ioDispatcher) {
            val folder = database.scanDao().folders().firstOrNull { it.id == folderId } ?: return@withContext SubfolderListing.NoFolder
            if (!permissions.isHeld(folder.treeUri)) {
                Log.w(LOG_TAG, "Folder $folderId: sub-folders not listed, access no longer held")
                return@withContext SubfolderListing.Unreadable
            }
            val tree = openTree(folder)
            when (val result = tree.listChildren(tree.rootDocumentId)) {
                is TreeResult.Found ->
                    SubfolderListing.Listed(
                        result.value.filter { it.isDirectory && !JunkFilter.isSkippedDirectory(it.name) }
                            .map { it.name }
                            .sortedWith(String.CASE_INSENSITIVE_ORDER),
                    )
                else -> {
                    Log.w(LOG_TAG, "Folder $folderId: sub-folders not listed (${result.javaClass.simpleName})")
                    SubfolderListing.Unreadable
                }
            }
        }

    /**
     * After an upgrade whose naming rules changed ([NAMING_RULES_VERSION] above [storedVersion],
     * the app's stored value): marks the named rows of the systems that changed to be named again
     * by the next pass ([scan] or [nameWaiting]); nothing is named or read here. Waits for a
     * running scan, so its pass does not race the marks. A person's fix, favourites, hidden marks
     * and play time stay: the pass keeps a fix as it always does. Safe to call again (a call that
     * marked rows and then lost its stored version only names them once more).
     *
     * @return how many rows were marked.
     */
    suspend fun nameAgainForNewRules(storedVersion: Int): Int {
        val everySystem = allSystemsNamedDifferentlySince(storedVersion)
        val systems = systemsNamedDifferentlySince(storedVersion)
        if (!everySystem && systems.isEmpty()) return 0
        return withContext(ioDispatcher) {
            processWideScanLock.withLock {
                val dao = database.identifyDao()
                val marked = if (everySystem) dao.markAllToNameAgain() else dao.markSystemsToNameAgain(systems)
                Log.i(LOG_TAG, "Naming rules $storedVersion -> $NAMING_RULES_VERSION: $marked games of ${if (everySystem) "every system" else "${systems.size} systems"} to name again")
                marked
            }
        }
    }

    /**
     * After an upgrade whose scraper judges answers differently ([SCRAPE_RULES_VERSION] above
     * [storedVersion], the app's stored value): forgets the stored results the old rules got wrong
     * (`ScrapedGameDao.forgetSerialOnlyHashMatches`, [WrongSystemMatches]), so the next box-art run asks those games as
     * never asked, and sends the games named from them to the next pass ([scan] or
     * [nameWaiting]). Waits for a running scan, so its pass does not race the marks. A person's
     * choice, name fixes, favourites, hidden marks and play time stay. Safe to call again.
     *
     * @return the stored results forgotten.
     */
    suspend fun askAgainForNewScrapeRules(storedVersion: Int): Int {
        if (storedVersion >= SCRAPE_RULES_VERSION) return 0
        return withContext(ioDispatcher) {
            processWideScanLock.withLock {
                val dao = database.scrapedGameDao()
                val forgotten = if (storedVersion < SERIAL_ONLY_REPAIR_SINCE_VERSION) dao.forgetSerialOnlyHashMatches() else 0
                val wrongSystem = if (storedVersion < WRONG_SYSTEM_REPAIR_SINCE_VERSION) WrongSystemMatches.forget(database) else 0
                // Version 4: cartridge games whose code the naming pass dropped, and version 3's leftovers without a file title.
                val codeDropped = if (storedVersion < CODE_DROPPED_REPAIR_SINCE_VERSION) WrongSystemMatches.forgetCodeDroppedMatches(database) else 0
                val leftovers = if (storedVersion < CODE_DROPPED_REPAIR_SINCE_VERSION) FileNameTitles.showWhereMissing(database) else 0
                // Version 2 (name search variants): earlier no-matches may match now, so ask them again.
                val due = if (storedVersion < NO_MATCH_RETRY_SINCE_VERSION) dao.makeNoMatchesDueNow() else 0
                Log.i(LOG_TAG, "Scrape rules $storedVersion -> $SCRAPE_RULES_VERSION: $forgotten serial-only matches and $wrongSystem other-system matches and $codeDropped code-dropped matches to ask again, $leftovers names reset to the file's, $due no-matches made due")
                forgotten + wrongSystem + codeDropped + leftovers + due
            }
        }
    }

    /**
     * After a person's fix: reads [gameId]'s file whole once for its content key and
     * stores the fix under it too ([WholeFileRead]), for a row with no fingerprint key (a loose
     * cartridge read at QUICK depth). The read does not wait for a running scan; the write does.
     * A file that cannot be read keeps the fix on its row and is logged. Without [naming] nothing
     * is read.
     */
    suspend fun addContentKey(gameId: Long): ContentKeyOutcome {
        val naming = naming ?: return ContentKeyOutcome.NO_KEY_FOUND
        return withContext(ioDispatcher) {
            WholeFileRead(database, permissions, openTree, naming).run(gameId) { write -> processWideScanLock.withLock { write() } }
        }
    }

    /**
     * The content pass ([ContentPass]): reads loose cartridges (and zips
     * whose entry may hide a header) whole for their content CRC and names them again from it,
     * one file at a time, until every such row is done or [budget] is spent
     * ([ContentReadResult.moreLeft]: run another slice). Reads do not wait for a running scan;
     * each write does. Cancelling stops before the next file; nothing half-written stays. Without
     * [naming] nothing is read.
     */
    suspend fun readContent(budget: ContentReadBudget = ContentReadBudget()): ContentReadResult {
        val naming = naming ?: return ContentReadResult()
        return withContext(ioDispatcher) {
            ContentPass(database, permissions, openTree, naming, processWideScanLock, clock).run(budget)
        }
    }

    /** True when [readContent] has at least one row to read; the app asks before it enqueues the background pass. */
    suspend fun hasContentToRead(): Boolean {
        val naming = naming ?: return false
        return withContext(ioDispatcher) {
            ContentPass(database, permissions, openTree, naming, processWideScanLock, clock).hasRows()
        }
    }

    /** Every picked folder, or only those in [onlyFolderIds]. */
    private suspend fun FlowCollector<ScanEvent>.scanAll(onlyFolderIds: Set<Long>? = null) {
        val folders = database.scanDao().folders().filter { onlyFolderIds == null || it.id in onlyFolderIds }
        val scanned = folders.mapNotNull { folder -> scanFolder(folder)?.let { folder to it } }
        val naming = naming ?: return
        for ((folder, stats) in scanned) nameFolder(folder, naming, gamesRemoved = stats.gamesRemoved > 0)
    }

    private suspend fun FlowCollector<ScanEvent>.nameFolder(folder: RomFolderEntity, naming: GameNaming, gamesRemoved: Boolean = false) {
        val s = IdentifyPass(folder, openTree(folder), database, naming, gamesRemoved, preferredRegions, namingReaders, readerDispatcher = ioDispatcher) { emit(it) }.run()
        if (s.gamesNamed + s.notRead + s.waitingForPack > 0) {
            Log.i(
                LOG_TAG,
                "Folder ${folder.id}: named ${s.gamesNamed} games, ${s.filesRead} files read, ${s.fromCache} from cache, " +
                    "${s.notRead} not read, ${s.hidden} hidden, ${s.waitingForPack} waiting for a pack",
            )
        }
    }

    /** Walks one folder; its stats when it was scanned (its games may be named next), else null. */
    private suspend fun FlowCollector<ScanEvent>.scanFolder(folder: RomFolderEntity): ScanStats? {
        if (!permissions.isHeld(folder.treeUri)) {
            Log.w(LOG_TAG, "Folder ${folder.id}: access no longer held; the person must pick it again")
            emit(ScanEvent.FolderFinished(folder.id, forgetIfProvenGone(folder, UnavailableReason.PERMISSION_REVOKED)))
            return null
        }
        val scan = FolderScan(folder, openTree(folder), database.scanDao(), matcher, database.folderSystemDao(), database.skippedSubfolderDao(), cataloguer, batchSize, clock) { emit(it) }
        val outcome = scan.run().let { if (it is FolderOutcome.Unavailable) forgetIfProvenGone(folder, it.reason) else it }
        if (outcome is FolderOutcome.Scanned) {
            database.scanDao().markScanFinished(folder.id, clock())
            val s = outcome.stats
            Log.i(
                LOG_TAG,
                "Folder ${folder.id}: listed ${s.directoriesListed} directories, ${s.directoriesUnchanged} unchanged, " +
                    "+${s.gamesAdded} ~${s.gamesUpdated} -${s.gamesRemoved} games",
            )
            ScanPhaseLog.log(folder.id, ScanPhaseLog.WALK, scan.runNanos - scan.databaseWriteNanos, s.filesSeen, "${s.directoriesListed} directories listed")
            ScanPhaseLog.log(
                folder.id,
                ScanPhaseLog.DATABASE_WRITES,
                scan.databaseWriteNanos,
                s.filesSeen,
                "${s.gamesAdded + s.gamesUpdated + s.gamesRemoved} rows written",
            )
        }
        emit(ScanEvent.FolderFinished(folder.id, outcome))
        return (outcome as? FolderOutcome.Scanned)?.stats
    }

    /**
     * The outcome of a folder that came out unavailable for [reason]: [FolderOutcome.Deleted] when
     * the folder is missing or its grant gone and [presence] proves it was deleted, after
     * forgetting it the way [RomFolders.removeFolder] does; otherwise [FolderOutcome.Unavailable]
     * and nothing deleted (a card taken out, a grant revoked in Android's settings, a backup from
     * another device). A read failure never deletes.
     */
    private suspend fun forgetIfProvenGone(folder: RomFolderEntity, reason: UnavailableReason): FolderOutcome {
        val unavailable = FolderOutcome.Unavailable(reason)
        if (reason == UnavailableReason.READ_FAILED) return unavailable
        val proven = try {
            presence.isProvenGone(folder.treeUri)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Folder ${folder.id}: could not check whether it was deleted (${e.javaClass.simpleName})")
            false
        }
        if (!proven) return unavailable
        val games = database.scanDao().gamesInFolder(folder.id)
        RomFolders(database.scanDao(), permissions, clock).removeFolder(folder)
        Log.i(LOG_TAG, "Folder ${folder.id}: deleted from the device ($reason); forgot it and its ${games.size} games")
        return FolderOutcome.Deleted(games.size, games.groupingBy { it.systemId }.eachCount())
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 500

        // Process-wide, not per instance: two scanners (a WorkManager job and a screen) must not
        // walk and delete in the same folders at once.
        private val processWideScanLock = Mutex()

        /** The scanner wired to the device's documents providers. */
        fun forDevice(
            context: Context,
            database: PlumDatabase,
            rules: List<SystemRule>,
            settings: CatalogSettings = CatalogSettings(),
            naming: GameNaming? = null,
        ): LibraryScanner {
            val resolver = context.applicationContext.contentResolver
            return LibraryScanner(
                database = database,
                permissions = AndroidFolderPermissions(resolver),
                openTree = { ContentResolverDocumentTree(resolver, Uri.parse(it.treeUri)) },
                rules = rules,
                settings = settings,
                naming = naming,
                presence = ExternalStorageFolderPresence(),
            )
        }
    }
}

/** A picked folder's first-level sub-folders for Settings ([LibraryScanner.subfolders]). */
sealed interface SubfolderListing {
    /** The sub-folders' names, A to Z; empty when it has none. */
    data class Listed(val names: List<String>) : SubfolderListing

    /** The folder could not be read (card out, access revoked, provider failure); logged by class. */
    data object Unreadable : SubfolderListing

    /** No such picked folder (removed meanwhile). */
    data object NoFolder : SubfolderListing
}
