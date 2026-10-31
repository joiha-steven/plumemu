package com.plumemu.graph

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The document Android's save picker made for a backup (`ACTION_CREATE_DOCUMENT`): written through
 * the [resolver], deleted again after a cancel or a failure. No permission is needed: the picker
 * grants this one document.
 */
internal class BackupDocument(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val log: (String) -> Unit,
) : BackupOutput {
    override val id: String get() = uri.toString()

    /**
     * Keeps write access past a restart, so an interrupted backup's file can still be deleted at the
     * next start. The picker grants it persistably; no permission is declared for it.
     */
    override fun keep(): Boolean =
        try {
            resolver.takePersistableUriPermission(uri, ACCESS)
            true
        } catch (e: SecurityException) {
            log("Backup: the picked document gave no lasting access (${e.javaClass.simpleName})")
            false
        }

    override fun release() {
        try {
            resolver.releasePersistableUriPermission(uri, ACCESS)
        } catch (e: SecurityException) {
            // Never kept (the provider did not offer it) or already gone with the document.
            log("Backup: no lasting access to release (${e.javaClass.simpleName})")
        }
    }

    override fun open(): OutputStream = resolver.openOutputStream(uri, "w") ?: throw IOException("the picked document gave no stream")

    override fun read(): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException("the picked document gave no stream to read")

    override fun delete(): Boolean = DocumentsContract.deleteDocument(resolver, uri)

    override fun name(): String? =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            // Only the done line's name: it then says "Backup saved" with the size alone.
            log("Backup: the saved file's name could not be read (${e.javaClass.simpleName})")
            null
        }

    private companion object {
        const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
