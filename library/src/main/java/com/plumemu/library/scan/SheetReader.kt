package com.plumemu.library.scan

import android.util.Log
import com.plumemu.identify.fingerprint.disc.SheetReferences
import com.plumemu.library.catalog.JunkFilter
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/** What the sheets of one directory name: sheet document id → document ids of the files of the same directory it names. */
internal class SheetParts(val bySheet: Map<String, Set<String>>) {
    companion object {
        val NONE = SheetParts(emptyMap())
    }
}

/**
 * Reads the `.cue`, `.gdi` and `.m3u` files of a directory (only those, each at most
 * [SheetReferences.MAX_BYTES]) and finds the files they name: by exact name, then ignoring case,
 * in the sheet's own directory only (a name with a folder in it names nothing: a file in another
 * folder is a game of that folder, decided when that folder is walked).
 * A name that is not there is skipped: the sheet stays a game of its own and a launch finds the
 * file missing like any broken sheet. A sheet that cannot be read names nothing (logged without
 * file names); the walk goes on as it did before sheets were used.
 */
internal class SheetReader(private val tree: DocumentTree) {
    fun read(directoryId: String, children: List<DocumentEntry>): SheetParts {
        val sheets = children.filter {
            !it.isDirectory && !JunkFilter.isJunkFile(it.name) && SheetReferences.isSheet(extensionOf(it.name)) && it.sizeBytes <= SheetReferences.MAX_BYTES
        }
        if (sheets.isEmpty()) return SheetParts.NONE
        val files = children.filter { !it.isDirectory }
        val byName = files.associateBy { it.name }
        val byLowerName = files.groupBy { it.name.lowercase(Locale.ROOT) }
        val bySheet = LinkedHashMap<String, Set<String>>()
        for (sheet in sheets) {
            val names = namesIn(sheet) ?: continue
            val found = LinkedHashSet<String>()
            for (name in names) {
                val part = byName[name] ?: byLowerName[name.lowercase(Locale.ROOT)]?.firstOrNull() ?: continue
                found += part.documentId
            }
            if (found.isNotEmpty()) bySheet[sheet.documentId] = found
        }
        return SheetParts(bySheet)
    }

    private fun namesIn(sheet: DocumentEntry): List<String>? {
        val text = try {
            when (val opened = tree.open(sheet.documentId)) {
                is TreeResult.Found -> opened.value.use { readText(it) }
                else -> {
                    Log.w(LOG_TAG, "A sheet could not be opened; its files keep their own rows")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(LOG_TAG, "A sheet could not be read (${e.javaClass.simpleName}); its files keep their own rows")
            null
        } catch (e: RuntimeException) {
            // A provider may throw SecurityException or IllegalArgumentException: one bad sheet never fails the scan.
            Log.w(LOG_TAG, "A sheet could not be read (${e.javaClass.simpleName}); its files keep their own rows")
            null
        } ?: return null
        return SheetReferences.names(extensionOf(sheet.name), text)
    }

    private fun readText(file: OpenedFile): String? {
        val channel = file.channel
        if (channel.size() > SheetReferences.MAX_BYTES) return null
        val bytes = ByteBuffer.allocate(channel.size().toInt())
        while (bytes.hasRemaining()) if (channel.read(bytes, bytes.position().toLong()) < 0) break
        bytes.flip()
        // Sheets made on old Windows tools are Latin-1; UTF-8 first, Latin-1 when it does not decode.
        return try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(bytes.duplicate()).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            StandardCharsets.ISO_8859_1.decode(bytes).toString()
        }
    }

    private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

}
