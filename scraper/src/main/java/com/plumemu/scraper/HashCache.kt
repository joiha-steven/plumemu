package com.plumemu.scraper

/**
 * The hashes ScreenScraper matches on. CRC32 is 8 uppercase hex digits; MD5 and SHA1 are
 * lowercase hex. A field is null when it was not computed (large file, or a compressed image
 * whose hash ScreenScraper does not know).
 */
public data class RomHashes(val crc32: String?, val md5: String?, val sha1: String?) {
    public val isEmpty: Boolean get() = crc32 == null && md5 == null && sha1 == null

    public companion object {
        public val NONE: RomHashes = RomHashes(null, null, null)
    }
}

/**
 * Remembers ROM hashes so an unchanged file is never read again. An entry belongs to one
 * [key] at one size and modification time; a changed size or time is a miss.
 * The scraper ships a file-backed one; the library may back it with its database instead.
 */
public interface HashCache {
    public suspend fun get(key: String, sizeBytes: Long, lastModifiedMillis: Long): RomHashes?

    public suspend fun put(key: String, sizeBytes: Long, lastModifiedMillis: Long, hashes: RomHashes)
}
