package com.plumemu.achievements.hash

import java.io.ByteArrayOutputStream
import java.util.zip.Adler32
import java.util.zip.Deflater

// Wii images, and the .gcz and .wbfs containers, for the Nintendo disc tests.

internal fun ByteArray.putBe32(at: Int, value: Long) {
    for (i in 0 until 4) this[at + i] = (value shr (24 - 8 * i)).toByte()
}

private fun ByteArray.putLe(at: Int, value: Long, bytes: Int) {
    for (i in 0 until bytes) this[at + i] = (value shr (8 * i)).toByte()
}

internal object WiiLayout {
    const val SIZE = 0x80000
    const val UPDATE_PARTITION = 0x48000
    const val GAME_PARTITION = 0x50000
    const val TMD_AT = GAME_PARTITION + 0x400
    const val TMD_SIZE = 100
    const val DATA_AT = 0x60000
    const val DATA_BYTES = 0x18000
    const val APPLOADER_BODY = 0x100
    const val APPLOADER_TRAILER = 0x80
    const val DOL_AT = 0x3000

    /** Segments of the decrypted main executable: (offset in the partition, size). */
    val SEGMENTS = listOf(0x3100 to 0x40, 0x3200 to 0x80)
}

/**
 * A Wii disc of two partitions (an update one the hash skips and a game one). [encrypted] sets the
 * flag byte at 0x61; a decrypted disc also gets an apploader and a main executable at the data offset.
 * The last 32 KiB sector is all zeros so a `.wbfs` can leave it out.
 */
internal fun wiiDisc(encrypted: Boolean): ByteArray {
    val image = ByteArray(WiiLayout.SIZE)
    fillImage(image, 0, WiiLayout.SIZE - 0x8000)
    image.putBe32(0x18, 0x5D1C9EA3L)
    image[0x61] = if (encrypted) 0 else 1
    image.fill(0, 0x40000, 0x40100) // the four group counts and table offsets, then the table
    image.putBe32(0x40000, 2)
    image.putBe32(0x40004, 0x40020L shr 2)
    image.putBe32(0x40020, WiiLayout.UPDATE_PARTITION.toLong() shr 2)
    image.putBe32(0x40024, 1)
    image.putBe32(0x40028, WiiLayout.GAME_PARTITION.toLong() shr 2)
    image.putBe32(0x4002C, 0)
    val base = WiiLayout.GAME_PARTITION
    image.putBe32(base + 0x2A4, WiiLayout.TMD_SIZE.toLong())
    image.putBe32(base + 0x2A8, 0x400L shr 2)
    image.putBe32(base + 0x2B8, WiiLayout.DATA_AT.toLong() shr 2)
    image.putBe32(base + 0x2BC, WiiLayout.DATA_BYTES.toLong() shr 2)
    if (!encrypted) {
        val part = WiiLayout.DATA_AT
        image.putBe32(part + 0x2440 + 0x14, WiiLayout.APPLOADER_BODY.toLong())
        image.putBe32(part + 0x2440 + 0x18, WiiLayout.APPLOADER_TRAILER.toLong())
        image.putBe32(part + 0x420, WiiLayout.DOL_AT.toLong() shr 2)
        image.fill(0, part + WiiLayout.DOL_AT, part + WiiLayout.DOL_AT + 0xD8)
        WiiLayout.SEGMENTS.forEachIndexed { n, (offset, size) ->
            image.putBe32(part + WiiLayout.DOL_AT + n * 4, offset.toLong() shr 2)
            image.putBe32(part + WiiLayout.DOL_AT + 0x90 + n * 4, size.toLong() shr 2)
        }
    }
    return image
}

/** A WiiWare package: `Is` at 4, one certificate chain, one ticket, a title metadata of two contents, then the contents. */
internal fun wiiWarePackage(): ByteArray {
    val image = ByteArray(0x500)
    fillImage(image)
    image.fill(0, 0, 0x40)
    image.putBe32(0x08, 0x40)
    image.putBe32(0x10, 0x40)
    image.putBe32(0x14, 0x240)
    image.put(4, "Is")
    image[6] = 0
    image[7] = 0
    val tmd = 0x40 + 0x40 + 0x40
    image[tmd + 0x1DE] = 0
    image[tmd + 0x1DF] = 2
    for ((n, size) in listOf(100, 200).withIndex()) {
        val entry = tmd + 0x1E4 + 8 + n * 0x24
        image.putBe32(entry, 0)
        image.putBe32(entry + 4, size.toLong())
    }
    return image
}

private fun ByteArray.put(at: Int, text: String) = text.forEachIndexed { i, c -> this[at + i] = c.code.toByte() }

/** The sum Dolphin stores for a block: Adler-32 of the bytes as they sit in the file. */
private fun adler(bytes: ByteArray): Long = Adler32().also { it.update(bytes) }.value

/** Packs [image] as a `.gcz`: a block is deflated when that is smaller, else stored (bit 63 of its offset). */
internal fun gczOf(image: ByteArray, blockSize: Int = 0x8000): ByteArray {
    val blocks = (image.size + blockSize - 1) / blockSize
    val stored = ArrayList<ByteArray>()
    val flags = ArrayList<Boolean>()
    for (n in 0 until blocks) {
        val raw = image.copyOfRange(n * blockSize, minOf(image.size, (n + 1) * blockSize)).copyOf(blockSize)
        val packed = deflateZlib(raw)
        if (packed.size < blockSize) {
            stored += packed
            flags += false
        } else {
            stored += raw
            flags += true
        }
    }
    val packedSize = stored.sumOf { it.size }
    val out = ByteArray(32 + blocks * 12 + packedSize)
    byteArrayOf(0x01, 0xC0.toByte(), 0x0B, 0xB1.toByte()).copyInto(out)
    out.putLe(4, 0, 4)
    out.putLe(8, packedSize.toLong(), 8)
    out.putLe(16, image.size.toLong(), 8)
    out.putLe(24, blockSize.toLong(), 4)
    out.putLe(28, blocks.toLong(), 4)
    var offset = 0L
    var at = 32 + blocks * 12
    for (n in 0 until blocks) {
        out.putLe(32 + n * 8, offset or (if (flags[n]) Long.MIN_VALUE else 0), 8)
        out.putLe(32 + blocks * 8 + n * 4, adler(stored[n]), 4)
        stored[n].copyInto(out, at)
        at += stored[n].size
        offset += stored[n].size
    }
    return out
}

private fun deflateZlib(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_SPEED)
    deflater.setInput(data)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    return out.toByteArray()
}

/**
 * Packs a Wii [image] as a `.wbfs` with 512-byte hard-disc sectors and 32 KiB Wii sectors. A Wii sector
 * that is all zeros is left out (table entry 0). [pointOutsideAt] sends that sector's entry past the file's end.
 */
internal fun wbfsOf(image: ByteArray, pointOutsideAt: Int = -1): ByteArray {
    val sectorSize = 0x8000
    val entries = 143_432 * 2
    val firstData = 18 // the table ends at 512 + 0x100 + 2 * 286,864 bytes, before block 18
    val sectors = image.size / sectorSize
    val kept = (0 until sectors).filter { s -> image.copyOfRange(s * sectorSize, (s + 1) * sectorSize).any { it != 0.toByte() } }
    val out = ByteArray((firstData + kept.size) * sectorSize)
    out.put(0, "WBFS")
    out[8] = 9
    out[9] = 15
    out[12] = 1
    val table = 512 + 0x100
    kept.forEachIndexed { i, s ->
        val where = firstData + i
        out[table + s * 2] = (where shr 8).toByte()
        out[table + s * 2 + 1] = where.toByte()
        image.copyInto(out, where * sectorSize, s * sectorSize, (s + 1) * sectorSize)
    }
    if (pointOutsideAt >= 0) {
        out[table + pointOutsideAt * 2] = 0x7F
        out[table + pointOutsideAt * 2 + 1] = 0x00
    }
    check(table + entries * 2 <= firstData * sectorSize)
    return out
}
