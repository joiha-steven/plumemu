// Kotlin rewrite of rcheevos' GameCube and Wii hashes (src/hash/rc_hash_disc.c). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import java.io.IOException
import java.security.MessageDigest

private val U32_MASK = -1L ushr 32

private const val BASE_HEADER_SIZE = 0x2440L
private const val MAX_HEADER_SIZE = 1024L * 1024
private const val DOL_SEGMENTS = 18
private const val DOL_CODE_SEGMENTS = 7

/** Most of a game's executable that is hashed; a DOL is a few megabytes, so more means the header is garbage. */
private const val MAX_EXECUTABLE_BYTES = 256L * 1024 * 1024

private fun notARom(message: String) = DiscFormatException(NotARom, message)

/** Reads from a disc image at absolute offsets; a read that comes up short is an image cut short. */
private class ImageReader(val file: DiscFile, val check: () -> Unit) {
    @Throws(IOException::class)
    fun bytes(offset: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        if (length > 0 && file.readAt(offset, out, 0, length) < length) throw notARom("The image is cut short at byte $offset")
        return out
    }

    @Throws(IOException::class)
    fun u32(offset: Long): Long {
        val b = bytes(offset, 4)
        return ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
    }

    /** Feeds [length] bytes at [offset] into [md] a megabyte at a time. */
    @Throws(IOException::class)
    fun feed(md: MessageDigest, offset: Long, length: Long) {
        var at = offset
        var left = length
        val chunk = ByteArray(minOf(left, 1L shl 20).toInt().coerceAtLeast(1))
        while (left > 0) {
            check()
            val n = minOf(left, chunk.size.toLong()).toInt()
            if (file.readAt(at, chunk, 0, n) < n) throw notARom("The image is cut short at byte $at")
            md.update(chunk, 0, n)
            at += n
            left -= n
        }
    }
}

/**
 * One partition's boot data: its header (with the apploader), then the code and data segments of the
 * main executable (a DOL). [shift] is 0 on GameCube and 2 on Wii, where offsets are in words.
 */
@Throws(IOException::class)
private fun hashPartition(md: MessageDigest, image: ImageReader, partOffset: Long, shift: Int) {
    val apploaderBody = image.u32(partOffset + BASE_HEADER_SIZE + 0x14)
    val apploaderTrailer = image.u32(partOffset + BASE_HEADER_SIZE + 0x18)
    // rc_hash adds these in 32 bits.
    val headerSize = minOf((BASE_HEADER_SIZE + 0x20 + apploaderBody + apploaderTrailer).toUInt().toLong(), MAX_HEADER_SIZE).coerceAtLeast(BASE_HEADER_SIZE)
    val header = image.bytes(partOffset, headerSize.toInt())
    md.update(header)
    fun be32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    val dolOffset = be32(header, 0x420) shl shift
    // 7 code segments and 11 data segments: offsets, then (0x48 bytes on) sizes.
    val table = image.bytes(partOffset + dolOffset, 0xD8)
    var total = 0L
    for (i in 0 until DOL_SEGMENTS) {
        val size = be32(table, 0x90 + i * 4) shl shift
        if (size == 0L) continue
        total += size
        if (total > MAX_EXECUTABLE_BYTES) throw notARom("The executable's segments are implausibly large")
        image.feed(md, partOffset + (be32(table, i * 4) shl shift), size)
    }
}

/** A GameCube disc: the magic word at 0x1C, then the one partition. */
@Throws(IOException::class)
internal fun hashGameCube(file: DiscFile, check: () -> Unit): RaHashResult {
    val image = ImageReader(file, check)
    val magic = if (file.size >= 0x20) image.bytes(0x1C, 4) else ByteArray(4)
    if (!magic.contentEquals(byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D))) throw notARom("Not a Gamecube disc")
    val md = newMd5()
    hashPartition(md, image, 0, 0)
    return RaHashResult.Hashed(md.hex())
}

// --- Wii

private const val WII_HEADER_BYTES = 0x80
private const val WII_REGION_AT = 0x4E000L
private const val WII_PARTITION_TABLE_AT = 0x40000L
private const val CLUSTER_SIZE = 0x7C00
private const val CLUSTER_STRIDE = 0x8000L
private const val MAX_CLUSTERS = 1024L
private const val MAX_PARTITIONS = 16L
private const val UPDATE_PARTITION = 1L

private class Partition(val offset: Long, val type: Long)

/** The partitions listed in the four groups of the partition table. */
@Throws(IOException::class)
private fun wiiPartitions(image: ImageReader): List<Partition> {
    val info = LongArray(8) { image.u32(WII_PARTITION_TABLE_AT + it * 4L) }
    val total = (0 until 8 step 2).sumOf { info[it] }
    if (total == 0L) throw notARom("No partitions found")
    if (total > MAX_PARTITIONS) throw notARom("Too many partitions")
    val partitions = ArrayList<Partition>()
    for (group in 0 until 8 step 2) {
        val table = info[group + 1] shl 2
        for (n in 0 until info[group]) {
            partitions += Partition(image.u32(table + n * 8), image.u32(table + n * 8 + 4))
        }
    }
    return partitions
}

/**
 * A Wii disc: the main header, the region code, then for each partition but the update one its title
 * metadata and either (encrypted) its first 1,024 clusters or (decrypted) its boot data.
 */
@Throws(IOException::class)
private fun hashWiiDisc(file: DiscFile, check: () -> Unit): RaHashResult {
    val image = ImageReader(file, check)
    val md = newMd5()
    val encrypted = image.bytes(0x61, 1)[0].toInt() == 0
    md.update(image.bytes(0, WII_HEADER_BYTES))
    md.update(image.bytes(WII_REGION_AT, 4))
    for (partition in wiiPartitions(image)) {
        check()
        if (partition.type == UPDATE_PARTITION) continue
        val base = partition.offset shl 2
        val tmdSize = minOf(image.u32(base + 0x2A4), CLUSTER_SIZE.toLong()).toInt()
        val tmdOffset = image.u32(base + 0x2A8) shl 2
        md.update(image.bytes(base + tmdOffset, tmdSize))
        val dataOffset = image.u32(base + 0x2B8) shl 2
        val dataSize = image.u32(base + 0x2BC) shl 2
        if (encrypted) {
            val clusters = minOf(dataSize / CLUSTER_STRIDE, MAX_CLUSTERS)
            for (n in 0 until clusters) md.update(image.bytes(dataOffset + n * CLUSTER_STRIDE + 0x400, CLUSTER_SIZE))
        } else {
            hashPartition(md, image, dataOffset, 2)
        }
    }
    return RaHashResult.Hashed(md.hex())
}

private fun roundUp(value: Long, to: Long): Long = (value + to - 1) / to * to

/** WiiWare: the title metadata, then each content (still encrypted), rounded up to the cipher's block. */
@Throws(IOException::class)
private fun hashWiiWare(file: DiscFile, check: () -> Unit): RaHashResult {
    val image = ImageReader(file, check)
    val md = newMd5()
    val certChain = roundUp(image.u32(0x08), 0x40)
    val ticket = roundUp(image.u32(0x10), 0x40)
    val tmdSize = minOf(roundUp(image.u32(0x14), 0x40), MAX_HASH_BYTES)
    val tmdStart = 0x40 + certChain + ticket
    image.feed(md, tmdStart, tmdSize)
    val countBytes = image.bytes(tmdStart + 0x1DE, 2)
    val contentCount = ((countBytes[0].toInt() and 0xFF) shl 8) or (countBytes[1].toInt() and 0xFF)
    var contentAddress = tmdStart + tmdSize
    for (n in 0 until contentCount) {
        check()
        val entry = tmdStart + 0x1E4 + 8 + n * 0x24L
        // A size above 4 GiB (high word not zero) is hashed as far as the limit.
        // rc_hash rounds in 32 bits.
        val contentSize = if (image.u32(entry) == 0L) roundUp(image.u32(entry + 4), 0x10) and U32_MASK else MAX_HASH_BYTES
        image.feed(md, contentAddress, minOf(contentSize, MAX_HASH_BYTES))
        contentAddress = roundUp(contentAddress + contentSize, 0x40)
    }
    return RaHashResult.Hashed(md.hex())
}

/** A Wii disc by the magic word at 0x18, or a WiiWare package by `Is` at 4. */
@Throws(IOException::class)
internal fun hashWii(file: DiscFile, check: () -> Unit): RaHashResult {
    val image = ImageReader(file, check)
    val disc = if (file.size >= 0x1C) image.bytes(0x18, 4) else ByteArray(4)
    if (disc.contentEquals(byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()))) return hashWiiDisc(file, check)
    val ware = if (file.size >= 8) image.bytes(4, 4) else ByteArray(4)
    if (ware.contentEquals(byteArrayOf('I'.code.toByte(), 's'.code.toByte(), 0, 0))) return hashWiiWare(file, check)
    throw notARom("Not a supported Wii file")
}
