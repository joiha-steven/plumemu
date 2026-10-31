package com.plumemu.library.scan

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real tree against a provider that misbehaves the ways providers do on devices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentResolverDocumentTreeTest {
    private val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
    private lateinit var tree: ContentResolverDocumentTree

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(MisbehavingProvider::class.java, AUTHORITY)
        tree = ContentResolverDocumentTree(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, treeUri)
    }

    @Test
    fun listsChildrenWithTheirColumns() {
        MisbehavingProvider.answer = { cursorOf(arrayOf("root/a.sfc", "a.sfc", 7L, 9L, "application/octet-stream")) }
        assertEquals(TreeResult.Found(listOf(DocumentEntry("root/a.sfc", "a.sfc", 7, 9, isDirectory = false))), tree.listChildren("root"))
    }

    @Test
    fun noAnswerIsMissingOnlyForThePickedFolder() {
        MisbehavingProvider.answer = { null }
        assertEquals(TreeResult.Missing, tree.listChildren("root"))
        assertTrue(tree.listChildren("root/snes") is TreeResult.Failed)
        assertTrue(tree.stat("root/snes") is TreeResult.Failed)
    }

    @Test
    fun providerCrashIsAFailureNotACrash() {
        MisbehavingProvider.answer = { throw NullPointerException("card pulled") }
        assertTrue(tree.listChildren("root/snes") is TreeResult.Failed)
    }

    @Test
    fun rowWithoutADocumentIdIsAFailure() {
        MisbehavingProvider.answer = { cursorOf(arrayOf(null, "a.sfc", 1L, 1L, "application/octet-stream")) }
        assertTrue(tree.listChildren("root") is TreeResult.Failed)
    }

    @Test
    fun securityExceptionIsPermissionDenied() {
        MisbehavingProvider.answer = { throw SecurityException("revoked") }
        assertTrue(tree.stat("root") is TreeResult.PermissionDenied)
    }

    private fun cursorOf(vararg rows: Array<Any?>): Cursor = MatrixCursor(
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_MIME_TYPE),
    ).apply { rows.forEach { addRow(it) } }

    class MisbehavingProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
            answer()

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        companion object {
            var answer: () -> Cursor? = { null }
        }
    }

    private companion object {
        const val AUTHORITY = "test.documents"
    }
}
