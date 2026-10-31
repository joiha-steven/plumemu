package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/** How each stored sector holds its 2048 bytes of user data. */
internal enum class SectorLayout(val stride: Int) {
    /** ISO, PS2 DVD, PSP UMD, a cue's MODE1/2048 track: user data only. */
    COOKED_2048(2048),

    /**
     * Raw CD sector (ECMA-130): 12-byte sync `00 FF*10 00`, 4-byte header whose last byte is the
     * mode; Mode 1 data at +16, Mode 2 Form 1 at +24 (after the 8-byte subheader). A sector with
     * no sync (a cooked track stored in raw frames, as a CHD stores MODE1/2048) has its data at 0.
     */
    RAW_2352(2352),

    /** Mode 2 without sync and header (a cue's MODE2/2336 track): data after the 8-byte subheader. */
    MODE2_2336(2336),

    /**
     * A raw 2352-byte sector followed by 96 bytes of subchannel: CHD CD frames, and `.img` dumps
     * made with subchannel data. Read as [RAW_2352].
     */
    RAW_2448(2448),
}

/**
 * Logical 2048-byte sectors of a disc image: sector n of the data track, whatever the storage.
 * [base] is where sector 0 starts in [source] (a cue track that does not start its file).
 * [cookedOffset] is where user data starts in a raw-layout sector without a sync header (a
 * cooked track stored in raw frames): 0, or 8 for a CHD MODE2 (2336-byte) track.
 */
internal class SectorImage(
    private val source: RandomAccessSource,
    val layout: SectorLayout,
    private val base: Long = 0,
    private val cookedOffset: Int = 0,
) {
    /** Whole sectors stored after [base]. */
    val sectorCount: Long get() = ((source.size - base) / layout.stride).coerceAtLeast(0)

    /**
     * The first [length] bytes of user data starting at sector [lba], across consecutive
     * sectors; shorter when the image ends first, empty past the end.
     */
    @Throws(IOException::class)
    fun read(lba: Long, length: Int): ByteArray {
        if (lba < 0 || lba >= sectorCount || length <= 0) return ByteArray(0)
        if (layout == SectorLayout.COOKED_2048) {
            val available = (sectorCount - lba) * SECTOR
            return source.readFully(base + lba * SECTOR, minOf(length.toLong(), available).toInt())
        }
        val out = java.io.ByteArrayOutputStream(length)
        var sector = lba
        while (out.size() < length && sector < sectorCount) {
            val want = minOf(SECTOR, length - out.size())
            val data = userData(sector, want)
            out.write(data, 0, data.size)
            if (data.size < want) break
            sector++
        }
        return out.toByteArray()
    }

    @Throws(IOException::class)
    private fun userData(lba: Long, length: Int): ByteArray {
        val start = base + lba * layout.stride
        val offset = when (layout) {
            SectorLayout.COOKED_2048 -> 0
            SectorLayout.MODE2_2336 -> MODE2_SUBHEADER
            SectorLayout.RAW_2352, SectorLayout.RAW_2448 -> rawDataOffset(source.readFully(start, RAW_HEADER), cookedOffset)
        }
        return source.readFully(start + offset, length)
    }

    companion object {
        const val SECTOR = 2048
        private const val RAW_HEADER = 16
        private const val MODE2_SUBHEADER = 8
        private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)

        /** True when [bytes] start with a raw CD sector's sync pattern. */
        fun hasSync(bytes: ByteArray, at: Int = 0): Boolean = bytes.startsWith(SYNC, at)

        /** Where user data starts in a raw sector whose first 16 bytes are [header]; [cooked] when it has no sync. */
        fun rawDataOffset(header: ByteArray, cooked: Int = 0): Int = when {
            !hasSync(header) || header.size < RAW_HEADER -> cooked
            header.u8(15) == 2 -> 24
            else -> 16
        }

        /**
         * The layout of a loose image: raw when it starts with a sync pattern (2448-byte sectors
         * when the next sync is at 2448 rather than 2352), else cooked. A raw image that starts
         * with audio (no sync at 0) is read as cooked and finds nothing.
         */
        @Throws(IOException::class)
        fun detect(source: RandomAccessSource): SectorLayout {
            if (!hasSync(source.readFully(0, SYNC.size))) return SectorLayout.COOKED_2048
            if (hasSync(source.readFully(SectorLayout.RAW_2352.stride.toLong(), SYNC.size))) return SectorLayout.RAW_2352
            val with2448 = hasSync(source.readFully(SectorLayout.RAW_2448.stride.toLong(), SYNC.size))
            return if (with2448) SectorLayout.RAW_2448 else SectorLayout.RAW_2352
        }
    }
}
