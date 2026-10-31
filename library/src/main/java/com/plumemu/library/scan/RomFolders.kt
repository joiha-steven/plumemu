package com.plumemu.library.scan

import android.util.Log
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanDao

sealed interface AddFolderResult {
    data class Added(val folderId: Long) : AddFolderResult

    data class AlreadyAdded(val folderId: Long) : AddFolderResult

    /** Android gave no lasting access to the folder; ask the person to pick it again. */
    data object PermissionNotGranted : AddFolderResult

    /** The database did not store the folder (see the log); the grant taken for it was released. */
    data object NotRecorded : AddFolderResult
}

/**
 * The picked ROM folders. The folder picker's result goes to [addPickedFolder], which
 * keeps Android's grant and records the folder; a scan then reads it.
 */
class RomFolders(
    private val scanDao: ScanDao,
    private val permissions: FolderPermissions,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun addPickedFolder(treeUri: String, displayName: String): AddFolderResult {
        if (!permissions.take(treeUri)) return AddFolderResult.PermissionNotGranted
        scanDao.folderByTreeUri(treeUri)?.let { return AddFolderResult.AlreadyAdded(it.id) }
        val id = scanDao.insertFolder(RomFolderEntity(treeUri = treeUri, displayName = displayName, addedAt = clock()))
        if (id == -1L) {
            // Another call recorded the same folder between the lookup and the insert.
            val existing = scanDao.folderByTreeUri(treeUri)
            if (existing != null) return AddFolderResult.AlreadyAdded(existing.id)
            Log.e(LOG_TAG, "Picked folder could not be recorded and is not in the database; releasing its grant")
            permissions.release(treeUri)
            return AddFolderResult.NotRecorded
        }
        Log.i(LOG_TAG, "Folder $id added")
        return AddFolderResult.Added(id)
    }

    /** Forgets a folder: its games, play history and per-game choices go, and the grant is released. */
    suspend fun removeFolder(folder: RomFolderEntity) {
        scanDao.deleteFolder(folder.id)
        permissions.release(folder.treeUri)
    }
}
