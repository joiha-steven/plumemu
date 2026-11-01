package com.plumemu.graph

import com.plumemu.achievements.hash.RaRomSource
import com.plumemu.library.scan.DocumentEntry
import com.plumemu.library.scan.DocumentTree
import com.plumemu.library.scan.OpenedFile
import com.plumemu.library.scan.TreeResult
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels

/**
 * A library file read through a plain path (the device's own storage, an SD card Android mounted):
 * [key] is the real path, so the Arcade hash finds its parent folder's name. The size and the
 * modified time are read once, when it is made (on the IO dispatcher).
 */
internal class FileRaRomSource(private val file: File) : RaRomSource {
    override val key: String = file.path
    override val fileName: String = file.name
    override val sizeBytes: Long = file.length()
    val lastModifiedMillis: Long = file.lastModified()

    override fun open(): InputStream = FileInputStream(file)

    override fun sibling(name: String): RaRomSource? {
        val parts = relativeParts(name) ?: return null
        var directory = file.parentFile ?: return null
        for ((index, part) in parts.withIndex()) {
            // The exact name first, one stat; the folder is listed only for a name in another case.
            val entry = File(directory, part).takeIf { it.exists() }
                ?: directory.listFiles()?.toList()?.let { children -> pick(children, part, File::getName) }
                ?: return null
            if (index < parts.lastIndex) {
                if (!entry.isDirectory) return null
                directory = entry
            } else {
                return if (entry.isFile) FileRaRomSource(entry) else null
            }
        }
        return null
    }
}

/**
 * A library file read through the documents provider (a picked folder with no real path, or one
 * Plumemu cannot read as a file). [key] is the real path when the provider has one, else the
 * document URI. [sibling] walks the folder that holds the file, by name. A source and the siblings
 * it gives share [listings]: one hash lists each folder once, however many tracks a cue names.
 */
internal class DocumentRaRomSource(
    private val tree: DocumentTree,
    private val documentId: String,
    private val parentDocumentId: String,
    override val key: String,
    override val fileName: String,
    override val sizeBytes: Long,
    private val listings: MutableMap<String, List<DocumentEntry>> = java.util.concurrent.ConcurrentHashMap(),
) : RaRomSource {
    override fun open(): InputStream = when (val opened = tree.open(documentId)) {
        is TreeResult.Found -> ClosingStream(opened.value)
        // No document URI in the messages: it names the person's files.
        TreeResult.Missing -> throw IOException("the folder is gone")
        is TreeResult.PermissionDenied -> throw IOException("folder access revoked (${opened.cause.javaClass.simpleName})")
        is TreeResult.Failed -> throw IOException("the provider could not open it (${opened.cause.javaClass.simpleName})")
    }

    override fun sibling(name: String): RaRomSource? {
        val parts = relativeParts(name) ?: return null
        var directoryId = parentDocumentId
        for ((index, part) in parts.withIndex()) {
            val children = listingOf(directoryId) ?: return null
            val entry = pick(children, part, DocumentEntry::name) ?: return null
            if (index < parts.lastIndex) {
                if (!entry.isDirectory) return null
                directoryId = entry.documentId
            } else {
                if (entry.isDirectory) return null
                // Only Arcade reads the key's folder, and Arcade never asks for a sibling.
                val siblingKey = key.substringBeforeLast('/', missingDelimiterValue = "") + "/" + parts.joinToString("/")
                return DocumentRaRomSource(tree, entry.documentId, directoryId, siblingKey, entry.name, entry.sizeBytes, listings)
            }
        }
        return null
    }

    /** The children of [directoryId], listed once per hash; null when the provider cannot list it (not kept). */
    private fun listingOf(directoryId: String): List<DocumentEntry>? =
        listings[directoryId] ?: (tree.listChildren(directoryId) as? TreeResult.Found)?.value?.also { listings[directoryId] = it }

    /** Reads from the start of the file; closing it closes the descriptor. */
    private class ClosingStream(private val file: OpenedFile) : FilterInputStream(Channels.newInputStream(file.channel.position(0))) {
        override fun close() {
            try {
                super.close()
            } finally {
                file.close()
            }
        }
    }
}

/** [name]'s folder steps, or null for an empty name or one with `..` (the hasher refuses those first). */
private fun relativeParts(name: String): List<String>? {
    val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
    return parts.takeIf { it.isNotEmpty() && ".." !in it }
}

/** The entry called exactly [name], else one whose name differs only in case: cue sheets often get the case wrong. */
private fun <T> pick(entries: List<T>, name: String, nameOf: (T) -> String): T? =
    entries.firstOrNull { nameOf(it) == name } ?: entries.firstOrNull { nameOf(it).equals(name, ignoreCase = true) }
