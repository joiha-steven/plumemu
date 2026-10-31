package com.plumemu.library.scan

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * The real [DocumentTree]: child queries through `DocumentsContract` on a picked tree, asking only
 * for id, name, size, modified time and type. Not `DocumentFile`, which queries once per column
 * per file and is many times slower on large folders.
 */
class ContentResolverDocumentTree(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : DocumentTree {
    override val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun stat(documentId: String): TreeResult<DocumentEntry> =
        query(documentId, DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)) { cursor ->
            if (cursor.moveToFirst()) TreeResult.Found(cursor.entry()) else missingOrFailed(documentId)
        }

    override fun listChildren(documentId: String): TreeResult<List<DocumentEntry>> =
        query(documentId, DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)) { cursor ->
            val entries = ArrayList<DocumentEntry>(cursor.count)
            while (cursor.moveToNext()) entries += cursor.entry()
            TreeResult.Found(entries)
        }

    override fun open(documentId: String): TreeResult<OpenedFile> = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val descriptor = resolver.openFileDescriptor(uri, "r")
        if (descriptor == null) {
            TreeResult.Failed(IOException("Provider gave no file descriptor"))
        } else {
            TreeResult.Found(DescriptorFile(descriptor))
        }
    } catch (e: SecurityException) {
        TreeResult.PermissionDenied(e)
    } catch (e: IOException) {
        // FileNotFoundException carries the document URI in its message: the class only.
        Log.w(LOG_TAG, "Provider ${treeUri.authority} could not open a file (${e.javaClass.simpleName})")
        TreeResult.Failed(e)
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Provider ${treeUri.authority} failed to open a file (${e.javaClass.simpleName})")
        TreeResult.Failed(e)
    }

    private class DescriptorFile(private val descriptor: ParcelFileDescriptor) : OpenedFile {
        private val stream = FileInputStream(descriptor.fileDescriptor)
        override val channel: FileChannel = stream.channel

        override fun close() {
            try {
                stream.close()
            } finally {
                descriptor.close()
            }
        }
    }

    private fun <T> query(documentId: String, uri: Uri, read: (Cursor) -> TreeResult<T>): TreeResult<T> = try {
        // DocumentsProvider.query answers null when the document is not found, but also when the
        // provider failed: only the picked folder itself is taken as gone (missingOrFailed).
        resolver.query(uri, PROJECTION, null, null, null)?.use(read) ?: missingOrFailed(documentId)
    } catch (e: SecurityException) {
        TreeResult.PermissionDenied(e)
    } catch (e: RuntimeException) {
        // Providers throw many kinds here: IllegalArgumentException for an id that no longer
        // resolves, NullPointerException on Android 6 when the SD card was just pulled, their own
        // exceptions, or a row with a null id. One bad directory must not end the whole scan.
        // The class only: a provider's message holds the document URI, so file and folder names.
        Log.w(LOG_TAG, "Provider ${treeUri.authority} failed on a query (${e.javaClass.simpleName})")
        TreeResult.Failed(e)
    }

    /**
     * No answer for a document: the picked folder itself is gone (deleted, SD card out); for any
     * other document the provider failed, which must never read as "deleted" and empty a folder.
     */
    private fun <T> missingOrFailed(documentId: String): TreeResult<T> =
        if (documentId == rootDocumentId) {
            TreeResult.Missing
        } else {
            TreeResult.Failed(IllegalStateException("Provider gave no answer for a document"))
        }

    private fun Cursor.entry(): DocumentEntry {
        val mime = getString(COL_MIME)
        return DocumentEntry(
            documentId = checkNotNull(getString(COL_ID)) { "Provider returned a row without a document id" },
            name = getString(COL_NAME) ?: "",
            sizeBytes = if (isNull(COL_SIZE)) 0 else getLong(COL_SIZE),
            lastModified = if (isNull(COL_MODIFIED)) 0 else getLong(COL_MODIFIED),
            isDirectory = mime == Document.MIME_TYPE_DIR,
        )
    }

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
        )
        const val COL_ID = 0
        const val COL_NAME = 1
        const val COL_SIZE = 2
        const val COL_MODIFIED = 3
        const val COL_MIME = 4
    }
}
