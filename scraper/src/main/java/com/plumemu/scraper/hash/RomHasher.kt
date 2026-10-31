package com.plumemu.scraper.hash

import com.plumemu.scraper.HashCache
import com.plumemu.scraper.RomHashes
import com.plumemu.scraper.RomSource
import com.plumemu.scraper.timing.GameTiming
import com.plumemu.scraper.timing.msSince
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

/**
 * Hashes ROMs for ScreenScraper's hash lookup, in one streamed pass, off the caller's thread.
 *
 * - Up to [fullHashLimitBytes]: CRC32, MD5 and SHA1.
 * - Above it: CRC32 only (disc images are large; MD5 and SHA1 would cost seconds each).
 * - Compressed disc images ([NO_HASH_EXTENSIONS]): nothing; ScreenScraper indexes the
 *   uncompressed image, so their hash would never match. They are looked up by name and size.
 *
 * Results go through [cache], so an unchanged file is read once.
 */
internal class RomHasher(
    private val cache: HashCache,
    private val ioDispatcher: CoroutineDispatcher,
    private val fullHashLimitBytes: Long = FULL_HASH_LIMIT_BYTES,
) {
    /** The files read at once ([MAX_PARALLEL_READS]); a cache hit takes no permit. */
    private val reading = Semaphore(MAX_PARALLEL_READS)

    /** Throws the [java.io.IOException] of an unreadable ROM. */
    suspend fun hash(rom: RomSource): RomHashes {
        val game = currentCoroutineContext()[GameTiming]
        if (!isHashable(rom.fileName)) {
            game?.hash(0, "skipped")
            return RomHashes.NONE
        }
        val startedAt = System.nanoTime()
        val size = rom.sizeBytes
        val modified = rom.lastModifiedMillis
        cache.get(rom.key, size, modified)?.let {
            game?.hash(msSince(startedAt), "cache")
            return it
        }
        val full = size <= fullHashLimitBytes
        // The wait for a permit is not hashing: the game line shows reading time only.
        val waitStartedAt = System.nanoTime()
        var waitedNanos = 0L
        val hashes = reading.withPermit {
            waitedNanos = System.nanoTime() - waitStartedAt
            withContext(ioDispatcher) { stream(rom, full = full) }
        }
        cache.put(rom.key, size, modified, hashes)
        game?.hash(msSince(startedAt + waitedNanos), if (full) "read-full" else "read-crc")
        return hashes
    }

    private suspend fun stream(rom: RomSource, full: Boolean): RomHashes {
        val crc = CRC32()
        val md5 = if (full) MessageDigest.getInstance("MD5") else null
        val sha1 = if (full) MessageDigest.getInstance("SHA-1") else null
        val buffer = ByteArray(BUFFER_BYTES)
        rom.open().use { input ->
            while (true) {
                // Checked between chunks so a cancelled scrape stops reading a large image.
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
                md5?.update(buffer, 0, read)
                sha1?.update(buffer, 0, read)
            }
        }
        return RomHashes(
            crc32 = String.format(Locale.ROOT, "%08X", crc.value),
            md5 = md5?.digest()?.toLowerHex(),
            sha1 = sha1?.digest()?.toLowerHex(),
        )
    }

    companion object {
        const val FULL_HASH_LIMIT_BYTES: Long = 64L * 1024 * 1024
        const val BUFFER_BYTES: Int = 64 * 1024

        /**
         * At most this many ROM files are read at once, whatever the run's parallel games: an SD
         * card read by more streams at once seeks between them and gets slower for all. Most games are never read (the library sends hints).
         */
        const val MAX_PARALLEL_READS: Int = 2
        val NO_HASH_EXTENSIONS: Set<String> = setOf("chd", "rvz", "cso", "7z")

        fun isHashable(fileName: String): Boolean =
            fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) !in NO_HASH_EXTENSIONS
    }
}

internal fun ByteArray.toLowerHex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(digits[value ushr 4]).append(digits[value and 0x0F])
    }
    return out.toString()
}
