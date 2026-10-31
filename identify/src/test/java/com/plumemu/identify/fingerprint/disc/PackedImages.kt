package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.disc.DiscImages.putAscii
import com.plumemu.identify.fingerprint.disc.DiscImages.putU32be
import com.plumemu.identify.fingerprint.disc.DiscImages.putU32le
import com.plumemu.identify.fingerprint.disc.DiscImages.putU64be
import com.plumemu.identify.fingerprint.disc.DiscImages.putU64le
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/** Generated compressed containers for tests: CSO, CHD v5, GCZ, WBFS, RVZ / WIA. */
internal object PackedImages {
    const val CD_FRAME = 2448
    const val FRAMES_PER_HUNK = 8

    fun deflate(data: ByteArray, raw: Boolean): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, raw)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    /**
     * CSO v1 of [image]; blocks in [stored] are kept as is (index bit 31). With [shift] > 0 every
     * block starts on a 2^shift boundary (the index holds offset >> shift) and is zero-padded.
     */
    fun cso(image: ByteArray, blockSize: Int = 2048, stored: Set<Int> = emptySet(), version: Int = 1, shift: Int = 0): ByteArray {
        val blocks = (image.size + blockSize - 1) / blockSize
        val header = ByteArray(0x18)
        header.putAscii(0, "CISO")
        header.putU32le(4, 0x18)
        header.putU64le(8, image.size.toLong())
        header.putU32le(0x10, blockSize.toLong())
        header[0x14] = version.toByte()
        header[0x15] = shift.toByte()
        val index = ByteArray((blocks + 1) * 4)
        val data = ByteArrayOutputStream()
        val align = 1 shl shift
        val dataStart = (0x18 + index.size + align - 1) / align * align
        val lead = dataStart - 0x18 - index.size
        for (b in 0..blocks) {
            val offset = (dataStart + data.size()).toLong()
            val flag = if (b in stored) 1L shl 31 else 0L
            index.putU32le(b * 4, (offset shr shift) or flag)
            if (b == blocks) break
            val block = image.copyOfRange(b * blockSize, minOf(image.size, (b + 1) * blockSize)).copyOf(blockSize)
            data.write(if (b in stored) block else deflate(block, raw = true))
            while (data.size() % align != 0) data.write(0)
        }
        return header + index + ByteArray(lead) + data.toByteArray()
    }

    /** Most significant bit first, as MAME's bitstream reads. */
    class BitWriter {
        private val out = ByteArrayOutputStream()
        private var current = 0
        private var count = 0

        fun write(value: Long, bits: Int) {
            for (i in bits - 1 downTo 0) {
                current = (current shl 1) or ((value ushr i).toInt() and 1)
                if (++count == 8) {
                    out.write(current)
                    current = 0
                    count = 0
                }
            }
        }

        fun bytes(): ByteArray {
            if (count > 0) write(0, 8 - count)
            return out.toByteArray()
        }
    }

    /** How a test CHD stores each hunk: a codec slot 0-3, 4 stored, 5 a copy of hunk [selfOf], 6 in the parent. */
    data class HunkPlan(val type: Int, val selfOf: Int = 0)

    /**
     * A CHD v5 of [logical] bytes (CD frames when [unitBytes] is 2448). [codecs] fills the four
     * codec slots; [plan] says how each hunk is stored; [compressedMap] false writes an
     * uncompressed CHD (codecs ignored, every hunk stored, map of u32 offsets / hunk size).
     * [metadata] entries are (tag, text). [eccFrames] are CD frames whose sync is cleared before
     * compression and flagged in the cdzl bitmap, as chdman does when it can rebuild them.
     * [encoded] holds hunks already compressed elsewhere (the `cdlz` / `lzma` fixtures of
     * `containers/make_fixtures.py`), written as they are for a codec-slot plan.
     */
    fun chd(
        logical: ByteArray,
        hunkBytes: Int,
        unitBytes: Int,
        codecs: List<String> = listOf("cdzl"),
        plan: (Int) -> HunkPlan = { HunkPlan(0) },
        compressedMap: Boolean = true,
        metadata: List<Pair<String, String>> = emptyList(),
        eccFrames: Set<Int> = emptySet(),
        rawSha1: ByteArray = ByteArray(20) { (0x11 + it).toByte() },
        encoded: Map<Int, ByteArray> = emptyMap(),
    ): ByteArray {
        val hunks = (logical.size + hunkBytes - 1) / hunkBytes
        val header = ByteArray(124)
        header.putAscii(0, "MComprHD")
        header.putU32be(8, 124)
        header.putU32be(12, 5)
        if (compressedMap) codecs.forEachIndexed { i, c -> header.putAscii(16 + i * 4, c) }
        header.putU64be(32, logical.size.toLong())
        header.putU32be(56, hunkBytes.toLong())
        header.putU32be(60, unitBytes.toLong())
        rawSha1.copyInto(header, 0x40)
        ByteArray(20) { 0x77 }.copyInto(header, 0x54)
        val meta = metadataBytes(metadata, 124L)
        header.putU64be(48, if (metadata.isEmpty()) 0 else 124L)
        val mapOffset = 124L + meta.size
        header.putU64be(40, mapOffset)
        val hunkData = (0 until hunks).map { logical.copyOfRange(it * hunkBytes, minOf(logical.size, (it + 1) * hunkBytes)).copyOf(hunkBytes) }
        if (!compressedMap) {
            val map = ByteArray(hunks * 4)
            val first = ((mapOffset + map.size + hunkBytes - 1) / hunkBytes).toInt()
            for (h in 0 until hunks) map.putU32be(h * 4, (first + h).toLong())
            val pad = ByteArray((first.toLong() * hunkBytes - mapOffset - map.size).toInt())
            return header + meta + map + pad + hunkData.fold(ByteArray(0)) { a, b -> a + b }
        }
        val plans = (0 until hunks).map(plan)
        val stored = plans.mapIndexed { h, p ->
            encoded[h]?.takeIf { p.type < 4 } ?: encodeHunk(hunkData[h], p, codecs, h, eccFrames, hunkBytes)
        }
        val bits = mapBits(plans, stored, hunks)
        val mapHeader = ByteArray(16)
        mapHeader.putU32be(0, bits.size.toLong())
        val firstOffset = mapOffset + 16 + bits.size
        mapHeader.putU32be(4, firstOffset ushr 16)
        mapHeader[8] = (firstOffset ushr 8).toByte()
        mapHeader[9] = firstOffset.toByte()
        mapHeader[12] = 24
        mapHeader[13] = 16
        mapHeader[14] = 16
        return header + meta + mapHeader + bits + stored.fold(ByteArray(0)) { a, b -> a + b }
    }

    private fun encodeHunk(hunk: ByteArray, plan: HunkPlan, codecs: List<String>, h: Int, ecc: Set<Int>, hunkBytes: Int): ByteArray =
        when (plan.type) {
            4 -> hunk
            5, 6 -> ByteArray(0)
            else -> when (codecs[plan.type]) {
                "zlib" -> deflate(hunk, raw = true)
                "cdzl" -> cdzl(hunk, h, ecc, hunkBytes)
                else -> ByteArray(100) { 0x5A }
            }
        }

    private fun cdzl(hunk: ByteArray, h: Int, ecc: Set<Int>, hunkBytes: Int): ByteArray {
        val frames = hunkBytes / CD_FRAME
        val bitmap = ByteArray((frames + 7) / 8)
        val sectors = ByteArray(frames * 2352)
        val subcode = ByteArray(frames * 96)
        for (f in 0 until frames) {
            System.arraycopy(hunk, f * CD_FRAME, sectors, f * 2352, 2352)
            System.arraycopy(hunk, f * CD_FRAME + 2352, subcode, f * 96, 96)
            if (h * frames + f in ecc) {
                bitmap[f / 8] = (bitmap[f / 8].toInt() or (1 shl (f % 8))).toByte()
                for (i in 0 until 12) sectors[f * 2352 + i] = 0
            }
        }
        val base = deflate(sectors, raw = true)
        // The base length takes 3 bytes when the hunk is 64 KB or more.
        val length = if (hunkBytes < 65536) {
            byteArrayOf((base.size ushr 8).toByte(), base.size.toByte())
        } else {
            byteArrayOf((base.size ushr 16).toByte(), (base.size ushr 8).toByte(), base.size.toByte())
        }
        return bitmap + length + base + deflate(subcode, raw = true)
    }

    /** Tree: all 16 symbols 4 bits long (code = symbol); types with run-length symbol 7; then lengths. */
    private fun mapBits(plans: List<HunkPlan>, stored: List<ByteArray>, hunks: Int): ByteArray {
        val w = BitWriter()
        repeat(16) { w.write(4, 4) }
        var last = 0
        var h = 0
        while (h < hunks) {
            val type = plans[h].type
            var run = 0
            while (h + run < hunks && plans[h + run].type == last && run < 18) run++
            if (type == last && run >= 3) {
                w.write(7, 4)
                w.write((run - 3).toLong(), 4)
                h += run
            } else {
                w.write(type.toLong(), 4)
                last = type
                h++
            }
        }
        for (i in 0 until hunks) {
            when (plans[i].type) {
                4 -> w.write(0, 16)
                5, 6 -> w.write(plans[i].selfOf.toLong(), 16)
                else -> {
                    w.write(stored[i].size.toLong(), 24)
                    w.write(0, 16)
                }
            }
        }
        return w.bytes()
    }

    private fun metadataBytes(entries: List<Pair<String, String>>, start: Long): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEachIndexed { i, (tag, text) ->
            val data = text.toByteArray(Charsets.US_ASCII) + 0
            val head = ByteArray(16)
            head.putAscii(0, tag)
            head[4] = 1
            head[5] = (data.size ushr 16).toByte()
            head[6] = (data.size ushr 8).toByte()
            head[7] = data.size.toByte()
            val next = if (i == entries.size - 1) 0 else start + out.size() + 16 + data.size
            head.putU64be(8, next)
            out.write(head)
            out.write(data)
        }
        return out.toByteArray()
    }

    /**
     * A GD-ROM CHD: track 1 (302 frames), track 2 audio (75), track 3 holding [track3] (raw
     * sectors), each padded to 4 frames as chdman does, listed in `CHGD` metadata.
     */
    fun dreamcastChd(track3: ByteArray): ByteArray {
        val frames = toFrames(ByteArray(302 * 2352)) + ByteArray(2 * CD_FRAME) + toFrames(ByteArray(75 * 2352)) +
            ByteArray(CD_FRAME) + toFrames(track3)
        val count = track3.size / 2352
        val meta = listOf(
            "CHGD" to "TRACK:1 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:302 PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0",
            "CHGD" to "TRACK:2 TYPE:AUDIO SUBTYPE:NONE FRAMES:75 PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0",
            "CHGD" to "TRACK:3 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:$count PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0",
        )
        return chd(frames, CD_FRAME * FRAMES_PER_HUNK, CD_FRAME, listOf("cdzl"), metadata = meta)
    }

    /** 2048-byte sectors stored as CHD MODE2 (2336) frames: 8-byte subheader, data, no sync. */
    fun mode2CookedFrames(cooked: ByteArray): ByteArray {
        val count = cooked.size / 2048
        val out = ByteArray(count * CD_FRAME)
        for (s in 0 until count) System.arraycopy(cooked, s * 2048, out, s * CD_FRAME + 8, 2048)
        return out
    }

    /** Raw 2352-byte sectors as CHD CD frames (96 zero subcode bytes each). */
    fun toFrames(raw: ByteArray): ByteArray {
        val count = raw.size / 2352
        val out = ByteArray(count * CD_FRAME)
        for (s in 0 until count) System.arraycopy(raw, s * 2352, out, s * CD_FRAME, 2352)
        return out
    }

    /** GCZ of [image]: block 0 zlib-compressed unless [storeFirst]. */
    fun gcz(image: ByteArray, blockSize: Int = 0x4000, storeFirst: Boolean = false): ByteArray {
        val blocks = (image.size + blockSize - 1) / blockSize
        val header = ByteArray(32)
        header.putU32le(0, 0xB10BC001L)
        val pointers = ByteArray(blocks * 8)
        val data = ByteArrayOutputStream()
        for (b in 0 until blocks) {
            val block = image.copyOfRange(b * blockSize, minOf(image.size, (b + 1) * blockSize)).copyOf(blockSize)
            val store = b == 0 && storeFirst
            pointers.putU64le(b * 8, data.size().toLong() or if (store) Long.MIN_VALUE else 0)
            data.write(if (store) block else deflate(block, raw = false))
        }
        header.putU64le(8, data.size().toLong())
        header.putU64le(16, image.size.toLong())
        header.putU32le(24, blockSize.toLong())
        header.putU32le(28, blocks.toLong())
        return header + pointers + ByteArray(blocks * 4) + data.toByteArray()
    }

    /** WBFS with one disc in slot [slot]: 512-byte sectors, 2 MB WBFS sectors. */
    fun wbfs(discHeader: ByteArray, slot: Int = 0, size: Int = 1 shl 20): ByteArray {
        val out = ByteArray(size)
        out.putAscii(0, "WBFS")
        out.putU32be(4, size / 512L)
        out[8] = 9
        out[9] = 21
        out[12 + slot] = 1
        // Disc info: 0x100 header copy + 2 bytes per WBFS sector of a disc (143432 * 2 Wii sectors).
        val infoSize = ((0x100 + (143432L * 2 shr 6) * 2 + 511) / 512 * 512).toInt()
        discHeader.copyInto(out, 512 + slot * infoSize, 0, 0x100)
        return out
    }

    /** RVZ or WIA: file header 1 (0x48 bytes), then file header 2 with the disc header at 0x58. */
    fun rvz(discHeader: ByteArray, magic: String = "RVZ", size: Int = 1 shl 16): ByteArray {
        val out = ByteArray(size)
        out.putAscii(0, magic)
        out[3] = 1
        out.putU32be(0x48, 1)
        discHeader.copyInto(out, 0x58, 0, 0x80)
        return out
    }
}
