package com.plumemu.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A provider whose answer the test picks by the last path segment of the queried URI. */
class ScriptedDocumentsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
        when (uri.lastPathSegment) {
            "present" -> MatrixCursor(projection).apply { addRow(arrayOf("present", "application/zip")) }
            "root-ok" -> MatrixCursor(projection).apply { addRow(arrayOf("root-ok", DocumentsContract.Document.MIME_TYPE_DIR)) }
            // What ExternalStorageProvider says when the SD card or drive behind the root is gone.
            "root-gone" -> throw IllegalArgumentException("Failed to determine", FileNotFoundException("No root for root-gone"))
            "empty" -> MatrixCursor(projection)
            "null" -> null
            "gone" -> throw IllegalArgumentException("Failed to determine", FileNotFoundException("Missing file"))
            "revoked" -> throw SecurityException("Permission Denial")
            else -> throw IllegalArgumentException("broken provider")
        }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentProbeTest {
    private val authority = "com.plumemu.test.documents"

    private fun probe(documentId: String, root: String = "root-ok"): DocumentState {
        Robolectric.setupContentProvider(ScriptedDocumentsProvider::class.java, authority)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, root)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString()
        return ContentResolverDocumentProbe(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver).probe(uri)
    }

    @Test
    fun aPresentFileGivesItsMimeType() = assertEquals(DocumentState.Present("application/zip"), probe("present"))

    @Test
    fun noRowOrNoCursorMeansMissing() {
        assertEquals(DocumentState.Missing, probe("empty"))
        assertEquals(DocumentState.Missing, probe("null"))
    }

    @Test
    fun theStorageProvidersWayOfSayingGoneIsMissing() = assertEquals(DocumentState.Missing, probe("gone"))

    @Test
    fun aGoneFileOnARemovedCardIsUnreachableNotGone() {
        assertTrue(probe("gone", root = "root-gone") is DocumentState.Unreachable)
        assertTrue(probe("null", root = "root-gone") is DocumentState.Unreachable)
        assertTrue(probe("empty", root = "root-gone") is DocumentState.Unreachable)
    }

    @Test
    fun revokedAccessAndBrokenProvidersAreUnreachable() {
        assertTrue(probe("revoked") is DocumentState.Unreachable)
        assertTrue(probe("broken") is DocumentState.Unreachable)
    }
}
