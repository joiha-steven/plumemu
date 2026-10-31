package com.plumemu.platforms

/**
 * The text of a small launch file (a `.psvita` title ID, a `.steam` app id) as people save it:
 * UTF-8 unless a byte order mark says UTF-16 (Notepad's "Unicode" saves FF FE); a UTF-8 byte order
 * mark is dropped, and so are NULs (some tools pad the file or end it with one; an id never holds one).
 */
internal object LaunchFileText {
    fun decode(bytes: ByteArray): String {
        val text = when {
            bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }
        return text.replace(Char(0).toString(), "")
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
