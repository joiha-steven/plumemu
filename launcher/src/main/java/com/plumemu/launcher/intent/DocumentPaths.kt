package com.plumemu.launcher.intent

import com.plumemu.library.storage.ExternalStorageIds
import com.plumemu.library.storage.StorageRoots

/**
 * The real file path of a document in a picked folder, for emulators that open the file
 * themselves. Only Android's own storage provider has ids that are paths (read by library's
 * [ExternalStorageIds]): `primary:` is this user's internal storage, `home:` its `Documents`
 * folder, a volume id (`1A2B-3C4D`, an NTFS or ext4 id) an SD card or a USB drive the system
 * mounted. Where each is on the device comes from [StorageRoots]. Every other provider (USB through
 * a vendor app, cloud, Downloads) has no path; those games work only with emulators that take a
 * document URI.
 */
object DocumentPaths {
    const val EXTERNAL_STORAGE_AUTHORITY = ExternalStorageIds.AUTHORITY

    /** The path of [documentId] from the provider [authority], or null when it has none. */
    fun realPath(authority: String?, documentId: String, roots: StorageRoots): String? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY) return null
        return ExternalStorageIds.parse(documentId)?.realPath(roots)
    }
}
