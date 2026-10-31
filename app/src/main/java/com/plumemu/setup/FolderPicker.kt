package com.plumemu.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import com.plumemu.library.storage.ExternalStorageIds

/*
 * Android's own folder picker (no storage permission). The tree it returns carries a
 * persistable read grant, which `RomFolders.addPickedFolder` keeps.
 */

/** The authority of the device's own storage and SD cards (`ExternalStorageProvider`). */
internal const val EXTERNAL_STORAGE_AUTHORITY = ExternalStorageIds.AUTHORITY

private const val ROMS_FOLDER = "ROMs"

/**
 * `DocumentsContract.EXTRA_SHOW_ADVANCED`, a constant hidden from the SDK: asks the picker to list
 * the internal storage in its drawer, which Android 6's picker otherwise hides on a first pick.
 * Later versions of DocumentsUI still read it; where the drawer shows the storage anyway it
 * changes nothing, so sending it is harmless.
 */
private const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"

/**
 * `ACTION_OPEN_DOCUMENT_TREE`, opened at the primary volume's `ROMs` on Android 8+ (androidx puts
 * the input in `EXTRA_INITIAL_URI` from API 26; on 6 and 7 the picker opens where it opens).
 */
internal class OpenRomsFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(
        context: Context,
        input: Uri?,
    ): Intent = super.createIntent(context, input).putExtra(EXTRA_SHOW_ADVANCED, true)
}

/** Where the picker opens: `/ROMs` on the internal storage (the picker falls back if it is missing). */
internal fun romsInitialUri(): Uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "${ExternalStorageIds.PRIMARY_ROOT}:$ROMS_FOLDER")

/** Where a picked tree lives, read from its URI alone (no provider call). */
internal sealed interface FolderPlace {
    /** On the device's own storage (the "Documents" shortcut too); [path] below its root, empty for the root itself. */
    data class Internal(val path: List<String>) : FolderPlace

    /** On a removable volume (SD card). */
    data class Removable(val path: List<String>) : FolderPlace

    /** Another provider: only the stored display name says what it is. */
    data object Other : FolderPlace
}

/**
 * `content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fsnes` →
 * `Internal(["ROMs", "snes"])`; the "Documents" shortcut (`home:ROMs`) → `Internal(["Documents", "ROMs"])`;
 * a volume id (`1234-5678:Games`, an NTFS or ext4 id) → `Removable`. Read by library's [ExternalStorageIds];
 * an id it refuses is `Other`.
 */
internal fun folderPlaceOf(treeUri: String): FolderPlace {
    val document = ExternalStorageIds.treeDocument(treeUri) ?: return FolderPlace.Other
    return document.internalPath?.let { FolderPlace.Internal(it) } ?: FolderPlace.Removable(document.path)
}

/**
 * The name to store for a picked folder: its last path segment, or empty when the URI does not say.
 * A storage-provider tree whose root [ExternalStorageIds] does not know (`secondary:ROMs`) still
 * names the part after the colon, as before the shared reader.
 */
internal fun folderNameOf(treeUri: String): String =
    when (val place = folderPlaceOf(treeUri)) {
        is FolderPlace.Internal -> place.path.lastOrNull().orEmpty()
        is FolderPlace.Removable -> place.path.lastOrNull().orEmpty()
        FolderPlace.Other -> ExternalStorageIds.treeDocumentId(treeUri)
            ?.substringAfter(':', "")
            ?.split('/')
            ?.lastOrNull { it.isNotEmpty() }
            .orEmpty()
    }
