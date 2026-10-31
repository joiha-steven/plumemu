package com.plumemu.library.scan

/** One file or directory as the documents provider reports it; only the columns a scan needs. */
data class DocumentEntry(
    val documentId: String,
    val name: String,
    val sizeBytes: Long,
    /** Milliseconds since the epoch; 0 when the provider does not say. */
    val lastModified: Long,
    val isDirectory: Boolean,
)

/**
 * A file opened for reading: [channel] answers positional reads. Closing it closes the channel
 * and the descriptor behind it.
 */
interface OpenedFile : java.io.Closeable {
    val channel: java.nio.channels.FileChannel
}

/** The answer of one provider query. */
sealed interface TreeResult<out T> {
    data class Found<T>(val value: T) : TreeResult<T>

    /**
     * The picked folder is gone: deleted, or its storage (an SD card) was removed. Only the root
     * answers this; for any other document "no answer" is [Failed], because a provider that died
     * also answers nothing, and a deleted subdirectory shows up as missing from its parent's list.
     */
    data object Missing : TreeResult<Nothing>

    /** Plumemu no longer holds access to the folder (revoked in Android's settings). */
    data class PermissionDenied(val cause: Throwable) : TreeResult<Nothing>

    data class Failed(val cause: Throwable) : TreeResult<Nothing>
}

/**
 * A picked folder seen through the documents provider. The real one is
 * [ContentResolverDocumentTree]; tests use a fake built on the JVM filesystem. Calls block and
 * belong on `Dispatchers.IO`.
 */
interface DocumentTree {
    val rootDocumentId: String

    /** The document itself: one single-row query. */
    fun stat(documentId: String): TreeResult<DocumentEntry>

    /** The direct children of a directory: one query, however many children. */
    fun listChildren(documentId: String): TreeResult<List<DocumentEntry>>

    /**
     * Opens a file for reading its first bytes (the identification pass). A provider that cannot
     * give a seekable file (a pipe) may still open: reads then fail with an `IOException`.
     */
    fun open(documentId: String): TreeResult<OpenedFile>
}
