package com.plumemu.coredata.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The raw bytes under a `ZipInputStream`, with the last [RECENT_BYTES] it took kept: it reads ahead
 * (its own 512-byte buffers), so after a damaged entry the next entry's header may already be
 * among them. [ZipResync] searches these first.
 */
internal class RecentBytesTap(
    /** The rest of the file; [ZipResync] reads on from here once the zip stream is given up. */
    val source: ResumableSource,
) : InputStream() {
    private val ring = ByteArray(RECENT_BYTES)
    private var total = 0L

    override fun read(): Int {
        val b = source.read()
        if (b >= 0) remember(byteArrayOf(b.toByte()), 0, 1)
        return b
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val read = source.read(b, off, len)
        if (read > 0) remember(b, off, read)
        return read
    }

    // ZipInputStream skips through read(); a skip of the source would bypass the ring.
    override fun skip(n: Long): Long {
        val chunk = ByteArray(minOf(n, SKIP_CHUNK.toLong()).toInt().coerceAtLeast(1))
        var skipped = 0L
        while (skipped < n) {
            val read = read(chunk, 0, minOf(chunk.size.toLong(), n - skipped).toInt())
            if (read < 0) break
            skipped += read
        }
        return skipped
    }

    /** The bytes taken last, oldest first: at most [RECENT_BYTES]. */
    fun recent(): ByteArray {
        val kept = minOf(total, RECENT_BYTES.toLong()).toInt()
        val start = ((total - kept) % RECENT_BYTES).toInt()
        val out = ByteArray(kept)
        val first = minOf(kept, RECENT_BYTES - start)
        System.arraycopy(ring, start, out, 0, first)
        System.arraycopy(ring, 0, out, first, kept - first)
        return out
    }

    private fun remember(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        var from = off
        var left = len
        if (left > RECENT_BYTES) {
            from += left - RECENT_BYTES
            total += (left - RECENT_BYTES).toLong()
            left = RECENT_BYTES
        }
        while (left > 0) {
            val at = (total % RECENT_BYTES).toInt()
            val n = minOf(left, RECENT_BYTES - at)
            System.arraycopy(b, from, ring, at, n)
            from += n
            left -= n
            total += n
        }
    }

    companion object {
        /** Far more than ZipInputStream reads ahead (512 + 512 bytes). */
        const val RECENT_BYTES = 64 * 1024
        private const val SKIP_CHUNK = 8 * 1024
    }
}

/**
 * The file under every zip stream of one restore. Bytes [ZipResync] read past a damaged entry are
 * handed back with [unread] and come out first, then the rest of the file: however many entries
 * are damaged, there is one stream under the zip stream, not a chain of them, and a handed-back
 * window is dropped as soon as it has been read.
 */
internal class ResumableSource(
    private val rest: InputStream,
) : InputStream() {
    private var pending: ByteArray? = null
    private var at = 0

    /** Puts [len] bytes of [bytes] from [off] back, ahead of anything not read yet. */
    fun unread(
        bytes: ByteArray,
        off: Int,
        len: Int,
    ) {
        val old = pending
        val left = if (old == null) 0 else old.size - at
        val merged = ByteArray(len + left)
        System.arraycopy(bytes, off, merged, 0, len)
        if (old != null) System.arraycopy(old, at, merged, len, left)
        pending = merged.takeIf { it.isNotEmpty() }
        at = 0
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val bytes = pending ?: return rest.read(b, off, len)
        if (len == 0) return 0
        val n = minOf(len, bytes.size - at)
        System.arraycopy(bytes, at, b, off, n)
        at += n
        if (at == bytes.size) {
            pending = null
            at = 0
        }
        return n
    }

    override fun close() = rest.close()
}

/**
 * Finds where the entry after a damaged deflated entry starts, so Restore can leave a damaged
 * box-art file out instead of refusing the whole backup.
 *
 * A deflated entry written as a stream (`ZipOutputStream`, which wrote every entry of the backups
 * before 0.0.3) has no size in its header: only inflating finds its end, and a damaged one cannot be
 * inflated. But its end is marked: its data descriptor (`PK\x07\x08`, CRC, sizes: 16 bytes, or 24
 * with Zip64 sizes) is followed at once by the next entry's local header (`PK\x03\x04`). Both
 * signatures at that exact distance, and a header that is not the damaged entry's own, are taken
 * as the next entry. A descriptor followed by the central directory (`PK\x01\x02`) or the end of
 * the zip (`PK\x05\x06`, Zip64's `PK\x06\x06`) means the damaged entry was the last one (an
 * older backup without an account); eight fixed bytes in the right places inside deflated data are not a risk worth
 * a second pass. The scan starts after the damaged entry's own header when it is still among the
 * recent bytes (so no earlier entry is found again, and the scan only moves forward), and holds
 * about [OVERLAP_BYTES] plus one read at a time.
 */
internal object ZipResync {
    /**
     * Hands the bytes from the next entry's local header (or the central directory) on back to
     * [tap]'s source, so a new zip stream on it reads on from there; false when the file ends first.
     * [tap] is the stream the given-up `ZipInputStream` read through; [damagedName] is the entry it failed in.
     */
    fun skipDamagedEntry(
        tap: RecentBytesTap,
        damagedName: String,
    ): Boolean {
        val damagedNameBytes = damagedName.toByteArray(Charsets.UTF_8)
        var window = tap.recent()
        var from = lastHeaderNamed(window, damagedNameBytes)?.let { it + damagedNameBytes.size } ?: 0
        val chunk = ByteArray(READ_BYTES)
        while (true) {
            val found = nextHeaderAfterDescriptor(window, from, damagedName)
            if (found is Found.At) {
                tap.source.unread(window, found.index, window.size - found.index)
                return true
            }
            val read = tap.source.read(chunk)
            if (read < 0) return false
            // Keep the window's tail: a descriptor, the header and its name may span two reads.
            val keepFrom = if (found is Found.NeedMore) found.index else maxOf(from, window.size - OVERLAP_BYTES)
            val grown = ByteArrayOutputStream(window.size - keepFrom + read)
            grown.write(window, keepFrom, window.size - keepFrom)
            grown.write(chunk, 0, read)
            window = grown.toByteArray()
            from = 0
        }
    }

    private sealed interface Found {
        data class At(val index: Int) : Found

        /** A candidate starts at [index] but its header runs past the window: read more first. */
        data class NeedMore(val index: Int) : Found

        data object None : Found
    }

    private fun nextHeaderAfterDescriptor(
        window: ByteArray,
        from: Int,
        damagedName: String,
    ): Found {
        var i = from
        while (i + SIGNATURE_BYTES <= window.size) {
            if (matches(window, i, DESCRIPTOR_SIGNATURE)) {
                for (descriptorBytes in DESCRIPTOR_SIZES) {
                    val header = i + descriptorBytes
                    if (header + SIGNATURE_BYTES > window.size) return Found.NeedMore(i)
                    // No entry after the damaged one: the new zip stream finds the end there.
                    if (END_SIGNATURES.any { matches(window, header, it) }) return Found.At(header)
                    if (!matches(window, header, LOCAL_HEADER_SIGNATURE)) continue
                    if (header + LOCAL_HEADER_BYTES > window.size) return Found.NeedMore(i)
                    val nameLength = u16(window, header + NAME_LENGTH_AT)
                    if (header + LOCAL_HEADER_BYTES + nameLength > window.size) return Found.NeedMore(i)
                    val name = String(window, header + LOCAL_HEADER_BYTES, nameLength, Charsets.UTF_8)
                    if (nameLength > 0 && name != damagedName) return Found.At(header)
                }
            }
            i++
        }
        return Found.None
    }

    /** The last place [name] stands as the name of a local header in [window]: the index of its first byte. */
    private fun lastHeaderNamed(
        window: ByteArray,
        name: ByteArray,
    ): Int? {
        var i = window.size - name.size
        while (i >= LOCAL_HEADER_BYTES) {
            val header = i - LOCAL_HEADER_BYTES
            if (matches(window, header, LOCAL_HEADER_SIGNATURE) && u16(window, header + NAME_LENGTH_AT) == name.size && matches(window, i, name)) return i
            i--
        }
        return null
    }

    private fun matches(
        window: ByteArray,
        at: Int,
        bytes: ByteArray,
    ): Boolean {
        if (at < 0 || at + bytes.size > window.size) return false
        for (k in bytes.indices) if (window[at + k] != bytes[k]) return false
        return true
    }

    private fun u16(
        b: ByteArray,
        at: Int,
    ): Int = (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)

    private val DESCRIPTOR_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x07, 0x08)
    private val LOCAL_HEADER_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    /** The central directory, the end of central directory record, and Zip64's. */
    private val END_SIGNATURES = listOf(byteArrayOf(0x50, 0x4b, 0x01, 0x02), byteArrayOf(0x50, 0x4b, 0x05, 0x06), byteArrayOf(0x50, 0x4b, 0x06, 0x06))
    private const val SIGNATURE_BYTES = 4

    /** A data descriptor with its signature: CRC and 32-bit sizes, or Zip64's 64-bit sizes. */
    private val DESCRIPTOR_SIZES = intArrayOf(16, 24)
    private const val LOCAL_HEADER_BYTES = 30
    private const val NAME_LENGTH_AT = 26
    private const val READ_BYTES = 64 * 1024

    /** A descriptor, a header and the longest name a zip can hold. */
    private const val OVERLAP_BYTES = 24 + LOCAL_HEADER_BYTES + 0xffff
}
