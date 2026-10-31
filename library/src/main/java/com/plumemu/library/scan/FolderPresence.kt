package com.plumemu.library.scan

import android.os.Environment
import com.plumemu.library.storage.ExternalStorageIds
import com.plumemu.library.storage.StorageRoot
import java.io.File

/**
 * Whether a picked folder that no longer answers was deleted, as opposed to being on an SD card
 * taken out or behind a revoked grant. A folder restored from another device's backup counts as
 * deleted when this device has no folder at that path (accepted: its files are not here). Android revokes
 * a folder's grant when the folder is deleted, so neither a missing grant nor a missing root is
 * proof by itself. [LibraryScanner] forgets a folder only when this answers true; when in doubt
 * it answers false and the folder's games stay.
 */
fun interface FolderPresence {
    fun isProvenGone(treeUri: String): Boolean

    companion object {
        /** Proves nothing: every unavailable folder keeps its games. */
        val NEVER_PROVEN = FolderPresence { false }
    }
}

/**
 * [FolderPresence] for folders of Android's own storage provider, whose tree document id is
 * `<volume>:<relative/path>` (`primary` is internal storage, an SD card goes by its uuid such as
 * `6AD6-712E`). Proof is the scan's rule for subdirectories: a deleted folder is missing from its
 * parent's successful listing. The volume's root must be a listable directory (a card taken out
 * has none), then each path component is looked up in its parent's listing; any listing that
 * fails (no storage permission on Android 6 to 9, scoped storage refusing) proves nothing.
 *
 * [volumeRoot] maps a volume id to its root directory (null when unknown); [listNames] lists a
 * directory's names, null when it cannot. Both are parameters so the logic runs in JVM tests.
 */
class ExternalStorageFolderPresence(
    private val volumeRoot: (String) -> File? = ::deviceVolumeRoot,
    private val listNames: (File) -> Array<String>? = { it.list() },
) : FolderPresence {
    override fun isProvenGone(treeUri: String): Boolean {
        // A malformed id, or one with `.` or `..` in it, proves nothing.
        val document = ExternalStorageIds.treeDocument(treeUri) ?: return false
        val volume = when (val root = document.root) {
            StorageRoot.Internal -> ExternalStorageIds.PRIMARY_ROOT
            is StorageRoot.Volume -> root.id
            // The "Documents" shortcut is never proven gone: forgetting a folder
            // drops its games' marks, so this proof stays with the roots it was reviewed for.
            StorageRoot.Home -> return false
        }
        val components = document.path
        // The whole volume picked: its root not answering is a card taken out, never a deletion.
        if (components.isEmpty()) return false
        // Android 11+ hides other apps' folders under Android/ from listings: missing there proves nothing.
        if (components.first().equals("Android", ignoreCase = true)) return false
        var parent = volumeRoot(volume) ?: return false
        if (!parent.isDirectory || listNames(parent) == null) return false
        for (component in components) {
            val names = listNames(parent) ?: return false
            if (component !in names) {
                // FAT and exFAT cards match names without case: a folder that still opens is not gone.
                return !File(parent, component).exists()
            }
            parent = File(parent, component)
        }
        return false
    }

    companion object {
        const val AUTHORITY = ExternalStorageIds.AUTHORITY

        // Only a mounted volume: a card being ejected can leave its mount point behind as an empty
        // directory, whose listing would lack every folder.
        private fun deviceVolumeRoot(volume: String): File? {
            val root = if (volume == ExternalStorageIds.PRIMARY_ROOT) Environment.getExternalStorageDirectory() else File("/storage", volume)
            val state = Environment.getExternalStorageState(root)
            return root.takeIf { state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY }
        }
    }
}
