package com.plumemu.launcher

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A folder with one game, served the way a USB drive's provider would: document ids are not paths,
 * so only emulators that take a URI can open it, through the read grant of the launch.
 */
class FakeDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: ROOT_COLUMNS).apply {
        newRow().add(Root.COLUMN_ROOT_ID, ROOT).add(Root.COLUMN_DOCUMENT_ID, ROOT).add(Root.COLUMN_TITLE, "Fake drive")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { addDocument(this, documentId) }

    override fun queryChildDocuments(parentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { if (parentId == ROOT) addDocument(this, GAME) }

    override fun isChildDocument(parentId: String, documentId: String) = parentId == ROOT && documentId == GAME

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (documentId != GAME) throw FileNotFoundException(documentId)
        val file = File(checkNotNull(context).cacheDir, "fake-game.bin").apply { writeText(GAME_CONTENT) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun addDocument(cursor: MatrixCursor, documentId: String) {
        val row = cursor.newRow()
        when (documentId) {
            ROOT -> row.add(Document.COLUMN_DOCUMENT_ID, ROOT).add(Document.COLUMN_DISPLAY_NAME, "ROMs")
                .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            GAME -> row.add(Document.COLUMN_DOCUMENT_ID, GAME).add(Document.COLUMN_DISPLAY_NAME, GAME_NAME)
                .add(Document.COLUMN_MIME_TYPE, "application/octet-stream").add(Document.COLUMN_SIZE, GAME_CONTENT.length)
            else -> throw IllegalArgumentException("no document", FileNotFoundException(documentId))
        }
    }

    companion object {
        const val AUTHORITY = "com.plumemu.launcher.test.documents"
        const val ROOT = "root"
        const val GAME = "game-1"
        const val GAME_NAME = "Ridge Racer (USA).bin"
        const val GAME_CONTENT = "PLUMEMU-FAKE-ROM"
        private val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
        )
    }
}
