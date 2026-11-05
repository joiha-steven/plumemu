package com.plumemu.achievements.hash

import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

// Builders of `.rvz` and `.wia` files for tests, written from Dolphin's format description (WiaAndRvz.md).

internal const val WIA_NONE = 0
internal const val WIA_PURGE = 1
internal const val WIA_BZIP2 = 2
internal const val WIA_LZMA = 3
internal const val WIA_LZMA2 = 4
internal const val WIA_ZSTD = 5

/** The pieces of a built file the damage tests patch; tables are plain (codec none) only when the test says so. */
internal class WiaFile(val bytes: ByteArray, val rawTableAt: Int, val groupTableAt: Int, val groupEntries: Int, val groupEntryBytes: Int)

private fun ByteArray.putBe64(at: Int, value: Long) {
    putBe32(at, value ushr 32)
    putBe32(at + 4, value and 0xFFFFFFFFL)
}

private fun sha1(data: ByteArray, from: Int = 0, to: Int = data.size): ByteArray =
    MessageDigest.getInstance("SHA-1").also { it.update(data, from, to - from) }.digest()

/** Recomputes the two checksums in the file head after a test patched the file head or the disc struct. */
internal fun resealWia(file: ByteArray): ByteArray {
    val out = file.copyOf()
    val discSize = ((out[0x0C].toInt() and 0xFF) shl 24) or ((out[0x0D].toInt() and 0xFF) shl 16) or ((out[0x0E].toInt() and 0xFF) shl 8) or (out[0x0F].toInt() and 0xFF)
    sha1(out, 0x48, 0x48 + minOf(discSize, 0xDC)).copyInto(out, 0x10)
    sha1(out, 0, 0x34).copyInto(out, 0x34)
    return out
}

/** One LZMA stream as a `.rvz`/`.wia` stores it: raw (no header), properties lc 3, lp 0, pb 2. [endMarker]: an end marker, as liblzma writes. */
internal fun lzmaRaw(data: ByteArray, endMarker: Boolean): ByteArray {
    val out = ByteArrayOutputStream()
    LZMAOutputStream(out, LZMA2Options().also { it.setLc(3); it.setLp(0); it.setPb(2); it.setDictSize(1 shl 16) }, endMarker).use { it.write(data) }
    return out.toByteArray()
}

internal fun lzma2Raw(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    LZMA2Options().also { it.setDictSize(1 shl 16) }.getOutputStream(FinishableWrapperOutputStream(out)).use { it.write(data) }
    return out.toByteArray()
}

internal const val LZMA_PROPS_BYTE = (2 * 5 + 0) * 9 + 3

/**
 * A GameCube [image] as `.rvz` or `.wia`: the file head, the disc struct, the groups, the raw-data table (one entry from
 * 0x80, as Dolphin writes it) and the group table. [codec] is the id in the disc struct; [encode] compresses a table or a
 * group (given its payload and its group number, or -1 for a table). [payloads] replaces the payload of a group (an RVZ
 * packed stream, whose unpacked size is [packedSizes]); [storedGroups] are RVZ groups kept uncompressed (bit 31 clear).
 * A group of zeros is written as size 0.
 */
internal fun wiaOf(
    image: ByteArray,
    codec: Int,
    rvz: Boolean = true,
    chunk: Int = 0x8000,
    encode: (ByteArray, Int) -> ByteArray = { data, _ -> data },
    payloads: Map<Int, ByteArray> = emptyMap(),
    storedGroups: Set<Int> = emptySet(),
    extraRaw: List<LongArray> = emptyList(), // more raw-data entries: offset, length, first group, group count
    comprData: ByteArray = if (codec == WIA_LZMA) byteArrayOf(LZMA_PROPS_BYTE.toByte(), 0, 0, 1, 0) else if (codec == WIA_LZMA2) byteArrayOf(16) else ByteArray(0),
): WiaFile {
    val discStructAt = 0x48
    val at = discStructAt + 0xDC
    val groupBytes = if (rvz) 12 else 8
    // The entry starts at 0x80, but its first group holds the disc from sector 0.
    val groups = (image.size + chunk - 1) / chunk
    val body = ByteArrayOutputStream()
    val table = ByteArray(groups * groupBytes)
    for (g in 0 until groups) {
        val raw = image.copyOfRange(g * chunk, minOf(image.size, (g + 1) * chunk))
        val payload = payloads[g] ?: raw
        val packed = if (g in payloads) payload.size else 0
        if (raw.all { it == 0.toByte() } && g !in payloads) {
            table.putBe32(g * groupBytes, 0)
            table.putBe32(g * groupBytes + 4, 0)
            continue
        }
        val compressed = codec != WIA_NONE && g !in storedGroups
        val stored = if (compressed) encode(payload, g) else payload
        table.putBe32(g * groupBytes, (at + body.size()).toLong() / 4)
        table.putBe32(g * groupBytes + 4, stored.size.toLong() or (if (rvz && compressed) 0x80000000L else 0))
        if (rvz) table.putBe32(g * groupBytes + 8, packed.toLong())
        body.write(stored)
        while (body.size() % 4 != 0) body.write(0)
    }
    val groupData = body.toByteArray()
    val rawEntry = ByteArray(24 * (1 + extraRaw.size))
    rawEntry.putBe64(0, 0x80)
    rawEntry.putBe64(8, (image.size - 0x80).toLong())
    rawEntry.putBe32(16, 0)
    rawEntry.putBe32(20, groups.toLong())
    extraRaw.forEachIndexed { i, e ->
        rawEntry.putBe64(24 * (i + 1), e[0])
        rawEntry.putBe64(24 * (i + 1) + 8, e[1])
        rawEntry.putBe32(24 * (i + 1) + 16, e[2])
        rawEntry.putBe32(24 * (i + 1) + 20, e[3])
    }
    val rawStored = if (codec == WIA_NONE) rawEntry else encode(rawEntry, -1)
    val groupsStored = if (codec == WIA_NONE) table else encode(table, -1)
    val rawTableAt = at + groupData.size
    val groupTableAt = rawTableAt + rawStored.size + (4 - rawStored.size % 4) % 4
    val fileSize = groupTableAt + groupsStored.size
    val out = ByteArray(fileSize)
    "${if (rvz) "RVZ" else "WIA"}\u0001".forEachIndexed { i, c -> out[i] = c.code.toByte() }
    out.putBe32(4, 0x01000000)
    out.putBe32(8, if (rvz) 0x00030000 else 0x00090000)
    out.putBe32(0x0C, 0xDC)
    out.putBe64(0x24, image.size.toLong())
    out.putBe64(0x2C, fileSize.toLong())
    val disc = ByteArray(0xDC)
    disc.putBe32(0, 1)
    disc.putBe32(4, codec.toLong())
    disc.putBe32(8, 5)
    disc.putBe32(0x0C, chunk.toLong())
    image.copyInto(disc, 0x10, 0, 0x80)
    disc.putBe32(0xB4, 1L + extraRaw.size)
    disc.putBe64(0xB8, rawTableAt.toLong())
    disc.putBe32(0xC0, rawStored.size.toLong())
    disc.putBe32(0xC4, groups.toLong())
    disc.putBe64(0xC8, groupTableAt.toLong())
    disc.putBe32(0xD0, groupsStored.size.toLong())
    disc[0xD4] = comprData.size.toByte()
    comprData.copyInto(disc, 0xD5)
    disc.copyInto(out, discStructAt)
    groupData.copyInto(out, at)
    rawStored.copyInto(out, rawTableAt)
    groupsStored.copyInto(out, groupTableAt)
    return WiaFile(resealWia(out), rawTableAt, groupTableAt, groups, groupBytes)
}

/** Dolphin's lagged-Fibonacci padding, written separately from the module's decoder: whole 521-word runs, then a slice. */
internal fun junkBytes(seed: ByteArray, discOffset: Long, count: Int): ByteArray {
    val words = IntArray(521)
    for (i in 0 until 17) {
        words[i] = ((seed[i * 4].toInt() and 0xFF) shl 24) or ((seed[i * 4 + 1].toInt() and 0xFF) shl 16) or
            ((seed[i * 4 + 2].toInt() and 0xFF) shl 8) or (seed[i * 4 + 3].toInt() and 0xFF)
    }
    for (i in 17 until 521) words[i] = (words[i - 17] shl 23) xor (words[i - 16] ushr 9) xor words[i - 1]
    fun forward() {
        for (i in 0 until 32) words[i] = words[i] xor words[i + 521 - 32]
        for (i in 32 until 521) words[i] = words[i] xor words[i - 32]
    }
    repeat(4) { forward() }
    val skip = (discOffset % 0x8000).toInt()
    val stream = ByteArrayOutputStream()
    while (stream.size() < skip + count) {
        for (w in words) {
            stream.write(w ushr 24)
            stream.write(w ushr 18)
            stream.write(w ushr 8)
            stream.write(w)
        }
        forward()
    }
    return stream.toByteArray().copyOfRange(skip, skip + count)
}

/** An RVZ packed stream: the bytes before [junkAt] as they are, then [count] junk bytes of [seed]. */
internal fun packedWithJunk(plain: ByteArray, junkAt: Int, count: Int, seed: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val head = ByteArray(4).also { it.putBe32(0, junkAt.toLong()) }
    out.write(head)
    out.write(plain, 0, junkAt)
    out.write(ByteArray(4).also { it.putBe32(0, count.toLong() or 0x80000000L) })
    out.write(seed)
    return out.toByteArray()
}
