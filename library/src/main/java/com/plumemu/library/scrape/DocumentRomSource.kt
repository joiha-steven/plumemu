package com.plumemu.library.scrape

import com.plumemu.library.scan.DocumentTree
import com.plumemu.library.scan.OpenedFile
import com.plumemu.library.scan.TreeResult
import com.plumemu.scraper.RomSource
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels

/**
 * A library game's file as the scraper sees it. [key] is the document URI. The run always gets
 * hints ([ScrapeHintsFromFingerprint]), so [open] is not called by it; it opens the file through
 * the folder's [DocumentTree] like the identifier does, for any other caller.
 *
 * [gameId] and [scrapeKey] say where [LibraryScrapeSource.deliver] stores the result.
 */
internal class DocumentRomSource(
    val gameId: Long,
    val scrapeKey: String,
    override val key: String,
    override val fileName: String,
    override val sizeBytes: Long,
    override val lastModifiedMillis: Long,
    private val documentId: String,
    private val tree: () -> DocumentTree,
) : RomSource {
    /** @throws IOException when the provider cannot open the file (moved, access revoked). */
    override fun open(): InputStream = when (val opened = tree().open(documentId)) {
        is TreeResult.Found -> ClosingStream(opened.value)
        // No document URI in the message: it names the person's files.
        TreeResult.Missing -> throw IOException("game $gameId: its folder is gone")
        is TreeResult.PermissionDenied -> throw IOException("game $gameId: folder access revoked (${opened.cause.javaClass.simpleName})")
        is TreeResult.Failed -> throw IOException("game $gameId: the provider could not open it (${opened.cause.javaClass.simpleName})")
    }

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
