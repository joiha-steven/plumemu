package com.plumemu.launcher

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException

/** What a cheap question to the provider says about a game's file right before it starts. */
internal sealed interface DocumentState {
    /** [mimeType] is what the provider reports, for emulators whose data leaves the type to the file. */
    data class Present(val mimeType: String?) : DocumentState

    /** The file is gone from a folder that still answers. */
    data object Missing : DocumentState

    /**
     * The provider refused (folder access revoked) or the picked folder itself does not answer (SD
     * card or drive removed). [error] is for the outcome; only its class may be logged.
     */
    data class Unreachable(val error: Throwable) : DocumentState
}

internal fun interface DocumentProbe {
    /** [documentUri] is a tree-based document URI. Blocking; call off the main thread. */
    fun probe(documentUri: String): DocumentState
}

/**
 * One single-row query with two columns; never opens the file. A file that seems missing gets a
 * second query for the picked folder's own document, because a removed SD card or drive looks just
 * like a missing file to the first query (ExternalStorageProvider: "No root for ...", other
 * providers: no row) and the message must say "card removed", not "file deleted".
 */
internal class ContentResolverDocumentProbe(private val resolver: ContentResolver) : DocumentProbe {
    override fun probe(documentUri: String): DocumentState {
        val uri = Uri.parse(documentUri)
        val file = query(uri)
        if (file != DocumentState.Missing) return file
        val root = try {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } catch (e: IllegalArgumentException) {
            return DocumentState.Unreachable(e)
        }
        return when (val folder = query(root)) {
            is DocumentState.Present -> DocumentState.Missing
            DocumentState.Missing -> DocumentState.Unreachable(FileNotFoundException("the picked folder does not answer"))
            is DocumentState.Unreachable -> folder
        }
    }

    private fun query(uri: Uri): DocumentState = try {
        resolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) DocumentState.Present(cursor.getString(1)) else DocumentState.Missing
        } ?: DocumentState.Missing
    } catch (e: SecurityException) {
        DocumentState.Unreachable(e)
    } catch (e: IllegalArgumentException) {
        // ExternalStorageProvider reports a missing file, or a missing root, this way.
        if (e.cause is FileNotFoundException) DocumentState.Missing else DocumentState.Unreachable(e)
    } catch (e: IllegalStateException) {
        DocumentState.Unreachable(e)
    } catch (e: UnsupportedOperationException) {
        DocumentState.Unreachable(e)
    }

    private companion object {
        val COLUMNS = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
    }
}
