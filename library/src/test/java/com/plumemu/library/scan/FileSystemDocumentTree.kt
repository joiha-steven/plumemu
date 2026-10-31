package com.plumemu.library.scan

import java.io.File
import java.io.IOException

/**
 * A [DocumentTree] over a directory on the JVM filesystem, for tests. Document ids look like
 * ExternalStorageProvider's (`fs:snes/Game.sfc`). Counts every query so tests can assert which
 * directories a scan listed. Like the real tree, a document that does not answer is [TreeResult.Missing]
 * only for the root and [TreeResult.Failed] otherwise.
 *
 * With [fileIdsByName], files get ids that survive a move (`name:Game.sfc`), like providers whose
 * ids are not paths.
 */
internal class FileSystemDocumentTree(
    private val root: File,
    private val fileIdsByName: Boolean = false,
) : DocumentTree {
    val listed: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    var statCount = 0
        private set

    /** Simulates access revoked in Android's settings. */
    var isPermissionRevoked = false

    /** Document ids whose queries fail with an I/O error. */
    val failing = mutableSetOf<String>()

    /** Document ids of every file opened for reading, in order (the identification pass). */
    val opened: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** Document ids that open, but whose reads fail with an I/O error (card pulled mid-read). */
    val failingReads = mutableSetOf<String>()

    /** Runs before every query; lets a test change the tree in the middle of a scan. */
    var beforeQuery: (String) -> Unit = {}

    /** How long each query takes, to make overlapping scans visible. */
    var queryDelayMs = 0L

    private val inFlight = java.util.concurrent.atomic.AtomicInteger()

    /** The most queries that ran at the same moment. */
    @Volatile
    var maxConcurrentQueries = 0
        private set

    override val rootDocumentId: String = idOf(root)

    override fun stat(documentId: String): TreeResult<DocumentEntry> {
        statCount++
        beforeQuery(documentId)
        return guard(documentId) { file -> TreeResult.Found(entryOf(file)) }
    }

    override fun listChildren(documentId: String): TreeResult<List<DocumentEntry>> {
        listed += documentId
        beforeQuery(documentId)
        return guard(documentId) { dir ->
            TreeResult.Found(dir.listFiles().orEmpty().sortedBy { it.name }.map(::entryOf))
        }
    }

    override fun open(documentId: String): TreeResult<OpenedFile> {
        opened += documentId
        beforeQuery(documentId)
        return guard(documentId) { file ->
            if (file.isDirectory) return@guard TreeResult.Failed(IOException("is a directory"))
            val stream = java.io.FileInputStream(file)
            if (documentId in failingReads) stream.close()
            TreeResult.Found(
                object : OpenedFile {
                    override val channel: java.nio.channels.FileChannel = stream.channel

                    override fun close() = stream.close()
                },
            )
        }
    }

    private fun <T> guard(documentId: String, read: (File) -> TreeResult<T>): TreeResult<T> {
        val running = inFlight.incrementAndGet()
        try {
            synchronized(this) { maxConcurrentQueries = maxOf(maxConcurrentQueries, running) }
            if (queryDelayMs > 0) Thread.sleep(queryDelayMs)
            return answer(documentId, read)
        } finally {
            inFlight.decrementAndGet()
        }
    }

    private fun <T> answer(documentId: String, read: (File) -> TreeResult<T>): TreeResult<T> {
        if (isPermissionRevoked) return TreeResult.PermissionDenied(SecurityException("revoked"))
        if (documentId in failing) return TreeResult.Failed(IOException("read error"))
        val file = fileOf(documentId)
        return when {
            file.exists() -> read(file)
            documentId == rootDocumentId -> TreeResult.Missing
            else -> TreeResult.Failed(IOException("provider gave no answer"))
        }
    }

    private fun entryOf(file: File) = DocumentEntry(
        documentId = idOf(file),
        name = file.name,
        sizeBytes = if (file.isDirectory) 0 else file.length(),
        lastModified = file.lastModified(),
        isDirectory = file.isDirectory,
    )

    private fun idOf(file: File): String =
        if (fileIdsByName && file.isFile) "name:${file.name}" else "fs:" + file.relativeTo(root).path

    private fun fileOf(documentId: String): File =
        if (documentId.startsWith("name:")) {
            root.walkTopDown().firstOrNull { it.isFile && it.name == documentId.removePrefix("name:") } ?: File(root, documentId)
        } else {
            File(root, documentId.removePrefix("fs:"))
        }
}
