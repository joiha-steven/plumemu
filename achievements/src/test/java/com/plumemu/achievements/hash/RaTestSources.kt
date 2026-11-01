package com.plumemu.achievements.hash

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A ROM held in memory. [key] defaults to the file name; set it to test folder rules. */
internal class MemorySource(
    override val fileName: String,
    private val bytes: ByteArray,
    override val key: String = fileName,
) : RaRomSource {
    override val sizeBytes: Long get() = bytes.size.toLong()
    override fun open(): InputStream = ByteArrayInputStream(bytes)
}

/** A file whose reading fails, as an unplugged SD card does. */
internal class FailingSource(override val fileName: String, override val sizeBytes: Long = 1024) : RaRomSource {
    override val key: String = fileName
    override fun open(): InputStream = throw IOException("simulated: no such device")
}

/** A file that opens but fails halfway. */
internal class BreaksHalfwaySource(override val fileName: String) : RaRomSource {
    override val key: String = fileName
    override val sizeBytes: Long = 200_000
    override fun open(): InputStream = object : InputStream() {
        private var served = 0
        override fun read(): Int = if (served++ < 100_000) 0 else throw IOException("simulated: read error")
    }
}

/** [sizeBytes] zero bytes that are never held in memory, for the 64 MiB limit. */
internal class ZeroStreamSource(override val fileName: String, override val sizeBytes: Long) : RaRomSource {
    override val key: String = fileName
    override fun open(): InputStream = object : InputStream() {
        private var left = sizeBytes
        override fun read(): Int = if (left-- > 0) 0 else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = minOf(left, len.toLong()).toInt()
            java.util.Arrays.fill(b, off, off + n, 0)
            left -= n
            return n
        }
    }
}

/** rcheevos test/hash/hash_data.c `fill_image`: the pseudo-random ROM bytes its expected hashes are of. */
internal fun fillImage(image: ByteArray, from: Int = 0, count: Int = image.size - from) {
    val size = count.toLong()
    var seed = (size xor (size shr 8) xor ((size - 1) * 25387)).toInt()
    var left = count
    var pos = from
    while (left > 0) {
        var n: Int
        val value: Int
        when (seed and 0xFF) {
            0 -> {
                n = ((seed shr 8) and 0x3F) and (left.toLong() and 0x0F).inv().toInt()
                if (n == 0) n = 1
                value = 0
            }
            1 -> { n = ((seed shr 8) and 0x07) + 1; value = (seed shr 16) and 0xFF }
            2 -> { n = ((seed shr 8) and 0x03) + 1; value = ((seed shr 16) and 0xFF) xor 0xFF }
            3 -> { n = ((seed shr 8) and 0x03) + 1; value = ((seed shr 16) and 0xFF) xor 0xA5 }
            4 -> { n = ((seed shr 8) and 0x03) + 1; value = ((seed shr 16) and 0xFF) xor 0xC3 }
            5 -> { n = ((seed shr 8) and 0x03) + 1; value = ((seed shr 16) and 0xFF) xor 0x96 }
            6, 7 -> { n = ((seed shr 8) and 0x03) + 1; value = ((seed shr 16) and 0xFF) xor 0x78 }
            else -> { n = 1; value = ((seed shr 8) xor (seed shr 16)) and 0xFF }
        }
        // The C do/while writes at least one byte, then stops at [n] bytes or the end.
        do {
            image[pos++] = value.toByte()
            left--
        } while (left > 0 && --n > 0)
        seed = (seed * 0x41C64E6D + 12345) and 0x7FFFFFFF
    }
}

/** `generate_generic_file`. */
internal fun genericFile(size: Int): ByteArray = ByteArray(size).also { fillImage(it) }

internal fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
