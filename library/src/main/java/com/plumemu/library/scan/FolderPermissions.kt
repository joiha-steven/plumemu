package com.plumemu.library.scan

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log

/** The lasting read grants Plumemu holds on picked folders. */
interface FolderPermissions {
    /** Keeps the grant the picker just gave across restarts; false when Android refuses. */
    fun take(treeUri: String): Boolean

    fun isHeld(treeUri: String): Boolean

    fun release(treeUri: String)
}

/** [FolderPermissions] through `ContentResolver`'s persisted URI permissions. */
class AndroidFolderPermissions(private val resolver: ContentResolver) : FolderPermissions {
    override fun take(treeUri: String): Boolean = try {
        resolver.takePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        // The picker result carried no persistable grant (wrong intent flags, or a provider that
        // does not offer one); the folder cannot be kept.
        Log.w(LOG_TAG, "Could not keep access to picked folder ${Uri.parse(treeUri).authority} (${e.javaClass.simpleName})")
        false
    }

    override fun isHeld(treeUri: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission }

    override fun release(treeUri: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already gone (revoked in Android's settings): nothing left to release.
            Log.i(LOG_TAG, "Folder access was already released (${e.javaClass.simpleName})")
        }
    }
}

internal const val LOG_TAG = "PlumLibrary"
