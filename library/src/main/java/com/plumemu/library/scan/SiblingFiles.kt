package com.plumemu.library.scan

import com.plumemu.identify.source.FileSource
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import java.io.IOException

/**
 * Opens the files a `.cue`, `.gdi` or `.m3u` names, through the documents provider: a name is
 * looked up in the listing of the sheet's directory (exact name first, then ignoring case, as
 * sheets made on Windows often differ in case), following at most [MAX_DEPTH] folder levels
 * below it; `..` is never followed. The last few listings are kept, so the cue sheets of one
 * directory list it once. Safe to share between threads.
 */
internal class SiblingFiles(private val tree: DocumentTree) {
    private val listings = object : LinkedHashMap<String, List<DocumentEntry>>(CACHED_LISTINGS, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DocumentEntry>>?) = size > CACHED_LISTINGS
    }

    fun openerFor(parentDocumentId: String) = SiblingOpener { name -> open(parentDocumentId, name) }

    private fun open(parentDocumentId: String, name: String): RandomAccessSource? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.size > MAX_DEPTH + 1 || ".." in parts) return null
        var directoryId = parentDocumentId
        for ((index, part) in parts.withIndex()) {
            val children = childrenOf(directoryId)
            val entry = children.firstOrNull { it.name == part } ?: children.firstOrNull { it.name.equals(part, ignoreCase = true) }
                ?: return null
            if (index < parts.lastIndex) {
                if (!entry.isDirectory) return null
                directoryId = entry.documentId
            } else {
                return if (entry.isDirectory) null else openFile(entry.documentId)
            }
        }
        return null
    }

    // Locked: the naming pass's readers share one instance, and an access-ordered map changes on every read.
    private fun childrenOf(directoryId: String): List<DocumentEntry> = synchronized(listings) { listingOf(directoryId) }

    private fun listingOf(directoryId: String): List<DocumentEntry> = listings.getOrPut(directoryId) {
        when (val result = tree.listChildren(directoryId)) {
            is TreeResult.Found -> result.value
            is TreeResult.PermissionDenied -> throw IOException("sibling listing denied (${result.cause.javaClass.simpleName})")
            is TreeResult.Failed -> throw IOException("sibling listing failed (${result.cause.javaClass.simpleName})")
            TreeResult.Missing -> throw IOException("sibling directory missing")
        }
    }

    private fun openFile(documentId: String): RandomAccessSource = when (val result = tree.open(documentId)) {
        is TreeResult.Found -> OpenedSource(result.value)
        is TreeResult.PermissionDenied -> throw IOException("sibling open denied (${result.cause.javaClass.simpleName})")
        is TreeResult.Failed -> throw IOException("sibling open failed (${result.cause.javaClass.simpleName})")
        TreeResult.Missing -> throw IOException("sibling missing")
    }

    /** A sibling as a source; closing it closes the descriptor as well. */
    private class OpenedSource(private val file: OpenedFile) : RandomAccessSource {
        private val source = try {
            FileSource(file.channel)
        } catch (e: IOException) {
            file.close()
            throw e
        }

        override val size: Long get() = source.size

        override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int = source.readAt(position, dst, offset, length)

        override fun close() {
            try {
                source.close()
            } finally {
                file.close()
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 2
        const val CACHED_LISTINGS = 4
        const val LOAD_FACTOR = 0.75f
    }
}
