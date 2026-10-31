package com.plumemu.launcher

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException

/** What reading the start of a small game file gave. */
internal sealed interface SmallFileRead {
    /**
     * [bytes] are the first bytes, undecoded (the caller knows the format, UTF-16 included);
     * [complete] is false when the file had more than were asked for.
     */
    class Read(val bytes: ByteArray, val complete: Boolean) : SmallFileRead {
        override fun equals(other: Any?): Boolean = other is Read && other.complete == complete && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + complete.hashCode()

        override fun toString(): String = "Read(${bytes.size} bytes, complete=$complete)"
    }

    /** The provider refused or failed. [error] is for the outcome; only its class may be logged. */
    data class Unreadable(val error: Throwable) : SmallFileRead
}

/**
 * Reads at most a few bytes of a game file whose content is the launch value (a PS Vita `.psvita`
 * file holds the title ID). Never reads a whole file: a wrong file of gigabytes costs [maxBytes] + 1.
 */
internal fun interface SmallFileReader {
    /** [documentUri] is a tree-based document URI. Blocking; call off the main thread. */
    fun read(documentUri: String, maxBytes: Int): SmallFileRead
}

internal class ContentResolverSmallFileReader(private val resolver: ContentResolver) : SmallFileReader {
    override fun read(documentUri: String, maxBytes: Int): SmallFileRead = try {
        resolver.openInputStream(Uri.parse(documentUri))?.use { input ->
            // One byte more than allowed tells a file that is too long from one that fits exactly.
            val buffer = ByteArray(maxBytes + 1)
            var total = 0
            while (total < buffer.size) {
                val count = input.read(buffer, total, buffer.size - total)
                if (count < 0) break
                total += count
            }
            SmallFileRead.Read(buffer.copyOf(minOf(total, maxBytes)), complete = total <= maxBytes)
        } ?: SmallFileRead.Unreadable(IOException("the provider gave no stream"))
    } catch (e: IOException) {
        SmallFileRead.Unreadable(e)
    } catch (e: RuntimeException) {
        // A provider may throw anything (SecurityException when access was revoked, SQLiteException,
        // UnsupportedOperationException from a provider that cannot open files): a failed read of this
        // file, never a crash and never the library's "database error". Blocking code, so no coroutine
        // cancellation passes through here.
        SmallFileRead.Unreadable(e)
    }
}
