package com.plumemu.library.scan

import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanDao

/**
 * What [LibraryScanner.changedFolders] found: the picked folders a rescan would list something in,
 * and the folders that could not be asked (SD card out, grant revoked, provider failing). Folders
 * in neither are unchanged since their last finished scan.
 */
data class FolderChanges(
    val changedFolderIds: Set<Long>,
    val unavailable: Map<Long, UnavailableReason>,
    /** Folders asked, and single-row queries made: what the check cost. */
    val foldersChecked: Int,
    val directoriesAsked: Int,
    /** Changed folders that had no directory records (a first scan): their games are not news. */
    val neverScannedFolderIds: Set<Long> = emptySet(),
) {
    val hasChanges: Boolean get() = changedFolderIds.isNotEmpty()

    companion object {
        val NONE = FolderChanges(emptySet(), emptyMap(), foldersChecked = 0, directoriesAsked = 0)
    }
}

/** One folder's answer to the change check. */
internal sealed interface FolderChangeState {
    data object Unchanged : FolderChangeState

    data object Changed : FolderChangeState

    /** No directory records yet (never scanned to the end, or the rules changed): a scan walks it all. */
    data object NeverScanned : FolderChangeState

    data class Unavailable(val reason: UnavailableReason) : FolderChangeState
}

/**
 * Asks one picked folder whether a rescan would list anything, with the same comparison the rescan
 * makes ([FolderScan.isUnchanged]) over the same directory records, in the same order (the folder
 * first, then by depth), and stops at the first directory that changed. Lists nothing and opens
 * nothing; a directory's own time changes when a child is added, removed or renamed in it.
 *
 * - No records (never scanned to the end, or the rules changed): changed, a scan walks it all.
 * - A recorded directory that does not answer: changed, so a rescan retries it as it would.
 * - The picked folder does not answer, or access is refused: unavailable, nothing to scan.
 */
internal class FolderChangeCheck(
    private val folder: RomFolderEntity,
    private val tree: DocumentTree,
    private val dao: ScanDao,
) {
    /** Single-row queries made so far. */
    var directoriesAsked = 0
        private set

    suspend fun run(): FolderChangeState {
        directoriesAsked++
        val root = when (val result = tree.stat(tree.rootDocumentId)) {
            is TreeResult.Found -> result.value
            else -> return FolderChangeState.Unavailable(reasonOf(result))
        }
        val known = dao.directories(folder.id)
        if (known.isEmpty()) return FolderChangeState.NeverScanned
        for (record in known.sortedBy { FolderScan.depth(it.relativePath) }) {
            val entry = if (record.documentId == root.documentId) {
                root
            } else {
                directoriesAsked++
                when (val result = tree.stat(record.documentId)) {
                    is TreeResult.Found -> result.value
                    is TreeResult.PermissionDenied -> return FolderChangeState.Unavailable(UnavailableReason.PERMISSION_REVOKED)
                    else -> return FolderChangeState.Changed
                }
            }
            if (!FolderScan.isUnchanged(entry, record)) return FolderChangeState.Changed
        }
        return FolderChangeState.Unchanged
    }

    private fun reasonOf(result: TreeResult<*>): UnavailableReason =
        when (result) {
            is TreeResult.PermissionDenied -> UnavailableReason.PERMISSION_REVOKED
            is TreeResult.Failed -> UnavailableReason.READ_FAILED
            else -> UnavailableReason.FOLDER_MISSING
        }
}
