package com.plumemu.launcher.intent

import android.net.Uri
import android.provider.DocumentsContract
import com.plumemu.coredata.db.GameLaunchTarget
import com.plumemu.library.storage.StorageRoots
import com.plumemu.platforms.GameLocation

/** Where a game in the library is, in the form the platforms module builds launch specs from. */
internal object GameLocations {
    /** The document URI always; the real path too when the picked folder has one ([DocumentPaths]). */
    fun of(target: GameLaunchTarget, roots: StorageRoots): GameLocation {
        val tree = Uri.parse(target.treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, target.documentId).toString()
        val path = DocumentPaths.realPath(tree.authority, target.documentId, roots)
        return if (path == null) GameLocation.Uri(documentUri) else GameLocation.Both(path, documentUri)
    }

    fun documentUriOf(location: GameLocation): String? = when (location) {
        is GameLocation.Uri -> location.uri
        is GameLocation.Both -> location.uri
        is GameLocation.Path -> null
    }
}
