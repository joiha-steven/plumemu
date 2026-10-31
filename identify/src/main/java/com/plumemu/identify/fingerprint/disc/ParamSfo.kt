package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u16le
import com.plumemu.identify.fingerprint.u32le
import java.util.Locale

/**
 * PSP `PARAM.SFO` (psdevwiki, "PARAM.SFO"), little-endian: magic "\0PSF" at 0, version at 4, key
 * table offset at 8, data table offset at 12, entry count at 16, then 16-byte index entries from
 * 20 (key offset u16, data format u16, data length u32, data maximum length u32, data offset
 * u32). Keys are NUL-terminated ASCII; formats 0x0004 and 0x0204 are UTF-8 strings, 0x0404 is an
 * integer (not kept). Every offset is checked against the block; a bad entry is skipped.
 */
internal object ParamSfo {
    private val MAGIC = byteArrayOf(0, 'P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte())
    private const val INDEX = 20
    private const val ENTRY = 16
    private const val MAX_ENTRIES = 256
    private const val UTF8_SPECIAL = 0x0004
    private const val UTF8 = 0x0204

    /** The string entries by key, or null when [bytes] is not a PARAM.SFO. */
    fun parse(bytes: ByteArray): Map<String, String>? {
        if (bytes.size < INDEX || !bytes.startsWith(MAGIC)) return null
        val keyTable = bytes.u32le(8)
        val dataTable = bytes.u32le(12)
        val count = bytes.u32le(16)
        if (count > MAX_ENTRIES || INDEX + count * ENTRY > bytes.size) return null
        val out = LinkedHashMap<String, String>()
        for (i in 0 until count.toInt()) {
            val at = INDEX + i * ENTRY
            val format = bytes.u16le(at + 2)
            if (format != UTF8 && format != UTF8_SPECIAL) continue
            val key = cString(bytes, keyTable + bytes.u16le(at)) ?: continue
            val start = dataTable + bytes.u32le(at + 12)
            val length = minOf(bytes.u32le(at + 4), bytes.u32le(at + 8))
            if (start < 0 || length < 0 || start + length > bytes.size) continue
            val value = String(bytes, start.toInt(), length.toInt(), Charsets.UTF_8).substringBefore('\u0000')
            out[key] = value
        }
        return out
    }

    private fun cString(bytes: ByteArray, at: Long): String? {
        if (at < 0 || at >= bytes.size) return null
        var end = at.toInt()
        while (end < bytes.size && bytes[end].toInt() != 0) end++
        if (end == bytes.size) return null
        return String(bytes, at.toInt(), end - at.toInt(), Charsets.US_ASCII).uppercase(Locale.ROOT)
    }
}
