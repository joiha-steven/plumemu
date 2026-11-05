package com.plumemu.identify.pack

import java.security.MessageDigest

/**
 * The keys of a disc's track sizes, as the `TRKS` section of a pack stores them
 * (`identify/PACKS.md`, "TRKS"). The Python pack tool computes the same
 * keys from a Redump DAT; change both or neither.
 *
 * A key is the first 8 bytes (little-endian) of SHA-256 over a kind byte and the sizes as
 * little-endian u64. [ORDERED] covers the sizes of all track files in track order; [TOTAL] only
 * their sum, so a single-bin copy of a multi-track disc and a CHD match the split-bin dump.
 * Sizes are in bytes of the raw 2352-byte sector layout Redump lists for CD discs, and the file
 * size for a DVD or GameCube/Wii image; the kind byte only keeps the two kinds apart in the one
 * key space of the section. How to get them from a file is the reader's job
 * (PACKS.md, "Normalising a copy").
 */
object TrackSizeKeys {
    /** Kind byte hashed into an ORDERED key: the ordered list of every track file's size. */
    const val ORDERED = 1

    /** Kind byte hashed into a TOTAL key: the sum of all track sizes, stored for discs with two or more tracks. */
    const val TOTAL = 2

    /** The key of the ordered track sizes [sizes] (bytes, track order). */
    fun ordered(sizes: List<Long>): Long {
        require(sizes.isNotEmpty() && sizes.all { it > 0 }) { "track sizes must be positive" }
        return hash(ORDERED, sizes)
    }

    /** The key of the sum of all track sizes, [bytes]. */
    fun total(bytes: Long): Long {
        require(bytes > 0) { "total size must be positive" }
        return hash(TOTAL, listOf(bytes))
    }

    private fun hash(kind: Int, values: List<Long>): Long {
        val input = ByteArray(1 + values.size * 8)
        input[0] = kind.toByte()
        values.forEachIndexed { i, v -> for (b in 0 until 8) input[1 + i * 8 + b] = (v ushr (8 * b)).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        var key = 0L
        for (b in 0 until 8) key = key or ((digest[b].toLong() and 0xFF) shl (8 * b))
        return key
    }
}
