package com.plumemu.library.scan

import android.util.Log
import androidx.room.withTransaction
import com.plumemu.coredata.db.ContentReadCursor
import com.plumemu.coredata.db.ContentReadFilter
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameToReadContent
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.identify.Identification
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.library.naming.FingerprintCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Limits of one [LibraryScanner.readContent] run, checked before each file; null: no limit.
 * [stopBeforeNextFile] true ends the run there as a spent budget does (what was read is written,
 * [ContentReadResult.moreLeft] is set): the app stops the pass while a game is played.
 */
data class ContentReadBudget(
    val maxMillis: Long? = null,
    val maxBytes: Long? = null,
    val stopBeforeNextFile: (suspend () -> Boolean)? = null,
)

/** What one [LibraryScanner.readContent] run did. */
data class ContentReadResult(
    /** Files read whole (some may then be skipped: see [written]). */
    val read: Int = 0,
    /**
     * Of those, rows whose stored fingerprint was written (stamped done): the progress a caller may
     * count on. A read that was skipped (file or pack changed meanwhile) is not written and comes
     * again on the next run, so a caller that goes on only after progress counts this, not [read].
     */
    val written: Int = 0,
    /** Of those, games whose shown title changed. */
    val renamed: Int = 0,
    /** Files that could not be opened or read (logged; given up on after a few runs). */
    val failed: Int = 0,
    /**
     * Rows left as they were because the file or the row changed meanwhile, the folder is
     * unavailable, or the database refused the row's write (logged; read again next run).
     */
    val skipped: Int = 0,
    val bytesRead: Long = 0,
    val millis: Long = 0,
    /** The budget ran out with rows still to read: the caller runs another slice. */
    val moreLeft: Boolean = false,
)

/**
 * The content pass: reads loose cartridges,
 * and zips whose entry may hide a header, whole at [FingerprintReader.Depth.FULL] for the content
 * CRC a scan's QUICK read never computes, and names them again from it. Which rows:
 * `IdentifyDao.contentReadPage` (played, then favourites, then the rest; smallest first), paged,
 * one file at a time on the caller's dispatcher.
 *
 * The files of a page are read without the scan lock; the page's results are then written under
 * [scanLock] in one transaction (one invalidation of `game` per page, not per file: each
 * invalidation re-runs every Room observer of the library). Each row is written only while it is
 * still as read, checked at write time (size, time, name, cached fingerprint, not waiting for the
 * naming pass): otherwise it is left for the next scan. A batch holds fingerprints and names only,
 * at most one page of them. The name follows the naming
 * pass's rules ([NameWrite]): a CRC match is `IDENTIFIER_EXACT`, a person's fix always wins.
 *
 * Box art: the new `crc:` key becomes the scrape key. When the game's title stays the same, its
 * matched `scraped_game` row is copied to the new key in the same transaction (no request, no
 * download); when the title changes, nothing is copied and the box art run asks again by CRC. A
 * person's pick ("Fix box art...") is always copied: the file is the same, only what is known of
 * it grew, and its title is then offered again as after any naming. The old row stays (other
 * copies of the file may still be under it).
 *
 * Every stored value it is done with carries a stamp (`FingerprintCodec`, `full`): read, or
 * failed [MAX_FAILED_READS] times; a failed read counts one in the same place, so a broken file is
 * not read on every run, and a file that changes gets a new QUICK fingerprint and is read again.
 * Logs carry ids, counts and exception classes, never names.
 */
internal class ContentPass(
    private val database: PlumDatabase,
    private val permissions: FolderPermissions,
    private val openTree: (RomFolderEntity) -> DocumentTree,
    private val naming: GameNaming,
    private val scanLock: Mutex,
    private val clock: () -> Long,
) {
    private enum class Written { STALE, NOT_WRITTEN, KEPT_NAME, RENAMED }

    /** A file read whole, waiting for its page's write. */
    private class ReadResult(val row: GameToReadContent, val identification: Identification, val fingerprint: String)

    private val trees = HashMap<Long, DocumentTree>()
    private val unavailableFolders = HashSet<Long>()
    private val packReady = HashMap<String, Boolean?>()
    private var read = 0
    private var written = 0
    private var renamed = 0
    private var failed = 0
    private var skipped = 0
    private var bytes = 0L

    suspend fun run(budget: ContentReadBudget): ContentReadResult {
        val start = clock()
        val filter = filter()
        val folders = database.scanDao().folders().associateBy { it.id }
        var cursor = ContentReadCursor()
        var moreLeft = false
        var outcome = "cancelled"
        var stoppedEarly = false
        try {
            pages@ while (true) {
                val page = database.identifyDao().contentReadPage(filter, cursor, PAGE_SIZE)
                if (page.isEmpty()) break
                val batch = ArrayList<ReadResult>(page.size)
                try {
                    for (row in page) {
                        currentCoroutineContext().ensureActive()
                        if (spent(budget, start)) {
                            moreLeft = true
                            break
                        }
                        if (budget.stopBeforeNextFile?.invoke() == true) {
                            moreLeft = true
                            stoppedEarly = true
                            break
                        }
                        cursor = ContentReadCursor(row.readGroup, row.game.sizeBytes, row.game.id)
                        readOne(row, folders[row.folderId])?.let(batch::add)
                    }
                } catch (e: Exception) {
                    writeOnStop(batch, cancelled = e is CancellationException)
                    throw e
                }
                // Also when the budget ran out mid-page: what was read is written, not read again.
                writeBatch(batch)
                if (moreLeft) break@pages
            }
            outcome = if (stoppedEarly) "stopped by its caller" else if (moreLeft) "budget spent" else "done"
        } finally {
            Log.i(
                LOG_TAG,
                "Content pass $outcome: $read read, $renamed renamed, $failed failed, $skipped skipped, $written written, " +
                    "${bytes / 1024} KiB in ${clock() - start} ms",
            )
        }
        return ContentReadResult(read, written, renamed, failed, skipped, bytes, clock() - start, moreLeft)
    }

    /** True when at least one row is still waiting to be read (the check behind the worker's trigger). */
    suspend fun hasRows(): Boolean =
        database.identifyDao().contentReadPage(filter(), ContentReadCursor(), 1).isNotEmpty()

    private fun filter(): ContentReadFilter {
        val systems = FingerprintReader.contentReadSystems()
        return ContentReadFilter(
            looseSystems = systems.loose,
            zippedSystems = systems.zipped,
            maxBytes = FingerprintReader.DEFAULT_WHOLE_FILE_CRC_LIMIT,
            looseMark = FingerprintCodec.LOOSE_MARK,
            zipMark = FingerprintCodec.ZIP_MARK,
            wholeFileCrcMark = FingerprintCodec.WHOLE_FILE_CRC_MARK,
            zipContentCrcMark = FingerprintCodec.ZIP_CONTENT_CRC_MARK,
            doneMark = FingerprintCodec.CONTENT_READ_DONE_MARK,
        )
    }

    private fun spent(budget: ContentReadBudget, start: Long): Boolean =
        budget.maxMillis?.let { clock() - start >= it } == true || budget.maxBytes?.let { bytes >= it } == true

    /** Reads one file; the result to write, or null when it was counted already (failed, skipped). */
    private suspend fun readOne(row: GameToReadContent, folder: RomFolderEntity?): ReadResult? {
        val game = row.game
        if (folder == null || row.folderId in unavailableFolders) {
            skipped++
            return null
        }
        if (!permissions.isHeld(folder.treeUri)) {
            folderUnavailable(row, "access no longer held")
            return null
        }
        // A pack that ships but could not be installed: the answer would be the file name's.
        val pack = packReadyFor(game.systemId)
        if (pack == false) {
            skipped++
            return null
        }
        val tree = trees.getOrPut(folder.id) { openTree(folder) }
        val identification = try {
            WholeFileRead.readWhole(naming, tree, game)
        } catch (e: WholeFileNotRead) {
            if (e.folderGone) folderUnavailable(row, e.reason) else notRead(row, tree, e.reason)
            return null
        } catch (e: IOException) {
            notRead(row, tree, e.javaClass.simpleName)
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Not expected (the identifier turns bad bytes into notes); one file must not stop the pass.
            notRead(row, tree, e.javaClass.simpleName)
            return null
        }
        read++
        bytes += game.sizeBytes
        val fingerprints = identification.fingerprints
        if (fingerprints == null) {
            notRead(row, tree, "no fingerprints")
            return null
        }
        // The file changed after the scan listed it (the next scan names it again), or the pack
        // failed its lookups (damaged on disk; a later run tries again).
        if (fingerprints.size != game.sizeBytes || (pack == true && identification.packVersion == null)) {
            skipped++
            return null
        }
        return ReadResult(row, identification, FingerprintCodec.encode(fingerprints, game.systemId, contentReadDone = true))
    }

    /**
     * Writes a page's results in one transaction under [scanLock]. Should the database refuse the
     * batch (one row's write throws), the whole transaction is rolled back and each row is written
     * again in its own transaction, so one bad row costs only its own result. A cancel while
     * waiting for the lock or inside the transaction writes nothing (rolled back): the rows are
     * not stamped and the next run reads them again. When every row is refused alone as well, the
     * batch's exception is thrown: the database fails, and the pass stops.
     */
    private suspend fun writeBatch(batch: List<ReadResult>) {
        if (batch.isEmpty()) return
        try {
            scanLock.withLock { writeBatchHeld(batch) }
        } catch (e: CancellationException) {
            Log.w(LOG_TAG, "Content pass: ${batch.size} results not written (cancelled while writing); the next run reads them again")
            throw e
        }
    }

    /** [writeBatch] with [scanLock] already held. */
    private suspend fun writeBatchHeld(batch: List<ReadResult>) {
        val outcomes = try {
            database.withTransaction { batch.map { write(it.row, it.identification, it.fingerprint) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Content pass: a batch of ${batch.size} not written (${e.javaClass.simpleName}); writing its rows one at a time")
            // Every row refused too: the database itself fails (disk full, read-only), not one row.
            // That stops the pass, as before batching, instead of counting the page skipped.
            batch.map { writeAlone(it) }.also { alone -> if (alone.all { it == Written.NOT_WRITTEN }) throw e }
        }
        for (outcome in outcomes) {
            when (outcome) {
                Written.STALE, Written.NOT_WRITTEN -> skipped++
                Written.RENAMED -> { renamed++; written++ }
                Written.KEPT_NAME -> written++
            }
        }
    }

    /** Inside the scan lock: one row in its own transaction; a refused write is logged and left for the next run. */
    private suspend fun writeAlone(result: ReadResult): Written = try {
        database.withTransaction { write(result.row, result.identification, result.fingerprint) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Folder ${result.row.folderId}: game ${result.row.game.id} content result not written (${e.javaClass.simpleName})")
        Written.NOT_WRITTEN
    }

    /**
     * The pass stops mid-page (cancelled, or an unexpected error): the results read so far are
     * written before it goes, so a slice the worker stops does not lose them. A cancel writes only
     * when the scan lock is free at once ([Mutex.tryLock]) and in [NonCancellable]: one transaction
     * of at most a page of rows takes milliseconds, while waiting for a scan that holds the lock
     * could take minutes after the worker was told to stop. With the lock taken they are dropped
     * (logged): their rows are not stamped, so the next run reads them again. A failure here is
     * logged, never thrown over the exception that stopped the pass.
     */
    private suspend fun writeOnStop(batch: List<ReadResult>, cancelled: Boolean) {
        if (batch.isEmpty()) return
        withContext(NonCancellable) {
            try {
                when {
                    !cancelled -> writeBatch(batch)
                    scanLock.tryLock() -> try {
                        writeBatchHeld(batch)
                    } finally {
                        scanLock.unlock()
                    }
                    else -> Log.w(LOG_TAG, "Content pass: ${batch.size} results not written (the scan holds the lock); the next run reads them again")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "Content pass: ${batch.size} results not written on stop (${e.javaClass.simpleName})")
            }
        }
    }

    /** Inside the scan lock and one transaction. */
    private suspend fun write(row: GameToReadContent, identification: Identification, fingerprint: String): Written {
        val dao = database.identifyDao()
        val scrapedDao = database.scrapedGameDao()
        val game = row.game
        val before = dao.contentReadState(game.id) ?: return Written.STALE
        if (before.needsIdentify || before.sizeBytes != game.sizeBytes || before.modifiedAt != game.modifiedAt ||
            before.fileName != game.fileName || before.fingerprint != game.fingerprint
        ) {
            return Written.STALE
        }
        val oldKey = FixKeys.forGame(game.id, before.fixKeys).first()
        val newKey = FixKeys.forGame(game.id, FixKeys.join(identification.fixKeys)).first()
        val oldResult = if (oldKey != newKey) scrapedDao.get(oldKey) else null
        val written = NameWrite.write(database, listOf(NamedFile(game, identification, fingerprint))) { named ->
            val sameTitle = (named.shown?.title ?: before.title) == before.title
            val following = oldResult?.takeIf { it.isPersonChoice || (sameTitle && it.outcome == ScrapeOutcome.MATCHED) }
            when {
                following == null -> false
                newKey.startsWith(NameFixEntity.ROW_PREFIX) -> {
                    // The full read gave no key at all: nothing to carry the box art to.
                    Log.w(LOG_TAG, "Folder ${row.folderId}: game ${game.id} content read gave no key; box art left under its old key")
                    false
                }
                // A pick replaces an automatic answer stored for the same content (another copy's).
                scrapedDao.copyToKey(oldKey, newKey, replaceAutomatic = following.isPersonChoice) -> true
                else -> {
                    if (following.isPersonChoice) {
                        Log.w(LOG_TAG, "Folder ${row.folderId}: game ${game.id} pick not carried: its content already has one")
                    }
                    false
                }
            }
        }
        if (written.isEmpty()) return Written.STALE
        val after = dao.contentReadState(game.id) ?: return Written.STALE
        return if (after.title != before.title) Written.RENAMED else Written.KEPT_NAME
    }

    /** Counts a failed read in the row's stamp, unless the picked folder itself stopped answering. */
    private suspend fun notRead(row: GameToReadContent, tree: DocumentTree, cause: String) {
        val game = row.game
        if (tree.stat(tree.rootDocumentId) !is TreeResult.Found) return folderUnavailable(row, "folder not answering")
        failed++
        Log.w(LOG_TAG, "Folder ${row.folderId}: game ${game.id} content read failed ($cause)")
        val old = game.fingerprint ?: return
        val counted = FingerprintCodec.withContentReadFailure(old, MAX_FAILED_READS) ?: return
        scanLock.withLock { database.identifyDao().replaceCachedFingerprint(game.id, game.sizeBytes, game.modifiedAt, old, counted) }
    }

    /** No file of the folder is read this run, and nothing is counted against them. */
    private fun folderUnavailable(row: GameToReadContent, reason: String) {
        skipped++
        if (unavailableFolders.add(row.folderId)) Log.w(LOG_TAG, "Folder ${row.folderId}: content pass skips the folder ($reason)")
    }

    /** True: a pack is ready; false: one ships but could not be installed; null: the system has none. */
    private fun packReadyFor(systemId: String): Boolean? = packReady.getOrPut(systemId) {
        when {
            naming.packs.packFor(systemId) != null -> true
            naming.packs.hasBundled(systemId) -> false
            else -> null
        }
    }

    companion object {
        /** Rows per page; one page is read from the database at a time. */
        private const val PAGE_SIZE = 50

        /** Failed reads of one file (its size and time unchanged) before the pass gives up on it. */
        const val MAX_FAILED_READS = 3
    }
}
